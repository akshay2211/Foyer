package com.foyerlauncher.foyer

import com.foyerlauncher.foyer.data.matchLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The drawer's fuzzy matcher: rank ordering (prefix, word start, substring,
 * letters-in-order) and the highlight ranges each tier reports. Ranges index
 * into the lowercased label, which is also what the drawer rows display.
 */
class FuzzySearchTest {
    @Test fun prefix_outranks_everything() {
        val m = matchLabel("Instagram", "in")!!
        assertEquals(0, m.rank)
        assertEquals(listOf(0..1), m.ranges)
    }

    @Test fun word_start_beats_mid_word() {
        val m = matchLabel("Google Maps", "ma")!!
        assertEquals(1, m.rank)
        assertEquals(listOf(7..8), m.ranges)
    }

    @Test fun substring_matches_anywhere() {
        val linkedin = matchLabel("LinkedIn", "in")!!
        assertEquals(2, linkedin.rank)
        assertEquals(listOf(1..2), linkedin.ranges)

        val pinterest = matchLabel("Pinterest", "in")!!
        assertEquals(2, pinterest.rank)
        assertEquals(listOf(1..2), pinterest.ranges)
    }

    @Test fun letters_in_order_is_the_loosest_tier() {
        val m = matchLabel("WhatsApp", "wsp")!!
        assertEquals(3, m.rank)
        assertEquals(listOf(0..0, 4..4, 6..6), m.ranges)
    }

    @Test fun adjacent_scattered_hits_merge_into_one_range() {
        // l-i land at 0..1, the n jumps to 3 — two ranges, not three.
        val m = matchLabel("Lion", "lin")!!
        assertEquals(3, m.rank)
        assertEquals(listOf(0..1, 3..3), m.ranges)
    }

    @Test fun out_of_order_letters_do_not_match() {
        assertNull(matchLabel("Instagram", "zz"))
        assertNull(matchLabel("Maps", "spam")) // all letters present, wrong order
    }

    @Test fun matching_ignores_case_both_ways() {
        val m = matchLabel("linkedin", "IN")!!
        assertEquals(listOf(1..2), m.ranges)
    }

    @Test fun empty_query_matches_nothing() {
        assertNull(matchLabel("Instagram", ""))
    }
}
