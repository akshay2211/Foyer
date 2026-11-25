package com.foyerlauncher.foyer.data

/** The movable home-screen widgets. */
enum class HomeWidget { CLOCK, FAVORITES }

/**
 * The selectable home-grid densities (chosen inside edit mode, Pixel-style).
 * Positions are stored in cell units, so switching reinterprets the layout;
 * [fitLayout] re-clamps it into the new grid.
 */
enum class GridSize(
    val cols: Int,
    val rows: Int,
    val label: String,
) {
    COZY(5, 8, "5×8"),
    DENSE(6, 10, "6×10"),
    FINE(7, 12, "7×12"),
}

/** Position and size of one widget, in grid cells. */
data class WidgetSpec(
    val col: Int,
    val row: Int,
    val spanW: Int,
    val spanH: Int,
)

/** Span limits a widget can be resized within. */
data class WidgetLimits(
    val minW: Int,
    val minH: Int,
    val maxW: Int,
    val maxH: Int,
)

fun limitsFor(
    widget: HomeWidget,
    cols: Int,
    rows: Int,
): WidgetLimits =
    when (widget) {
        HomeWidget.CLOCK ->
            WidgetLimits(
                minW = 2.coerceAtMost(cols),
                minH = 1,
                maxW = cols,
                maxH = (rows / 2).coerceAtLeast(1),
            )
        HomeWidget.FAVORITES ->
            WidgetLimits(
                minW = 2.coerceAtMost(cols),
                minH = 2.coerceAtMost(rows),
                maxW = cols,
                maxH = (rows - 1).coerceAtLeast(1),
            )
    }

/**
 * Defaults reproduce the pre-widget home proportions on any grid: clock
 * lockup across the top third, favorites down the left spine below it.
 */
fun defaultSpec(
    widget: HomeWidget,
    cols: Int,
    rows: Int,
): WidgetSpec {
    val clockH = (rows / 3).coerceAtLeast(1)
    val raw =
        when (widget) {
            HomeWidget.CLOCK -> WidgetSpec(col = 0, row = 0, spanW = cols, spanH = clockH)
            HomeWidget.FAVORITES -> WidgetSpec(col = 0, row = clockH, spanW = (cols + 1) / 2, spanH = rows - clockH)
        }
    return clampSpec(widget, raw, cols, rows)
}

fun defaultLayout(
    cols: Int,
    rows: Int,
): Map<HomeWidget, WidgetSpec> = HomeWidget.entries.associateWith { defaultSpec(it, cols, rows) }

fun WidgetSpec.overlaps(other: WidgetSpec): Boolean =
    col < other.col + other.spanW &&
        other.col < col + spanW &&
        row < other.row + other.spanH &&
        other.row < row + spanH

/** Clamps spans to the widget's limits and keeps the whole box inside the grid. */
fun clampSpec(
    widget: HomeWidget,
    spec: WidgetSpec,
    cols: Int,
    rows: Int,
): WidgetSpec {
    val limits = limitsFor(widget, cols, rows)
    val w = spec.spanW.coerceIn(limits.minW, limits.maxW).coerceAtMost(cols)
    val h = spec.spanH.coerceIn(limits.minH, limits.maxH).coerceAtMost(rows)
    return WidgetSpec(
        col = spec.col.coerceIn(0, cols - w),
        row = spec.row.coerceIn(0, rows - h),
        spanW = w,
        spanH = h,
    )
}

/**
 * Re-clamps a layout into a (possibly different) grid. An overlap after
 * clamping reverts favorites (then clock, if still needed) to that grid's
 * defaults — never stacked widgets.
 */
fun fitLayout(
    layout: Map<HomeWidget, WidgetSpec>,
    cols: Int,
    rows: Int,
): Map<HomeWidget, WidgetSpec> {
    val out = layout.mapValues { (widget, spec) -> clampSpec(widget, spec, cols, rows) }.toMutableMap()
    HomeWidget.entries.forEach { if (it !in out) out[it] = defaultSpec(it, cols, rows) }
    if (out.getValue(HomeWidget.CLOCK).overlaps(out.getValue(HomeWidget.FAVORITES))) {
        out[HomeWidget.FAVORITES] = defaultSpec(HomeWidget.FAVORITES, cols, rows)
        if (out.getValue(HomeWidget.CLOCK).overlaps(out.getValue(HomeWidget.FAVORITES))) {
            out[HomeWidget.CLOCK] = defaultSpec(HomeWidget.CLOCK, cols, rows)
        }
    }
    return out
}

/** Which edge of a selected widget a resize handle drags. */
enum class ResizeEdge { LEFT, RIGHT, TOP, BOTTOM }

/** Snaps a finished move drag ([dxCells]/[dyCells] in cell units) to the grid. */
fun snapMove(
    widget: HomeWidget,
    spec: WidgetSpec,
    dxCells: Float,
    dyCells: Float,
    cols: Int,
    rows: Int,
): WidgetSpec =
    clampSpec(
        widget,
        spec.copy(
            col = Math.round(spec.col + dxCells),
            row = Math.round(spec.row + dyCells),
        ),
        cols,
        rows,
    )

/**
 * Snaps a finished resize drag of [edge] by [dCells] (cell units along the
 * edge's axis). The opposite edge stays anchored, like Pixel's resize.
 */
fun snapResize(
    widget: HomeWidget,
    spec: WidgetSpec,
    edge: ResizeEdge,
    dCells: Float,
    cols: Int,
    rows: Int,
): WidgetSpec {
    val d = Math.round(dCells)
    val limits = limitsFor(widget, cols, rows)
    val resized =
        when (edge) {
            ResizeEdge.LEFT -> {
                val right = spec.col + spec.spanW
                val spanW = (spec.spanW - d).coerceIn(limits.minW, minOf(limits.maxW, right))
                spec.copy(col = right - spanW, spanW = spanW)
            }
            ResizeEdge.RIGHT -> {
                val spanW = (spec.spanW + d).coerceIn(limits.minW, minOf(limits.maxW, cols - spec.col))
                spec.copy(spanW = spanW)
            }
            ResizeEdge.TOP -> {
                val bottom = spec.row + spec.spanH
                val spanH = (spec.spanH - d).coerceIn(limits.minH, minOf(limits.maxH, bottom))
                spec.copy(row = bottom - spanH, spanH = spanH)
            }
            ResizeEdge.BOTTOM -> {
                val spanH = (spec.spanH + d).coerceIn(limits.minH, minOf(limits.maxH, rows - spec.row))
                spec.copy(spanH = spanH)
            }
        }
    return clampSpec(widget, resized, cols, rows)
}

fun encodeLayout(layout: Map<HomeWidget, WidgetSpec>): String =
    layout.entries.joinToString(REC_SEP) { (widget, s) ->
        "${widget.name}$UNIT_SEP${s.col}$UNIT_SEP${s.row}$UNIT_SEP${s.spanW}$UNIT_SEP${s.spanH}"
    }

/**
 * Decodes a persisted layout, falling back per widget on anything suspect:
 * unknown names are skipped, bad numbers revert that widget to its default,
 * and an overlapping pair reverts via [fitLayout] — a corrupt pref string
 * must never crash or produce stacked widgets.
 */
fun decodeLayout(
    raw: String?,
    cols: Int,
    rows: Int,
): Map<HomeWidget, WidgetSpec> {
    val out = defaultLayout(cols, rows).toMutableMap()
    raw?.split(REC_SEP)?.forEach { rec ->
        val p = rec.split(UNIT_SEP)
        if (p.size != 5) return@forEach
        val widget = runCatching { HomeWidget.valueOf(p[0]) }.getOrNull() ?: return@forEach
        val col = p[1].toIntOrNull() ?: return@forEach
        val row = p[2].toIntOrNull() ?: return@forEach
        val spanW = p[3].toIntOrNull() ?: return@forEach
        val spanH = p[4].toIntOrNull() ?: return@forEach
        out[widget] = WidgetSpec(col, row, spanW, spanH)
    }
    return fitLayout(out, cols, rows)
}
