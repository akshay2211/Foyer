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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.foyerlauncher.foyer.ui.theme.LocalPalette
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * The assistant's state, drawn in the dial clock's language: a ring of thin
 * radial ticks around an empty centre. Monochrome, line-only — the fancy part
 * is motion, not decoration.
 *
 *  - idle       a quiet dial: every fourth tick, a stroked centre dot
 *  - listening  ticks around the ring rise with *real* mic [amplitude]; a wave
 *               travels the circle in quantized steps and thin ripple rings
 *               breathe outward from the centre while you speak
 *  - thinking   the ring settles and a short arc chases around it in stepped
 *               hops — the loading spinner
 *  - done       the ring closes into one unbroken circle
 *  - error      the dial goes muted and the arc falls to the bottom
 */
@Composable
fun AssistantDial(
    state: GlyphState,
    modifier: Modifier = Modifier,
    amplitude: () -> Float = { 0f },
) {
    val clock by rememberInfiniteTransition(label = "dialClock").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart),
        label = "dialClock",
    )
    // Attack/decay smoothing so the ring breathes instead of flickering with
    // every rms packet. Mutated during draw: the canvas redraws every frame
    // anyway while the clock runs.
    val smoothed = remember { floatArrayOf(0f) }

    val palette = LocalPalette.current
    Canvas(modifier.semantics { contentDescription = state.announce }) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val radius = minOf(size.width, size.height) / 2f
        val stroke = (radius * 0.045f).coerceIn(2f, 5f)

        val target = amplitude().coerceIn(0f, 1f)
        smoothed[0] += (target - smoothed[0]) * if (target > smoothed[0]) 0.35f else 0.08f
        val amp = smoothed[0]

        val tickInner = radius * 0.64f
        val tickBase = radius * 0.10f
        val tickMax = radius * 0.30f

        fun tick(
            i: Int,
            length: Float,
            color: androidx.compose.ui.graphics.Color,
        ) {
            val a = (i.toFloat() / TICKS) * 2f * PI.toFloat() - PI.toFloat() / 2f
            val dir = Offset(cos(a), sin(a))
            drawLine(
                color = color,
                start = c + dir * tickInner,
                end = c + dir * (tickInner + length),
                strokeWidth = stroke,
                cap = StrokeCap.Butt,
            )
        }

        when (state) {
            GlyphState.IDLE -> {
                for (i in 0 until TICKS step 4) tick(i, tickBase, palette.muted)
                drawCircle(palette.muted, radius * 0.07f, c, style = Stroke(stroke * 0.9f))
            }

            GlyphState.LISTENING -> {
                // A wave travels the ring in quantized steps; mic amplitude sets
                // how far the ticks rise above their resting length.
                for (i in 0 until TICKS) {
                    val phase = (i.toFloat() / TICKS + clock * 2f) % 1f
                    val wave = quantize(0.5f + 0.5f * sin(phase * 2f * PI.toFloat() * 3f), 4)
                    val rise = tickMax * wave * (0.18f + 0.82f * amp)
                    val lit = wave > 0.6f && amp > 0.05f
                    tick(i, tickBase + rise, if (lit) palette.text else palette.muted)
                }

                // The sweep: a short accent arc circling the inside of the ring.
                val sweepStart = clock * 360f - 90f
                drawArc(
                    color = palette.accent,
                    startAngle = sweepStart,
                    sweepAngle = 32f,
                    useCenter = false,
                    topLeft = Offset(c.x - tickInner + stroke * 2, c.y - tickInner + stroke * 2),
                    size = Size((tickInner - stroke * 2) * 2, (tickInner - stroke * 2) * 2),
                    style = Stroke(stroke, cap = StrokeCap.Butt),
                )

                // Ripples: two thin rings breathing out from the centre, visible
                // only while there is voice to carry them.
                for (j in 0 until 2) {
                    val p = (clock * 3f + j * 0.5f) % 1f
                    val alpha = (1f - p) * amp * 0.8f
                    if (alpha > 0.02f) {
                        drawCircle(
                            color = palette.text.copy(alpha = alpha),
                            radius = radius * (0.14f + 0.38f * p),
                            center = c,
                            style = Stroke(stroke * 0.8f),
                        )
                    }
                }

                drawCircle(palette.text, radius * (0.055f + 0.05f * amp), c)
            }

            GlyphState.THINKING -> {
                for (i in 0 until TICKS step 2) tick(i, tickBase, palette.muted)
                // The loading spinner: an arc hopping around the dial in
                // discrete steps — metronome, not lava lamp.
                val hop = floor(clock * STEPS) / STEPS
                drawArc(
                    color = palette.accent,
                    startAngle = hop * 360f - 90f,
                    sweepAngle = 70f,
                    useCenter = false,
                    topLeft = Offset(c.x - tickInner, c.y - tickInner),
                    size = Size(tickInner * 2, tickInner * 2),
                    style = Stroke(stroke, cap = StrokeCap.Butt),
                )
                // The centre dot steps between two sizes on the same beat.
                val beat = floor(clock * STEPS).toInt() % 2
                drawCircle(palette.text, radius * if (beat == 0) 0.055f else 0.075f, c)
            }

            GlyphState.DONE -> {
                drawCircle(palette.text, tickInner + tickBase / 2f, c, style = Stroke(stroke))
                drawCircle(palette.accent, radius * 0.07f, c)
            }

            GlyphState.ERROR -> {
                for (i in 0 until TICKS step 4) tick(i, tickBase * 0.7f, palette.muted)
                // The arc has fallen to the bottom of the dial and lies still.
                drawArc(
                    color = palette.muted,
                    startAngle = 70f,
                    sweepAngle = 40f,
                    useCenter = false,
                    topLeft = Offset(c.x - tickInner, c.y - tickInner),
                    size = Size(tickInner * 2, tickInner * 2),
                    style = Stroke(stroke, cap = StrokeCap.Butt),
                )
                drawCircle(palette.muted, radius * 0.06f, c, style = Stroke(stroke * 0.9f))
            }
        }
    }
}

private fun quantize(
    value: Float,
    steps: Int,
): Float = floor(value * steps) / steps

private const val TICKS = 48
private const val STEPS = 12f
