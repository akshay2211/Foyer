package io.ak1.testuilauncher

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.Settings
import android.util.Log
import org.json.JSONObject

/**
 * A resolved thing the user can run — produced by the AI, rendered by the UI.
 * The launcher does no intent *parsing*; the model decides, this just executes.
 */
enum class CommandKind { ANSWER, ACTION }

data class Command(
    val title: String,
    val subtitle: String?,
    val kind: CommandKind,
    val run: () -> Unit,
)

/**
 * The structured decision the on-device model returns. These are the only
 * "tools" it can pick; everything the model wants to do maps to one of them.
 */
sealed interface AiIntent {
    data class OpenApp(val app: AppEntry) : AiIntent
    data class SetAlarm(val hour: Int, val minute: Int) : AiIntent
    data class Call(val number: String) : AiIntent
    data class OpenSetting(val key: String) : AiIntent
    data class Web(val query: String) : AiIntent
    data class Answer(val text: String) : AiIntent
    data object Unknown : AiIntent
}

/** Parses the model's JSON reply into an [AiIntent]. Lenient by design. */
fun parseIntent(raw: String, apps: List<AppEntry>): AiIntent {
    val cleaned = raw.replace("```json", "").replace("```", "")
    // The model may emit several objects; take the first that maps to a real action.
    val objects = Regex("\\{[^{}]*\\}").findAll(cleaned).map { it.value }.toList()
    for (json in objects) {
        val obj = runCatching { JSONObject(json) }.getOrNull() ?: continue
        val intent = toIntent(obj, apps)
        if (intent != AiIntent.Unknown) return intent
    }
    Log.w(AI_TAG, "parse: no usable action in reply")
    return AiIntent.Unknown
}

private fun toIntent(obj: JSONObject, apps: List<AppEntry>): AiIntent =
    when (obj.optString("action").lowercase()) {
        "open_app" -> {
            val name = obj.optString("app").trim()
            val app = resolveApp(name, apps)
            if (app == null) Log.w(AI_TAG, "parse: open_app '$name' not matched to any app")
            app?.let { AiIntent.OpenApp(it) } ?: AiIntent.Unknown
        }
        "alarm" -> {
            val h = obj.optInt("hour", -1)
            val m = obj.optInt("minute", 0)
            if (h in 0..23 && m in 0..59) AiIntent.SetAlarm(h, m) else AiIntent.Unknown
        }
        "call" -> obj.optString("number").filter { it.isDigit() || it == '+' }
            .takeIf { it.isNotEmpty() }?.let { AiIntent.Call(it) } ?: AiIntent.Unknown
        "setting" -> obj.optString("key").lowercase().takeIf { it.isNotEmpty() }
            ?.let { AiIntent.OpenSetting(it) } ?: AiIntent.Unknown
        "web" -> obj.optString("query").takeIf { it.isNotBlank() }?.let { AiIntent.Web(it) } ?: AiIntent.Unknown
        "answer" -> obj.optString("text").takeIf { it.isNotBlank() }?.let { AiIntent.Answer(it) } ?: AiIntent.Unknown
        else -> AiIntent.Unknown
    }

private fun resolveApp(name: String, apps: List<AppEntry>): AppEntry? {
    if (name.isBlank()) return null
    val n = name.lowercase()
    return apps.firstOrNull { it.label.equals(name, ignoreCase = true) }
        ?: apps.firstOrNull { it.label.lowercase().startsWith(n) }
        ?: apps.firstOrNull { it.label.lowercase().contains(n) }
}

/** Turns an [AiIntent] into a runnable, display-ready [Command]. */
fun AiIntent.toCommand(context: Context): Command = when (this) {
    is AiIntent.OpenApp -> Command("open ${app.label.lowercase()}", "app", CommandKind.ACTION) {
        launchApp(context, app)
    }
    is AiIntent.SetAlarm -> Command("set alarm ${"%d:%02d".format(hour, minute)}", "clock", CommandKind.ACTION) {
        startNewTask(
            context,
            Intent(AlarmClock.ACTION_SET_ALARM)
                .putExtra(AlarmClock.EXTRA_HOUR, hour)
                .putExtra(AlarmClock.EXTRA_MINUTES, minute),
        )
    }
    is AiIntent.Call -> Command("call $number", "dialer", CommandKind.ACTION) {
        startNewTask(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")))
    }
    is AiIntent.OpenSetting -> Command(settingLabel(key), "settings", CommandKind.ACTION) {
        settingAction(key)?.let { startNewTask(context, Intent(it)) }
    }
    is AiIntent.Web -> Command("search web for “$query”", null, CommandKind.ACTION) {
        webSearch(context, query)
    }
    is AiIntent.Answer -> Command(text, null, CommandKind.ANSWER) { }
    AiIntent.Unknown -> Command("didn't get that", null, CommandKind.ANSWER) { }
}

private fun startNewTask(context: Context, intent: Intent) {
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private fun settingAction(key: String): String? = when (key) {
    "wifi", "wi-fi" -> Settings.ACTION_WIFI_SETTINGS
    "bluetooth", "bt" -> Settings.ACTION_BLUETOOTH_SETTINGS
    "airplane" -> Settings.ACTION_AIRPLANE_MODE_SETTINGS
    "battery" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
    "display", "brightness" -> Settings.ACTION_DISPLAY_SETTINGS
    "sound", "volume" -> Settings.ACTION_SOUND_SETTINGS
    "location", "gps" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
    "data" -> Settings.ACTION_DATA_USAGE_SETTINGS
    else -> null
}

private fun settingLabel(key: String): String =
    if (settingAction(key) != null) "$key settings" else "settings"
