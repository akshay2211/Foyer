package io.ak1.testuilauncher

/**
 * Cheap deterministic text cleanup applied before any routing (§5.2). Keeps the
 * original text for display; this is only for matching. It collapses whitespace
 * and removes a *repeated* leading command verb ("open open developer options"
 * → "open developer options") without stripping the primary verb that Tier-0
 * routing keys off, and without changing meaning.
 */
object Normalizer {
    private val VERBS = setOf(
        "open", "launch", "start", "run", "show", "go", "goto",
    )

    fun normalize(raw: String): String {
        val collapsed = raw.trim().replace(Regex("\\s+"), " ")
        if (collapsed.isEmpty()) return collapsed
        val tokens = collapsed.split(" ").toMutableList()
        // Drop consecutive duplicate leading command verbs: "open open x" -> "open x".
        while (tokens.size >= 2 &&
            tokens[0].lowercase() in VERBS &&
            tokens[0].equals(tokens[1], ignoreCase = true)
        ) {
            tokens.removeAt(0)
        }
        return tokens.joinToString(" ")
    }

    /** The phrase with one leading command verb removed, e.g. "open developer options" → "developer options". */
    fun stripLeadingVerb(normalized: String): String {
        val tokens = normalized.split(" ")
        return if (tokens.size >= 2 && tokens[0].lowercase() in VERBS) {
            tokens.drop(1).joinToString(" ")
        } else {
            normalized
        }
    }
}
