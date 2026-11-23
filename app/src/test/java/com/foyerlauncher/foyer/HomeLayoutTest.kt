package com.foyerlauncher.foyer

import com.foyerlauncher.foyer.data.GridSize
import com.foyerlauncher.foyer.data.HomeWidget
import com.foyerlauncher.foyer.data.ResizeEdge
import com.foyerlauncher.foyer.data.WidgetSpec
import com.foyerlauncher.foyer.data.clampSpec
import com.foyerlauncher.foyer.data.decodeLayout
import com.foyerlauncher.foyer.data.defaultLayout
import com.foyerlauncher.foyer.data.defaultSpec
import com.foyerlauncher.foyer.data.encodeLayout
import com.foyerlauncher.foyer.data.fitLayout
import com.foyerlauncher.foyer.data.limitsFor
import com.foyerlauncher.foyer.data.overlaps
import com.foyerlauncher.foyer.data.snapMove
import com.foyerlauncher.foyer.data.snapResize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// The default grid (5×8) — most cases run on it; grid-switch cases cover the rest.
private const val COLS = 5
private const val ROWS = 8

/**
 * Pins the persisted home-widget layout format and its defensive decoding.
 * This string lives in users' SharedPreferences (and backups) — a change that
 * breaks decoding scrambles their home screen, so it's locked down here.
 */
class HomeLayoutTest {
    @Test fun cozy_defaults_keep_the_classic_proportions() {
        // Clock across the top quarter, favorites down the left spine below.
        assertEquals(WidgetSpec(0, 0, 5, 2), defaultSpec(HomeWidget.CLOCK, COLS, ROWS))
        assertEquals(WidgetSpec(0, 2, 3, 6), defaultSpec(HomeWidget.FAVORITES, COLS, ROWS))
    }

    @Test fun defaults_round_trip() {
        assertEquals(defaultLayout(COLS, ROWS), decodeLayout(encodeLayout(defaultLayout(COLS, ROWS)), COLS, ROWS))
    }

    @Test fun custom_layout_round_trips() {
        val layout =
            mapOf(
                HomeWidget.CLOCK to WidgetSpec(0, 4, 2, 2),
                HomeWidget.FAVORITES to WidgetSpec(2, 0, 2, 4),
            )
        assertEquals(layout, decodeLayout(encodeLayout(layout), COLS, ROWS))
    }

    @Test fun defaults_do_not_overlap_and_fit_every_grid() {
        GridSize.entries.forEach { grid ->
            val layout = defaultLayout(grid.cols, grid.rows)
            val clock = layout.getValue(HomeWidget.CLOCK)
            val favs = layout.getValue(HomeWidget.FAVORITES)
            assertFalse("defaults overlap on ${grid.label}", clock.overlaps(favs))
            layout.forEach { (widget, spec) ->
                assertEquals("default not stable on ${grid.label}", spec, clampSpec(widget, spec, grid.cols, grid.rows))
            }
        }
    }

    @Test fun null_blank_and_garbage_fall_back_to_defaults() {
        assertEquals(defaultLayout(COLS, ROWS), decodeLayout(null, COLS, ROWS))
        assertEquals(defaultLayout(COLS, ROWS), decodeLayout("", COLS, ROWS))
        assertEquals(defaultLayout(COLS, ROWS), decodeLayout("total nonsense", COLS, ROWS))
        assertEquals(defaultLayout(COLS, ROWS), decodeLayout("CLOCKxyzw", COLS, ROWS))
        assertEquals(defaultLayout(COLS, ROWS), decodeLayout("SIDEBAR0022", COLS, ROWS))
    }

    @Test fun partial_record_only_replaces_that_widget() {
        // Clock moved beside the default favorites (cols 0-2, rows 2-7).
        val raw = "CLOCK3222"
        val decoded = decodeLayout(raw, COLS, ROWS)
        assertEquals(WidgetSpec(3, 2, 2, 2), decoded.getValue(HomeWidget.CLOCK))
        assertEquals(defaultSpec(HomeWidget.FAVORITES, COLS, ROWS), decoded.getValue(HomeWidget.FAVORITES))
    }

    @Test fun out_of_grid_specs_are_clamped_inside() {
        HomeWidget.entries.forEach { widget ->
            val wild = clampSpec(widget, WidgetSpec(99, -3, 99, 0), COLS, ROWS)
            val limits = limitsFor(widget, COLS, ROWS)
            assertTrue(wild.col >= 0 && wild.col + wild.spanW <= COLS)
            assertTrue(wild.row >= 0 && wild.row + wild.spanH <= ROWS)
            assertTrue(wild.spanW in limits.minW..limits.maxW)
            assertTrue(wild.spanH in limits.minH..limits.maxH)
        }
    }

    @Test fun overlapping_decoded_pair_reverts_favorites() {
        // Clock stretched to rows 0-2 overlaps favorites placed at rows 1-4.
        val raw =
            "CLOCK0043" +
                "FAVORITES0124"
        val decoded = decodeLayout(raw, COLS, ROWS)
        // Favorites reverts first; default favorites (rows 2-5) still overlap
        // a 3-row clock, so the clock reverts too — never stacked widgets.
        assertEquals(defaultSpec(HomeWidget.FAVORITES, COLS, ROWS), decoded.getValue(HomeWidget.FAVORITES))
        assertEquals(defaultSpec(HomeWidget.CLOCK, COLS, ROWS), decoded.getValue(HomeWidget.CLOCK))
        assertFalse(decoded.getValue(HomeWidget.CLOCK).overlaps(decoded.getValue(HomeWidget.FAVORITES)))
    }

    @Test fun snap_move_rounds_to_nearest_cell_and_stays_in_grid() {
        val clock = WidgetSpec(0, 0, 2, 2)
        // 0.6 cells right, 1.4 cells down → snaps to col 1, row 1.
        assertEquals(WidgetSpec(1, 1, 2, 2), snapMove(HomeWidget.CLOCK, clock, 0.6f, 1.4f, COLS, ROWS))
        // A wild fling clamps to the far corner (col 5-2, row 8-2).
        assertEquals(WidgetSpec(3, 6, 2, 2), snapMove(HomeWidget.CLOCK, clock, 99f, 99f, COLS, ROWS))
    }

    @Test fun snap_resize_anchors_the_opposite_edge() {
        val spec = WidgetSpec(1, 1, 2, 2)
        // Dragging the right edge out one cell widens in place.
        assertEquals(WidgetSpec(1, 1, 3, 2), snapResize(HomeWidget.FAVORITES, spec, ResizeEdge.RIGHT, 1.2f, COLS, ROWS))
        // Dragging the left edge out one cell keeps the right edge at col 3.
        assertEquals(WidgetSpec(0, 1, 3, 2), snapResize(HomeWidget.FAVORITES, spec, ResizeEdge.LEFT, -0.8f, COLS, ROWS))
        // Shrinking below the minimum span holds at the minimum.
        assertEquals(WidgetSpec(1, 1, 2, 2), snapResize(HomeWidget.FAVORITES, spec, ResizeEdge.BOTTOM, -5f, COLS, ROWS))
        // Growing past the grid edge stops at the grid.
        assertEquals(WidgetSpec(1, 1, 2, 7), snapResize(HomeWidget.FAVORITES, spec, ResizeEdge.BOTTOM, 99f, COLS, ROWS))
    }

    @Test fun switching_grids_refits_without_overlap() {
        GridSize.entries.forEach { from ->
            GridSize.entries.forEach { to ->
                val moved =
                    mapOf(
                        HomeWidget.CLOCK to WidgetSpec(0, from.rows - 2, from.cols, 2),
                        HomeWidget.FAVORITES to WidgetSpec(from.cols - 2, 0, 2, from.rows - 2),
                    )
                val fitted = fitLayout(moved, to.cols, to.rows)
                val clock = fitted.getValue(HomeWidget.CLOCK)
                val favs = fitted.getValue(HomeWidget.FAVORITES)
                assertFalse("overlap after ${from.label} → ${to.label}", clock.overlaps(favs))
                fitted.forEach { (widget, spec) ->
                    assertEquals(
                        "out of grid after ${from.label} → ${to.label}",
                        spec,
                        clampSpec(widget, spec, to.cols, to.rows),
                    )
                }
            }
        }
    }

    @Test fun overlap_is_symmetric_and_edge_touching_is_not_overlap() {
        val a = WidgetSpec(0, 0, 2, 2)
        val b = WidgetSpec(1, 1, 2, 2)
        val beside = WidgetSpec(2, 0, 2, 2)
        assertTrue(a.overlaps(b))
        assertTrue(b.overlaps(a))
        assertFalse(a.overlaps(beside))
        assertFalse(beside.overlaps(a))
    }
}
