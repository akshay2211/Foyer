package com.foyerlauncher.foyer.ai.engine

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.foyerlauncher.foyer.ai.router.ActionPair
import com.foyerlauncher.foyer.ai.router.ActionRegistry
import com.foyerlauncher.foyer.ai.router.AiIntent
import com.foyerlauncher.foyer.ai.router.AppCatalog
import com.foyerlauncher.foyer.ai.router.AppIndex
import com.foyerlauncher.foyer.ai.router.CAPABILITIES
import com.foyerlauncher.foyer.ai.router.Normalizer
import com.foyerlauncher.foyer.ai.router.Plan
import com.foyerlauncher.foyer.ai.router.allowedActionsBlock
import com.foyerlauncher.foyer.ai.router.appScope
import com.foyerlauncher.foyer.ai.router.fastRoute
import com.foyerlauncher.foyer.ai.router.fewShotBlock
import com.foyerlauncher.foyer.ai.router.parsePairs
import com.foyerlauncher.foyer.ai.router.parsePlan
import com.foyerlauncher.foyer.ai.router.planFromPairs
import com.foyerlauncher.foyer.data.AppEntry
import com.foyerlauncher.foyer.data.LauncherStore

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
    val suffix =
        buildString {
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
private fun groundingLine(
    index: AppIndex,
    query: String,
): String? {
    val cands = index.candidates(query, 8)
    return if (cands.isEmpty()) {
        null
    } else {
        "Installed apps you can open (use the exact name): " + cands.joinToString(", ") { it.label }
    }
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
        appendLine("A question asking for information (what/how/why/who) maps to \"answer\" with a short answer you compose — never \"navigate\" or \"web\" for a fact you know.")
        appendLine()
        appendLine("Examples:")
        appendLine(STABLE_FEWSHOTS)
        appendLine(""""turn on wifi and open spotify" -> [{"action":"setting","key":"wifi"},{"action":"open_app","app":"Spotify"}]""")
        appendLine(""""how tall is the eiffel tower" -> {"action":"answer","text":"about 330 metres (1,083 ft)"}""")
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

/**
 * The judge's verdict on raw input — the explicit first step of every model
 * turn. Every verdict is logged at INFO on [AI_TAG], so a demo
 * (`adb logcat -s FoyerAI`) shows the decision the moment the text arrives.
 */
enum class InputKind { QUESTION, ACTION, UNCLEAR }

/** Stable classification preamble (KV prefix-cached like the other prefixes). */
private val CLASSIFY_PREFIX: String by lazy {
    buildString {
        appendLine("Classify the phone input. Reply with exactly one word: ACTION or QUESTION.")
        appendLine("ACTION — the user wants the phone to do something: open, call, message, set, toggle, search, play, navigate, create.")
        appendLine("QUESTION — the user asks for information, facts, or chats.")
        appendLine()
        appendLine("Examples:")
        appendLine("\"open spotify\" -> ACTION")
        appendLine("\"how tall is the eiffel tower\" -> QUESTION")
        appendLine("\"turn on wifi and open spotify\" -> ACTION")
        appendLine("\"message mom i'm running late\" -> ACTION")
        appendLine("\"what is the capital of france\" -> QUESTION")
        appendLine("\"set an alarm for 7 am\" -> ACTION")
    }
}

/** Asks the model for a one-word verdict; anything unparseable is [InputKind.UNCLEAR]. */
suspend fun classifyInput(
    generate: suspend (String, String) -> String?,
    query: String,
): InputKind {
    val raw = timedGenerate(generate, CLASSIFY_PREFIX, "\"$query\" -> ")?.trim().orEmpty()
    Log.d(AI_TAG, "classify raw='$raw'")
    val word = raw.uppercase()
    return when {
        word.startsWith("ACTION") -> InputKind.ACTION
        word.startsWith("QUESTION") -> InputKind.QUESTION
        "ACTION" in word -> InputKind.ACTION
        "QUESTION" in word -> InputKind.QUESTION
        else -> InputKind.UNCLEAR
    }
}

/**
 * Cycle-2 prompt: break an ACTION input into action→value pairs. The model
 * only names the pairs; [planFromPairs] maps them onto intents in code.
 */
private val PAIRS_PREFIX: String by lazy {
    buildString {
        appendLine("Break the phone command into action/value pairs. Output ONLY a JSON array, no prose.")
        appendLine("""Each pair is {"action":"<verb>","value":"<target>"}; message/email pairs also carry "text".""")
        appendLine("Verbs: open, message, call, email, play, navigate, setting, alarm, timer, web, note.")
        appendLine("""If an app is named anywhere, the whole command belongs to that app — emit {"action":"open","value":"<app>"} as the FIRST pair.""")
        appendLine("Copy names, apps and message text VERBATIM from the user's input — never reuse words from the examples.")
        appendLine()
        appendLine("Examples:")
        appendLine(""""message mom hello on whatsapp" -> [{"action":"open","value":"whatsapp"},{"action":"message","value":"mom","text":"hello"}]""")
        appendLine(""""open spotify" -> [{"action":"open","value":"spotify"}]""")
        appendLine(""""turn on wifi and open spotify" -> [{"action":"setting","value":"wifi"},{"action":"open","value":"spotify"}]""")
        appendLine(""""call dad" -> [{"action":"call","value":"dad"}]""")
        appendLine(""""set an alarm for 7 am" -> [{"action":"alarm","value":"7:00 am"}]""")
        appendLine(""""play jazz on spotify" -> [{"action":"open","value":"spotify"},{"action":"play","value":"jazz"}]""")
    }
}

/** Cycle-2 call: the model names the pairs; empty on a dud reply. */
suspend fun extractPairs(
    generate: suspend (String, String) -> String?,
    query: String,
): List<ActionPair> {
    val raw = timedGenerate(generate, PAIRS_PREFIX, "\"$query\" -> ").orEmpty()
    Log.d(AI_TAG, "pairs raw='$raw'")
    return parsePairs(raw)
}

/** Prompt for the question branch: answer only, never JSON. */
private val ANSWER_PREFIX: String by lazy {
    buildString {
        appendLine("You are a concise on-device phone assistant. Answer the user's question with ONE short plain sentence.")
        appendLine("No JSON. Never write \"User:\" or extra turns. Answer only the last line.")
    }
}

private val SETTINGS_HINTS =
    setOf(
        "setting",
        "settings",
        "wifi",
        "wi-fi",
        "bluetooth",
        "developer",
        "usb",
        "debugging",
        "brightness",
        "display",
        "screen",
        "volume",
        "sound",
        "audio",
        "notification",
        "notifications",
        "location",
        "gps",
        "accessibility",
        "airplane",
        "flight",
        "battery",
        "data",
        "mobile",
        "enable",
        "disable",
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
    data class Say(
        val text: String,
    ) : AgentTurn

    data class Do(
        val plan: Plan,
    ) : AgentTurn

    data class Ask(
        val text: String,
        val candidates: List<AppEntry>,
    ) : AgentTurn
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
    suspend fun decide(
        query: String,
        apps: List<AppEntry>,
    ): Plan {
        AiMetrics.recordRequest()
        val index = indexFor(apps)
        val text = Normalizer.normalize(query)
        Log.d(AI_TAG, "assistant input=\"$query\" normalized=\"$text\"")

        if (!ready) ready = prepareFn()
        if (!ready) {
            Log.i(AI_TAG, "judge: SKIPPED (model not ready) \"$text\" -> web search")
            return listOf(AiIntent.Web(text))
        }

        // The judge runs here too: a typed question becomes an answer, never
        // a forced action (the decide prompt would shoehorn it into one).
        val t0 = System.currentTimeMillis()
        val kind = classifyInput(generateFn, text)
        Log.i(AI_TAG, "judge: $kind (model, ${System.currentTimeMillis() - t0}ms) \"$text\"")
        if (kind == InputKind.QUESTION) {
            val reply = timedGenerate(generateFn, ANSWER_PREFIX, "\"$text\" -> ").orEmpty()
            Log.d(AI_TAG, "answer reply='$reply'")
            val answerPlan = resolveInventory(parsePlan(reply, index), apps)
            if (answerPlan.isNotEmpty()) return answerPlan
            val clean = sanitizeReply(reply)
            if (clean.isNotBlank() && !clean.trimStart().startsWith("{")) {
                Log.i(AI_TAG, "say: \"$clean\"")
                return listOf(AiIntent.Answer(clean))
            }
        }
        // Cycle 2 — pair extraction (app scope stripped first); cycle 3 — decide.
        val scope = appScope(text, index)
        if (scope != null) Log.i(AI_TAG, "scope: app=${scope.app.label} rest=\"${scope.cleaned}\"")
        val pairs = extractPairs(generateFn, scope?.cleaned ?: text)
        if (pairs.isNotEmpty()) {
            val pairPlan = resolveInventory(planFromPairs(pairs, index, scope?.app), apps)
            Log.i(AI_TAG, "pairs: ${pairs.joinToString(", ")} -> ${pairPlan.ifEmpty { "unmappable, falling back" }}")
            if (pairPlan.isNotEmpty()) return pairPlan
        } else {
            Log.i(AI_TAG, "pairs: none parsed, falling back")
        }
        return resolveInventory(decideIntent(generateFn, text, index), apps)
    }

    /** Multi-turn conversation: the model can chat, answer, ask, or emit a plan. */
    suspend fun converse(
        userText: String,
        apps: List<AppEntry>,
    ): AgentTurn {
        AiMetrics.recordRequest()
        val index = indexFor(apps)
        val text = Normalizer.normalize(userText)
        Log.d(AI_TAG, "assistant input=\"$userText\" normalized=\"$text\" pending=${pendingCandidates.size}")

        // 0. If we asked "did you mean…?", interpret a yes/no/next before anything else.
        resolvePending(text)?.let {
            Log.i(AI_TAG, "judge: CONFIRMATION (pending answer, no model) \"$text\"")
            return it
        }

        // 1. Tier-0 resolver — no model call. Inventory questions are answered locally here.
        fastRoute(text, index)?.let {
            val plan = resolveInventory(it, apps)
            Log.i(AI_TAG, "judge: ACTION (deterministic, no model) \"$text\" -> $plan")
            return turnFromPlan(text, plan)
        }

        // 2. Everything past this point needs the model.
        if (!ready) ready = prepareFn()
        if (!ready) {
            Log.i(AI_TAG, "judge: SKIPPED (model not ready) \"$text\" -> web search")
            return AgentTurn.Do(listOf(AiIntent.Web(text)))
        }

        // The judge — the explicit first model step: question or action?
        val t0 = System.currentTimeMillis()
        val kind = classifyInput(generateFn, text)
        Log.i(AI_TAG, "judge: $kind (model, ${System.currentTimeMillis() - t0}ms) \"$text\"")

        // 3. The branch the verdict chose. UNCLEAR still tries to act — the
        //    action path falls back to open conversation when no plan parses.
        return when (kind) {
            InputKind.QUESTION -> answerQuestion(index, apps, text)
            else -> actOn(index, apps, text)
        }
    }

    /** The question branch: answer in one sentence; a JSON reply (misjudge) still executes. */
    private suspend fun answerQuestion(
        index: AppIndex,
        apps: List<AppEntry>,
        text: String,
    ): AgentTurn {
        val convo = history.joinToString("\n")
        val suffix =
            buildString {
                if (convo.isNotBlank()) {
                    appendLine("Conversation so far:")
                    appendLine(convo)
                    appendLine()
                }
                append("\"$text\" -> ")
            }
        val reply = timedGenerate(generateFn, ANSWER_PREFIX, suffix).orEmpty()
        Log.d(AI_TAG, "answer reply='$reply'")

        // The judge can be wrong — honour a plan if the model emitted one anyway.
        val plan = resolveInventory(parsePlan(reply, index), apps)
        if (plan.isNotEmpty()) {
            Log.i(AI_TAG, "act: judge said QUESTION but the reply was a plan -> $plan")
            return turnFromPlan(text, plan)
        }
        return sayFrom(text, reply)
    }

    /** The action branch: pair extraction first, then the full decide prompt, then open conversation. */
    private suspend fun actOn(
        index: AppIndex,
        apps: List<AppEntry>,
        text: String,
    ): AgentTurn {
        // Deterministic app scope: an installed app named anywhere claims the
        // whole command. Detected in code (the model doesn't know the app list)
        // and stripped from the text so the model can't mangle it.
        val scope = appScope(text, index)
        if (scope != null) Log.i(AI_TAG, "scope: app=${scope.app.label} rest=\"${scope.cleaned}\"")

        // Cycle 2 — the model names action→value pairs; the launcher maps them.
        val pairs = extractPairs(generateFn, scope?.cleaned ?: text)
        if (pairs.isNotEmpty()) {
            val pairPlan = resolveInventory(planFromPairs(pairs, index, scope?.app), apps)
            Log.i(AI_TAG, "pairs: ${pairs.joinToString(", ")} -> ${pairPlan.ifEmpty { "unmappable, falling back" }}")
            if (pairPlan.isNotEmpty()) return turnFromPlan(text, pairPlan)
        } else {
            Log.i(AI_TAG, "pairs: none parsed, falling back")
        }

        // Cycle 3 — the full decide prompt.
        val plan = resolveInventory(decideIntent(generateFn, text, index), apps)
        if (plan.isNotEmpty()) {
            Log.i(AI_TAG, "act: $plan")
            return turnFromPlan(text, plan)
        }

        Log.i(AI_TAG, "act: no plan parsed -> converse fallback")
        val suffix = buildConverseSuffix(index, text)
        Log.d(AI_TAG, "route=SEMANTIC_FALLBACK llm=true promptChars=${CONVERSE_PREFIX.length + suffix.length}")
        val reply = timedGenerate(generateFn, CONVERSE_PREFIX, suffix).orEmpty()
        Log.d(AI_TAG, "converse reply='$reply'")
        val fallbackPlan = resolveInventory(parsePlan(reply, index), apps)
        if (fallbackPlan.isNotEmpty()) return turnFromPlan(text, fallbackPlan)
        return sayFrom(text, reply)
    }

    /** Cleans a free-text reply, records the exchange, and logs what will be spoken. */
    private fun sayFrom(
        text: String,
        reply: String,
    ): AgentTurn {
        val clean = sanitizeReply(reply)
        val answer =
            when {
                clean.isBlank() -> "sorry, i didn't catch that"
                clean.trimStart().startsWith("{") || clean.trimStart().startsWith("[") -> "i can't do that one yet"
                else -> clean
            }
        remember(text, answer)
        Log.i(AI_TAG, "say: \"$answer\"")
        return AgentTurn.Say(answer)
    }

    /** Turns a plan into a turn, converting an ambiguous app-open into a spoken question. */
    private fun turnFromPlan(
        userText: String,
        plan: Plan,
    ): AgentTurn {
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
        if (System.currentTimeMillis() > pendingExpiresAt) {
            clearPending()
            return null
        }
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
            else -> {
                clearPending()
                null
            } // fresh request
        }
    }

    /** The per-turn variable tail for a conversation; the preamble is [CONVERSE_PREFIX]. */
    private fun buildConverseSuffix(
        index: AppIndex,
        userText: String,
    ): String {
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
    private fun resolveInventory(
        plan: Plan,
        apps: List<AppEntry>,
    ): Plan = plan.map { if (it is AiIntent.FindApps) AiIntent.Answer(AppCatalog.describe(apps, it.query)) else it }

    /** Records one exchange in the neutral `"input" -> reply` form used by the prompt. */
    private fun remember(
        userText: String,
        reply: String,
    ) {
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

private val AFFIRMATIVE =
    setOf(
        "yes",
        "yeah",
        "yep",
        "yup",
        "sure",
        "ok",
        "okay",
        "correct",
        "right",
        "confirm",
        "do it",
        "open it",
        "that one",
        "yes please",
        "haan",
        "ha",
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
    var s =
        raw
            .replace("<end_of_turn>", "")
            .replace("<start_of_turn>", "")
            .replace("<eos>", "")
            .trim()
    Regex("(?im)(^|\\n)\\s*(user|assistant)\\s*:").find(s)?.let { s = s.substring(0, it.range.first) }
    Regex("\\n\\s*\"[^\"]*\"\\s*->").find(s)?.let { s = s.substring(0, it.range.first) }
    return s.trim().removePrefix("->").trim()
}

/** Builds the [Assistant] over the downloaded model, or null if AI is off or no model is set up. */
@Composable
fun rememberAssistant(store: LauncherStore): Assistant? {
    val context = LocalContext.current
    val downloaded = store.modelDownloaded()
    return remember(store.aiEnabled, downloaded, store.modelUrl) {
        if (!store.aiEnabled || !downloaded) return@remember null
        val engine = LlmEngine(context, modelFile(context).path, ChatTemplate.fromUrl(store.modelUrl))
        // The engine reuses the prefilled preamble via its prefix-cache path.
        Assistant(
            { engine.prepare() },
            { prefix, suffix -> engine.generate(prefix, suffix) },
            { store.aliases.toMap() },
        )
    }
}
