package com.foyerlauncher.foyer.ui.components

import android.content.Context
import android.content.pm.LauncherApps
import android.os.UserManager
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.foyerlauncher.foyer.data.AppEntry
import com.foyerlauncher.foyer.data.key
import com.foyerlauncher.foyer.data.resolveUserHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/** How the drawer's A–Z rail looks (#4). */
enum class FastScrollStyle(
    val label: String,
) {
    INTERACTIVE("icon preview"),
    SIMPLE("simple"),
    ;

    fun next(): FastScrollStyle = entries[(ordinal + 1) % entries.size]
}

/**
 * Icon loading for the drawer's A–Z rail in icon-preview mode (#4). Foyer is
 * text-first — icons exist *nowhere* else in the app. [warmAll] pre-loads
 * every installed app's icon in the background as soon as the drawer opens
 * (icon-preview mode only), so [peek] returns instantly once the finger
 * actually touches the rail; [icon] is the suspend fallback for anything not
 * yet warmed. Profile-aware via LauncherApps.
 */
object AppIconCache {
    // Generous enough to hold virtually any real device's app count without
    // eviction — this cache is only ever populated in icon-preview mode.
    private const val MAX = 800
    private val cache = LinkedHashMap<String, ImageBitmap?>(MAX, 0.75f, true)

    /** Synchronous, no coroutine hop — null only if not yet warmed/fetched. */
    fun peek(app: AppEntry): ImageBitmap? = synchronized(cache) { cache[app.key] }

    /**
     * Drops every cached icon for [packageName] (all activities, all
     * profiles) so the next [peek]/[icon] call re-fetches it. Without this,
     * an app update that changes its icon would show the stale one for the
     * rest of the process's life — warmAll and icon() both skip anything
     * already present via [LinkedHashMap.containsKey], and the cache is now
     * large enough (MAX = 800) that natural LRU eviction rarely reaches it.
     * Call this from LauncherApps.Callback.onPackageChanged/onPackageRemoved.
     */
    fun invalidate(packageName: String) {
        synchronized(cache) { cache.keys.removeAll { it.startsWith("$packageName/") } }
    }

    suspend fun icon(
        context: Context,
        app: AppEntry,
    ): ImageBitmap? =
        withContext(Dispatchers.IO) {
            synchronized(cache) { if (cache.containsKey(app.key)) return@withContext cache[app.key] }
            val bitmap = loadIcon(context, app)
            synchronized(cache) {
                cache[app.key] = bitmap
                if (cache.size > MAX) cache.remove(cache.keys.first())
            }
            bitmap
        }

    /** Loads every app not already cached, one background dispatch for the whole batch. */
    suspend fun warmAll(
        context: Context,
        apps: List<AppEntry>,
    ) = withContext(Dispatchers.IO) {
        for (app in apps) {
            if (!isActive) return@withContext
            val cached = synchronized(cache) { cache.containsKey(app.key) }
            if (cached) continue
            val bitmap = loadIcon(context, app)
            synchronized(cache) {
                cache[app.key] = bitmap
                if (cache.size > MAX) cache.remove(cache.keys.first())
            }
        }
    }

    private fun loadIcon(
        context: Context,
        app: AppEntry,
    ): ImageBitmap? =
        runCatching {
            val um = context.getSystemService(Context.USER_SERVICE) as UserManager
            val user = resolveUserHandle(um, app.userSerial) ?: return@runCatching null
            val la = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            val activities = la.getActivityList(app.packageName, user)
            val match = activities.firstOrNull { it.componentName.className == app.activityName }
            if (match == null) {
                Log.w("FoyerIcon", "no activity match for ${app.label} (${app.packageName}/${app.activityName}), found ${activities.size}")
            }
            match
                ?.getIcon(0)
                ?.toBitmap(width = 96, height = 96)
                ?.asImageBitmap()
        }.onFailure { Log.w("FoyerIcon", "load failed for ${app.label}: $it") }.getOrNull()
}
