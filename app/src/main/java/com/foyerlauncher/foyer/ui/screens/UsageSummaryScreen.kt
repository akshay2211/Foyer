package com.foyerlauncher.foyer.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foyerlauncher.foyer.R
import com.foyerlauncher.foyer.data.AppEntry
import com.foyerlauncher.foyer.data.LauncherStore
import com.foyerlauncher.foyer.data.UsageTracker
import com.foyerlauncher.foyer.data.formatDuration
import com.foyerlauncher.foyer.ui.components.IconAction
import com.foyerlauncher.foyer.ui.theme.LocalFontFamily
import com.foyerlauncher.foyer.ui.theme.LocalPalette
import com.foyerlauncher.foyer.ui.theme.tracked
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class UsageSummary(
    val todayMs: Long,
    val unlocks: Int,
    val topApps: List<Triple<String, Long, Boolean>>, // label, ms, distracting
    val days: List<Triple<String, Long, Boolean>>, // weekday initial, ms, isToday
    val thisWeekMs: Long,
    val lastWeekMs: Long,
)

/**
 * Wellbeing summary (#12): everything computed on-device from UsageStats the
 * launcher already reads — today's total and per-app breakdown, unlocks, and
 * a 14-day trend rendered as quiet text-first bars. Nothing leaves the phone.
 */
@Composable
fun UsageSummaryScreen(
    apps: List<AppEntry>,
    store: LauncherStore,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val palette = LocalPalette.current
    val ff = LocalFontFamily.current
    BackHandler { onClose() }

    val labelByPkg = apps.associate { it.packageName to it.label.lowercase() }
    val summary by produceState<UsageSummary?>(initialValue = null) {
        value =
            withContext(Dispatchers.IO) {
                val daily = UsageTracker.dailyTotals(context, 14)
                val dayFmt = SimpleDateFormat("EEEEE", Locale.getDefault()) // single-letter weekday
                UsageSummary(
                    todayMs = UsageTracker.screenTimeTodayMs(context),
                    unlocks = UsageTracker.unlocksToday(context),
                    topApps =
                        UsageTracker.topAppsToday(context).take(10).map { (pkg, ms) ->
                            val label = labelByPkg[pkg] ?: pkg.substringAfterLast('.')
                            Triple(label, ms, store.distracting.contains(pkg))
                        },
                    days =
                        daily.takeLast(7).mapIndexed { i, (start, ms) ->
                            Triple(dayFmt.format(Date(start)).lowercase(), ms, i == 6)
                        },
                    thisWeekMs = daily.takeLast(7).sumOf { it.second },
                    lastWeekMs = daily.take(7).sumOf { it.second },
                )
            }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(palette.background)
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 28.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                "screen time",
                style = TextStyle(color = palette.muted, fontSize = 13.sp, letterSpacing = tracked(2.sp), fontFamily = ff),
            )
            IconAction(
                icon = R.drawable.ic_tabler_x,
                label = "close",
                tint = palette.muted,
                size = 22.dp,
            ) { onClose() }
        }

        val s = summary
        if (s == null) {
            BasicText(
                "reading usage…",
                modifier = Modifier.padding(top = 24.dp),
                style = TextStyle(color = palette.muted, fontSize = 16.sp, fontFamily = ff),
            )
            return@Column
        }

        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            BasicText(
                text = formatDuration(s.todayMs),
                modifier = Modifier.padding(top = 16.dp),
                style = TextStyle(color = palette.text, fontSize = 56.sp, fontWeight = FontWeight.Thin, fontFamily = ff),
            )
            val bypasses = store.bypassesToday()
            BasicText(
                text =
                    "today · ${s.unlocks} unlocks" +
                        if (bypasses > 0) " · $bypasses limit bypass${if (bypasses == 1) "" else "es"}" else "",
                modifier = Modifier.padding(top = 2.dp, bottom = 26.dp),
                style = TextStyle(color = palette.muted, fontSize = 14.sp, letterSpacing = tracked(1.sp), fontFamily = ff),
            )

            // The week, as quiet bars; today in accent.
            val maxDay = (s.days.maxOfOrNull { it.second } ?: 1L).coerceAtLeast(1L)
            s.days.forEach { (initial, ms, isToday) ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicText(
                        text = initial,
                        modifier = Modifier.width(22.dp),
                        style = TextStyle(color = palette.muted, fontSize = 13.sp, fontFamily = ff),
                    )
                    Box(
                        Modifier
                            .weight(1f)
                            .padding(end = 14.dp),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(fraction = (ms.toFloat() / maxDay).coerceIn(0.02f, 1f))
                                .height(3.dp)
                                .background(if (isToday) palette.accent else palette.muted.copy(alpha = 0.45f)),
                        )
                    }
                    BasicText(
                        text = formatDuration(ms),
                        style =
                            TextStyle(
                                color = if (isToday) palette.text else palette.muted,
                                fontSize = 13.sp,
                                fontFamily = ff,
                            ),
                    )
                }
            }
            val delta = s.thisWeekMs - s.lastWeekMs
            val deltaText =
                when {
                    s.lastWeekMs == 0L -> ""
                    delta > 0 -> " · up ${formatDuration(delta)} from last week"
                    else -> " · down ${formatDuration(-delta)} from last week"
                }
            BasicText(
                text = "this week ${formatDuration(s.thisWeekMs)}$deltaText",
                modifier = Modifier.padding(top = 10.dp, bottom = 26.dp),
                style = TextStyle(color = palette.muted, fontSize = 14.sp, fontFamily = ff),
            )

            BasicText(
                text = "today's apps",
                modifier = Modifier.padding(bottom = 6.dp),
                style = TextStyle(color = palette.text, fontSize = 22.sp, fontFamily = ff),
            )
            if (s.topApps.isEmpty()) {
                BasicText(
                    "nothing over a minute yet",
                    style = TextStyle(color = palette.muted, fontSize = 15.sp, fontFamily = ff),
                )
            }
            s.topApps.forEach { (label, ms, distracting) ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BasicText(
                            text = label,
                            style = TextStyle(color = palette.text, fontSize = 17.sp, fontFamily = ff),
                        )
                        if (distracting) {
                            Box(
                                Modifier
                                    .padding(start = 10.dp)
                                    .width(7.dp)
                                    .height(7.dp)
                                    .background(palette.accent),
                            )
                        }
                    }
                    BasicText(
                        text = formatDuration(ms),
                        style = TextStyle(color = palette.muted, fontSize = 15.sp, fontFamily = ff),
                    )
                }
            }
            BasicText(
                text = "■ marked distracting · all data stays on this phone",
                modifier = Modifier.padding(top = 18.dp),
                style = TextStyle(color = palette.muted, fontSize = 11.sp, letterSpacing = tracked(1.sp), fontFamily = ff),
            )
        }
    }
}
