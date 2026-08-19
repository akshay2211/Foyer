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
        val options = LlmInferenceOptions.builder()
            .setModelPath(modelPath)
            .setMaxTopK(64)
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
        runCatching { llm.generateResponse(prompt) }
            .onFailure { Log.w(AI_TAG, "gemma generate FAILED: ${it.message}", it) }
            .getOrNull()
    }

    fun close() {
        runCatching { llm.close() }
    }
}
