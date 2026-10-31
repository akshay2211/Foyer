package com.foyerlauncher.foyer.ai.engine

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession.LlmInferenceSessionOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** The chat template the downloaded model was trained with. */
enum class ChatTemplate {
    /** Gemma: a single user turn, no system role. */
    GEMMA,

    /** ChatML (Qwen): instructions and examples ride in a system turn, the input alone in user. */
    CHATML,
    ;

    companion object {
        /** Detects the template from the model's download URL; Gemma is the default. */
        fun fromUrl(url: String): ChatTemplate = if (url.contains("qwen", ignoreCase = true)) CHATML else GEMMA
    }
}

/**
 * On-device text generation via MediaPipe LLM Inference, backed by whatever
 * `.task` model the user downloaded (Qwen, Gemma, …) — fully local, no network
 * at inference time.
 *
 * Two generation paths:
 *  - [generate] (one-shot): wraps the whole prompt in the chat template and
 *    generates. Simple, always correct.
 *  - [generate] (prefix/suffix): KV **prefix-cache** — the stable preamble is
 *    prefilled into a base session once, then each turn clones that session and
 *    only processes the variable suffix, skipping re-prefill of the (~500-token)
 *    preamble. Falls back to the one-shot path on any failure, so it can never
 *    regress correctness; flip [PREFIX_CACHE] off to disable entirely.
 */
class LlmEngine(
    context: Context,
    private val modelPath: String,
    private val template: ChatTemplate = ChatTemplate.GEMMA,
) {
    private val appContext = context.applicationContext

    private val llm: LlmInference by lazy {
        val options =
            LlmInferenceOptions
                .builder()
                .setModelPath(modelPath)
                .setMaxTopK(64)
                .setMaxTokens(2048)
                .build()
        LlmInference.createFromOptions(appContext, options)
    }

    // Base session holding the prefilled preamble, keyed by the preamble text.
    private var cachedPrefix: String? = null
    private var baseSession: LlmInferenceSession? = null

    fun modelExists(): Boolean = File(modelPath).exists()

    /** Warms up the model (loads weights); returns whether it's usable. */
    suspend fun prepare(): Boolean =
        withContext(Dispatchers.IO) {
            runCatching { llm }.fold(
                {
                    Log.d(AI_TAG, "llm: loaded $modelPath")
                    true
                },
                {
                    Log.w(AI_TAG, "llm load FAILED: ${it.message}", it)
                    false
                },
            )
        }

    /** One-shot generation over a full prompt. */
    suspend fun generate(prompt: String): String? = withContext(Dispatchers.IO) { generateOneShot(wrapUserOnly(prompt)) }

    /**
     * Prefix-cached generation: [prefix] is the stable preamble (prefilled once
     * and reused), [suffix] is the per-turn variable part. Falls back to one-shot
     * over `prefix + suffix` if the session path throws.
     */
    suspend fun generate(
        prefix: String,
        suffix: String,
    ): String? =
        withContext(Dispatchers.IO) {
            if (PREFIX_CACHE) {
                runCatching { generateCached(prefix, suffix) }
                    .onFailure { Log.w(AI_TAG, "llm prefix-cache failed → one-shot fallback: ${it.message}") }
                    .getOrNull()
                    ?: generateOneShot(wrap(prefix, suffix))
            } else {
                generateOneShot(wrap(prefix, suffix))
            }
        }

    /**
     * Template-correct framing. For ChatML the instruction preamble goes in a
     * *system* turn and the input alone in *user* — the structural fix that
     * stops a small model from treating prompt examples as the query.
     */
    private fun wrap(
        prefix: String,
        suffix: String,
    ): String =
        when (template) {
            ChatTemplate.GEMMA -> "<start_of_turn>user\n$prefix$suffix<end_of_turn>\n<start_of_turn>model\n"
            ChatTemplate.CHATML ->
                "<|im_start|>system\n$prefix<|im_end|>\n<|im_start|>user\n$suffix<|im_end|>\n<|im_start|>assistant\n"
        }

    private fun wrapUserOnly(prompt: String): String =
        when (template) {
            ChatTemplate.GEMMA -> "<start_of_turn>user\n$prompt<end_of_turn>\n<start_of_turn>model\n"
            ChatTemplate.CHATML -> "<|im_start|>user\n$prompt<|im_end|>\n<|im_start|>assistant\n"
        }

    private fun generateCached(
        prefix: String,
        suffix: String,
    ): String? {
        val (wrappedPrefix, suffixTail) =
            when (template) {
                ChatTemplate.GEMMA ->
                    "<start_of_turn>user\n$prefix" to "$suffix<end_of_turn>\n<start_of_turn>model\n"
                ChatTemplate.CHATML ->
                    "<|im_start|>system\n$prefix<|im_end|>\n<|im_start|>user\n" to "$suffix<|im_end|>\n<|im_start|>assistant\n"
            }
        if (cachedPrefix != wrappedPrefix || baseSession == null) {
            baseSession?.let { runCatching { it.close() } }
            baseSession =
                LlmInferenceSession.createFromOptions(llm, sessionOptions()).apply {
                    addQueryChunk(wrappedPrefix) // prefill the preamble once
                }
            cachedPrefix = wrappedPrefix
            Log.d(AI_TAG, "llm: prefilled preamble (${prefix.length} chars) into base session")
        }
        val work = baseSession!!.cloneSession() // reuse the preamble's KV cache
        return try {
            work.addQueryChunk(suffixTail)
            work.generateResponse()?.clean()
        } finally {
            runCatching { work.close() }
        }
    }

    /**
     * One-shot through a throwaway session so decoding is (near-)greedy — the
     * raw engine call has no temperature control and samples at its default,
     * which made classification and pair extraction visibly random.
     */
    private fun generateOneShot(formatted: String): String? =
        runCatching {
            val session = LlmInferenceSession.createFromOptions(llm, sessionOptions())
            try {
                session.addQueryChunk(formatted)
                session.generateResponse()?.clean()
            } finally {
                runCatching { session.close() }
            }
        }.onFailure { Log.w(AI_TAG, "llm session generate failed → raw fallback: ${it.message}") }
            .getOrNull()
            ?: runCatching { llm.generateResponse(formatted) }
                .onFailure { Log.w(AI_TAG, "llm generate FAILED: ${it.message}", it) }
                .getOrNull()
                ?.clean()

    // topK=1 is greedy decoding — the temperature value is then irrelevant,
    // but 0.2 keeps the raw fallback calm too.
    private fun sessionOptions(): LlmInferenceSessionOptions =
        LlmInferenceSessionOptions
            .builder()
            .setTopK(1)
            .setTemperature(0.2f)
            .build()

    /** Strips the stop token and any leaked chat-control tokens (both templates). */
    private fun String.clean(): String =
        substringBefore("<end_of_turn>")
            .substringBefore("<|im_end|>")
            .replace("<start_of_turn>", "")
            .replace("<|im_start|>assistant\n", "")
            .replace("<|im_start|>", "")
            .replace("<eos>", "")
            .replace("<|endoftext|>", "")
            .trim()

    fun close() {
        runCatching { baseSession?.close() }
        runCatching { llm.close() }
    }

    companion object {
        /**
         * Kill-switch for the KV prefix-cache. Disabled: on-device testing showed
         * cloned sessions leaked KV state across turns (a message request echoed the
         * previous answer verbatim) with no latency benefit. Kept behind the flag so
         * the session path can be revisited with correct clone semantics later.
         */
        private const val PREFIX_CACHE = false
    }
}
