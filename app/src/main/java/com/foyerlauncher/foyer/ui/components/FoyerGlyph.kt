package com.foyerlauncher.foyer.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.foyerlauncher.foyer.ui.theme.LocalPalette
import kotlin.math.floor
import kotlin.random.Random

/**
 * What the assistant is doing, as the glyph shows it. [announce] doubles as
 * the TalkBack description so the indicator is never the only channel (#10).
 */
enum class GlyphState(
    val announce: String,
) {
    IDLE("assistant ready"),
    LISTENING("listening"),
    THINKING("thinking"),
    DONE("done"),
    ERROR("didn't work"),
}

/**
 * Foyer's AI state indicator (#18): a constructed glyph in the wordmark's
 * parts kit — thin vertical strokes, one horizontal bar, a small circle.
 * Motion is Manhattan only: everything moves along axes and snaps in
 * discrete steps (metronome, not lava lamp). No blob, no glow.
 *
 *  - idle       a quiet static glyph
 *  - listening  strokes equalize with *real* mic [amplitude] (0..1) — they
 *               visibly flatline when the mic is closed
 *  - thinking   a pulse marches bar-to-bar in quantized steps; the top bar
 *               steps across — this same drawing doubles as a loading bar
 *  - done       strokes snap into alignment under a full-width bar
 *  - error      the assembly drops apart and settles low
 */
@Composable
fun FoyerGlyph(
    state: GlyphState,
    modifier: Modifier = Modifier,
    amplitude: () -> Float = { 0f },
) {
    // Seeded jitter: stable per composition, alive but never noisy.
    val jitter = remember { FloatArray(BARS) { Random(it * 31 + 7).nextFloat() * 0.12f - 0.06f } }
    val clock by rememberInfiniteTransition(label = "glyphClock").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
        label = "glyphClock",
    )

    val palette = LocalPalette.current
    Canvas(modifier.semantics { contentDescription = state.announce }) {
        val w = size.width
        val h = size.height
        val stroke = (h * 0.09f).coerceIn(3f, 7f)
        val gap = w / (BARS + 1)
        val baseline = h * 0.92f
        val maxBar = h * 0.78f

        fun barHeight(i: Int): Float =
            when (state) {
                GlyphState.IDLE -> maxBar * (IDLE_PATTERN[i] + jitter[i])
                GlyphState.LISTENING -> {
                    val amp = amplitude().coerceIn(0f, 1f)
                    // Quantized per-bar weight so equal input still reads as strokes,
                    // not a smooth meter; flat floor when the mic is silent/closed.
                    val step = floor(clock * 6f + i * 1.3f).toInt() % 3
                    maxBar * (0.12f + amp * (0.55f + 0.33f * step / 2f + jitter[i]))
                }
                GlyphState.THINKING -> {
                    // A single pulse hops bar to bar in discrete steps (90° motion:
                    // bars only ever grow/shrink vertically, the pulse moves in snaps).
                    val active = floor(clock * BARS * 2).toInt() % BARS
                    maxBar * (if (i == active) 0.72f else 0.26f + jitter[i] / 2f)
                }
                GlyphState.DONE -> maxBar * 0.52f
                GlyphState.ERROR -> maxBar * ERROR_PATTERN[i]
            }

        // The vertical strokes.
        for (i in 0 until BARS) {
            val x = gap * (i + 1)
            drawLine(
                color = if (state == GlyphState.ERROR) palette.muted else palette.text,
                start = Offset(x, baseline),
                end = Offset(x, baseline - barHeight(i)),
                strokeWidth = stroke,
                cap = StrokeCap.Butt,
            )
        }

        // The horizontal bar — the wordmark F's floating elbow.
        val capY =
            when (state) {
                GlyphState.ERROR -> baseline // fallen
                else -> h * 0.08f
            }
        val capHalf =
            when (state) {
                GlyphState.DONE -> (gap * BARS) / 2f // full width: snapped together
                GlyphState.THINKING -> {
                    // Steps across the top in quantized hops, loading-bar style.
                    gap * 0.8f
                }
                else -> gap * 0.8f
            }
        val capCenter =
            when (state) {
                GlyphState.THINKING -> gap * (floor(clock * BARS * 2).toInt() % BARS + 1)
                GlyphState.DONE -> gap * (BARS + 1) / 2f
                GlyphState.ERROR -> gap * 2f
                else -> gap * 1.5f // resting over the first bars, like the F
            }
        drawLine(
            color = if (state == GlyphState.ERROR) palette.muted else palette.accent,
            start = Offset(capCenter - capHalf, capY),
            end = Offset(capCenter + capHalf, capY),
            strokeWidth = stroke,
            cap = StrokeCap.Butt,
        )

        // The circle — the wordmark O, quietly closing the glyph on the right.
        if (state == GlyphState.IDLE || state == GlyphState.DONE) {
            drawCircle(
                color = palette.muted,
                radius = h * 0.09f,
                center = Offset(w - gap * 0.4f, baseline - h * 0.09f),
                style = Stroke(width = stroke * 0.8f),
            )
        }
    }
}

private const val BARS = 5
private val IDLE_PATTERN = floatArrayOf(0.42f, 0.66f, 0.30f, 0.54f, 0.38f)
private val ERROR_PATTERN = floatArrayOf(0.18f, 0.08f, 0.24f, 0.06f, 0.14f)
