package io.ak1.testuilauncher

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * On-device text generation via MediaPipe LLM Inference, backed by a downloaded
 * Gemma `.task` model. This is the "bigger brain" alternative to Gemini Nano —
 * fully local, no network at inference time. Same [generate] shape as the Nano
 * engine so the agent can use either interchangeably.
 */
class GemmaEngine(context: Context, private val modelPath: String) {
    private val appContext = context.applicationContext

    private val llm: LlmInference by lazy {
        // maxTokens is the whole KV-cache window (prompt + output). It defaults to
        // 512; our grounded, catalog-driven prompt is larger than that, and
        // overflowing the window crashes the native runtime instead of throwing.
        // Gemma3-1B-IT supports a much bigger context, so give it real headroom.
        val options = LlmInferenceOptions.builder()
            .setModelPath(modelPath)
            .setMaxTopK(64)
            .setMaxTokens(2048)
            .build()
        LlmInference.createFromOptions(appContext, options)
    }

    fun modelExists(): Boolean = File(modelPath).exists()

    /** Warms up the model (loads weights); returns whether it's usable. */
    suspend fun prepare(): Boolean = withContext(Dispatchers.IO) {
        runCatching { llm }.fold(
            { Log.d(AI_TAG, "gemma: loaded $modelPath"); true },
            { Log.w(AI_TAG, "gemma load FAILED: ${it.message}", it); false },
        )
    }

    suspend fun generate(prompt: String): String? = withContext(Dispatchers.IO) {
        // Gemma3-IT is only in "instruction" mode when wrapped in its chat turn
        // template; fed raw text it free-runs and hallucinates extra turns. The
        // template also gives it a real stop token (<end_of_turn>) so it answers
        // once. We strip that (and any leaked control tokens) from the output.
        val formatted = "<start_of_turn>user\n$prompt<end_of_turn>\n<start_of_turn>model\n"
        runCatching { llm.generateResponse(formatted) }
            .onFailure { Log.w(AI_TAG, "gemma generate FAILED: ${it.message}", it) }
            .getOrNull()
            ?.substringBefore("<end_of_turn>")
            ?.replace("<start_of_turn>", "")
            ?.replace("<eos>", "")
            ?.trim()
    }

    fun close() {
        runCatching { llm.close() }
    }
}
