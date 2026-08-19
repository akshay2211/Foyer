package io.ak1.testuilauncher

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** A launchable app: just what we need to show a name and start it. */
data class AppEntry(
    val label: String,
    val packageName: String,
    val activityName: String,
)

private fun loadApps(context: Context): List<AppEntry> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val self = context.packageName
    return pm.queryIntentActivities(intent, 0)
        .mapNotNull { ri ->
            val ai = ri.activityInfo ?: return@mapNotNull null
            if (ai.packageName == self) return@mapNotNull null
            val label = ri.loadLabel(pm).toString().trim()
            if (label.isEmpty()) return@mapNotNull null
            AppEntry(label = label, packageName = ai.packageName, activityName = ai.name)
        }
        .sortedBy { it.label.lowercase() }
}

private fun launchApp(context: Context, app: AppEntry) {
    val intent = Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LAUNCHER)
        .setClassName(app.packageName, app.activityName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    runCatching { context.startActivity(intent) }
}

private fun openSettings(context: Context) {
    val intent = Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

private fun sectionOf(label: String): Char {
    val c = label.firstOrNull()?.uppercaseChar() ?: '#'
    return if (c in 'A'..'Z') c else '#'
}

@Composable
fun LauncherScreen(themeMode: ThemeMode, onCycleTheme: () -> Unit) {
    val context = LocalContext.current
    val palette = LocalPalette.current

    val apps by produceState(initialValue = emptyList<AppEntry>(), context) {
        value = withContext(Dispatchers.IO) { loadApps(context) }
    }
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current

    var query by remember { mutableStateOf("") }
    val filtered = remember(apps, query) {
        val q = query.trim()
        if (q.isEmpty()) apps else apps.filter { it.label.startsWith(q, ignoreCase = true) }
    }

    val actionStyle = TextStyle(color = palette.muted, fontSize = 13.sp, letterSpacing = 2.sp)

    Box(
        Modifier
            .fillMaxSize()
            .background(palette.background)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
        ) {
            // Header: text-only actions. No icons anywhere.
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                BasicText(
                    text = themeMode.label,
                    modifier = Modifier
                        .clickable { onCycleTheme() }
                        .padding(6.dp),
                    style = actionStyle,
                )
                BasicText(
                    text = "settings",
                    modifier = Modifier
                        .clickable { openSettings(context) }
                        .padding(6.dp),
                    style = actionStyle,
                )
            }

            // Minimal search field — text only, filters the list live and
            // launches the top match when the keyboard's Go action is pressed.
            Column(Modifier.padding(horizontal = 24.dp, vertical = 4.dp)) {
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = palette.text, fontSize = 18.sp),
                    cursorBrush = SolidColor(palette.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(
                        onGo = {
                            filtered.firstOrNull()?.let {
                                focusManager.clearFocus()
                                launchApp(context, it)
                            }
                        },
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    decorationBox = { inner ->
                        if (query.isEmpty()) {
                            BasicText(
                                text = "search",
                                style = TextStyle(color = palette.muted, fontSize = 18.sp),
                            )
                        }
                        inner()
                    },
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(palette.muted.copy(alpha = 0.25f)),
                )
            }

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 24.dp,
                        end = 44.dp,
                        top = 4.dp,
                        bottom = 32.dp,
                    ),
                ) {
                    items(filtered, key = { it.packageName + "/" + it.activityName }) { app ->
                        BasicText(
                            text = app.label,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { launchApp(context, app) }
                                .padding(vertical = 11.dp),
                            style = TextStyle(color = palette.text, fontSize = 19.sp),
                        )
                    }
                }

                // The rail indexes the full list; hide it while searching.
                if (query.isEmpty() && filtered.isNotEmpty()) {
                    AlphabetScroller(
                        apps = filtered,
                        listState = listState,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight(),
                    )
                }
            }
        }
    }
}

/**
 * The "fancy" part: an A–Z rail on the right edge. Drag or tap to fast-scroll,
 * the active letter enlarges, and a large bubble follows the finger. The rail
 * also passively highlights whichever section is currently at the top.
 */
@Composable
private fun AlphabetScroller(
    apps: List<AppEntry>,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val letters = remember { ('A'..'Z').toList() + '#' }
    val indexForLetter = remember(apps) {
        val map = HashMap<Char, Int>()
        apps.forEachIndexed { i, app ->
            val key = sectionOf(app.label)
            if (!map.containsKey(key)) map[key] = i
        }
        map
    }

    // Nearest section with actual apps, searching outward from the pressed letter.
    fun targetIndex(letter: Char): Int? {
        indexForLetter[letter]?.let { return it }
        val li = letters.indexOf(letter)
        for (j in li + 1 until letters.size) indexForLetter[letters[j]]?.let { return it }
        for (j in li - 1 downTo 0) indexForLetter[letters[j]]?.let { return it }
        return null
    }

    var railHeight by remember { mutableIntStateOf(0) }
    var isDragging by remember { mutableStateOf(false) }
    var activeLetter by remember { mutableStateOf<Char?>(null) }
    var pointerY by remember { mutableFloatStateOf(0f) }

    val currentSection by remember(apps) {
        derivedStateOf {
            apps.getOrNull(listState.firstVisibleItemIndex)?.let { sectionOf(it.label) }
        }
    }

    fun updateFromY(y: Float) {
        if (railHeight <= 0) return
        val frac = (y / railHeight).coerceIn(0f, 1f)
        val li = (frac * letters.size).toInt().coerceIn(0, letters.size - 1)
        val letter = letters[li]
        activeLetter = letter
        pointerY = y.coerceIn(0f, railHeight.toFloat())
        targetIndex(letter)?.let { ti -> scope.launch { listState.scrollToItem(ti) } }
    }

    Box(modifier) {
        // Letter bubble that follows the finger while dragging.
        val bubbleVisible = isDragging && activeLetter != null
        val bubbleAlpha by animateFloatAsState(
            targetValue = if (bubbleVisible) 1f else 0f,
            label = "bubbleAlpha",
        )
        if (bubbleAlpha > 0.01f) {
            val bubbleSize = 76.dp
            val bubblePx = with(density) { bubbleSize.toPx() }
            val offsetX = with(density) { (-108).dp.roundToPx() }
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset {
                        IntOffset(
                            x = offsetX,
                            y = (pointerY - bubblePx / 2f).roundToInt(),
                        )
                    }
                    .alpha(bubbleAlpha)
                    .size(bubbleSize)
                    .background(palette.accent, CircleShape),
            ) {
                BasicText(
                    text = (activeLetter ?: ' ').toString(),
                    modifier = Modifier.align(Alignment.Center),
                    style = TextStyle(
                        color = palette.onAccent,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
            }
        }

        // The A–Z rail.
        Column(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(30.dp)
                .onSizeChanged { railHeight = it.height }
                .pointerInput(apps) {
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            updateFromY(offset.y)
                        },
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false },
                        onVerticalDrag = { change, _ ->
                            change.consume()
                            updateFromY(change.position.y)
                        },
                    )
                }
                .pointerInput(apps) {
                    detectTapGestures { offset -> updateFromY(offset.y) }
                },
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            letters.forEach { c ->
                val active = if (isDragging) c == activeLetter else c == currentSection
                val enabled = indexForLetter.containsKey(c)
                val targetColor = when {
                    active -> palette.accent
                    enabled -> palette.muted
                    else -> palette.muted.copy(alpha = 0.35f)
                }
                val color by animateColorAsState(targetColor, label = "letterColor")
                val scale by animateFloatAsState(if (active) 1.5f else 1f, label = "letterScale")
                BasicText(
                    text = c.toString(),
                    modifier = Modifier.graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
                    style = TextStyle(
                        color = color,
                        fontSize = 10.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                    ),
                )
            }
        }
    }
}
