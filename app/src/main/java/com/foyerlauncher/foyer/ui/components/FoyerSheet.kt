package com.foyerlauncher.foyer.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foyerlauncher.foyer.ui.theme.LocalFontFamily
import com.foyerlauncher.foyer.ui.theme.LocalPalette
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Foyer's reusable bottom sheet — the one pop-up surface for long-press menus
 * and pickers. Foundation-only (no Material): spring slide-up, drag-to-dismiss,
 * scrim tap / back to close with the reverse animation, long-press haptic on
 * open. [title] is the muted header tag (usually the app label).
 */
@Composable
fun FoyerSheet(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = LocalPalette.current
    val ff = LocalFontFamily.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val transition = remember { MutableTransitionState(false).apply { targetState = true } }
    LaunchedEffect(Unit) { haptics.performHapticFeedback(HapticFeedbackType.LongPress) }

    fun close() {
        transition.targetState = false
    }
    // Exit animation finished → actually remove the overlay.
    LaunchedEffect(transition.currentState, transition.targetState) {
        if (!transition.targetState && !transition.currentState) onDismiss()
    }
    BackHandler { close() }

    val scrimAlpha by animateFloatAsState(if (transition.targetState) 0.55f else 0f, label = "sheetScrim")
    val dragOffset = remember { Animatable(0f) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = scrimAlpha))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { close() },
    ) {
        AnimatedVisibility(
            visibleState = transition,
            enter =
                slideInVertically(
                    spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
                ) { it },
            exit = slideOutVertically(tween(160)) { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                Modifier
                    .offset { IntOffset(0, dragOffset.value.roundToInt().coerceAtLeast(0)) }
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                    .background(palette.background)
                    // Swallow taps so touching the sheet doesn't hit the scrim.
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {}
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { change, delta ->
                                change.consume()
                                scope.launch { dragOffset.snapTo((dragOffset.value + delta).coerceAtLeast(0f)) }
                            },
                            onDragEnd = {
                                if (dragOffset.value > size.height * 0.3f) {
                                    close()
                                } else {
                                    scope.launch { dragOffset.animateTo(0f, spring()) }
                                }
                            },
                            onDragCancel = { scope.launch { dragOffset.animateTo(0f) } },
                        )
                    }.windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 26.dp, vertical = 14.dp),
            ) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = 14.dp)
                        .size(width = 34.dp, height = 3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(palette.muted.copy(alpha = 0.4f)),
                )
                BasicText(
                    text = title,
                    modifier = Modifier.padding(bottom = 4.dp),
                    style = TextStyle(color = palette.muted, fontSize = 13.sp, letterSpacing = 1.sp, fontFamily = ff),
                )
                content()
            }
        }
    }
}

/**
 * One action row in a [FoyerSheet]. [active] marks a toggled-on state with an
 * accent dot (and announces it), so state is visible without parsing the verb.
 */
@Composable
fun SheetRow(
    text: String,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    val palette = LocalPalette.current
    val ff = LocalFontFamily.current
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .semantics {
                    role = Role.Button
                    if (active) stateDescription = "on"
                }.clickable { onClick() }
                .padding(vertical = 13.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(text, style = TextStyle(color = palette.text, fontSize = 18.sp, fontFamily = ff))
        if (active) {
            Box(
                Modifier
                    .size(7.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(palette.accent),
            )
        }
    }
}

/** A non-interactive explanatory line inside a sheet. */
@Composable
fun SheetNote(text: String) {
    val palette = LocalPalette.current
    val ff = LocalFontFamily.current
    BasicText(
        text = text,
        modifier = Modifier.padding(vertical = 10.dp),
        style = TextStyle(color = palette.muted, fontSize = 15.sp, fontFamily = ff),
    )
}
