package com.foyerlauncher.foyer.ai.engine

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.foyerlauncher.foyer.ai.router.ActionRegistry
import com.foyerlauncher.foyer.ai.router.AiIntent
import com.foyerlauncher.foyer.ai.router.AppCatalog
import com.foyerlauncher.foyer.ai.router.AppIndex
import com.foyerlauncher.foyer.ai.router.CAPABILITIES
import com.foyerlauncher.foyer.ai.router.Normalizer
import com.foyerlauncher.foyer.ai.router.Plan
import com.foyerlauncher.foyer.ai.router.allowedActionsBlock
import com.foyerlauncher.foyer.ai.router.fastRoute
import com.foyerlauncher.foyer.ai.router.fewShotBlock
import com.foyerlauncher.foyer.ai.router.parsePlan
import com.foyerlauncher.foyer.data.AppEntry
import com.foyerlauncher.foyer.data.LauncherStore
import com.foyerlauncher.foyer.data.key

const val AI_TAG = "FoyerAI"

/**
 * Model-driven decision: given a text generator, ask the
 * model to pick one or more actions. The prompt is generated from the capability
 * [CAPABILITIES] catalog and *grounded* with the actual installed apps that look
 * relevant, so the model selects a real app instead of guessing a name. The
 * model decides; we only parse + execute.
 */
suspend fun decideIntent(
    generate: suspend (String, String) -> String?,
    query: String,
    index: AppIndex,
): Plan {
    // Variable, query-dependent tail; the stable preamble is [DECIDE_PREFIX].
    val suffix = buildString {
        settingsBlock(query)?.let { appendLine(it) }
        groundingLine(index, query)?.let { appendLine(it) }
        append("\"$query\" -> ")
    }

    Log.d(AI_TAG, "decide: query='$query' promptChars=${DECIDE_PREFIX.length + suffix.length}")
    val reply = timedGenerate(generate, DECIDE_PREFIX, suffix)
    Log.d(AI_TAG, "decide: raw reply=${reply?.let { "\"$it\"" } ?: "null"}")
    val plan = if (reply == null) emptyList() else parsePlan(reply, index)
    Log.d(AI_TAG, "decide: parsed plan=$plan")
    return plan
}

/** Grounds the prompt with installed apps relevant to [query], or null if none. */
private fun groundingLine(index: AppIndex, query: String): String? {
    val cands = index.candidates(query, 8)
    return if (cands.isEmpty()) null
    else "Installed apps you can open (use the exact name): " + cands.joinToString(", ") { it.label }
}

// The instruction/catalog/few-shot preamble is identical every turn — build the
// generated blocks once instead of re-joining the catalog on each request.
private val STABLE_ACTIONS: String by lazy { allowedActionsBlock() }
private val STABLE_FEWSHOTS: String by lazy { fewShotBlock() }

/**
 * The stable, query-independent preambles used as the KV prefix-cache key
 * ([LlmEngine.generate]). Everything that varies per turn (settings block,
 * app grounding, conversation history, the command itself) lives in the suffix.
 */
private val DECIDE_PREFIX: String by lazy {
    buildString {
        appendLine("Convert the phone command into JSON actions.")
        appendLine("Output ONLY JSON — a single object, or a JSON array for several independent tasks. No prose, no code fences, no extra text.")
        appendLine()
        appendLine("Allowed actions:")
        appendLine(STABLE_ACTIONS)
        appendLine()
        appendLine("For \"message\"/\"email\" you compose the text yourself. When opening an app, use one of the installed names listed below.")
        appendLine("To send a message in an app, use ONE \"message\" action (it opens the app itself); do NOT emit \"open_app\" too.")
        appendLine()
        appendLine("Examples:")
        appendLine(STABLE_FEWSHOTS)
        appendLine(""""turn on wifi and open spotify" -> [{"action":"setting","key":"wifi"},{"action":"open_app","app":"Spotify"}]""")
    }
}

private val CONVERSE_PREFIX: String by lazy {
    buildString {
        appendLine("You are a concise on-device phone assistant. You either perform an action or answer.")
        appendLine("If the command maps to an action, output ONLY JSON — one object, or an array for several independent tasks:")
        appendLine(STABLE_ACTIONS)
        appendLine("Otherwise (a question, fact, or chit-chat) reply with ONE short plain sentence.")
        appendLine("Never invent new JSON actions. Never write \"User:\" or extra turns. Answer only the last line.")
        appendLine("For \"message\"/\"email\" you compose the text. When opening an app, use one of the installed names below.")
        appendLine("To send a message in an app, use ONE \"message\" action (it opens the app itself); do NOT emit \"open_app\" too.")
        appendLine()
        appendLine("Examples:")
        appendLine(STABLE_FEWSHOTS)
        appendLine(""""how tall is the eiffel tower" -> It's about 330 metres (1,083 ft).""")
        appendLine(""""turn on wifi and open spotify" -> [{"action":"setting","key":"wifi"},{"action":"open_app","app":"Spotify"}]""")
    }
}

private val SETTINGS_HINTS = setOf(
    "setting", "settings", "wifi", "wi-fi", "bluetooth", "developer", "usb", "debugging",
    "brightness", "display", "screen", "volume", "sound", "audio", "notification", "notifications",
    "location", "gps", "accessibility", "airplane", "flight", "battery", "data", "mobile",
    "enable", "disable",
)

/**
 * The settings-key reference — included only when the request looks settings-related
 * (retrieval-first, SoT §16), so non-settings prompts don't carry ~12 extra lines.
 */
private fun settingsBlock(query: String): String? =
    if (query.lowercase().split(Regex("[^a-z0-9]+")).any { it in SETTINGS_HINTS }) {
        "For a settings request, use \"setting\" with the closest key below:\n" + ActionRegistry.promptReference()
    } else {
        null
    }

/** Runs a model call (stable prefix + variable suffix), timing it and recording metrics. */
private suspend fun timedGenerate(
    generate: suspend (String, String) -> String?,
    prefix: String,
    suffix: String,
): String? {
    val t0 = System.currentTimeMillis()
    val reply = generate(prefix, suffix)
    AiMetrics.recordLlm(System.currentTimeMillis() - t0, ok = !reply.isNullOrBlank(), promptChars = prefix.length + suffix.length)
    return reply
}

/**
 * One turn of the conversation:
 *  - [Do]  the assistant wants to perform a plan,
 *  - [Say] the assistant spoke,
 *  - [Ask] the assistant needs the user to disambiguate — [candidates] are the
 *    apps it's unsure between. The UI shows them as buttons AND re-opens the mic
 *    so the user can just say "yes"/"no"; the answer resolves against pending state.
 */
sealed interface AgentTurn {
    data class Say(val text: String) : AgentTurn
    data class Do(val plan: Plan) : AgentTurn
    data class Ask(val text: String, val candidates: List<AppEntry>) : AgentTurn
}

class Assistant(
    private val prepareFn: suspend () -> Boolean,
    private val generateFn: suspend (prefix: String, suffix: String) -> String?,
    /** Snapshot of the user-taught aliases (alias → app key) at query time. */
    private val aliasesFn: () -> Map<String, String> = { emptyMap() },
) {
    private var ready = false
    private val history = ArrayDeque<String>()

    /** A confirmation we're waiting on: the apps the last question offered, best first. */
    private var pendingCandidates: List<AppEntry> = emptyList()

    /** When the pending confirmation goes stale (§11). A late "yes" after this is ignored. */
    private var pendingExpiresAt: Long = 0L

    private fun setPending(candidates: List<AppEntry>) {
        pendingCandidates = candidates
        pendingExpiresAt = System.currentTimeMillis() + PENDING_TIMEOUT_MS
    }

    private fun clearPending() {
        pendingCandidates = emptyList()
        pendingExpiresAt = 0L
    }

    // Cache the index by app-list identity + alias snapshot so it's built once,
    // not per query, but refreshes when the user teaches a new alias.
    private var cachedApps: List<AppEntry>? = null
    private var cachedAliases: Map<String, String>? = null
    private var cachedIndex: AppIndex? = null

    private fun indexFor(apps: List<AppEntry>): AppIndex {
        val aliases = aliasesFn()
        if (cachedApps !== apps || cachedAliases != aliases || cachedIndex == null) {
            cachedIndex = AppIndex.of(apps, aliases)
            cachedApps = apps
            cachedAliases = aliases
        }
        return cachedIndex!!
    }

    /** One-shot command classification (used by the typed bar). */
    suspend fun decide(query: String, apps: List<AppEntry>): Plan {
        AiMetrics.recordRequest()
        val index = indexFor(apps)
        val text = Normalizer.normalize(query)
        Log.d(AI_TAG, "assistant input=\"$query\" normalized=\"$text\"")
        // Tier-0: obvious commands never touch the model.
        fastRoute(text, index)?.let {
            val plan = resolveInventory(it, apps)
            Log.d(AI_TAG, "route=DETERMINISTIC llm=false -> $plan")
            return plan
        }
        if (!ready) ready = prepareFn()
        Log.d(AI_TAG, "route=SEMANTIC_FALLBACK llm=$ready")
        val plan = if (ready) decideIntent(generateFn, text, index) else listOf(AiIntent.Web(text))
        return resolveInventory(plan, apps)
    }

    /** Multi-turn conversation: the model can chat, answer, ask, or emit a plan. */
    suspend fun converse(userText: String, apps: List<AppEntry>): AgentTurn {
        AiMetrics.recordRequest()
        val index = indexFor(apps)
        val text = Normalizer.normalize(userText)
        Log.d(AI_TAG, "assistant input=\"$userText\" normalized=\"$text\" pending=${pendingCandidates.size}")

        // 0. If we asked "did you mean…?", interpret a yes/no/next before anything else.
        resolvePending(text)?.let { Log.d(AI_TAG, "route=CONFIRMATION llm=false"); return it }

        // 1. Tier-0 resolver — no model call. Inventory questions are answered locally here.
        fastRoute(text, index)?.let {
            val plan = resolveInventory(it, apps)
            Log.d(AI_TAG, "route=DETERMINISTIC llm=false -> $plan")
            return turnFromPlan(text, plan)
        }

        // 2. The model.
        if (!ready) ready = prepareFn()
        if (!ready) return AgentTurn.Do(listOf(AiIntent.Web(text)))

        val suffix = buildConverseSuffix(index, text)
        Log.d(AI_TAG, "route=SEMANTIC_FALLBACK llm=true promptChars=${CONVERSE_PREFIX.length + suffix.length}")
        val reply = timedGenerate(generateFn, CONVERSE_PREFIX, suffix).orEmpty()
        Log.d(AI_TAG, "converse reply='$reply'")

        // Parse plan from the RAW reply (JSON extraction tolerates trailing noise);
        // only sanitise the free-text path.
        val plan = resolveInventory(parsePlan(reply, index), apps)
        if (plan.isNotEmpty()) return turnFromPlan(text, plan)

        val clean = sanitizeReply(reply)
        val answer = when {
            clean.isBlank() -> "sorry, i didn't catch that"
            clean.trimStart().startsWith("{") || clean.trimStart().startsWith("[") -> "i can't do that one yet"
            else -> clean
        }
        remember(text, answer)
        return AgentTurn.Say(answer)
    }

    /** Turns a plan into a turn, converting an ambiguous app-open into a spoken question. */
    private fun turnFromPlan(userText: String, plan: Plan): AgentTurn {
        val open = plan.singleOrNull() as? AiIntent.OpenApp
        if (open != null && !open.confident) {
            val candidates = open.alternatives.ifEmpty { listOf(open.app) }
            setPending(candidates)
            val q = askText(candidates)
            remember(userText, q)
            return AgentTurn.Ask(q, candidates)
        }
        clearPending()
        remember(userText, "(done)")
        return AgentTurn.Do(plan)
    }

    /**
     * Handles the answer to a pending "did you mean…?": "yes" opens the top
     * candidate, "no" drops it and offers the next (or asks openly when none
     * remain), anything else is treated as a fresh request.
     */
    private fun resolvePending(userText: String): AgentTurn? {
        if (pendingCandidates.isEmpty()) return null
        // Stale pending: a late confirmation is ignored; treat as a fresh request.
        if (System.currentTimeMillis() > pendingExpiresAt) { clearPending(); return null }
        val t = userText.trim().lowercase()
        return when {
            isAffirmative(t) -> {
                val app = pendingCandidates.first()
                clearPending()
                remember(userText, "(done)")
                AgentTurn.Do(listOf(AiIntent.OpenApp(app, confident = true)))
            }
            isNegative(t) -> {
                val rest = pendingCandidates.drop(1)
                if (rest.isEmpty()) {
                    clearPending()
                    remember(userText, "which app did you mean?")
                    AgentTurn.Say("which app did you mean?")
                } else {
                    setPending(rest)
                    val q = askText(rest)
                    remember(userText, q)
                    AgentTurn.Ask(q, rest)
                }
            }
            else -> { clearPending(); null } // fresh request
        }
    }

    /** The per-turn variable tail for a conversation; the preamble is [CONVERSE_PREFIX]. */
    private fun buildConverseSuffix(index: AppIndex, userText: String): String {
        val convo = history.joinToString("\n")
        return buildString {
            settingsBlock(userText)?.let { appendLine(it) }
            groundingLine(index, userText)?.let { appendLine(it) }
            if (convo.isNotBlank()) {
                appendLine("Conversation so far:")
                appendLine(convo)
                appendLine()
            }
            append("\"$userText\" -> ")
        }
    }

    /** Answers "how many/list X apps" locally from the app list, so the model never guesses inventory. */
    private fun resolveInventory(plan: Plan, apps: List<AppEntry>): Plan =
        plan.map { if (it is AiIntent.FindApps) AiIntent.Answer(AppCatalog.describe(apps, it.query)) else it }

    /** Records one exchange in the neutral `"input" -> reply` form used by the prompt. */
    private fun remember(userText: String, reply: String) {
        history.addLast("\"$userText\" -> $reply")
        while (history.size > 6) history.removeFirst()
    }

    fun resetConversation() {
        history.clear()
        clearPending()
    }
}

/** How long a "did you mean…?" confirmation stays valid before a late "yes" is ignored. */
private const val PENDING_TIMEOUT_MS = 60_000L

/** "Did you mean X?" / "…X, Y, or Z?" for a candidate list. */
private fun askText(candidates: List<AppEntry>): String {
    val names = candidates.map { it.label }
    return when (names.size) {
        1 -> "did you mean ${names[0]}?"
        else -> "did you mean " + names.dropLast(1).joinToString(", ") + ", or ${names.last()}?"
    }
}

private val AFFIRMATIVE = setOf(
    "yes", "yeah", "yep", "yup", "sure", "ok", "okay", "correct", "right", "confirm",
    "do it", "open it", "that one", "yes please", "haan", "ha",
)
private val NEGATIVE = setOf("no", "nope", "nah", "not that", "wrong", "nahi")

private fun isAffirmative(t: String): Boolean = t in AFFIRMATIVE || t.startsWith("yes")
private fun isNegative(t: String): Boolean = t in NEGATIVE || t.startsWith("no ") || t == "no"

/**
 * Trims a model reply down to a single answer: strips any leaked chat-control
 * tokens and cuts everything from the first hallucinated new turn / example
 * ("User:", "Assistant:", or a fresh `"..." ->` line) onward.
 */
private fun sanitizeReply(raw: String): String {
    var s = raw.replace("<end_of_turn>", "").replace("<start_of_turn>", "").replace("<eos>", "").trim()
    Regex("(?im)(^|\\n)\\s*(user|assistant)\\s*:").find(s)?.let { s = s.substring(0, it.range.first) }
    Regex("\\n\\s*\"[^\"]*\"\\s*->").find(s)?.let { s = s.substring(0, it.range.first) }
    return s.trim().removePrefix("->").trim()
}

/** Builds the [Assistant] over the downloaded model, or null if AI is off or no model is set up. */
@Composable
fun rememberAssistant(store: LauncherStore): Assistant? {
    val context = LocalContext.current
    val downloaded = store.modelDownloaded()
    return remember(store.aiEnabled, downloaded) {
        if (!store.aiEnabled || !downloaded) return@remember null
        val engine = LlmEngine(context, modelFile(context).path)
        // The engine reuses the prefilled preamble via its prefix-cache path.
        Assistant(
            { engine.prepare() },
            { prefix, suffix -> engine.generate(prefix, suffix) },
            { store.aliases.toMap() },
        )
    }
}
