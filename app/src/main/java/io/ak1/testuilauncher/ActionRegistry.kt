package io.ak1.testuilauncher

/** Broad grouping for a system action, for future filtering/retrieval (§12). */
enum class ActionCategory { SYSTEM, CONNECTIVITY, DISPLAY, PRIVACY, DEVELOPER, POWER }

/**
 * One supported deterministic system action. [id] is the stable settings key the
 * executor ([settingAction] in CommandRouter) maps to a public Android Intent.
 */
data class ActionDefinition(
    val id: String,
    val aliases: List<String>,
    val description: String,
    val category: ActionCategory,
)

/**
 * The central deterministic registry of supported system/settings actions (§12).
 * Tier-0 routing resolves these from natural language with zero model calls and
 * no app-list search; the LLM never produces them. Free of Android imports so it
 * stays unit-testable in isolation.
 */
object ActionRegistry {

    val ACTIONS: List<ActionDefinition> = listOf(
        ActionDefinition(
            "root", listOf("settings", "phone settings", "system settings", "device settings"),
            "Open system settings", ActionCategory.SYSTEM,
        ),
        ActionDefinition(
            "developer", listOf("developer options", "developer settings", "developer mode", "dev options", "usb debugging"),
            "Open Developer options", ActionCategory.DEVELOPER,
        ),
        ActionDefinition(
            "wifi", listOf("wifi", "wi-fi", "wifi settings", "wi-fi settings", "wireless"),
            "Open Wi-Fi settings", ActionCategory.CONNECTIVITY,
        ),
        ActionDefinition(
            "bluetooth", listOf("bluetooth", "bluetooth settings", "bt"),
            "Open Bluetooth settings", ActionCategory.CONNECTIVITY,
        ),
        ActionDefinition(
            "airplane", listOf("airplane", "airplane mode", "flight mode"),
            "Open Airplane mode settings", ActionCategory.CONNECTIVITY,
        ),
        ActionDefinition(
            "data", listOf("data", "mobile data", "data usage", "cellular"),
            "Open Data usage settings", ActionCategory.CONNECTIVITY,
        ),
        ActionDefinition(
            "display", listOf("display", "display settings", "brightness", "screen"),
            "Open Display settings", ActionCategory.DISPLAY,
        ),
        ActionDefinition(
            "sound", listOf("sound", "sound settings", "volume", "audio"),
            "Open Sound settings", ActionCategory.DISPLAY,
        ),
        ActionDefinition(
            "notification", listOf("notification", "notifications", "notification settings"),
            "Open Notification settings", ActionCategory.SYSTEM,
        ),
        ActionDefinition(
            "location", listOf("location", "location settings", "gps"),
            "Open Location settings", ActionCategory.PRIVACY,
        ),
        ActionDefinition(
            "accessibility", listOf("accessibility", "accessibility settings"),
            "Open Accessibility settings", ActionCategory.PRIVACY,
        ),
        ActionDefinition(
            "battery", listOf("battery", "battery saver", "battery settings"),
            "Open Battery settings", ActionCategory.POWER,
        ),
    )

    private val byAlias: Map<String, String> =
        buildMap { ACTIONS.forEach { a -> a.aliases.forEach { put(it, a.id) } } }

    /** Resolves a normalized phrase to a settings key, or null if it isn't a system action. */
    fun settingKey(phrase: String): String? = byAlias[phrase.trim().lowercase()]

    /** Looks up the definition for a settings key. */
    fun definition(id: String): ActionDefinition? = ACTIONS.firstOrNull { it.id == id }

    /** Pipe-joined keys for the "setting" action schema in the prompt. */
    fun keyList(): String = ACTIONS.joinToString("|") { it.id }

    /**
     * Compact `key: alias, alias…` reference for grounding the model on a semantic
     * settings request (e.g. mapping "usb debugging" → developer) — SoT §16.
     */
    fun promptReference(): String =
        ACTIONS.joinToString("\n") { a -> "${a.id}: ${a.aliases.joinToString(", ")}" }
}
