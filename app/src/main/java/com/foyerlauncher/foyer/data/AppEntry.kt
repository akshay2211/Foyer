package com.foyerlauncher.foyer.data

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Build
import android.os.Process
import android.os.UserHandle
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
    return um.userProfiles
        .flatMap { user ->
            val serial = if (user == me) 0L else um.getSerialNumberForUser(user)
            // -1 is Android's documented sentinel for "no such user" — a
            // profile mid-removal can still show up in userProfiles for one
            // query while serial lookups already return this. Treat it like
            // a locked/quiet profile (no apps this pass) rather than caching
            // an AppEntry keyed on an unresolvable serial, which favorites/
            // aliases/icon lookups would never be able to match again.
            if (serial < 0L) return@flatMap emptyList()
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
        }.sortedWith(compareBy({ if (sectionOf(it.label) == '#') 1 else 0 }, { it.label.lowercase() }))
}

/**
 * 'A'–'Z' if [label] starts with a letter, otherwise '#' — the drawer A–Z
 * rail's overflow bucket for digits, symbols, and non-Latin scripts. The
 * single source of truth for this classification: the sort order above and
 * the rail's own section lookups (LauncherScreen.kt) both call this, so they
 * can't silently drift apart the way two independently-written copies of the
 * same rule eventually do. The sort puts '#' labels *after* Z on purpose —
 * plain ASCII order would put digits before letters, scattering them at the
 * list's start while the rail shows '#' last, so dragging to '#' would jump
 * to the top instead of wherever those apps actually are.
 */
fun sectionOf(label: String): Char {
    val c = label.firstOrNull()?.uppercaseChar() ?: return '#'
    return if (c in 'A'..'Z') c else '#'
}

/**
 * Resolves a stored [AppEntry.userSerial] back to the real profile it names
 * — null if that profile no longer exists (removed, or mid-removal). Shared
 * by every call site that needs a [UserHandle] for a [LauncherApps] call
 * (launching an app, loading its icon): each previously reimplemented this
 * same "0L means the personal profile, otherwise look up the serial" check.
 */
fun resolveUserHandle(
    um: UserManager,
    userSerial: Long,
): UserHandle? = if (userSerial == 0L) Process.myUserHandle() else um.getUserForSerialNumber(userSerial)
