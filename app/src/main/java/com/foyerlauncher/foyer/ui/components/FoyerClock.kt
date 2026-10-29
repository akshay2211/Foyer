package com.foyerlauncher.foyer.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foyerlauncher.foyer.ui.theme.LocalFontFamily
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
    ANALOGUE("dial"),
    OFF("off"),
    ;

    fun next(): ClockStyle = entries[(ordinal + 1) % entries.size]
}

/**
 * The dial clock (#15), v3 — after the rotating-dial reference:
 *
 *  - the hour sits large and still in the centre
 *  - around it, a ring of minute numerals (every five) over fine tick dots;
 *    the whole ring *rotates* so the current minute rests inside a fixed
 *    accent-outlined bubble at the lower right
 *  - an inner ring of faint dots gives the dial its depth
 *  - the ring winds in with a spring when the clock appears and glides one
 *    step each minute — always clockwise, never snapping back at :59
 *  - minute cadence only: nothing animates while the time stands still
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
    val minute = cal.get(Calendar.MINUTE)
    val hourText = remember(now) { SimpleDateFormat("HH", Locale.getDefault()).format(Date(now)) }
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    // The bubble sits still at this screen angle; the ring turns to meet it.
    val bubbleAngle = 32f
    val targetRotation = bubbleAngle - (minute * 6f - 90f)
    val rotation = remember { Animatable(targetRotation + 48f) } // wind in
    LaunchedEffect(targetRotation) {
        // Advance clockwise only: the next minute is always -6 degrees away.
        var delta = (targetRotation - rotation.value) % 360f
        if (delta > 0f) delta -= 360f
        if (delta < -180f && rotation.value == targetRotation + 48f) delta += 360f // first wind-in may swing back
        rotation.animateTo(
            rotation.value + delta,
            spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessLow),
        )
    }

    val measurer = rememberTextMeasurer()
    val numeralStyle = TextStyle(color = palette.muted, fontSize = 11.sp, fontFamily = ff)
    val currentStyle = TextStyle(color = palette.text, fontSize = 11.sp, fontFamily = ff)

    val timeText = timeFmt.format(Date(now))
    Box(
        modifier
            .size(188.dp)
            .semantics { contentDescription = "clock, $timeText" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.matchParentSize()) {
            val r = size.minDimension / 2f
            val c = Offset(r, r)
            val rNum = r * 0.86f // the numeral ring
            val rTick = r * 0.70f // fine ticks under it
            val rInner = r * 0.54f // faint texture ring

            for (m in 0 until 60) {
                val deg = m * 6f - 90f + rotation.value
                val a = Math.toRadians(deg.toDouble())
                val dir = Offset(kotlin.math.cos(a).toFloat(), kotlin.math.sin(a).toFloat())

                // fine ticks on both inner rings
                drawCircle(
                    color = palette.muted.copy(alpha = if (m % 5 == 0) 0.55f else 0.25f),
                    radius = r * if (m % 5 == 0) 0.016f else 0.010f,
                    center = c + dir * rTick,
                )
                drawCircle(
                    color = palette.muted.copy(alpha = 0.18f),
                    radius = r * 0.008f,
                    center = c + dir * rInner,
                )

                // numerals every five minutes, upright, riding the ring —
                // except the one currently inside the bubble, which the
                // bubble prints itself (as the exact minute).
                if (m % 5 == 0) {
                    val distToBubble = ((deg - bubbleAngle) % 360f + 540f) % 360f - 180f
                    if (kotlin.math.abs(distToBubble) > 9f) {
                        val layout = measurer.measure("%02d".format(m), numeralStyle)
                        drawText(
                            layout,
                            topLeft =
                                c + dir * rNum -
                                    Offset(layout.size.width / 2f, layout.size.height / 2f),
                        )
                    }
                }
            }

            // the fixed bubble that catches the current minute
            val ba = Math.toRadians(bubbleAngle.toDouble())
            val bdir = Offset(kotlin.math.cos(ba).toFloat(), kotlin.math.sin(ba).toFloat())
            val bc = c + bdir * rNum
            val bw = r * 0.44f
            val bh = r * 0.26f
            drawRoundRect(
                color = palette.accent,
                topLeft = bc - Offset(bw / 2f, bh / 2f),
                size =
                    androidx.compose.ui.geometry
                        .Size(bw, bh),
                cornerRadius =
                    androidx.compose.ui.geometry
                        .CornerRadius(bh / 2f),
                style = Stroke(width = r * 0.018f),
            )
            // the exact minute, printed inside the bubble
            val minuteLayout = measurer.measure("%02d".format(minute), currentStyle)
            drawText(
                minuteLayout,
                topLeft = bc - Offset(minuteLayout.size.width / 2f, minuteLayout.size.height / 2f),
            )
        }

        BasicText(
            text = hourText,
            style =
                TextStyle(
                    color = palette.text,
                    fontSize = 52.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = ff,
                ),
        )
    }
}
