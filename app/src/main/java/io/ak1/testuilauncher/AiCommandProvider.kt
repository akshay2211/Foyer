package io.ak1.testuilauncher

import android.content.Context

/**
 * On-device LLM integration seam.
 *
 * This is where a local model plugs into the command bar / voice, behind the
 * exact same [CommandProvider] contract the heuristics use. It stays a no-op
 * (reports unavailable, yields nothing) until a model is wired, so
 * [routeCommands] transparently falls back to [HeuristicCommandProvider].
 *
 * Recommended integrations (both fully on-device, private — no network):
 *
 *  1. Gemini Nano via AICore / ML Kit GenAI
 *     - Dependency: com.google.mlkit:genai-* (or AICore APIs)
 *     - ~0 MB app size (system-provided); Pixel 8+/select flagships only.
 *     - In [available]: check GenAI feature status == AVAILABLE.
 *     - In [suggest]: prompt the model to classify intent → app / action /
 *       calculation / web, and map the structured reply to Command(s).
 *
 *  2. MediaPipe LLM Inference + a small Gemma (broad device support)
 *     - Dependency: com.google.mediapipe:tasks-genai
 *     - Requires a quantized .task/.bin model downloaded at runtime (do NOT
 *       bundle it — keep the base APK tiny). Lazy-load and cache on disk.
 *     - In [available]: model file present && LlmInference initialised.
 *     - In [suggest]: run inference (debounced, off the main thread) and parse.
 *
 * Keep results to routing/short generation — that's where small on-device
 * models shine — and always leave the heuristic fallback in place.
 */
class AiCommandProvider : CommandProvider {

    /** True only once a real on-device model is loaded and ready. */
    fun available(context: Context): Boolean {
        // TODO: AICore GenAI feature check, or MediaPipe model-loaded check.
        return false
    }

    override fun suggest(context: Context, query: String): List<Command> {
        // TODO: run the on-device model, map its structured intent to Command(s).
        return emptyList()
    }
}

/** Whether an on-device model is actually usable right now (for settings copy). */
fun onDeviceAiAvailable(context: Context): Boolean = AiCommandProvider().available(context)
