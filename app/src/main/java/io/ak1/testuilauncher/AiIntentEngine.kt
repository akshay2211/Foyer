package io.ak1.testuilauncher

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.google.ai.edge.aicore.GenerativeModel
import com.google.ai.edge.aicore.generationConfig

const val AI_TAG = "unAI"

/** Readiness of the on-device model. */
enum class AiStatus { UNKNOWN, PREPARING, READY, UNAVAILABLE }

/** AICore/Gemini Nano needs API 31+; never touch the SDK below that. */
fun aiSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * Thin wrapper over Gemini Nano (AI Edge SDK / AICore). It only *generates* — the
 * decision of what to do is the model's; callers parse the structured reply and
 * execute it. Runs entirely on-device.
 */
class AiIntentEngine(context: Context) {
    private val appContext = context.applicationContext

    private val model: GenerativeModel by lazy {
        val config = generationConfig {
            this.context = appContext
            temperature = 0.2f
            topK = 16
            maxOutputTokens = 128
        }
        GenerativeModel(generationConfig = config)
    }

    /** Downloads (via AICore) / initialises the inference engine if needed. */
    suspend fun prepare(): AiStatus =
        runCatching { model.prepareInferenceEngine() }
            .fold(
                { Log.d(AI_TAG, "prepare: READY"); AiStatus.READY },
                { Log.w(AI_TAG, "prepare FAILED: ${it.javaClass.simpleName}: ${it.message}", it); AiStatus.UNAVAILABLE },
            )

    /** Raw generation. Returns null on any failure. */
    suspend fun generate(prompt: String): String? =
        runCatching { model.generateContent(prompt).text }
            .onFailure { Log.w(AI_TAG, "generate FAILED: ${it.javaClass.simpleName}: ${it.message}", it) }
            .getOrNull()

    fun close() {}
}

/**
 * Backend-agnostic decision: given any text generator (Nano or Gemma), ask the
 * model to pick one action. The model decides; we only parse + execute.
 */
suspend fun decideIntent(
    generate: suspend (String) -> String?,
    query: String,
    apps: List<AppEntry>,
): AiIntent {
    val prompt = """
        Convert ONE phone command into ONE JSON action.
        Output only a single JSON object. No list. No explanation. No code fences.

        Allowed actions:
        {"action":"open_app","app":"<app name>"}
        {"action":"alarm","hour":<0-23>,"minute":<0-59>}
        {"action":"call","number":"<digits>"}
        {"action":"setting","key":"wifi|bluetooth|airplane|battery|display|sound|location|data"}
        {"action":"message","app":"whatsapp|sms","to":"<contact or number>","text":"<the message you write>"}
        {"action":"web","query":"<text>"}
        {"action":"answer","text":"<short answer>"}

        For "message", YOU compose the message text yourself.

        User: open camera
        {"action":"open_app","app":"camera"}
        User: launch the slack app
        {"action":"open_app","app":"slack"}
        User: set an alarm for 7:30 am
        {"action":"alarm","hour":7,"minute":30}
        User: turn on wifi
        {"action":"setting","key":"wifi"}
        User: whatsapp mom that i'll be home late
        {"action":"message","app":"whatsapp","to":"mom","text":"Hi mom, I'll be home late tonight."}
        User: text John asking if he's free tomorrow
        {"action":"message","app":"sms","to":"John","text":"Hey John, are you free tomorrow?"}
        User: what is 24 times 7
        {"action":"answer","text":"168"}
        User: $query
    """.trimIndent()

    Log.d(AI_TAG, "decide: query='$query' apps=${apps.size}")
    val reply = generate(prompt)
    Log.d(AI_TAG, "decide: raw reply=${reply?.let { "\"$it\"" } ?: "null"}")
    val intent = if (reply == null) AiIntent.Unknown else parseIntent(reply, apps)
    Log.d(AI_TAG, "decide: parsed intent=$intent")
    return intent
}

/**
 * Picks the active brain (Gemini Nano or downloaded Gemma), prepares it lazily,
 * and exposes a uniform [decide]. Falls back to a plain web search when no model
 * is usable, so callers never break.
 */
/** One turn of the conversation: either the model spoke, or it wants to act. */
sealed interface AgentTurn {
    data class Say(val text: String) : AgentTurn
    data class Do(val intent: AiIntent) : AgentTurn
}

class Assistant(
    private val prepareFn: suspend () -> Boolean,
    private val generateFn: suspend (String) -> String?,
) {
    private var ready = false
    private val history = ArrayDeque<String>()

    /** One-shot command classification (used by the typed bar). */
    suspend fun decide(query: String, apps: List<AppEntry>): AiIntent {
        if (!ready) ready = prepareFn()
        return if (ready) decideIntent(generateFn, query, apps) else AiIntent.Web(query)
    }

    /** Multi-turn conversation: the model can chat, answer, or emit a tool call. */
    suspend fun converse(userText: String, apps: List<AppEntry>): AgentTurn {
        if (!ready) ready = prepareFn()
        if (!ready) return AgentTurn.Do(AiIntent.Web(userText))

        val convo = history.joinToString("\n")
        val prompt = """
            You are a concise on-device phone assistant. You can chat, answer questions, or perform actions.
            To perform an action, reply with ONLY one JSON tool call from this exact list:
            {"action":"open_app","app":"<name>"}
            {"action":"alarm","hour":<0-23>,"minute":<0-59>}
            {"action":"call","number":"<digits>"}
            {"action":"setting","key":"wifi|bluetooth|airplane|battery|display|sound|location|data"}
            {"action":"message","app":"whatsapp|sms","to":"<contact>","text":"<message you write>"}
            {"action":"web","query":"<text>"}
            For any question, fact, or chit-chat, reply in ONE short plain-text sentence. Never invent new JSON actions.

            User: open camera
            {"action":"open_app","app":"camera"}
            User: send a message to Mitali that i'll be playing cricket today
            {"action":"message","app":"whatsapp","to":"Mitali","text":"I'll be playing cricket today."}
            User: open whatsapp and message John i'm on my way
            {"action":"message","app":"whatsapp","to":"John","text":"I'm on my way."}
            User: set an alarm for 7
            {"action":"alarm","hour":7,"minute":0}
            User: how tall is the eiffel tower
            It's about 330 metres (1,083 ft).
            User: turn on wifi
            {"action":"setting","key":"wifi"}

            $convo
            User: $userText
            Assistant:
        """.trimIndent()

        Log.d(AI_TAG, "converse: '$userText'")
        val reply = generateFn(prompt)?.trim().orEmpty()
        Log.d(AI_TAG, "converse reply='$reply'")

        history.addLast("User: $userText")
        val intent = parseIntent(reply, apps)
        val turn = if (intent != AiIntent.Unknown) {
            history.addLast("Assistant: (performed an action)")
            AgentTurn.Do(intent)
        } else {
            // Don't echo raw JSON if the model invented an unknown action.
            val text = when {
                reply.isBlank() -> "sorry, i didn't catch that"
                reply.trimStart().startsWith("{") -> "i can't do that one yet"
                else -> reply
            }
            history.addLast("Assistant: $text")
            AgentTurn.Say(text)
        }
        while (history.size > 8) history.removeFirst()
        return turn
    }

    fun resetConversation() = history.clear()
}

/** Builds the [Assistant] for the active backend, or null if AI is off/unusable. */
@Composable
fun rememberAssistant(store: LauncherStore): Assistant? {
    val context = LocalContext.current
    val downloaded = store.modelDownloaded()
    return remember(store.aiEnabled, store.activeBackend, downloaded) {
        if (!store.aiEnabled) return@remember null
        when (store.activeBackend) {
            Backend.GEMMA -> if (downloaded) {
                val engine = GemmaEngine(context, gemmaModelFile(context).path)
                Assistant({ engine.prepare() }, engine::generate)
            } else {
                null
            }
            Backend.NANO -> if (aiSupported()) {
                val engine = AiIntentEngine(context)
                Assistant({ engine.prepare() == AiStatus.READY }, engine::generate)
            } else {
                null
            }
        }
    }
}
