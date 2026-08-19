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
            temperature = 0.1f
            topK = 8
            maxOutputTokens = 40
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
class Assistant(
    private val prepareFn: suspend () -> Boolean,
    private val generateFn: suspend (String) -> String?,
) {
    private var ready = false

    suspend fun decide(query: String, apps: List<AppEntry>): AiIntent {
        if (!ready) ready = prepareFn()
        return if (ready) decideIntent(generateFn, query, apps) else AiIntent.Web(query)
    }
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
