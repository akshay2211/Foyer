package com.foyerlauncher.foyer

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings
import java.util.Calendar

/**
 * Reads real per-app foreground time via UsageStatsManager. Requires the special
 * "usage access" grant (not a normal runtime permission) — the whole point of a
 * minimalist phone: see where the time goes and add friction to the time-sinks.
 */
object UsageTracker {

    fun hasAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        @Suppress("DEPRECATION")
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun openAccessSettings(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    /** Total foreground time across all apps since midnight (≈ screen time). */
    fun screenTimeTodayMs(context: Context): Long =
        todayStats(context).values.sumOf { it }

    /** Foreground time for one package since midnight. */
    fun usageTodayMs(context: Context, packageName: String): Long =
        todayStats(context)[packageName] ?: 0L

    private fun todayStats(context: Context): Map<String, Long> {
        if (!hasAccess(context)) return emptyMap()
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val start = startOfToday()
        val now = System.currentTimeMillis()
        return runCatching {
            usm.queryAndAggregateUsageStats(start, now)
                .mapValues { it.value.totalTimeInForeground }
        }.getOrDefault(emptyMap())
    }

    private fun startOfToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

/** "2h 15m", "45m", or "3m". */
fun formatDuration(ms: Long): String {
    val totalMinutes = ms / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        else -> "${minutes}m"
    }
}
