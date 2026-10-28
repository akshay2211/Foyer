package com.foyerlauncher.foyer

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.foyerlauncher.foyer.data.AppEntry
import com.foyerlauncher.foyer.data.UsageTracker
import com.foyerlauncher.foyer.data.formatDuration

/**
 * The "limit reached" nudge — awareness, not force (design guide §1). Foyer
 * never watches the foreground app: when a limited app is launched, an
 * *inexact* alarm is set for the moment today's remaining budget runs out
 * (no exact-alarm permission, no service). At fire time the receiver
 * re-checks real usage, so leaving the app early means no notification.
 * Hard enforcement stays where it always was: the launch gate.
 */
class LimitNudgeReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val pkg = intent.getStringExtra(EXTRA_PACKAGE) ?: return
        val label = intent.getStringExtra(EXTRA_LABEL) ?: pkg
        val limitMs = intent.getLongExtra(EXTRA_LIMIT, 0L)
        if (limitMs <= 0L) return
        // Only nudge if the limit was genuinely reached (user may have left).
        if (!UsageTracker.hasAccess(context)) {
            Log.d(TAG, "nudge $pkg: skipped, no usage access")
            return
        }
        val used = UsageTracker.usageTodayMs(context, pkg)
        if (used < limitMs) {
            // Fired early (the user left the app before the budget ran out).
            // If usage grew since arming, they're still using it (e.g. resumed
            // via recents, bypassing the launch gate) — re-arm for the new
            // remainder. If usage is unchanged they've stopped: end the chain,
            // never poll an idle app.
            val armedAtUsed = intent.getLongExtra(EXTRA_ARMED_USED, Long.MAX_VALUE)
            if (used > armedAtUsed) {
                Log.d(TAG, "nudge $pkg: early (used=${used}ms < ${limitMs}ms), still active - re-arming")
                schedule(context, pkg, label, limitMs, used)
            } else {
                Log.d(TAG, "nudge $pkg: skipped, used=${used}ms < limit=${limitMs}ms, app idle")
            }
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.limit_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = context.getString(R.string.limit_channel_description) },
        )
        val open =
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE,
            )
        val notification =
            Notification
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_monochrome)
                .setContentTitle("$label · ${formatDuration(limitMs)} reached today")
                .setContentText("you set this limit — a good moment to stop.")
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
        nm.notify(pkg.hashCode(), notification)
        Log.d(TAG, "nudge $pkg: posted (used=${used}ms limit=${limitMs}ms)")
    }

    companion object {
        private const val TAG = "FoyerLimits"
        private const val CHANNEL_ID = "app_limits"
        private const val EXTRA_PACKAGE = "package"
        private const val EXTRA_LABEL = "label"
        private const val EXTRA_LIMIT = "limit_ms"
        private const val EXTRA_ARMED_USED = "armed_used_ms"

        /**
         * (Re)arms the nudge for [app]: one pending alarm per package, replaced
         * on every launch so the trigger always reflects today's real usage.
         */
        fun schedule(
            context: Context,
            app: AppEntry,
            limitMs: Long,
            usedMs: Long,
        ) = schedule(context, app.packageName, app.label.lowercase(), limitMs, usedMs)

        private fun schedule(
            context: Context,
            packageName: String,
            label: String,
            limitMs: Long,
            usedMs: Long,
        ) {
            val remaining = limitMs - usedMs
            if (remaining <= 0L) return
            val intent =
                Intent(context, LimitNudgeReceiver::class.java)
                    .putExtra(EXTRA_PACKAGE, packageName)
                    .putExtra(EXTRA_LABEL, label)
                    .putExtra(EXTRA_LIMIT, limitMs)
                    .putExtra(EXTRA_ARMED_USED, usedMs)
            val pending =
                PendingIntent.getBroadcast(
                    context,
                    packageName.hashCode(),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            val alarms = context.getSystemService(AlarmManager::class.java) ?: return
            // +20s grace so the fire-time re-check sees usage past the limit;
            // a 3-minute delivery window — it's a nudge, exactness isn't worth
            // the exact-alarm permission.
            alarms.setWindow(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + remaining + 20_000L,
                3 * 60_000L,
                pending,
            )
        }
    }
}
