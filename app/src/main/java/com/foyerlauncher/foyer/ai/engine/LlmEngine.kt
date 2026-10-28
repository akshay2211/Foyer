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
    suspend fun generate(prompt: String): String? = withContext(Dispatchers.IO) { generateOneShot(prompt) }

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
                    ?: generateOneShot(prefix + suffix)
            } else {
                generateOneShot(prefix + suffix)
            }
        }

    private fun generateCached(
        prefix: String,
        suffix: String,
    ): String? {
        val wrappedPrefix = "<start_of_turn>user\n$prefix"
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
            work.addQueryChunk("$suffix<end_of_turn>\n<start_of_turn>model\n")
            work.generateResponse()?.clean()
        } finally {
            runCatching { work.close() }
        }
    }

    private fun generateOneShot(prompt: String): String? {
        val formatted = "<start_of_turn>user\n$prompt<end_of_turn>\n<start_of_turn>model\n"
        return runCatching { llm.generateResponse(formatted) }
            .onFailure { Log.w(AI_TAG, "llm generate FAILED: ${it.message}", it) }
            .getOrNull()
            ?.clean()
    }

    private fun sessionOptions(): LlmInferenceSessionOptions =
        LlmInferenceSessionOptions
            .builder()
            .setTopK(64)
            .setTemperature(0.2f)
            .build()

    /** Strips the stop token and any leaked chat-control tokens. */
    private fun String.clean(): String =
        substringBefore("<end_of_turn>")
            .replace("<start_of_turn>", "")
            .replace("<eos>", "")
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
