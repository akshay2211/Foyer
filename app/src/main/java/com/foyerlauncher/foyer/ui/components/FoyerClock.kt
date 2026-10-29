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
 * The dial clock (#15), matched to the rotating-dial reference:
 *
 *  - the hour sits large and still in the centre
 *  - two rings of minute numerals (every five) with dotted ticks running
 *    between them on the same circle; the inner ring is the live minutes,
 *    the outer is its phase-shifted echo (the reference's second ring is a
 *    seconds dial — a home screen that animates every second would break
 *    the minute-cadence battery rule, so ours steps with the minutes)
 *  - a fixed lens at the lower right — a circle with a tail line reaching
 *    toward the outer ring — holds the exact current minute
 *  - the rings wind in with a spring on appearance and glide one step per
 *    minute, always clockwise; nothing animates while time stands still
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

    // The lens stays at this screen angle; the rings turn to meet it.
    val bubbleAngle = 28f
    val targetRotation = bubbleAngle - (minute * 6f - 90f)
    val rotation = remember { Animatable(targetRotation + 48f) } // wind in
    LaunchedEffect(targetRotation) {
        var delta = (targetRotation - rotation.value) % 360f
        if (delta > 0f) delta -= 360f // clockwise only
        rotation.animateTo(
            rotation.value + delta,
            spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessLow),
        )
    }

    val measurer = rememberTextMeasurer()
    val numeralStyle = TextStyle(color = palette.muted, fontSize = 10.sp, fontFamily = ff)
    val echoStyle = TextStyle(color = palette.muted.copy(alpha = 0.55f), fontSize = 10.sp, fontFamily = ff)
    val currentStyle = TextStyle(color = palette.text, fontSize = 11.sp, fontFamily = ff)

    val timeText = timeFmt.format(Date(now))
    Box(
        modifier
            .size(204.dp)
            .semantics { contentDescription = "clock, $timeText" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.matchParentSize()) {
            val r = size.minDimension / 2f
            val c = Offset(r, r)
            val rOuter = r * 0.93f // the echo ring
            val rInner = r * 0.64f // the live minutes ring

            /** One ring: numerals every five, fine dots between, on one circle. */
            fun ring(
                radius: Float,
                rot: Float,
                style: TextStyle,
                dotAlpha: Float,
                skipNearBubble: Boolean,
            ) {
                for (m in 0 until 60) {
                    val deg = m * 6f - 90f + rot
                    val a = Math.toRadians(deg.toDouble())
                    val dir = Offset(kotlin.math.cos(a).toFloat(), kotlin.math.sin(a).toFloat())
                    val distToBubble = ((deg - bubbleAngle) % 360f + 540f) % 360f - 180f
                    if (m % 5 == 0) {
                        if (skipNearBubble && kotlin.math.abs(distToBubble) < 10f) continue
                        val layout = measurer.measure("%02d".format(m), style)
                        drawText(
                            layout,
                            topLeft =
                                c + dir * radius -
                                    Offset(layout.size.width / 2f, layout.size.height / 2f),
                        )
                    } else {
                        if (skipNearBubble && kotlin.math.abs(distToBubble) < 8f) continue
                        drawCircle(
                            color = palette.muted.copy(alpha = dotAlpha),
                            radius = r * 0.009f,
                            center = c + dir * radius,
                        )
                    }
                }
            }

            ring(rOuter, rotation.value + 42f, echoStyle, 0.22f, skipNearBubble = false)
            ring(rInner, rotation.value, numeralStyle, 0.35f, skipNearBubble = true)

            // The lens: a circle on the minutes ring with a tail line reaching
            // toward the outer ring, holding the exact current minute.
            val ba = Math.toRadians(bubbleAngle.toDouble())
            val bdir = Offset(kotlin.math.cos(ba).toFloat(), kotlin.math.sin(ba).toFloat())
            val bc = c + bdir * rInner
            val lensR = r * 0.14f
            drawCircle(
                color = palette.accent,
                radius = lensR,
                center = bc,
                style = Stroke(width = r * 0.014f),
            )
            drawLine(
                color = palette.accent,
                start = bc + bdir * lensR,
                end = bc + bdir * (rOuter - rInner - r * 0.04f + lensR),
                strokeWidth = r * 0.014f,
            )
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
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = ff,
                ),
        )
    }
}
