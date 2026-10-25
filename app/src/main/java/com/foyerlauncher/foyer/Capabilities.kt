package com.foyerlauncher.foyer

/**
 * The capability catalog — the single source of truth for what the on-device
 * model is allowed to do. Every entry is a *typed template*: the model only
 * picks an `action` id and fills the declared slots; it never authors a raw
 * Android Intent. Construction, validation and safety all live in Kotlin
 * ([CommandRouter]), so a hallucinated or malformed reply can at worst fail to
 * build — never fire something arbitrary.
 *
 * This list generates:
 *  - the "allowed actions" block in the prompts, and
 *  - the few-shot examples,
 * so adding a capability is a one-line change instead of editing four places.
 */
data class Capability(
    val id: String,
    /** The JSON shape shown to the model. */
    val schema: String,
    /** Few-shot pairs: user utterance -> the JSON the model should emit. */
    val shots: List<Pair<String, String>> = emptyList(),
)

val CAPABILITIES: List<Capability> = listOf(
    Capability(
        "open_app", """{"action":"open_app","app":"<installed app name>"}""",
        listOf("open camera" to """{"action":"open_app","app":"Camera"}"""),
    ),
    Capability("alarm", """{"action":"alarm","hour":<0-23>,"minute":<0-59>}"""),
    Capability("timer", """{"action":"timer","seconds":<int>,"label":"<optional>"}"""),
    Capability("call", """{"action":"call","number":"<digits>"}"""),
    Capability(
        "setting", """{"action":"setting","key":"${ActionRegistry.keyList()}"}""",
        listOf("where can i enable usb debugging" to """{"action":"setting","key":"developer"}"""),
    ),
    Capability(
        "message", """{"action":"message","app":"whatsapp|sms","to":"<contact or number>","text":"<the message you write>"}""",
        listOf(
            "whatsapp mom that i'll be home late" to
                """{"action":"message","app":"whatsapp","to":"mom","text":"Hi mom, I'll be home late tonight."}""",
            // Teaches that "open <app> and message/send … to <contact>" is ONE message action.
            "open whatsapp and send hello to Mitali" to
                """{"action":"message","app":"whatsapp","to":"Mitali","text":"Hello"}""",
            "open whatsapp and message John i'm on my way" to
                """{"action":"message","app":"whatsapp","to":"John","text":"I'm on my way."}""",
        ),
    ),
    Capability("email", """{"action":"email","to":"<email or name>","subject":"<subject>","text":"<body you write>"}"""),
    Capability(
        "navigate", """{"action":"navigate","to":"<place>"}""",
        listOf("navigate to the airport" to """{"action":"navigate","to":"airport"}"""),
    ),
    Capability("map", """{"action":"map","query":"<place or category>"}"""),
    Capability(
        "play", """{"action":"play","query":"<song or artist>"}""",
        listOf("play despacito" to """{"action":"play","query":"despacito"}"""),
    ),
    Capability("capture", """{"action":"capture","kind":"photo|video"}"""),
    Capability("event", """{"action":"event","title":"<title>","when":"<optional natural time>"}"""),
    Capability("note", """{"action":"note","text":"<content>"}"""),
    Capability("web", """{"action":"web","query":"<text>"}"""),
    Capability(
        "answer", """{"action":"answer","text":"<short answer>"}""",
        listOf("what is 24 times 7" to """{"action":"answer","text":"168"}"""),
    ),
    Capability(
        "find_apps", """{"action":"find_apps","query":"<category or keyword, e.g. music|games|video>"}""",
        listOf(
            "how many music apps do i have" to """{"action":"find_apps","query":"music"}""",
            "list all my games" to """{"action":"find_apps","query":"games"}""",
        ),
    ),
)

/** The allowed-actions block for the prompt, generated from the catalog. */
fun allowedActionsBlock(): String = CAPABILITIES.joinToString("\n") { it.schema }

/**
 * The few-shot block, generated from the catalog. Uses a flat `"input" -> json`
 * form (not a User:/Assistant: transcript) so a small model demonstrates the
 * mapping without being taught to role-play extra conversation turns.
 */
fun fewShotBlock(): String =
    CAPABILITIES.flatMap { it.shots }.joinToString("\n") { (user, json) -> "\"$user\" -> $json" }