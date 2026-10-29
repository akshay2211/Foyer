package com.foyerlauncher.foyer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.foyerlauncher.foyer.ui.theme.LocalPalette
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Which clock the home screen shows (#15). Cycled in settings / home menu. */
enum class ClockStyle(
    val label: String,
) {
    DIGITAL("digital"),
    ANALOGUE("analogue"),
    OFF("off"),
    ;

    fun next(): ClockStyle = entries[(ordinal + 1) % entries.size]
}

/**
 * The analogue clock (#15), v2: reads as a clock at first glance.
 *
 *  - a full ring of twelve ticks — the cardinals longer and brighter — gives
 *    the face its geometry without numerals or an outline
 *  - hands are rounded and told apart by length *and* weight (hour short and
 *    heavy, minute long and light), so the reading survives any accent choice
 *  - the only color is the hub: a small accent dot where the hands meet
 *  - minute cadence only — no second hand, same cost as the digital clock
 */
@Composable
fun AnalogueClockWidget(modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
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

    val timeText = timeFmt.format(Date(now))
    Canvas(
        modifier
            .size(148.dp)
            .semantics { contentDescription = "clock, $timeText" },
    ) {
        val r = size.minDimension / 2f
        val c = Offset(r, r)

        // Twelve ticks; the cardinals carry more weight.
        for (i in 0 until 12) {
            val a = Math.toRadians(i * 30.0 - 90.0)
            val dir = Offset(kotlin.math.cos(a).toFloat(), kotlin.math.sin(a).toFloat())
            val cardinal = i % 3 == 0
            val len = r * if (cardinal) 0.11f else 0.05f
            drawLine(
                color = palette.muted.copy(alpha = if (cardinal) 0.9f else 0.38f),
                start = c + dir * (r - len),
                end = c + dir * r,
                strokeWidth = r * if (cardinal) 0.045f else 0.028f,
                cap = StrokeCap.Round,
            )
        }

        fun hand(
            frac: Float,
            length: Float,
            width: Float,
        ) {
            val a = Math.toRadians(frac * 360.0 - 90.0)
            val dir = Offset(kotlin.math.cos(a).toFloat(), kotlin.math.sin(a).toFloat())
            drawLine(
                color = palette.text,
                start = c - dir * (r * 0.06f), // a short tail through the hub
                end = c + dir * length,
                strokeWidth = width,
                cap = StrokeCap.Round,
            )
        }

        hand((hours + minutes / 60f) / 12f, r * 0.50f, r * 0.085f) // hour: short, heavy
        hand(minutes / 60f, r * 0.76f, r * 0.05f) // minute: long, light
        drawCircle(color = palette.accent, radius = r * 0.055f, center = c)
    }
}
