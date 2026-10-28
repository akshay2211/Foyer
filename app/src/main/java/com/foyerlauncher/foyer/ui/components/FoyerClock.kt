package com.foyerlauncher.foyer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foyerlauncher.foyer.ui.theme.LocalFontFamily
import com.foyerlauncher.foyer.ui.theme.LocalPalette
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

/** Which clock the home screen shows (#15). Cycled in settings / home menu. */
enum class ClockStyle(val label: String) {
    DIGITAL("digital"),
    ANALOGUE("analogue"),
    OFF("off"),
    ;

    fun next(): ClockStyle = entries[(ordinal + 1) % entries.size]
}

/**
 * The analogue clock in the wordmark's geometric language (#15):
 *
 *  - hands are long straight thin strokes; the right-angle motif appears only
 *    as a small perpendicular bar at each tip (~8% of the hand — the F's
 *    elbow quoted as a cap, never a bend in the hand itself)
 *  - the hour hand is accent-colored, the minute hand text-colored, so the
 *    two are unmistakable at any time of day
 *  - faceless: no ring, no numerals, just four short ticks at 12/3/6/9 and a
 *    deconstructed gap where a center hub would be
 *  - minute cadence only — no second hand, same cost as the digital clock
 */
@Composable
fun AnalogueClockWidget(modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val ff = LocalFontFamily.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(10_000)
        }
    }
    val cal = remember(now) { Calendar.getInstance().apply { timeInMillis = now } }
    val minutes = cal.get(Calendar.MINUTE)
    val hours = cal.get(Calendar.HOUR)
    val timeFmt = remember { SimpleDateFormat("h:mm", Locale.getDefault()) }
    val dateFmt = remember { SimpleDateFormat("EEEE, MMM d", Locale.getDefault()) }

    Column(modifier = modifier, horizontalAlignment = Alignment.Start) {
        val timeText = timeFmt.format(Date(now))
        Canvas(
            Modifier
                .size(112.dp)
                .semantics { contentDescription = "clock, $timeText" },
        ) {
            val r = size.minDimension / 2f
            val c = Offset(r, r)
            val stroke = r * 0.045f // the wordmark's thin uniform weight

            // Four ticks, the only face furniture.
            for (q in 0 until 4) {
                val dir = when (q) {
                    0 -> Offset(0f, -1f); 1 -> Offset(1f, 0f); 2 -> Offset(0f, 1f); else -> Offset(-1f, 0f)
                }
                drawLine(
                    color = palette.muted.copy(alpha = 0.55f),
                    start = c + dir * (r * 0.90f),
                    end = c + dir * r,
                    strokeWidth = stroke,
                    cap = StrokeCap.Butt,
                )
            }

            fun hand(frac: Float, length: Float, color: Color) {
                val a = Math.toRadians(frac * 360.0 - 90.0)
                val dir = Offset(kotlin.math.cos(a).toFloat(), kotlin.math.sin(a).toFloat())
                val from = c + dir * (r * 0.14f) // deconstructed: no hub, a gap
                val to = c + dir * length
                drawLine(color, from, to, strokeWidth = stroke, cap = StrokeCap.Butt)
                // The F-elbow tip: a small perpendicular bar, ~8% of the hand.
                val perp = Offset(-dir.y, dir.x)
                val half = length * 0.08f / 2f
                drawLine(color, to - perp * half, to + perp * half, strokeWidth = stroke, cap = StrokeCap.Butt)
            }

            hand(minutes / 60f, r * 0.84f, palette.text)
            hand((hours + minutes / 60f) / 12f, r * 0.54f, palette.accent)
        }
        BasicText(
            text = dateFmt.format(Date(now)).lowercase(),
            modifier = Modifier.padding(top = 10.dp),
            style = TextStyle(color = palette.muted, fontSize = 14.sp, letterSpacing = 1.sp, fontFamily = ff),
        )
    }
}

