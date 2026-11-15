package com.foyerlauncher.foyer.data

/**
 * How a query hit a label: a rank for ordering (lower is better) plus the
 * matched character ranges — indices into the lowercased label — so rows can
 * highlight exactly the letters that were typed.
 *
 * Ranks: 0 prefix ("in" → INstagram), 1 word start ("ma" → google MAps),
 * 2 substring ("in" → lINkedin), 3 letters in order ("wsp" → WhatSapP).
 */
data class LabelMatch(
    val rank: Int,
    val ranges: List<IntRange>,
)

fun matchLabel(
    label: String,
    query: String,
): LabelMatch? {
    val l = label.lowercase()
    val q = query.lowercase()
    if (q.isEmpty()) return null

    val idx = l.indexOf(q)
    if (idx == 0) return LabelMatch(0, listOf(0 until q.length))
    if (idx > 0) {
        val rank = if (!l[idx - 1].isLetterOrDigit()) 1 else 2
        return LabelMatch(rank, listOf(idx until idx + q.length))
    }

    // Loosest tier: every typed letter appears, in order, not necessarily
    // adjacent. Greedy left-to-right scan; adjacent hits merge into one range.
    val ranges = mutableListOf<IntRange>()
    var from = 0
    for (c in q) {
        val hit = l.indexOf(c, from)
        if (hit < 0) return null
        if (ranges.isNotEmpty() && ranges.last().last == hit - 1) {
            ranges[ranges.lastIndex] = ranges.last().first..hit
        } else {
            ranges += hit..hit
        }
        from = hit + 1
    }
    return LabelMatch(3, ranges)
}
