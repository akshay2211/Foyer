package com.foyerlauncher.foyer.data

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Build
import android.os.Process
import android.os.UserManager

/**
 * A launchable app: just what we need to show a name and start it. [userSerial]
 * identifies which profile the app lives in: 0 = the launcher's own (personal)
 * profile, otherwise the work/private profile's stable serial number — the same
 * package can be installed in both, and they are distinct entries.
 */
data class AppEntry(
    val label: String,
    val packageName: String,
    val activityName: String,
    val userSerial: Long = 0L,
) {
    /**
     * Android's declared app category (ApplicationInfo.CATEGORY_*), or -1 if
     * unknown/pre-API-26. Kept out of the primary constructor so it stays out of
     * equals/hashCode/copy and the persisted (favorites/shortcuts) encoding —
     * it's populated only on the live list from [loadLaunchableApps].
     */
    var category: Int = -1
}

/**
 * Enumerates launchable apps across *all* profiles (personal + work + private
 * space) via [LauncherApps] — plain PackageManager queries only see the personal
 * profile. Locked/quiet profiles simply don't return activities, so their apps
 * disappear while paused, which is the system-consistent behavior.
 */
fun loadLaunchableApps(context: Context): List<AppEntry> {
    val la = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    val um = context.getSystemService(Context.USER_SERVICE) as UserManager
    val self = context.packageName
    val me = Process.myUserHandle()
    return um.userProfiles.flatMap { user ->
        val serial = if (user == me) 0L else um.getSerialNumberForUser(user)
        la.getActivityList(null, user).mapNotNull { ai ->
            if (ai.applicationInfo.packageName == self) return@mapNotNull null
            val label = ai.label.toString().trim()
            if (label.isEmpty()) return@mapNotNull null
            AppEntry(
                label = label,
                packageName = ai.componentName.packageName,
                activityName = ai.componentName.className,
                userSerial = serial,
            ).also {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) it.category = ai.applicationInfo.category
            }
        }
    }.sortedBy { it.label.lowercase() }
}
