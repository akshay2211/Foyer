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
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
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
import kotlin.time.Duration.Companion.milliseconds

/** Which clock the home screen shows (#15). Cycled in settings / home menu. */
enum class ClockStyle(
    val label: String,
) {
    DIGITAL("digital"),
    LOCK("bold"),
    ANALOGUE("dial"),
    MINIMAL("hands"),
    OFF("off"),
    ;

    fun next(): ClockStyle = entries[(ordinal + 1) % entries.size]
}

/**
 * Debug-only clock override for marketing screenshots — when set, every clock
 * widget below renders this instant instead of the real time, so digital,
 * bold, dial and hands all screenshot showing the same chosen time. Null (the
 * default) means real time; only app/src/debug/.../MarketingPreviewActivity
 * ever sets it.
 */
var marketingClockOverrideMillis: Long? = null

private fun clockNowMillis(): Long = marketingClockOverrideMillis ?: System.currentTimeMillis()

/**
 * The dial clock (#15), final form:
 *
 *  - the hour sits large just left of the lens, nested against it on the
 *    horizontal axis — hour and minute read as one tight pair, even though
 *    that puts the digits off the dial's geometric centre (intentional)
 *  - one ring: the live minutes (numerals every five over dotted ticks),
 *    rotating so the current minute rests inside the lens
 *  - the ring winds in with a spring on appearance and glides one step per
 *    minute, always clockwise; nothing animates while time stands still
 */
@Composable
fun AnalogueClockWidget(modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val ff = LocalFontFamily.current
    var now by remember { mutableLongStateOf(clockNowMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = clockNowMillis()
            delay(10_000.milliseconds)
        }
    }
    val cal = remember(now) { Calendar.getInstance().apply { timeInMillis = now } }
    val minute = cal.get(Calendar.MINUTE)
    val hourText = remember(now) { SimpleDateFormat("HH", Locale.getDefault()).format(Date(now)) }
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    // The lens sits on the horizontal, level with the centre hour.
    val bubbleAngle = 0f
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
    val currentStyle =
        TextStyle(
            color = palette.text,
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = ff,
        )

    val timeText = timeFmt.format(Date(now))
    Box(
        modifier
            .size(196.dp)
            .semantics { contentDescription = "clock, $timeText" },
    ) {
        Canvas(Modifier.matchParentSize()) {
            val r = size.minDimension / 2f
            val c = Offset(r, r)
            val rRing = r * 0.86f // the minutes ring

            for (m in 0 until 60) {
                val deg = m * 6f - 90f + rotation.value
                val a = Math.toRadians(deg.toDouble())
                val dir = Offset(kotlin.math.cos(a).toFloat(), kotlin.math.sin(a).toFloat())
                val distToBubble = ((deg - bubbleAngle) % 360f + 540f) % 360f - 180f
                if (m % 5 == 0) {
                    if (kotlin.math.abs(distToBubble) < 14f) continue // the lens owns this spot
                    val layout = measurer.measure("%02d".format(m), numeralStyle)
                    drawText(
                        layout,
                        topLeft =
                            c + dir * rRing -
                                Offset(layout.size.width / 2f, layout.size.height / 2f),
                    )
                } else {
                    if (kotlin.math.abs(distToBubble) < 12f) continue
                    drawCircle(
                        color = palette.muted.copy(alpha = 0.32f),
                        radius = r * 0.009f,
                        center = c + dir * rRing,
                    )
                }
            }

            // The lens: an accent circle on the ring, holding the exact minute —
            // larger than the ring numerals but drawn with a hairline stroke.
            val lensR = r * 0.19f
            val bc = c + Offset(rRing, 0f)
            drawCircle(
                color = palette.accent,
                radius = lensR,
                center = bc,
                style = Stroke(width = r * 0.008f),
            )
            val minuteLayout = measurer.measure("%02d".format(minute), currentStyle)
            drawText(
                minuteLayout,
                topLeft = bc - Offset(minuteLayout.size.width / 2f, minuteLayout.size.height / 2f),
            )

            // The hour, nested just left of the lens on the same horizontal —
            // measured, so the pairing holds in every font choice.
            val hourStyle =
                TextStyle(
                    color = palette.text,
                    fontSize = 46.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = ff,
                )
            val hourLayout = measurer.measure(hourText, hourStyle)
            val hourRight = bc.x - lensR - r * 0.10f // lens edge minus a breath
            drawText(
                hourLayout,
                topLeft = Offset(hourRight - hourLayout.size.width, c.y - hourLayout.size.height / 2f),
            )
        }
    }
}

/**
 * The minimal analogue clock: one hairline circle and two hands, no ticks,
 * no numerals. The hour hand drifts with the minutes so it never lies.
 */
@Composable
fun MinimalClockWidget(modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    var now by remember { mutableLongStateOf(clockNowMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = clockNowMillis()
            delay(10_000.milliseconds)
        }
    }
    val cal = remember(now) { Calendar.getInstance().apply { timeInMillis = now } }
    val minute = cal.get(Calendar.MINUTE)
    val hour = cal.get(Calendar.HOUR)
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val timeText = timeFmt.format(Date(now))

    Box(
        modifier
            .size(172.dp)
            .semantics { contentDescription = "clock, $timeText" },
    ) {
        Canvas(Modifier.matchParentSize()) {
            val r = size.minDimension / 2f
            val c = Offset(r, r)

            drawCircle(
                color = palette.muted.copy(alpha = 0.45f),
                radius = r * 0.96f,
                center = c,
                style = Stroke(width = r * 0.012f),
            )

            fun hand(
                deg: Float,
                length: Float,
                width: Float,
                color: androidx.compose.ui.graphics.Color,
            ) {
                val a = Math.toRadians(deg.toDouble() - 90.0)
                val dir = Offset(kotlin.math.cos(a).toFloat(), kotlin.math.sin(a).toFloat())
                drawLine(
                    color = color,
                    start = c - dir * (r * 0.06f), // a short counterweight past centre
                    end = c + dir * length,
                    strokeWidth = width,
                    cap = StrokeCap.Round,
                )
            }
            hand(deg = (hour + minute / 60f) * 30f, length = r * 0.48f, width = r * 0.045f, color = palette.text)
            hand(deg = minute * 6f, length = r * 0.74f, width = r * 0.028f, color = palette.text)

            drawCircle(color = palette.accent, radius = r * 0.035f, center = c)
        }
    }
}

/** The quiet digital clock: thin h:mm, nothing else. */
@Composable
fun DigitalClockWidget(modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val ff = LocalFontFamily.current
    var now by remember { mutableLongStateOf(clockNowMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = clockNowMillis()
            delay(10_000.milliseconds)
        }
    }
    val timeFmt = remember { SimpleDateFormat("h:mm", Locale.getDefault()) }
    BasicText(
        text = timeFmt.format(Date(now)),
        modifier = modifier,
        style = TextStyle(color = palette.text, fontSize = 60.sp, fontWeight = FontWeight.Thin, fontFamily = ff),
    )
}

/** The lock-screen digital clock: just the time, large and heavy. */
@Composable
fun LockClockWidget(modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val ff = LocalFontFamily.current
    var now by remember { mutableLongStateOf(clockNowMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = clockNowMillis()
            delay(10_000.milliseconds)
        }
    }
    val timeFmt = remember { SimpleDateFormat("h:mm", Locale.getDefault()) }
    BasicText(
        text = timeFmt.format(Date(now)),
        modifier = modifier,
        style =
            TextStyle(
                color = palette.text,
                fontSize = 88.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-2).sp,
                fontFamily = ff,
            ),
    )
}
