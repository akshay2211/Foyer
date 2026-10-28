package com.foyerlauncher.foyer.ui.components

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Process
import android.os.UserManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.foyerlauncher.foyer.data.AppEntry
import com.foyerlauncher.foyer.data.key
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** How the drawer's A–Z rail looks (#4). */
enum class FastScrollStyle(val label: String) {
    INTERACTIVE("icon preview"),
    SIMPLE("simple"),
    ;

    fun next(): FastScrollStyle = entries[(ordinal + 1) % entries.size]
}

/**
 * Lazy, scrub-time-only icon loading (#4). Foyer is text-first — icons exist
 * *nowhere* else, so they're fetched on demand while the finger is on the
 * rail and kept in a small in-memory cache. Profile-aware via LauncherApps.
 */
object AppIconCache {
    private const val MAX = 64
    private val cache = LinkedHashMap<String, ImageBitmap?>(MAX, 0.75f, true)

    suspend fun icon(context: Context, app: AppEntry): ImageBitmap? = withContext(Dispatchers.IO) {
        synchronized(cache) { if (cache.containsKey(app.key)) return@withContext cache[app.key] }
        val bitmap = runCatching {
            val um = context.getSystemService(Context.USER_SERVICE) as UserManager
            val user = if (app.userSerial == 0L) Process.myUserHandle()
            else um.getUserForSerialNumber(app.userSerial) ?: return@runCatching null
            val la = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            la.getActivityList(app.packageName, user)
                .firstOrNull { it.componentName.className == app.activityName }
                ?.getIcon(0)
                ?.toBitmap(width = 96, height = 96)
                ?.asImageBitmap()
        }.getOrNull()
        synchronized(cache) {
            cache[app.key] = bitmap
            if (cache.size > MAX) cache.remove(cache.keys.first())
        }
        bitmap
    }
}
