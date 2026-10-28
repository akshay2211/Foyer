package com.foyerlauncher.foyer.ai.router

import com.foyerlauncher.foyer.data.AppEntry
import com.foyerlauncher.foyer.data.key

/**
 * Precomputed, query-efficient index over the installed apps. Built once per app
 * list (not per query), it replaces the old per-query full scans + double
 * Levenshtein in [CommandRouter]. Two jobs:
 *
 *  - [resolve]  — map a *model-chosen* app name to a real installed app, with a
 *    confidence flag, through ordered tiers (exact → compact → acronym → prefix →
 *    token-prefix → substring → subsequence → fuzzy). Only the fuzzy tier is
 *    "not confident", so the UI knows when to ask.
 *  - [candidates] — top-K apps for a free-text query, used to *ground* the LLM
 *    prompt so the model picks a real installed app instead of hallucinating a
 *    name. This is where device-discovered entries (app shortcuts, deep-link
 *    targets) would also be folded in later — same ranking, richer corpus.
 */
class AppIndex private constructor(
    private val entries: List<Indexed>,
    /** User-taught alias (lowercase) → [AppEntry] — the top resolution tier. */
    private val userAliases: Map<String, AppEntry>,
) {
    private class Indexed(
        val app: AppEntry,
        val label: String, // lowercased full label
        val tokens: List<String>, // ["youtube", "music"]
        val acronym: String, // "ym"
        val compact: String, // "youtubemusic"
    )

    /**
     * The result of resolving a name to an installed app:
     *  - [app] null with empty [candidates]  → no match (say "can't find it"),
     *  - [confident] true                     → open immediately,
     *  - [confident] false                    → ask; [candidates] holds the
     *    "did you mean…" options (best first, includes [app]).
     */
    data class Resolution(
        val app: AppEntry?,
        val confident: Boolean,
        val candidates: List<AppEntry>,
        /** Match strength in [0,1]; drives the confidence policy. */
        val score: Float = 0f,
    ) {
        companion object {
            val NONE = Resolution(null, false, emptyList(), 0f)
        }
    }

    /**
     * Deterministic app resolver — the launcher's job, not the LLM's. Progressive
     * tiers (exact → alias → acronym → prefix → substring → subsequence → fuzzy)
     * each yield a numeric [Resolution.score]; the confidence *decision* (execute /
     * confirm / present candidates / no-match) is derived from the centralized,
     * tunable [ResolverConfig] thresholds (SoT §15) rather than hard-coded per tier.
     */
    fun lookup(
        name: String,
        config: ResolverConfig = ResolverConfig.DEFAULT,
    ): Resolution {
        val q = name.trim().lowercase()
        if (q.isEmpty()) return Resolution.NONE
        val qc = q.filter { it.isLetterOrDigit() }

        // 0. User-taught alias — the user said so; open it, no questions.
        userAliases[q]?.let { return decide(it, 1.0f, emptyList(), config) }

        // 1. Exact (full label or punctuation-stripped).
        entries
            .firstOrNull { it.label == q || (qc.isNotEmpty() && it.compact == qc) }
            ?.let { return decide(it.app, 1.0f, emptyList(), config) }

        // 2. Alias / semantic shortcut ("music" → spotify, "yt" → youtube).
        ALIASES[q]?.let { target ->
            entries
                .firstOrNull { it.label == target || it.label.startsWith(target) || it.label.contains(target) }
                ?.let { return decide(it.app, 0.97f, emptyList(), config) }
        }

        // 3. Acronym ("ym" → YouTube Music). Unique → strong, several → ambiguous.
        if (qc.length >= 2) {
            entries.filter { it.acronym == qc }.ifNotEmpty {
                return decide(it[0].app, if (it.size == 1) 0.95f else 0.60f, it.map { e -> e.app }, config)
            }
        }

        // 4. Prefix — label or any token starts with the query.
        entries.filter { it.label.startsWith(q) || it.tokens.any { t -> t.startsWith(q) } }.ifNotEmpty {
            return decide(it[0].app, if (it.size == 1) 0.92f else 0.60f, it.map { e -> e.app }, config)
        }

        // 5. Substring.
        entries.filter { it.label.contains(q) }.ifNotEmpty {
            return decide(it[0].app, if (it.size == 1) 0.90f else 0.55f, it.map { e -> e.app }, config)
        }

        // 6. Subsequence — dropped-letter typos ("spotfy" → Spotify). Gated on length
        //    so a short query isn't spuriously found scattered across a long name
        //    ("amol" ⊄ "warmup & cooldown"): at most 2 letters may be missing.
        if (qc.length >= 3) {
            entries
                .firstOrNull {
                    isSubsequence(qc, it.compact) && qc.length >= it.compact.length - 2
                }?.let { return decide(it.app, 0.90f, emptyList(), config) }
        }

        // 7. Fuzzy edit-distance → score scaled by distance; thresholds decide.
        val ranked = entries.map { it to levenshtein(q, it.label) }.sortedBy { it.second }
        val best = ranked.firstOrNull() ?: return Resolution.NONE
        val score = 1f - best.second.toFloat() / maxOf(q.length, best.first.label.length)
        val close =
            ranked
                .filter { it.second <= maxOf(3, it.first.label.length / 2) }
                .take(4)
                .map { it.first.app }
        return decide(best.first.app, score, close, config)
    }

    /** Applies the centralized confidence bands to a scored match (SoT §15). */
    private fun decide(
        app: AppEntry,
        score: Float,
        candidates: List<AppEntry>,
        config: ResolverConfig,
    ): Resolution =
        when {
            score < config.candidateThreshold -> Resolution.NONE // < 0.40 → no match
            score >= config.autoThreshold -> Resolution(app, true, emptyList(), score) // ≥ 0.90 → execute
            score >= config.confirmThreshold -> Resolution(app, false, listOf(app), score) // 0.70–0.89 → confirm best
            else -> Resolution(app, false, candidates.ifEmpty { listOf(app) }, score) // 0.40–0.69 → candidates
        }

    private inline fun <T> List<T>.ifNotEmpty(block: (List<T>) -> Unit) {
        if (isNotEmpty()) block(this)
    }

    /** Top-K installed apps most relevant to [query], for grounding the prompt. */
    fun candidates(
        query: String,
        k: Int,
    ): List<AppEntry> {
        val qTokens = tokenize(query).filter { it !in STOP }
        if (qTokens.isEmpty()) return emptyList()
        return entries
            .map { it to score(qTokens, it) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(k)
            .map { it.first.app }
    }

    private fun score(
        qTokens: List<String>,
        e: Indexed,
    ): Int {
        var s = 0
        for (qt in qTokens) {
            s +=
                when {
                    e.tokens.any { it == qt } -> 4
                    e.tokens.any { it.startsWith(qt) || qt.startsWith(it) } -> 3
                    e.label.contains(qt) -> 2
                    qt.length >= 3 && isSubsequence(qt, e.compact) -> 1
                    else -> 0
                }
        }
        return s
    }

    companion object {
        private val STOP = setOf("open", "launch", "start", "run", "the", "a", "an", "my", "app", "to", "in", "on")

        /**
         * Local, offline nickname/semantic shortcuts. Each maps to a keyword that
         * is then resolved against the *installed* apps, so an alias only fires if
         * the target is actually present (else it falls through to fuzzy).
         */
        private val ALIASES =
            mapOf(
                "maps" to "maps",
                "map" to "maps",
                "navigation" to "maps",
                "gps" to "maps",
                "music" to "spotify",
                "mail" to "gmail",
                "email" to "gmail",
                "photos" to "photos",
                "gallery" to "photos",
                "yt" to "youtube",
                "insta" to "instagram",
                "ig" to "instagram",
                "wp" to "whatsapp",
                "whatsap" to "whatsapp",
                "fb" to "facebook",
                "browser" to "chrome",
                "calc" to "calculator",
                "clock" to "clock",
                "alarm" to "clock",
            )

        /**
         * [userAliases] maps alias text → [AppEntry.key]; entries whose key no
         * longer resolves to an installed app are dropped here.
         */
        fun of(
            apps: List<AppEntry>,
            userAliases: Map<String, String> = emptyMap(),
        ): AppIndex =
            AppIndex(
                apps.map { app ->
                    val label = app.label.lowercase()
                    val tokens = tokenize(label)
                    Indexed(
                        app = app,
                        label = label,
                        tokens = tokens,
                        acronym = tokens.mapNotNull { it.firstOrNull() }.joinToString(""),
                        compact = label.filter { it.isLetterOrDigit() },
                    )
                },
                userAliases =
                    buildMap {
                        if (userAliases.isNotEmpty()) {
                            val byKey = apps.associateBy { "${it.packageName}/${it.activityName}" + if (it.userSerial != 0L) "/${it.userSerial}" else "" }
                            userAliases.forEach { (alias, key) -> byKey[key]?.let { put(alias.lowercase(), it) } }
                        }
                    },
            )

        private fun tokenize(s: String): List<String> = s.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() }

        /** True if [needle] appears in [haystack] in order (not necessarily contiguous). */
        private fun isSubsequence(
            needle: String,
            haystack: String,
        ): Boolean {
            if (needle.isEmpty()) return true
            var i = 0
            for (c in haystack) if (c == needle[i] && ++i == needle.length) return true
            return false
        }

        fun levenshtein(
            a: String,
            b: String,
        ): Int {
            if (a == b) return 0
            if (a.isEmpty()) return b.length
            if (b.isEmpty()) return a.length
            var prev = IntArray(b.length + 1) { it }
            var curr = IntArray(b.length + 1)
            for (i in 1..a.length) {
                curr[0] = i
                for (j in 1..b.length) {
                    val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                    curr[j] = minOf(prev[j] + 1, curr[j - 1] + 1, prev[j - 1] + cost)
                }
                val tmp = prev
                prev = curr
                curr = tmp
            }
            return prev[b.length]
        }
    }
}
