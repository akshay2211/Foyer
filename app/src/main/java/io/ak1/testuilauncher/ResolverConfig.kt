package io.ak1.testuilauncher

/**
 * Centralized, tunable confidence policy for app resolution (SoT §15). These are
 * *starting* values, not permanent constants — keep them here (not scattered
 * through the code) so they can be tuned from real device tests.
 *
 *   score ≥ [autoThreshold]      → execute automatically
 *   [confirmThreshold]‥          → ask confirmation of the single best match
 *   [candidateThreshold]‥        → present a candidate list
 *   < [candidateThreshold]       → no match
 */
data class ResolverConfig(
    val autoThreshold: Float = 0.90f,
    val confirmThreshold: Float = 0.70f,
    val candidateThreshold: Float = 0.40f,
) {
    companion object {
        val DEFAULT = ResolverConfig()
    }
}
