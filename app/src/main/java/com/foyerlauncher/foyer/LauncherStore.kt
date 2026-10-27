package com.foyerlauncher.foyer

import android.app.SearchManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.UserManager
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

/** Most favorites we allow pinned on the home screen. */
const val MAX_FAVORITES = 10

/** Schema version of the settings backup JSON (#11). */
const val BACKUP_VERSION = 1

private const val PREFS = "launcher_prefs"
private const val KEY_FAVS = "favorites"
private const val KEY_LEFT = "shortcut_left"
private const val KEY_RIGHT = "shortcut_right"
private const val KEY_CLOCK = "show_clock"
private const val KEY_HIDDEN = "hidden"
private const val KEY_USAGE = "usage"
private const val KEY_DISTRACTING = "distracting"

/** Nudge before opening a distracting app once it's over this much today. */
const val DISTRACTION_THRESHOLD_MS = 20 * 60 * 1000L

// Common time-sinks, pre-marked as distracting (user can unmark any).
private val DEFAULT_DISTRACTING = setOf(
    "com.instagram.android",
    "com.facebook.katana",
    "com.zhiliaoapp.musically", // TikTok
    "com.google.android.youtube",
    "com.twitter.android",
    "com.reddit.frontpage",
    "com.snapchat.android",
    "com.facebook.orca", // Messenger
)
private const val KEY_ONBOARDED = "onboarded"
private const val KEY_ALIASES = "aliases"
private const val KEY_SWIPE_UP = "gesture_swipe_up"
private const val KEY_SWIPE_DOWN = "gesture_swipe_down"
private const val KEY_DOUBLE_TAP = "gesture_double_tap"
private const val KEY_THEME = "theme_mode"
private const val KEY_ACCENT = "accent"
private const val KEY_FONT = "font"
private const val KEY_AI = "ai_enabled"
private const val KEY_BACKEND = "ai_backend"
private const val KEY_MODEL_URL = "gemma_url"
private const val KEY_HF_TOKEN = "hf_token"

// Product decision (2026-10-28): Foyer never ships or hosts a model — users
// bring their own third-party model; we only make setup easy. This is a
// known-good *suggestion* the user can one-tap fill, but fetching it is their
// action under the model's own license (gated: needs the HF license click +
// a classic read token).
const val SUGGESTED_GEMMA_URL =
    "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it-int4.task"

// Qwen repos are UNGATED (Apache 2.0): verified to download with no token,
// no account, no license click — the true zero-friction suggestions.
const val SUGGESTED_QWEN_SMALL_URL =
    "https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct/resolve/main/Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task"
const val SUGGESTED_QWEN_BALANCED_URL =
    "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.task"

/**
 * Stable identity for an app across sessions. Personal-profile apps keep the
 * historical two-part form so pre-existing prefs stay valid; other profiles
 * append their user serial ('/' never appears in package or class names).
 */
val AppEntry.key: String
    get() = if (userSerial == 0L) "$packageName/$activityName" else "$packageName/$activityName/$userSerial"

/** True if a stored [AppEntry.key] belongs to [packageName] in profile [userSerial]. */
private fun keyMatches(key: String, packageName: String, userSerial: Long): Boolean {
    if (!key.startsWith("$packageName/")) return false
    val serialPart = key.substringAfter('/').substringAfter('/', missingDelimiterValue = "")
    return if (userSerial == 0L) serialPart.isEmpty() else serialPart == userSerial.toString()
}

// Field/record separators that will never appear in a package or label.
private const val UNIT_SEP = '\u001F'
private const val REC_SEP = "\u001E"

private fun AppEntry.encode(): String =
    "$label$UNIT_SEP$packageName$UNIT_SEP$activityName" +
        if (userSerial != 0L) "$UNIT_SEP$userSerial" else ""

private fun decodeApp(raw: String?): AppEntry? {
    if (raw.isNullOrBlank()) return null
    val parts = raw.split(UNIT_SEP)
    return when (parts.size) {
        3 -> AppEntry(parts[0], parts[1], parts[2])
        4 -> AppEntry(parts[0], parts[1], parts[2], parts[3].toLongOrNull() ?: 0L)
        else -> null
    }
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

    /** First-run onboarding (pitch + default-launcher prompt) shown exactly once. */
    var onboarded by mutableStateOf(prefs.getBoolean(KEY_ONBOARDED, false))
        private set

    fun markOnboarded() {
        onboarded = true
        prefs.edit { putBoolean(KEY_ONBOARDED, true) }
    }

    var themeMode by mutableStateOf(enumFromPrefs(KEY_THEME, ThemeMode.SYSTEM))
        private set
    var accent by mutableStateOf(enumFromPrefs(KEY_ACCENT, AccentChoice.MONO))
        private set
    var font by mutableStateOf(enumFromPrefs(KEY_FONT, FontChoice.INTER))
        private set

    // Home gestures. Defaults preserve pre-gesture behavior: swipe up opened
    // the browser, swipe down the notification shade; double-tap did nothing.
    var swipeUpAction by mutableStateOf(enumFromPrefs(KEY_SWIPE_UP, GestureAction.BROWSER))
        private set
    var swipeDownAction by mutableStateOf(enumFromPrefs(KEY_SWIPE_DOWN, GestureAction.NOTIFICATIONS))
        private set
    var doubleTapAction by mutableStateOf(enumFromPrefs(KEY_DOUBLE_TAP, GestureAction.NONE))
        private set

    fun cycleSwipeUp() {
        swipeUpAction = swipeUpAction.next()
        prefs.edit { putString(KEY_SWIPE_UP, swipeUpAction.name) }
    }

    fun cycleSwipeDown() {
        swipeDownAction = swipeDownAction.next()
        prefs.edit { putString(KEY_SWIPE_DOWN, swipeDownAction.name) }
    }

    fun cycleDoubleTap() {
        doubleTapAction = doubleTapAction.next()
        prefs.edit { putString(KEY_DOUBLE_TAP, doubleTapAction.name) }
    }

    /** Route "ask me" (voice) through the on-device model. On by default. */
    var aiEnabled by mutableStateOf(prefs.getBoolean(KEY_AI, true))
        private set

    fun toggleAi() {
        aiEnabled = !aiEnabled
        prefs.edit { putBoolean(KEY_AI, aiEnabled) }
    }

    /** Which brain answers: Gemini Nano (AICore) or a downloaded Gemma. */
    var activeBackend by mutableStateOf(enumFromPrefs(KEY_BACKEND, Backend.NANO))
        private set
    // Empty until the user supplies a model URL — Foyer bundles no model.
    var gemmaModelUrl by mutableStateOf(prefs.getString(KEY_MODEL_URL, "") ?: "")
        private set
    // Optional — only needed for gated URLs. Empty for a public/self-hosted model.
    var hfToken by mutableStateOf(prefs.getString(KEY_HF_TOKEN, "") ?: "")
        private set

    var isDownloading by mutableStateOf(false)
        private set
    var downloadedBytes by mutableLongStateOf(0L)
        private set
    var totalBytes by mutableLongStateOf(0L)
        private set
    var downloadError by mutableStateOf<String?>(null)
        private set

    /** null = never run, otherwise the human-readable self-test outcome. */
    var selfTestResult by mutableStateOf<String?>(null)
        private set
    var isSelfTesting by mutableStateOf(false)
        private set

    fun modelDownloaded(): Boolean = gemmaModelFile(context).exists()

    /**
     * Loads the downloaded model and runs a one-word prompt so setup ends with
     * proof the model actually answers, not just that a file arrived (#2).
     */
    suspend fun selfTestModel() {
        if (isSelfTesting || !modelDownloaded()) return
        isSelfTesting = true
        selfTestResult = null
        val engine = GemmaEngine(context, gemmaModelFile(context).path)
        selfTestResult = runCatching {
            if (!engine.prepare()) return@runCatching "model failed to load"
            val reply = engine.generate("Reply with the single word: ready")
            if (reply.isNullOrBlank()) "model loaded but gave no answer" else "model working ✓"
        }.getOrElse { "self-test failed: ${it.message}" }
        engine.close()
        isSelfTesting = false
    }

    fun setBackend(backend: Backend) {
        activeBackend = backend
        prefs.edit { putString(KEY_BACKEND, backend.name) }
    }

    fun setModelUrl(url: String) {
        gemmaModelUrl = url.trim()
        prefs.edit { putString(KEY_MODEL_URL, gemmaModelUrl) }
    }

    fun updateHfToken(token: String) {
        hfToken = token.trim()
        prefs.edit { putString(KEY_HF_TOKEN, hfToken) }
    }

    suspend fun downloadModel() {
        if (isDownloading) return
        if (gemmaModelUrl.isBlank()) {
            downloadError = "paste a model url first"
            return
        }
        // Storage pre-check: the recommended model is ~555 MB; demand headroom
        // so the download can't wedge the phone into a low-storage state.
        val free = android.os.StatFs(context.filesDir.path).availableBytes
        val needed = 700L * 1024 * 1024
        if (free < needed) {
            downloadError = "not enough storage — needs ~700 mb free, you have ${free / 1_000_000} mb"
            return
        }
        isDownloading = true
        downloadError = null
        selfTestResult = null
        downloadedBytes = 0L
        totalBytes = 0L
        val result = ModelDownloader.download(gemmaModelUrl, gemmaModelFile(context), token = hfToken.ifBlank { null }) { d, t ->
            downloadedBytes = d
            totalBytes = t
        }
        isDownloading = false
        result
            .onSuccess {
                setBackend(Backend.GEMMA)
                selfTestModel() // prove it answers before calling setup done
            }
            .onFailure { downloadError = it.message ?: "download failed" }
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
        aliases.clear()
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

    /**
     * User-taught nicknames: alias text (lowercase) → [AppEntry.key]. These are
     * the highest-confidence resolution tier — "bank" → HDFC opens instantly,
     * before fuzzy matching or any model call.
     */
    val aliases: SnapshotStateMap<String, String> = mutableStateMapOf<String, String>().apply {
        (prefs.getString(KEY_ALIASES, "") ?: "")
            .split(REC_SEP)
            .forEach { rec ->
                val p = rec.split(UNIT_SEP)
                if (p.size == 2) put(p[0], p[1])
            }
    }

    fun addAlias(alias: String, app: AppEntry) {
        val a = alias.trim().lowercase()
        if (a.isEmpty()) return
        aliases[a] = app.key
        persistAliases()
    }

    fun removeAlias(alias: String) {
        aliases.remove(alias)
        persistAliases()
    }

    private fun persistAliases() {
        prefs.edit {
            putString(KEY_ALIASES, aliases.entries.joinToString(REC_SEP) { "${it.key}$UNIT_SEP${it.value}" })
        }
    }

    /** Packages the user treats as time-sinks (seeded with common ones). */
    val distracting: SnapshotStateList<String> = mutableStateListOf<String>().apply {
        val stored = prefs.getString(KEY_DISTRACTING, null)
        if (stored == null) {
            addAll(DEFAULT_DISTRACTING)
            prefs.edit { putString(KEY_DISTRACTING, DEFAULT_DISTRACTING.joinToString(REC_SEP)) }
        } else {
            addAll(stored.split(REC_SEP).filter { it.isNotBlank() })
        }
    }

    fun isDistracting(app: AppEntry): Boolean = distracting.contains(app.packageName)

    fun toggleDistracting(app: AppEntry) {
        if (!distracting.remove(app.packageName)) distracting.add(app.packageName)
        prefs.edit { putString(KEY_DISTRACTING, distracting.joinToString(REC_SEP)) }
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

    // ---- Backup (user-controlled file, no server, no accounts; see #11) ----

    /** Everything restorable as versioned JSON. Deliberately excludes the HF token. */
    fun exportBackup(): String = JSONObject().apply {
        put("app", "foyer")
        put("version", BACKUP_VERSION)
        put("favorites", JSONArray(favorites.map { it.encode() }))
        put("hidden", JSONArray(hidden.toList()))
        put("distracting", JSONArray(distracting.toList()))
        put("aliases", JSONObject(aliases.toMap() as Map<*, *>))
        put("showClock", showClock)
        put("theme", themeMode.name)
        put("accent", accent.name)
        put("font", font.name)
        put("aiEnabled", aiEnabled)
        put("backend", activeBackend.name)
        put("modelUrl", gemmaModelUrl)
        put("swipeUp", swipeUpAction.name)
        put("swipeDown", swipeDownAction.name)
        put("doubleTap", doubleTapAction.name)
        leftShortcut?.let { put("leftShortcut", it.encode()) }
        rightShortcut?.let { put("rightShortcut", it.encode()) }
    }.toString(2)

    /** What an import would restore — shown for confirmation before [applyBackup]. */
    data class BackupSummary(
        val favorites: Int,
        val hidden: Int,
        val distracting: Int,
        val aliases: Int,
        internal val json: JSONObject,
    )

    fun parseBackup(raw: String): Result<BackupSummary> = runCatching {
        val o = JSONObject(raw)
        require(o.optString("app") == "foyer") { "not a foyer backup file" }
        require(o.optInt("version", Int.MAX_VALUE) <= BACKUP_VERSION) {
            "backup from a newer foyer — update the app first"
        }
        BackupSummary(
            favorites = o.optJSONArray("favorites")?.length() ?: 0,
            hidden = o.optJSONArray("hidden")?.length() ?: 0,
            distracting = o.optJSONArray("distracting")?.length() ?: 0,
            aliases = o.optJSONObject("aliases")?.length() ?: 0,
            json = o,
        )
    }

    fun applyBackup(summary: BackupSummary) {
        val o = summary.json
        o.optJSONArray("favorites")?.let { arr ->
            favorites.clear()
            (0 until arr.length()).mapNotNull { decodeApp(arr.optString(it)) }.let { favorites.addAll(it) }
            persistFavorites()
        }
        o.optJSONArray("hidden")?.let { arr ->
            hidden.clear()
            (0 until arr.length()).mapNotNull { arr.optString(it).ifBlank { null } }.let { hidden.addAll(it) }
            prefs.edit { putString(KEY_HIDDEN, hidden.joinToString(REC_SEP)) }
        }
        o.optJSONArray("distracting")?.let { arr ->
            distracting.clear()
            (0 until arr.length()).mapNotNull { arr.optString(it).ifBlank { null } }.let { distracting.addAll(it) }
            prefs.edit { putString(KEY_DISTRACTING, distracting.joinToString(REC_SEP)) }
        }
        o.optJSONObject("aliases")?.let { obj ->
            aliases.clear()
            obj.keys().forEach { k -> aliases[k] = obj.optString(k) }
            persistAliases()
        }
        if (o.has("showClock")) {
            showClock = o.optBoolean("showClock", true)
            prefs.edit { putBoolean(KEY_CLOCK, showClock) }
        }
        themeMode = enumOrCurrent(o.optString("theme"), themeMode)
        accent = enumOrCurrent(o.optString("accent"), accent)
        font = enumOrCurrent(o.optString("font"), font)
        swipeUpAction = enumOrCurrent(o.optString("swipeUp"), swipeUpAction)
        swipeDownAction = enumOrCurrent(o.optString("swipeDown"), swipeDownAction)
        doubleTapAction = enumOrCurrent(o.optString("doubleTap"), doubleTapAction)
        if (o.has("aiEnabled")) aiEnabled = o.optBoolean("aiEnabled", aiEnabled)
        activeBackend = enumOrCurrent(o.optString("backend"), activeBackend)
        o.optString("modelUrl").ifBlank { null }?.let { gemmaModelUrl = it }
        decodeApp(o.optString("leftShortcut").ifBlank { null })?.let { leftShortcut = it }
        decodeApp(o.optString("rightShortcut").ifBlank { null })?.let { rightShortcut = it }
        prefs.edit {
            putString(KEY_THEME, themeMode.name)
            putString(KEY_ACCENT, accent.name)
            putString(KEY_FONT, font.name)
            putString(KEY_SWIPE_UP, swipeUpAction.name)
            putString(KEY_SWIPE_DOWN, swipeDownAction.name)
            putString(KEY_DOUBLE_TAP, doubleTapAction.name)
            putBoolean(KEY_AI, aiEnabled)
            putString(KEY_BACKEND, activeBackend.name)
            putString(KEY_MODEL_URL, gemmaModelUrl)
            leftShortcut?.let { putString(KEY_LEFT, it.encode()) }
            rightShortcut?.let { putString(KEY_RIGHT, it.encode()) }
        }
    }

    private inline fun <reified T : Enum<T>> enumOrCurrent(raw: String?, current: T): T =
        if (raw.isNullOrBlank()) current else runCatching { enumValueOf<T>(raw) }.getOrDefault(current)

    /**
     * Drops all stored state for a package that was uninstalled from profile
     * [userSerial], so favorites/hidden/usage don't accumulate ghosts. The
     * package-only distracting list is touched only for the personal profile.
     */
    fun pruneApp(packageName: String, userSerial: Long = 0L) {
        if (favorites.removeAll { it.packageName == packageName && it.userSerial == userSerial }) {
            persistFavorites()
        }
        if (hidden.removeAll { keyMatches(it, packageName, userSerial) }) {
            prefs.edit { putString(KEY_HIDDEN, hidden.joinToString(REC_SEP)) }
        }
        val staleUsage = usage.keys.filter { keyMatches(it, packageName, userSerial) }
        if (staleUsage.isNotEmpty()) {
            staleUsage.forEach { usage.remove(it) }
            prefs.edit {
                putString(KEY_USAGE, usage.entries.joinToString(REC_SEP) { "${it.key}$UNIT_SEP${it.value}" })
            }
        }
        if (userSerial == 0L && distracting.remove(packageName)) {
            prefs.edit { putString(KEY_DISTRACTING, distracting.joinToString(REC_SEP)) }
        }
        val staleAliases = aliases.filterValues { keyMatches(it, packageName, userSerial) }.keys
        if (staleAliases.isNotEmpty()) {
            staleAliases.forEach { aliases.remove(it) }
            persistAliases()
        }
        if (leftShortcut?.packageName == packageName && leftShortcut?.userSerial == userSerial) {
            leftShortcut = resolveAsLauncher(context, Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA), "camera")
            prefs.edit { remove(KEY_LEFT) }
        }
        if (rightShortcut?.packageName == packageName && rightShortcut?.userSerial == userSerial) {
            rightShortcut = resolveAsLauncher(context, Intent(Intent.ACTION_DIAL), "phone")
            prefs.edit { remove(KEY_RIGHT) }
        }
    }
}

enum class ShortcutSide { LEFT, RIGHT }

enum class Backend { NANO, GEMMA }

@Composable
fun rememberLauncherStore(): LauncherStore {
    val context = LocalContext.current.applicationContext
    return remember { LauncherStore(context) }
}

// ---- Intent helpers -------------------------------------------------------

fun launchApp(context: Context, app: AppEntry) {
    // Apps in another profile (work / private space) must be started through
    // LauncherApps so they open as *that* profile's copy.
    if (app.userSerial != 0L) {
        runCatching {
            val um = context.getSystemService(Context.USER_SERVICE) as UserManager
            val user = um.getUserForSerialNumber(app.userSerial) ?: return
            val la = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            la.startMainActivity(ComponentName(app.packageName, app.activityName), user, null, null)
        }
        return
    }
    val intent = Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LAUNCHER)
        .setClassName(app.packageName, app.activityName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    runCatching { context.startActivity(intent) }
}

/**
 * Whether the long-press menu should offer uninstall: personal-profile,
 * non-system apps only (system apps can't be uninstalled, and other profiles'
 * apps are managed from within that profile).
 */
fun canUninstall(context: Context, app: AppEntry): Boolean {
    if (app.userSerial != 0L) return false
    return runCatching {
        val ai = context.packageManager.getApplicationInfo(app.packageName, 0)
        ai.flags and ApplicationInfo.FLAG_SYSTEM == 0
    }.getOrDefault(false)
}

/** Fires the system uninstall confirmation for [app]; the OS does the rest. */
fun requestUninstall(context: Context, app: AppEntry) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_DELETE, Uri.fromParts("package", app.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
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
