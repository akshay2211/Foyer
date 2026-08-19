package io.ak1.testuilauncher

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit

/** Most favorites we allow pinned on the home screen. */
const val MAX_FAVORITES = 10

private const val PREFS = "launcher_prefs"
private const val KEY_FAVS = "favorites"
private const val KEY_LEFT = "shortcut_left"
private const val KEY_RIGHT = "shortcut_right"
private const val KEY_CLOCK = "show_clock"
private const val KEY_HIDDEN = "hidden"
private const val KEY_USAGE = "usage"
private const val KEY_THEME = "theme_mode"
private const val KEY_ACCENT = "accent"
private const val KEY_FONT = "font"
private const val KEY_AI = "ai_enabled"

/** Stable identity for an app across sessions. */
val AppEntry.key: String get() = "$packageName/$activityName"

// Field/record separators that will never appear in a package or label.
private const val UNIT_SEP = '\u001F'
private const val REC_SEP = "\u001E"

private fun AppEntry.encode(): String = "$label$UNIT_SEP$packageName$UNIT_SEP$activityName"

private fun decodeApp(raw: String?): AppEntry? {
    if (raw.isNullOrBlank()) return null
    val parts = raw.split(UNIT_SEP)
    return if (parts.size == 3) AppEntry(parts[0], parts[1], parts[2]) else null
}

/**
 * Resolves the default handler for [probe] to its *launcher* component, so it can
 * be started the same way as any app in the drawer.
 */
private fun resolveAsLauncher(context: Context, probe: Intent, label: String): AppEntry? {
    val pm = context.packageManager
    val pkg = pm.resolveActivity(probe, 0)?.activityInfo?.packageName ?: return null
    if (pkg == "android") return null // system resolver / no concrete handler
    val component = pm.getLaunchIntentForPackage(pkg)?.component ?: return null
    return AppEntry(label, component.packageName, component.className)
}

/** Snapshot-backed launcher settings, persisted to SharedPreferences. */
class LauncherStore(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val favorites: SnapshotStateList<AppEntry> = mutableStateListOf<AppEntry>().apply {
        (prefs.getString(KEY_FAVS, "") ?: "")
            .split(REC_SEP)
            .mapNotNull { decodeApp(it) }
            .let { addAll(it) }
    }

    var showClock by mutableStateOf(prefs.getBoolean(KEY_CLOCK, true))
        private set

    var themeMode by mutableStateOf(enumFromPrefs(KEY_THEME, ThemeMode.SYSTEM))
        private set
    var accent by mutableStateOf(enumFromPrefs(KEY_ACCENT, AccentChoice.MONO))
        private set
    var font by mutableStateOf(enumFromPrefs(KEY_FONT, FontChoice.INTER))
        private set

    /** Route "ask me" (voice) through the on-device model. On by default. */
    var aiEnabled by mutableStateOf(prefs.getBoolean(KEY_AI, true))
        private set

    fun toggleAi() {
        aiEnabled = !aiEnabled
        prefs.edit { putBoolean(KEY_AI, aiEnabled) }
    }

    private inline fun <reified T : Enum<T>> enumFromPrefs(key: String, default: T): T {
        val raw = prefs.getString(key, null) ?: return default
        return runCatching { enumValueOf<T>(raw) }.getOrDefault(default)
    }

    fun cycleTheme() {
        themeMode = themeMode.next()
        prefs.edit { putString(KEY_THEME, themeMode.name) }
    }

    fun chooseAccent(choice: AccentChoice) {
        accent = choice
        prefs.edit { putString(KEY_ACCENT, choice.name) }
    }

    fun chooseFont(choice: FontChoice) {
        font = choice
        prefs.edit { putString(KEY_FONT, choice.name) }
    }

    fun resetAll() {
        prefs.edit { clear() }
        favorites.clear()
        hidden.clear()
        usage.clear()
        showClock = true
        themeMode = ThemeMode.SYSTEM
        accent = AccentChoice.MONO
        font = FontChoice.INTER
        leftShortcut = resolveAsLauncher(context, Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA), "camera")
        rightShortcut = resolveAsLauncher(context, Intent(Intent.ACTION_DIAL), "phone")
    }

    var leftShortcut by mutableStateOf(
        decodeApp(prefs.getString(KEY_LEFT, null))
            ?: resolveAsLauncher(context, Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA), "camera"),
    )
        private set

    var rightShortcut by mutableStateOf(
        decodeApp(prefs.getString(KEY_RIGHT, null))
            ?: resolveAsLauncher(context, Intent(Intent.ACTION_DIAL), "phone"),
    )
        private set

    /** Keys of apps hidden from the default drawer browse list. */
    val hidden: SnapshotStateList<String> = mutableStateListOf<String>().apply {
        (prefs.getString(KEY_HIDDEN, "") ?: "")
            .split(REC_SEP)
            .filter { it.isNotBlank() }
            .let { addAll(it) }
    }

    /** Launch counts keyed by app key, used to float most-used apps. */
    val usage: SnapshotStateMap<String, Int> = mutableStateMapOf<String, Int>().apply {
        (prefs.getString(KEY_USAGE, "") ?: "")
            .split(REC_SEP)
            .forEach { rec ->
                val p = rec.split(UNIT_SEP)
                if (p.size == 2) p[1].toIntOrNull()?.let { put(p[0], it) }
            }
    }

    fun isFavorite(app: AppEntry): Boolean =
        favorites.any { it.packageName == app.packageName && it.activityName == app.activityName }

    fun isHidden(app: AppEntry): Boolean = hidden.contains(app.key)

    fun toggleHidden(app: AppEntry) {
        if (!hidden.remove(app.key)) hidden.add(app.key)
        prefs.edit { putString(KEY_HIDDEN, hidden.joinToString(REC_SEP)) }
    }

    fun usageCount(app: AppEntry): Int = usage[app.key] ?: 0

    fun recordLaunch(app: AppEntry) {
        usage[app.key] = (usage[app.key] ?: 0) + 1
        prefs.edit {
            putString(KEY_USAGE, usage.entries.joinToString(REC_SEP) { "${it.key}$UNIT_SEP${it.value}" })
        }
    }

    fun addFavorite(app: AppEntry) {
        if (isFavorite(app) || favorites.size >= MAX_FAVORITES) return
        favorites.add(app)
        favorites.sortBy { it.label.lowercase() }
        persistFavorites()
    }

    fun removeFavorite(app: AppEntry) {
        favorites.removeAll { it.packageName == app.packageName && it.activityName == app.activityName }
        persistFavorites()
    }

    fun toggleClock() {
        showClock = !showClock
        prefs.edit { putBoolean(KEY_CLOCK, showClock) }
    }

    fun setShortcut(side: ShortcutSide, app: AppEntry) {
        when (side) {
            ShortcutSide.LEFT -> {
                leftShortcut = app
                prefs.edit { putString(KEY_LEFT, app.encode()) }
            }
            ShortcutSide.RIGHT -> {
                rightShortcut = app
                prefs.edit { putString(KEY_RIGHT, app.encode()) }
            }
        }
    }

    private fun persistFavorites() {
        prefs.edit { putString(KEY_FAVS, favorites.joinToString(REC_SEP) { it.encode() }) }
    }
}

enum class ShortcutSide { LEFT, RIGHT }

@Composable
fun rememberLauncherStore(): LauncherStore {
    val context = LocalContext.current.applicationContext
    return remember { LauncherStore(context) }
}

// ---- Intent helpers -------------------------------------------------------

fun launchApp(context: Context, app: AppEntry) {
    val intent = Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LAUNCHER)
        .setClassName(app.packageName, app.activityName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    runCatching { context.startActivity(intent) }
}

fun openSettings(context: Context) {
    runCatching {
        context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

fun openBrowser(context: Context) {
    val browser = Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_APP_BROWSER)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(browser) }.onFailure {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}

fun openClockApp(context: Context) {
    runCatching {
        context.startActivity(
            Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

fun openAppInfo(context: Context, packageName: String) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

fun webSearch(context: Context, query: String) {
    val q = query.trim()
    val search = Intent(Intent.ACTION_WEB_SEARCH)
        .putExtra(SearchManager.QUERY, q)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(search) }.onFailure {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(q)))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}

fun playSearch(context: Context, query: String) {
    val q = Uri.encode(query.trim())
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=$q"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.onFailure {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/search?q=$q"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}

/** Pulls down the system notification shade (uses the hidden StatusBarManager API). */
fun expandNotifications(context: Context) {
    runCatching {
        val service = context.getSystemService("statusbar")
        val cls = Class.forName("android.app.StatusBarManager")
        cls.getMethod("expandNotificationsPanel").invoke(service)
    }
}
