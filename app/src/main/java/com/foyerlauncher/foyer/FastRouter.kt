package com.foyerlauncher.foyer

/**
 * Tier-0 deterministic router. The obvious ~70% of launcher commands — "open X",
 * "call <number>", "set alarm for 7", "set a 10 minute timer", "turn on wifi" —
 * don't need an LLM. This resolves them in microseconds with no model load, so
 * the model only ever runs for compositional, conversational or ambiguous input.
 *
 * It is deliberately conservative: it fires only on high-confidence matches and
 * bails (returns null) on anything compound ("… and …"), a contact-name call,
 * or an unresolved app — those fall through to the model.
 */
fun fastRoute(query: String, index: AppIndex): Plan? {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return null
    // Anything joining two clauses is a plan — let the model compose it.
    if (" and " in q || "," in q || " then " in q) return null

    // Inventory questions about installed apps ("how many music apps", "list my
    // games") — answered locally, never guessed by the model (SoT §6). Guarded
    // against usage-style questions (those are a future feature).
    if (!q.contains("use") && !q.contains("using") && !q.contains("time") && !q.contains("most")) {
        Regex("^(?:how many|list all|list my|list|which|what|show me)\\s+(.*?)\\bapps?\\b.*$").find(q)?.let { m ->
            return listOf(AiIntent.FindApps(m.groupValues[1].trim().removePrefix("my ").trim()))
        }
    }

    // System/settings actions first — a bare phrase ("developer options"), a
    // "turn on/off X" toggle, or a lightly-verbed request ("enable usb debugging",
    // "take me to wifi settings"). Deterministic: no app-list search, no model call.
    ActionRegistry.settingKey(q)?.let { return listOf(AiIntent.OpenSetting(it)) }
    val settingsPhrase = q
        .removePrefix("turn on ").removePrefix("turn off ").removePrefix("toggle ")
        .removePrefix("enable ").removePrefix("disable ")
        .removePrefix("open ").removePrefix("go to ").removePrefix("show me ").removePrefix("take me to ")
        .trim()
    if (settingsPhrase != q) ActionRegistry.settingKey(settingsPhrase)?.let { return listOf(AiIntent.OpenSetting(it)) }

    Regex("^(?:open|launch|start|run|go to|show)\\s+(?:the\\s+)?(.+?)(?:\\s+app)?$").find(q)?.let { m ->
        val name = m.groupValues[1].trim()
        // "open developer options" / "open wifi settings" resolve to a system action,
        // not an installed app.
        ActionRegistry.settingKey(name)?.let { return listOf(AiIntent.OpenSetting(it)) }
        val r = index.lookup(name)
        // Resolve the app entirely on-device (no model call): confident → open now;
        // ambiguous → carry candidates so the UI asks "did you mean…?"; not installed
        // → say so. Only a blank/degenerate lookup falls through to the model.
        return when {
            r.app == null && r.candidates.isEmpty() && name.isNotBlank() -> listOf(AiIntent.AppNotFound(name))
            r.app == null -> null
            r.confident -> listOf(AiIntent.OpenApp(r.app, true))
            else -> listOf(AiIntent.OpenApp(r.app, false, r.candidates.ifEmpty { listOf(r.app) }))
        }
    }

    Regex("^(?:call|dial)\\s+([0-9+\\-\\s]+)$").find(q)?.let { m ->
        val number = m.groupValues[1].filter { it.isDigit() || it == '+' }
        return if (number.count { it.isDigit() } >= 3) listOf(AiIntent.Call(number)) else null
    }

    Regex("^set (?:an? )?alarm for (\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?$").find(q)?.let { m ->
        var h = m.groupValues[1].toInt()
        val min = m.groupValues[2].toIntOrNull() ?: 0
        when (m.groupValues[3]) {
            "pm" -> if (h < 12) h += 12
            "am" -> if (h == 12) h = 0
        }
        return if (h in 0..23 && min in 0..59) listOf(AiIntent.SetAlarm(h, min)) else null
    }

    Regex("^set (?:a )?timer for (\\d+)\\s*(hour|hr|min|minute|sec|second)s?$").find(q)?.let { m ->
        val n = m.groupValues[1].toInt()
        val secs = when (m.groupValues[2]) {
            "hour", "hr" -> n * 3600
            "min", "minute" -> n * 60
            else -> n
        }
        return if (secs in 1..86_400) listOf(AiIntent.SetTimer(secs, null)) else null
    }

    return null
}