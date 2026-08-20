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
    val confirm: Boolean = false,
    val run: () -> Unit,
)

/**
 * The structured decision the on-device model returns. These are the only
 * "tools" it can pick; everything the model wants to do maps to one of them.
 */
sealed interface AiIntent {
    data class OpenApp(val app: AppEntry, val confident: Boolean = true) : AiIntent
    data class SetAlarm(val hour: Int, val minute: Int) : AiIntent
    data class Call(val number: String) : AiIntent
    data class OpenSetting(val key: String) : AiIntent
    data class Web(val query: String) : AiIntent
    data class Message(val app: String, val to: String, val text: String) : AiIntent
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
            val match = resolveApp(name, apps)
            if (match == null) Log.w(AI_TAG, "parse: open_app '$name' not matched to any app")
            match?.let { AiIntent.OpenApp(it.first, it.second) } ?: AiIntent.Unknown
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
        "message", "send_message" -> {
            val to = obj.optString("to").trim()
            val text = obj.optString("text").trim()
            val app = obj.optString("app").lowercase().ifBlank { "whatsapp" }
            if (to.isNotEmpty() && text.isNotEmpty()) AiIntent.Message(app, to, text) else AiIntent.Unknown
        }
        "answer" -> obj.optString("text").takeIf { it.isNotBlank() }?.let { AiIntent.Answer(it) } ?: AiIntent.Unknown
        else -> AiIntent.Unknown
    }

/** Resolves an app name to (app, confident). Confident = exact/prefix/substring;
 *  a fuzzy (edit-distance) hit returns confident=false so the UI can ask. */
private fun resolveApp(name: String, apps: List<AppEntry>): Pair<AppEntry, Boolean>? {
    if (name.isBlank()) return null
    val n = name.lowercase()
    apps.firstOrNull { it.label.equals(name, ignoreCase = true) }?.let { return it to true }
    apps.firstOrNull { it.label.lowercase().startsWith(n) }?.let { return it to true }
    apps.firstOrNull { it.label.lowercase().contains(n) }?.let { return it to true }
    // Fuzzy: closest by edit distance, within a length-scaled threshold.
    val best = apps.minByOrNull { levenshtein(n, it.label.lowercase()) } ?: return null
    val distance = levenshtein(n, best.label.lowercase())
    val threshold = maxOf(2, best.label.length / 3)
    return if (distance <= threshold) best to false else null
}

private fun levenshtein(a: String, b: String): Int {
    if (a == b) return 0
    if (a.isEmpty()) return b.length
    if (b.isEmpty()) return a.length
    var prev = IntArray(b.length + 1) { it }
    var curr = IntArray(b.length + 1)
    for (i in 1..a.length) {
        curr[0] = i
        for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            curr[j] = minOf(prev[j] + 1, curr[j - 1] + 1, prev[j - 1] + cost)
        }
        val tmp = prev; prev = curr; curr = tmp
    }
    return prev[b.length]
}

/** Turns an [AiIntent] into a runnable, display-ready [Command]. */
fun AiIntent.toCommand(context: Context): Command = when (this) {
    is AiIntent.OpenApp -> Command(
        title = "open ${app.label.lowercase()}",
        subtitle = "app",
        kind = CommandKind.ACTION,
        confirm = !confident,
    ) {
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
    is AiIntent.Message -> Command("message $to", "“$text”", CommandKind.ACTION) {
        sendMessage(context, app, to, text)
    }
    is AiIntent.Answer -> Command(text, null, CommandKind.ANSWER) { }
    AiIntent.Unknown -> Command("didn't get that", null, CommandKind.ANSWER) { }
}

private fun startNewTask(context: Context, intent: Intent) {
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/**
 * Opens a chat with [to] and the drafted [text] pre-filled. Android does not
 * allow auto-send, so the user taps send. Resolves a contact name to a number
 * via [resolveContactNumber] (needs READ_CONTACTS); a bare number is used as-is.
 */
private fun sendMessage(context: Context, app: String, to: String, text: String) {
    val digits = to.filter { it.isDigit() || it == '+' }
    val number = if (digits.count { it.isDigit() } >= 5) digits else resolveContactNumber(context, to)
    val wantsWhatsApp = app.contains("whatsapp")
    val whatsAppInstalled = context.packageManager.getLaunchIntentForPackage("com.whatsapp") != null

    if (wantsWhatsApp && whatsAppInstalled) {
        val intent = if (number != null) {
            // Open the exact chat with the message pre-filled.
            val e164 = number.filter { it.isDigit() }
            Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$e164?text=" + Uri.encode(text)))
        } else {
            // No number resolved — hand the draft to WhatsApp's own contact
            // picker instead of dropping to the SMS app.
            Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .setPackage("com.whatsapp")
                .putExtra(Intent.EXTRA_TEXT, text)
        }
        startNewTask(context, intent)
        return
    }
    // SMS (explicit sms request, or WhatsApp not installed).
    val smsUri = Uri.parse("smsto:" + (number ?: ""))
    startNewTask(context, Intent(Intent.ACTION_SENDTO, smsUri).putExtra("sms_body", text))
}

/** Best-effort contact-name -> phone number lookup. Returns null without permission. */
private fun resolveContactNumber(context: Context, name: String): String? = runCatching {
    val uri = android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI
    val projection = arrayOf(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER)
    val selection = "${android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
    context.contentResolver.query(uri, projection, selection, arrayOf("%$name%"), null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }
}.getOrNull()

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
