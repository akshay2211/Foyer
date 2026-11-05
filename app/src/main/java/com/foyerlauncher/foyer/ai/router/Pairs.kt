package com.foyerlauncher.foyer.ai.router

import com.foyerlauncher.foyer.data.AppEntry
import org.json.JSONObject

/**
 * An installed app named inside the command claims the whole command ("message
 * mummy hello on whatsapp" is a WhatsApp message, start to finish). Detection
 * is deterministic — the code knows the installed-app list, the model doesn't:
 * a marker phrase ("on/in/via/using <app>") resolved with confidence, or a
 * bare token that exactly equals an installed label. [cleaned] is the command
 * with the mention removed, so the model never sees — and can't mangle — it.
 */
class AppScope(
    val app: AppEntry,
    val cleaned: String,
)

private val SCOPE_MARKER = Regex("\\b(?:on|in|via|using|through)\\s+(\\S+(?:\\s+\\S+){0,2})")

fun appScope(
    query: String,
    index: AppIndex,
): AppScope? {
    val q = query.trim().lowercase().replace(Regex("\\s+"), " ")
    if (q.isEmpty()) return null

    // Marker-based mention: "… on whatsapp" (confident resolution, aliases ok).
    SCOPE_MARKER.findAll(q).forEach { m ->
        val words = m.groupValues[1].split(" ")
        for (len in words.size downTo 1) {
            val name = words.take(len).joinToString(" ")
            val r = index.lookup(name)
            if (r.confident && r.app != null) {
                val span = m.range.first until (m.range.first + m.value.indexOf(name) + name.length)
                return AppScope(r.app, q.removeRange(span).trim().replace(Regex("\\s+"), " "))
            }
        }
    }

    // Bare mention: an n-gram that exactly equals an installed label
    // ("whatsapp mummy hello"). Exact only — "message" must not scope to a
    // "Messages" app.
    val tokens = q.split(" ")
    for (start in tokens.indices) {
        for (len in minOf(3, tokens.size - start) downTo 1) {
            val name = tokens.subList(start, start + len).joinToString(" ")
            val r = index.lookup(name)
            if (r.confident && r.app != null && r.app.label.lowercase() == name) {
                val cleaned = (tokens.subList(0, start) + tokens.subList(start + len, tokens.size)).joinToString(" ").trim()
                return AppScope(r.app, cleaned)
            }
        }
    }
    return null
}

/**
 * Cycle-2 of the LLM pipeline: the model breaks an ACTION input into
 * action→value pairs ("message mom hello on whatsapp" → open→whatsapp,
 * message→mom "hello"); the launcher then maps the pairs onto intents
 * deterministically here — the model names the pairs, the code decides what
 * they execute as. Anything unmappable returns an empty plan so the caller
 * falls back to the full decide prompt (cycle 3).
 */
data class ActionPair(
    val action: String,
    val value: String,
    val text: String = "",
) {
    override fun toString(): String = "$action→$value" + if (text.isBlank()) "" else " (“$text”)"
}

/** Salvages `{"action":…,"value":…}` objects from a noisy model reply. */
fun parsePairs(raw: String): List<ActionPair> {
    val cleaned = raw.replace("```json", "").replace("```", "")
    return Regex("\\{[^{}]*\\}")
        .findAll(cleaned)
        .mapNotNull { runCatching { JSONObject(it.value) }.getOrNull() }
        .mapNotNull { obj ->
            val action = obj.optString("action").trim().lowercase()
            if (action.isEmpty()) {
                null
            } else {
                ActionPair(action, obj.optString("value").trim(), obj.optString("text").trim())
            }
        }.toList()
}

/**
 * Maps pairs to a [Plan]. An "open" pair names the app the whole command is
 * scoped to; when a message/email pair exists it absorbs that open (messaging
 * opens the app itself). Conservative: one unmappable pair empties the plan.
 */
fun planFromPairs(
    pairs: List<ActionPair>,
    index: AppIndex,
    scoped: AppEntry? = null,
): Plan {
    if (pairs.isEmpty()) return emptyList()
    // Always lowercase: `scoped` is a resolved AppEntry whose label the model
    // never sees (it's stripped from the model's input text), so a model
    // emitting its own "open" pair for the same app has no reason to match
    // scoped.label's exact casing — compare both sides the same way or the
    // dedup below silently fails and the open step survives as a duplicate.
    val appScope =
        scoped?.label?.lowercase()
            ?: pairs
                .firstOrNull { it.action == "open" || it.action == "open_app" }
                ?.value
                ?.lowercase()
                .orEmpty()
    val hasComms = pairs.any { it.action == "message" || it.action == "email" }
    val hasCall = pairs.any { it.action == "call" }
    val plan = mutableListOf<AiIntent>()
    for (p in pairs) {
        when (p.action) {
            "open", "open_app" -> {
                if (hasComms && p.value.lowercase() == appScope) continue // messaging opens the app itself
                if (hasCall && p.value.lowercase() in PHONE_APP_WORDS) continue // calling opens the dialer itself
                if (p.value.isBlank()) return emptyList()
                val r = index.lookup(p.value)
                plan +=
                    when {
                        r.app == null -> return emptyList()
                        r.confident -> AiIntent.OpenApp(r.app, true)
                        else -> AiIntent.OpenApp(r.app, false, r.candidates.ifEmpty { listOf(r.app) })
                    }
            }
            "message" -> {
                if (p.value.isBlank() || p.text.isBlank()) return emptyList()
                plan += AiIntent.Message(appScope, p.value, p.text)
            }
            "email" -> {
                if (p.value.isBlank() || p.text.isBlank()) return emptyList()
                plan += AiIntent.Email(p.value, "", p.text)
            }
            "call" -> {
                if (p.value.isBlank()) return emptyList()
                plan += AiIntent.Call(p.value)
            }
            "play" -> {
                if (p.value.isBlank()) return emptyList()
                plan += AiIntent.PlayMedia(p.value)
            }
            "navigate" -> {
                if (p.value.isBlank()) return emptyList()
                plan += AiIntent.Navigate(p.value)
            }
            "web" -> {
                if (p.value.isBlank()) return emptyList()
                plan += AiIntent.Web(p.value)
            }
            "note" -> {
                val body = p.value.ifBlank { p.text }
                if (body.isBlank()) return emptyList()
                plan += AiIntent.CreateNote(body)
            }
            "setting" -> {
                plan += AiIntent.OpenSetting(ActionRegistry.settingKey(p.value) ?: return emptyList())
            }
            "alarm" -> plan += parseAlarmValue(p.value) ?: return emptyList()
            "timer" -> plan += parseTimerValue(p.value) ?: return emptyList()
            else -> return emptyList()
        }
    }
    // A detected scope with no messaging step still means "do it in that app":
    // open it first unless the plan already does — or the step opens it anyway
    // (a call opens the dialer).
    val scopeIsDialer = scoped != null && hasCall && scoped.label.lowercase() in PHONE_APP_WORDS
    if (scoped != null && !hasComms && !scopeIsDialer && plan.none { it is AiIntent.OpenApp && it.app == scoped }) {
        plan.add(0, AiIntent.OpenApp(scoped, confident = true))
    }
    return plan
}

private val PHONE_APP_WORDS = setOf("phone", "dialer", "the phone", "phone app")

private fun parseAlarmValue(value: String): AiIntent? {
    val v = value.lowercase()
    val m = Regex("(\\d{1,2})(?::(\\d{2}))?").find(v) ?: return null
    var h = m.groupValues[1].toInt()
    val min = m.groupValues[2].toIntOrNull() ?: 0
    // Meridiem by word too — the model passes values like "7:00 evening" through.
    val pm = Regex("\\b(?:pm|p\\.m|evening|night|tonight|afternoon)\\b").containsMatchIn(v)
    val am = Regex("\\b(?:am|a\\.m|morning)\\b").containsMatchIn(v)
    when {
        pm -> if (h < 12) h += 12
        am -> if (h == 12) h = 0
    }
    return if (h in 0..23 && min in 0..59) AiIntent.SetAlarm(h, min) else null
}

private fun parseTimerValue(value: String): AiIntent? {
    val m = Regex("(\\d+)\\s*(hour|hr|min|minute|sec|second)?s?").find(value.lowercase()) ?: return null
    val n = m.groupValues[1].toIntOrNull() ?: return null
    val secs =
        when (m.groupValues[2]) {
            "hour", "hr" -> n * 3600
            "", "min", "minute" -> n * 60 // a bare number reads as minutes
            else -> n
        }
    return if (secs in 1..86_400) AiIntent.SetTimer(secs, null) else null
}
