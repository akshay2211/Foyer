package com.foyerlauncher.foyer.ai.engine

import android.util.Log

/**
 * Lightweight, on-device assistant metrics (SoT §36/§37). Tracks how often the
 * deterministic path handles a request vs. how often we fall back to the model,
 * plus model latency — so we can verify "deterministic commands cost 0 LLM calls"
 * and later judge whether prefix-caching actually helps. No data leaves the
 * device; only aggregate counters are logged.
 */
object AiMetrics {
    private var requests = 0L
    private var llmCalls = 0L
    private var llmFailures = 0L
    private var totalLatencyMs = 0L
    private var lastLatencyMs = 0L
    private var lastPromptChars = 0

    /** One user request entered the pipeline (deterministic or not). */
    @Synchronized fun recordRequest() {
        requests++
    }

    /** One model invocation completed (or failed). */
    @Synchronized fun recordLlm(
        latencyMs: Long,
        ok: Boolean,
        promptChars: Int,
    ) {
        llmCalls++
        if (!ok) llmFailures++
        totalLatencyMs += latencyMs
        lastLatencyMs = latencyMs
        lastPromptChars = promptChars
        val rate = if (requests > 0) llmCalls.toDouble() / requests else 0.0
        val avg = if (llmCalls > 0) totalLatencyMs / llmCalls else 0
        Log.d(
            AI_TAG,
            "llm.invoked=true latencyMs=$latencyMs ok=$ok promptChars=$promptChars ~tokens=${promptChars / 4} " +
                "avgMs=$avg llmRate=${"%.2f".format(rate)} failures=$llmFailures",
        )
    }

    /** Human-readable snapshot (e.g. for a debug/settings readout). */
    @Synchronized fun snapshot(): String {
        val rate = if (requests > 0) llmCalls.toDouble() / requests else 0.0
        val avg = if (llmCalls > 0) totalLatencyMs / llmCalls else 0
        return "requests=$requests · llm=$llmCalls (${"%.0f".format(rate * 100)}%) · " +
            "avg ${avg}ms · last ${lastLatencyMs}ms · ~${lastPromptChars / 4} tok · fail=$llmFailures"
    }
}
