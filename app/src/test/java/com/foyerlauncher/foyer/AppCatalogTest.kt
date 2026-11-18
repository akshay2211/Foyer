package com.foyerlauncher.foyer

import com.foyerlauncher.foyer.ai.router.AppCatalog
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for [AppCatalog.pickIndices] — the semantic app-finder's reply parser.
 * Small models produce messy replies, so this must salvage a JSON array of
 * 1-based app numbers, stay inside the offered pool, and never duplicate or
 * overflow the shown-match cap.
 */
class AppCatalogTest {
    private fun pick(
        reply: String,
        pool: Int = 5,
        max: Int = 5,
    ) = AppCatalog.pickIndices(reply, pool, max)

    @Test fun parses_single_index() {
        assertEquals(listOf(2), pick("[2]"))
    }

    @Test fun keeps_model_order_most_relevant_first() {
        assertEquals(listOf(5, 2), pick("[5, 2]"))
    }

    @Test fun drops_out_of_range_numbers() {
        assertEquals(listOf(2), pick("[2, 9]", pool = 3))
    }

    @Test fun ignores_zero_and_keeps_valid() {
        assertEquals(listOf(1), pick("[0, 1]"))
    }

    @Test fun deduplicates_repeated_picks() {
        assertEquals(listOf(2, 3), pick("[2, 2, 3]"))
    }

    @Test fun caps_at_max_results() {
        assertEquals(listOf(1, 2, 3), pick("[1, 2, 3, 4, 5, 6]", max = 3))
    }

    @Test fun empty_array_yields_nothing() {
        assertEquals(emptyList<Int>(), pick("[]"))
    }

    @Test fun no_array_yields_nothing() {
        assertEquals(emptyList<Int>(), pick("none of these fit"))
    }

    @Test fun salvages_array_from_surrounding_prose() {
        assertEquals(listOf(1), pick("the best match is [1] — hope that helps"))
    }
}
