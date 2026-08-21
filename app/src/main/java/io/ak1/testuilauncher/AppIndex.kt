package io.ak1.testuilauncher

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
class AppIndex private constructor(private val entries: List<Indexed>) {

    private class Indexed(
        val app: AppEntry,
        val label: String,          // lowercased full label
        val tokens: List<String>,   // ["youtube", "music"]
        val acronym: String,        // "ym"
        val compact: String,        // "youtubemusic"
    )

    /**
     * The result of resolving a name to an installed app:
     *  - [app] null with empty [candidates]  → no match (say "can't find it"),
     *  - [confident] true                     → open immediately,
     *  - [confident] false                    → ask; [candidates] holds the
     *    "did you mean…" options (best first, includes [app]).
     */
    data class Resolution(val app: AppEntry?, val confident: Boolean, val candidates: List<AppEntry>) {
        companion object {
            val NONE = Resolution(null, false, emptyList())
            fun sure(app: AppEntry) = Resolution(app, true, emptyList())
        }
    }

    /**
     * Deterministic app resolver — the launcher's job, not the LLM's. Progressive
     * tiers: exact → alias → acronym → prefix → substring → subsequence → fuzzy.
     * A tier that matches exactly one app is confident (open now); a tier that
     * matches several is ambiguous (ask which); fuzzy is always ask; nothing
     * within the fuzzy threshold is a no-match.
     */
    fun lookup(name: String): Resolution {
        val q = name.trim().lowercase()
        if (q.isEmpty()) return Resolution.NONE
        val qc = q.filter { it.isLetterOrDigit() }

        // 1. Exact (full label or punctuation-stripped) — always confident.
        entries.firstOrNull { it.label == q || (qc.isNotEmpty() && it.compact == qc) }
            ?.let { return Resolution.sure(it.app) }

        // 2. Alias / semantic shortcut ("music" → spotify, "yt" → youtube).
        ALIASES[q]?.let { target ->
            entries.firstOrNull { it.label == target || it.label.startsWith(target) || it.label.contains(target) }
                ?.let { return Resolution.sure(it.app) }
        }

        // 3. Acronym ("ym" → YouTube Music).
        if (qc.length >= 2) tierResult(entries.filter { it.acronym == qc })?.let { return it }

        // 4. Prefix — label or any token starts with the query. Unique → confident,
        //    several ("google" → Google, Google Maps, …) → ask which.
        tierResult(entries.filter { it.label.startsWith(q) || it.tokens.any { t -> t.startsWith(q) } })?.let { return it }

        // 5. Substring.
        tierResult(entries.filter { it.label.contains(q) })?.let { return it }

        // 6. Subsequence — handles dropped letters ("spotfy" → Spotify) as confident.
        if (qc.length >= 3) entries.firstOrNull { isSubsequence(qc, it.compact) }
            ?.let { return Resolution.sure(it.app) }

        // 7. Fuzzy edit-distance — always ask; nothing close enough → no match.
        val ranked = entries.map { it to levenshtein(q, it.label) }.sortedBy { it.second }
        val best = ranked.firstOrNull() ?: return Resolution.NONE
        if (best.second > maxOf(2, best.first.label.length / 3)) return Resolution.NONE
        val candidates = ranked.filter { it.second <= maxOf(3, it.first.label.length / 2) }
            .take(4).map { it.first.app }
        return Resolution(best.first.app, false, candidates.ifEmpty { listOf(best.first.app) })
    }

    /** A tier hit: one match → confident, several → ambiguous list, none → null (try next tier). */
    private fun tierResult(hits: List<Indexed>): Resolution? = when {
        hits.isEmpty() -> null
        hits.size == 1 -> Resolution.sure(hits[0].app)
        else -> Resolution(hits[0].app, false, hits.take(4).map { it.app })
    }

    /** Top-K installed apps most relevant to [query], for grounding the prompt. */
    fun candidates(query: String, k: Int): List<AppEntry> {
        val qTokens = tokenize(query).filter { it !in STOP }
        if (qTokens.isEmpty()) return emptyList()
        return entries
            .map { it to score(qTokens, it) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(k)
            .map { it.first.app }
    }

    private fun score(qTokens: List<String>, e: Indexed): Int {
        var s = 0
        for (qt in qTokens) {
            s += when {
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
        private val ALIASES = mapOf(
            "maps" to "maps", "map" to "maps", "navigation" to "maps", "gps" to "maps",
            "music" to "spotify",
            "mail" to "gmail", "email" to "gmail",
            "photos" to "photos", "gallery" to "photos",
            "yt" to "youtube",
            "insta" to "instagram", "ig" to "instagram",
            "wp" to "whatsapp", "whatsap" to "whatsapp",
            "fb" to "facebook",
            "browser" to "chrome",
            "calc" to "calculator",
            "clock" to "clock", "alarm" to "clock",
        )

        fun of(apps: List<AppEntry>): AppIndex = AppIndex(
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
        )

        private fun tokenize(s: String): List<String> =
            s.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() }

        /** True if [needle] appears in [haystack] in order (not necessarily contiguous). */
        private fun isSubsequence(needle: String, haystack: String): Boolean {
            if (needle.isEmpty()) return true
            var i = 0
            for (c in haystack) if (c == needle[i] && ++i == needle.length) return true
            return false
        }

        fun levenshtein(a: String, b: String): Int {
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
                val tmp = prev; prev = curr; curr = tmp
            }
            return prev[b.length]
        }
    }
}