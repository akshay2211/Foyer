package com.foyerlauncher.foyer.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Icon-only action (Tabler glyph) that keeps its text: [label] is the
 * long-press tooltip and the content description, so the action stays
 * nameable for TalkBack even though only the icon is drawn.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IconAction(
    @DrawableRes icon: Int,
    label: String,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    onClick: () -> Unit,
) {
    // The caller's modifier goes on a plain Box: TooltipBox drops parent-data
    // modifiers like BoxScope.align, which would silently reposition the icon.
    Box(modifier) {
        TooltipBox(
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(),
            tooltip = { PlainTooltip { Text(label) } },
            state = rememberTooltipState(),
        ) {
            IconButton(onClick = onClick) {
                Icon(
                    painterResource(icon),
                    contentDescription = label,
                    tint = tint,
                    modifier = Modifier.size(size),
                )
            }
        }
    }
}
