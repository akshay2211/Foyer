package io.ak1.testuilauncher

/**
 * The central deterministic registry of supported system/settings actions (§5.4).
 * It maps natural-language aliases to a stable settings *key*; the actual Intent
 * for each key (and its device validation / graceful fallback) lives in
 * [settingAction] in CommandRouter, so this file stays free of Android imports
 * and is unit-testable in isolation.
 *
 * The LLM is never asked to produce these — Tier-0 routing resolves them
 * deterministically, so "developer options" opens Developer Options with zero
 * model calls and no app-list search.
 */
object ActionRegistry {

    /** Normalized alias phrase -> settings key. */
    private val ALIASES: Map<String, String> = buildMap {
        putAll("root", "settings", "phone settings", "system settings", "device settings")
        putAll("developer", "developer options", "developer settings", "developer mode", "dev options", "usb debugging")
        putAll("wifi", "wifi", "wi-fi", "wifi settings", "wi-fi settings", "wireless")
        putAll("bluetooth", "bluetooth", "bluetooth settings", "bt")
        putAll("display", "display", "display settings", "brightness", "screen")
        putAll("sound", "sound", "sound settings", "volume", "audio")
        putAll("notification", "notification", "notifications", "notification settings")
        putAll("location", "location", "location settings", "gps")
        putAll("accessibility", "accessibility", "accessibility settings")
        putAll("airplane", "airplane", "airplane mode", "flight mode")
        putAll("battery", "battery", "battery saver", "battery settings")
        putAll("data", "data", "mobile data", "data usage", "cellular")
    }

    /** Resolves a normalized phrase to a settings key, or null if it isn't a system action. */
    fun settingKey(phrase: String): String? = ALIASES[phrase.trim().lowercase()]

    private fun MutableMap<String, String>.putAll(key: String, vararg aliases: String) {
        aliases.forEach { this[it] = key }
    }
}
