package com.foyerlauncher.foyer.ai.router

import com.foyerlauncher.foyer.data.AppEntry

/**
 * Answers questions *about* the installed apps deterministically from the local
 * index (SRS §6) — "how many music apps", "list my games" — so the LLM never has
 * to guess a count or invent an inventory. Matching is by Android's declared app
 * category (ApplicationInfo.CATEGORY_*), with a label-keyword fallback.
 */
object AppCatalog {

    // Category word -> ApplicationInfo.CATEGORY_* constant.
    private val CATEGORIES = mapOf(
        "game" to 0, "games" to 0, "gaming" to 0,
        "music" to 1, "audio" to 1, "podcast" to 1, "podcasts" to 1,
        "video" to 2, "streaming" to 2, "movie" to 2, "movies" to 2,
        "photo" to 3, "photos" to 3, "photography" to 3, "image" to 3, "images" to 3,
        "social" to 4, "social media" to 4,
        "news" to 5,
        "maps" to 6, "navigation" to 6,
        "productivity" to 7, "office" to 7, "work" to 7,
    )

    /** Apps matching a category word (preferred) or, failing that, a label keyword. */
    fun query(apps: List<AppEntry>, raw: String): List<AppEntry> {
        val q = raw.trim().lowercase().removeSuffix(" apps").removeSuffix(" app").trim()
        CATEGORIES[q]?.let { cat ->
            val byCat = apps.filter { it.category == cat }.sortedBy { it.label.lowercase() }
            if (byCat.isNotEmpty()) return byCat
        }
        if (q.isBlank()) return apps
        return apps.filter { it.label.lowercase().contains(q) }.sortedBy { it.label.lowercase() }
    }

    /** A short, minimal-UX answer for an inventory question. */
    fun describe(apps: List<AppEntry>, query: String): String {
        val q = query.trim().lowercase().removeSuffix(" apps").removeSuffix(" app").trim()
        if (q.isBlank()) return "you have ${apps.size} apps installed"
        val matches = query(apps, q)
        if (matches.isEmpty()) return "i don't see any $q apps on your phone"
        val names = matches.take(8).joinToString(", ") { it.label }
        val more = if (matches.size > 8) ", …" else ""
        val noun = if (matches.size == 1) "app" else "apps"
        return "you have ${matches.size} $q $noun: $names$more"
    }
}
