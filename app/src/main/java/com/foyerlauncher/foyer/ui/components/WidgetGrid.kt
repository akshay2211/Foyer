package com.foyerlauncher.foyer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.foyerlauncher.foyer.data.ResizeEdge
import com.foyerlauncher.foyer.ui.theme.LocalPalette

/**
 * Pixel-style home edit chrome: faint dots at the interior cell intersections
 * of the widget grid, drawn only while editing.
 */
@Composable
fun GridDots(
    cols: Int,
    rows: Int,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    Canvas(modifier) {
        val cw = size.width / cols
        val ch = size.height / rows
        val r = 1.5.dp.toPx()
        for (c in 1 until cols) {
            for (row in 1 until rows) {
                drawCircle(
                    color = palette.muted.copy(alpha = 0.35f),
                    radius = r,
                    center = Offset(c * cw, row * ch),
                )
            }
        }
    }
}

/**
 * Faint full grid lines, faded in by [alpha] only while a widget is actively
 * being dragged or resized — the cells read clearly right when snapping
 * matters, and recede to the dots otherwise.
 */
@Composable
fun GridLines(
    cols: Int,
    rows: Int,
    alpha: Float,
    modifier: Modifier = Modifier,
) {
    if (alpha <= 0.01f) return
    val palette = LocalPalette.current
    Canvas(modifier) {
        val color = palette.muted.copy(alpha = 0.22f * alpha)
        val stroke = 1.dp.toPx()
        val cw = size.width / cols
        val ch = size.height / rows
        for (c in 1 until cols) {
            drawLine(color, Offset(c * cw, 0f), Offset(c * cw, size.height), stroke)
        }
        for (r in 1 until rows) {
            drawLine(color, Offset(0f, r * ch), Offset(size.width, r * ch), stroke)
        }
    }
}

/**
 * The frame drawn over a widget while editing: a hairline outline (accented
 * when [selected]) plus, on the selected widget, four Pixel-style pill handles
 * centered per edge — each inside a 48dp touch target that drags the resize.
 */
@Composable
fun BoxScope.WidgetEditFrame(
    selected: Boolean,
    onResizeDrag: (ResizeEdge, Offset) -> Unit,
    onResizeEnd: () -> Unit,
    onResizeCancel: () -> Unit,
) {
    val palette = LocalPalette.current
    Box(
        Modifier
            .matchParentSize()
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) palette.accent else palette.muted.copy(alpha = 0.4f),
                shape = RoundedCornerShape(24.dp),
            ),
    )
    if (selected) {
        ResizeHandle(ResizeEdge.LEFT, Alignment.CenterStart, onResizeDrag, onResizeEnd, onResizeCancel)
        ResizeHandle(ResizeEdge.RIGHT, Alignment.CenterEnd, onResizeDrag, onResizeEnd, onResizeCancel)
        ResizeHandle(ResizeEdge.TOP, Alignment.TopCenter, onResizeDrag, onResizeEnd, onResizeCancel)
        ResizeHandle(ResizeEdge.BOTTOM, Alignment.BottomCenter, onResizeDrag, onResizeEnd, onResizeCancel)
    }
}

@Composable
private fun BoxScope.ResizeHandle(
    edge: ResizeEdge,
    alignment: Alignment,
    onDrag: (ResizeEdge, Offset) -> Unit,
    onEnd: () -> Unit,
    onCancel: () -> Unit,
) {
    val palette = LocalPalette.current
    val sideways = edge == ResizeEdge.LEFT || edge == ResizeEdge.RIGHT
    Box(
        Modifier
            .align(alignment)
            .size(48.dp)
            .pointerInput(edge) {
                detectDragGestures(
                    onDrag = { change, amount ->
                        change.consume()
                        onDrag(edge, amount)
                    },
                    onDragEnd = { onEnd() },
                    onDragCancel = { onCancel() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(width = if (sideways) 6.dp else 22.dp, height = if (sideways) 22.dp else 6.dp)
                .clip(RoundedCornerShape(50))
                .background(palette.accent),
        )
    }
}
