package io.ak1.testuilauncher

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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

private fun sectionOf(label: String): Char {
    val c = label.firstOrNull()?.uppercaseChar() ?: '#'
    return if (c in 'A'..'Z') c else '#'
}

@Composable
fun LauncherScreen(store: LauncherStore) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val apps by produceState(initialValue = emptyList<AppEntry>(), context) {
        value = withContext(Dispatchers.IO) { loadApps(context) }
    }

    // Page 0 = app drawer (to the left), page 1 = home (start here). Swiping
    // right from home reveals the drawer.
    val pagerState = rememberPagerState(initialPage = 1) { 2 }
    val onDrawer = pagerState.currentPage == 0

    var menuApp by remember { mutableStateOf<AppEntry?>(null) }
    var pickerTarget by remember { mutableStateOf<ShortcutSide?>(null) }
    var showHomeMenu by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showVoice by remember { mutableStateOf(false) }

    val overlayOpen = menuApp != null || pickerTarget != null || showHomeMenu ||
        showSettings || showVoice
    BackHandler(enabled = overlayOpen) {
        menuApp = null
        pickerTarget = null
        showHomeMenu = false
        showSettings = false
        showVoice = false
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(LocalPalette.current.background),
    ) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                0 -> DrawerPage(
                    apps = apps,
                    store = store,
                    themeMode = store.themeMode,
                    onCycleTheme = { store.cycleTheme() },
                    onLongPressApp = { menuApp = it },
                    onOpenSettings = { showSettings = true },
                    active = onDrawer,
                    backEnabled = onDrawer && !overlayOpen,
                    onNavigateHome = { scope.launch { pagerState.animateScrollToPage(1) } },
                )

                else -> HomePage(
                    store = store,
                    onLongPressFavorite = { menuApp = it },
                    onLongPressShortcut = { pickerTarget = it },
                    onLongPressBackground = { showHomeMenu = true },
                    onOpenVoice = { showVoice = true },
                    onSwipeUp = { openBrowser(context) },
                    onSwipeDown = { expandNotifications(context) },
                )
            }
        }

        menuApp?.let { app ->
            AppActionMenu(
                app = app,
                isFavorite = store.isFavorite(app),
                canAdd = store.favorites.size < MAX_FAVORITES,
                isHidden = store.isHidden(app),
                onAdd = { store.addFavorite(app); menuApp = null },
                onRemove = { store.removeFavorite(app); menuApp = null },
                onToggleHide = { store.toggleHidden(app); menuApp = null },
                onAppInfo = { openAppInfo(context, app.packageName); menuApp = null },
                onDismiss = { menuApp = null },
            )
        }

        pickerTarget?.let { side ->
            AppPicker(
                apps = apps,
                side = side,
                onPick = { store.setShortcut(side, it); pickerTarget = null },
                onDismiss = { pickerTarget = null },
            )
        }

        if (showHomeMenu) {
            HomeMenu(
                showClock = store.showClock,
                onToggleClock = { store.toggleClock(); showHomeMenu = false },
                onOpenSettings = { showHomeMenu = false; showSettings = true },
                onDismiss = { showHomeMenu = false },
            )
        }

        if (showSettings) {
            SettingsScreen(store = store, onClose = { showSettings = false })
        }

        if (showVoice) {
            VoiceScreen(onClose = { showVoice = false })
        }
    }
}

// ---- Home page ------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomePage(
    store: LauncherStore,
    onLongPressFavorite: (AppEntry) -> Unit,
    onLongPressShortcut: (ShortcutSide) -> Unit,
    onLongPressBackground: () -> Unit,
    onOpenVoice: () -> Unit,
    onSwipeUp: () -> Unit,
    onSwipeDown: () -> Unit,
) {
    val context = LocalContext.current
    val palette = LocalPalette.current
    val ff = LocalFontFamily.current
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 110.dp.toPx() }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                var dy = 0f
                detectVerticalDragGestures(
                    onDragStart = { dy = 0f },
                    onVerticalDrag = { change, delta -> dy += delta; change.consume() },
                    onDragEnd = {
                        if (dy < -swipeThresholdPx) onSwipeUp()
                        else if (dy > swipeThresholdPx) onSwipeDown()
                    },
                    onDragCancel = { dy = 0f },
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = { onLongPressBackground() })
            }
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        if (store.showClock) {
            ClockWidget(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 28.dp, top = 56.dp)
                    .clickable { openClockApp(context) },
            )
        }

        // Favorites, vertically centered on the left.
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 28.dp, end = 24.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (store.favorites.isEmpty()) {
                BasicText(
                    text = "long-press an app to pin it",
                    style = TextStyle(color = palette.muted, fontSize = 15.sp, fontFamily = ff),
                )
            } else {
                store.favorites.forEach { app ->
                    BasicText(
                        text = app.label.lowercase(),
                        modifier = Modifier
                            .combinedClickable(
                                onClick = { store.recordLaunch(app); launchApp(context, app) },
                                onLongClick = { onLongPressFavorite(app) },
                            )
                            .padding(vertical = 9.dp),
                        style = TextStyle(color = palette.text, fontSize = 22.sp, fontFamily = ff),
                    )
                }
            }
        }

        // Voice / command entry, bottom-center.
        BasicText(
            text = "ask me",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 20.dp)
                .clickable { onOpenVoice() }
                .padding(8.dp),
            style = TextStyle(
                color = palette.text,
                fontSize = 17.sp,
                letterSpacing = 1.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = ff,
            ),
        )

        // Bottom-corner shortcuts, rotated as vertical edge labels.
        EdgeShortcut(
            app = store.leftShortcut,
            rotation = -90f,
            onLongPress = { onLongPressShortcut(ShortcutSide.LEFT) },
            modifier = Modifier.align(Alignment.BottomStart),
        )
        EdgeShortcut(
            app = store.rightShortcut,
            rotation = 90f,
            onLongPress = { onLongPressShortcut(ShortcutSide.RIGHT) },
            modifier = Modifier.align(Alignment.BottomEnd),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EdgeShortcut(
    app: AppEntry?,
    rotation: Float,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val palette = LocalPalette.current
    val ff = LocalFontFamily.current
    BasicText(
        text = (app?.label ?: "set").lowercase(),
        modifier = modifier
            .padding(vertical = 18.dp)
            .rotate(rotation)
            .combinedClickable(
                onClick = { app?.let { launchApp(context, it) } ?: onLongPress() },
                onLongClick = onLongPress,
            )
            .padding(8.dp),
        style = TextStyle(
            color = if (app == null) palette.muted else palette.text,
            fontSize = 15.sp,
            letterSpacing = 1.sp,
            fontFamily = ff,
        ),
    )
}

@Composable
private fun ClockWidget(modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val ff = LocalFontFamily.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(10_000)
        }
    }
    val timeFmt = remember { SimpleDateFormat("h:mm", Locale.getDefault()) }
    val dateFmt = remember { SimpleDateFormat("EEEE, MMM d", Locale.getDefault()) }
    val date = Date(now)
    Column(modifier = modifier, horizontalAlignment = Alignment.Start) {
        BasicText(
            text = timeFmt.format(date),
            style = TextStyle(color = palette.text, fontSize = 60.sp, fontWeight = FontWeight.Thin, fontFamily = ff),
        )
        BasicText(
            text = dateFmt.format(date).lowercase(),
            style = TextStyle(color = palette.muted, fontSize = 14.sp, letterSpacing = 1.sp, fontFamily = ff),
        )
    }
}

// ---- App drawer page ------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DrawerPage(
    apps: List<AppEntry>,
    store: LauncherStore,
    themeMode: ThemeMode,
    onCycleTheme: () -> Unit,
    onLongPressApp: (AppEntry) -> Unit,
    onOpenSettings: () -> Unit,
    active: Boolean,
    backEnabled: Boolean,
    onNavigateHome: () -> Unit,
) {
    val context = LocalContext.current
    val palette = LocalPalette.current
    val ff = LocalFontFamily.current
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

    var query by remember { mutableStateOf("") }
    val q = query.trim()
    val searching = q.isNotEmpty()

    val onLaunch: (AppEntry) -> Unit = { app -> store.recordLaunch(app); launchApp(context, app) }

    // Browse list excludes hidden apps; search reveals everything (incl. hidden).
    val browse = apps.filterNot { store.isHidden(it) }
    val searchResults = if (searching) {
        apps.filter { it.label.startsWith(q, ignoreCase = true) }
    } else {
        emptyList()
    }
    val frequent = if (!searching) {
        browse.filter { store.usageCount(it) > 0 }
            .sortedByDescending { store.usageCount(it) }
            .take(5)
    } else {
        emptyList()
    }
    // Leading rows before the alphabetical list ("frequently used" header +
    // block + divider), so the A–Z rail can offset its scroll targets.
    val leadingCount = if (frequent.isNotEmpty()) frequent.size + 2 else 0

    // On-device command routing (math, quick actions, settings). Offline.
    val commands = if (searching) routeCommands(context, q, useAi = store.aiEnabled) else emptyList()

    // Back on the drawer clears the search first, then returns to home.
    BackHandler(enabled = backEnabled) {
        if (query.isNotEmpty()) query = "" else onNavigateHome()
    }

    // Auto-open the keyboard when the drawer becomes the visible page; hide it
    // (and drop focus) when we leave.
    LaunchedEffect(active) {
        if (active) {
            delay(80)
            runCatching { focusRequester.requestFocus() }
        } else {
            focusManager.clearFocus()
        }
    }

    val actionStyle = TextStyle(color = palette.muted, fontSize = 13.sp, letterSpacing = 2.sp, fontFamily = ff)

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            BasicText(
                text = themeMode.label,
                modifier = Modifier.clickable { onCycleTheme() }.padding(6.dp),
                style = actionStyle,
            )
            BasicText(
                text = "settings",
                modifier = Modifier.clickable { onOpenSettings() }.padding(6.dp),
                style = actionStyle,
            )
        }

        // Minimal search field with a text-only clear (×) affordance.
        Column(Modifier.padding(horizontal = 24.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(color = palette.text, fontSize = 18.sp, fontFamily = ff),
                        cursorBrush = SolidColor(palette.accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(
                            onGo = {
                                val app = searchResults.firstOrNull()
                                val cmd = commands.firstOrNull()
                                when {
                                    app != null -> { focusManager.clearFocus(); onLaunch(app) }
                                    cmd != null -> { focusManager.clearFocus(); cmd.run() }
                                    q.isNotEmpty() -> { focusManager.clearFocus(); webSearch(context, q) }
                                }
                            },
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .padding(vertical = 8.dp),
                        decorationBox = { inner ->
                            if (query.isEmpty()) {
                                BasicText(
                                    text = "search",
                                    style = TextStyle(color = palette.muted, fontSize = 18.sp, fontFamily = ff),
                                )
                            }
                            inner()
                        },
                    )
                }
                if (query.isNotEmpty()) {
                    BasicText(
                        text = "×",
                        modifier = Modifier
                            .clickable {
                                query = ""
                                focusManager.clearFocus()
                            }
                            .padding(start = 8.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
                        style = TextStyle(color = palette.muted, fontSize = 26.sp),
                    )
                }
            }
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
                .fillMaxWidth(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 24.dp, end = 44.dp, top = 4.dp, bottom = 32.dp),
            ) {
                if (searching) {
                    items(commands, key = { "c/" + it.title }) { cmd ->
                        CommandRow(cmd) { focusManager.clearFocus() }
                    }
                    items(searchResults, key = { "s/" + it.key }) { app ->
                        AppRow(app, onLaunch, onLongPressApp)
                    }
                    item(key = "web") {
                        FallbackRow("search web for “$q”") { focusManager.clearFocus(); webSearch(context, q) }
                    }
                    item(key = "play") {
                        FallbackRow("find “$q” in Play Store") { playSearch(context, q) }
                    }
                    item(key = "ai-tag") {
                        BasicText(
                            text = "on-device · private",
                            modifier = Modifier.padding(top = 16.dp),
                            style = TextStyle(color = palette.muted, fontSize = 11.sp, letterSpacing = 1.sp, fontFamily = ff),
                        )
                    }
                } else {
                    if (frequent.isNotEmpty()) {
                        item(key = "freq-header") { SectionHeader("frequently used") }
                        items(frequent, key = { "f/" + it.key }) { app ->
                            AppRow(app, onLaunch, onLongPressApp)
                        }
                        item(key = "freq-divider") {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                                    .height(1.dp)
                                    .background(palette.muted.copy(alpha = 0.2f)),
                            )
                        }
                    }
                    items(browse, key = { "a/" + it.key }) { app ->
                        AppRow(app, onLaunch, onLongPressApp)
                    }
                }
            }

            if (!searching && browse.isNotEmpty()) {
                AlphabetScroller(
                    apps = browse,
                    listState = listState,
                    leadingItemCount = leadingCount,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight(),
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    val palette = LocalPalette.current
    val ff = LocalFontFamily.current
    BasicText(
        text = text,
        modifier = Modifier.padding(top = 6.dp, bottom = 4.dp),
        style = TextStyle(color = palette.muted, fontSize = 12.sp, letterSpacing = 2.sp, fontFamily = ff),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppRow(
    app: AppEntry,
    onLaunch: (AppEntry) -> Unit,
    onLongPress: (AppEntry) -> Unit,
) {
    val palette = LocalPalette.current
    val ff = LocalFontFamily.current
    BasicText(
        text = app.label.lowercase(),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onLaunch(app) },
                onLongClick = { onLongPress(app) },
            )
            .padding(vertical = 11.dp),
        style = TextStyle(color = palette.text, fontSize = 19.sp, fontFamily = ff),
    )
}

@Composable
private fun FallbackRow(text: String, onClick: () -> Unit) {
    val palette = LocalPalette.current
    val ff = LocalFontFamily.current
    BasicText(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 11.dp),
        style = TextStyle(color = palette.muted, fontSize = 18.sp, fontFamily = ff),
    )
}

@Composable
private fun CommandRow(command: Command, onBeforeRun: () -> Unit) {
    val palette = LocalPalette.current
    val ff = LocalFontFamily.current
    Column(
        Modifier
            .fillMaxWidth()
            .clickable { onBeforeRun(); command.run() }
            .padding(vertical = 10.dp),
    ) {
        BasicText(
            text = command.title,
            style = TextStyle(color = palette.text, fontSize = 22.sp, fontFamily = ff),
        )
        command.subtitle?.let { sub ->
            BasicText(
                text = sub,
                style = TextStyle(color = palette.muted, fontSize = 12.sp, fontFamily = ff),
            )
        }
    }
}

/**
 * The A–Z rail on the right edge. Drag or tap to fast-scroll, the active letter
 * enlarges, and a large bubble follows the finger. Also passively highlights
 * whichever section is currently at the top. [leadingItemCount] accounts for any
 * non-alphabetical rows (e.g. the "frequent" block) above the sorted list.
 */
@Composable
private fun AlphabetScroller(
    apps: List<AppEntry>,
    listState: LazyListState,
    leadingItemCount: Int = 0,
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

    val currentSection by remember(apps, leadingItemCount) {
        derivedStateOf {
            val idx = listState.firstVisibleItemIndex - leadingItemCount
            apps.getOrNull(idx.coerceAtLeast(0))?.let { sectionOf(it.label) }
        }
    }

    fun updateFromY(y: Float) {
        if (railHeight <= 0) return
        val frac = (y / railHeight).coerceIn(0f, 1f)
        val li = (frac * letters.size).toInt().coerceIn(0, letters.size - 1)
        val letter = letters[li]
        activeLetter = letter
        pointerY = y.coerceIn(0f, railHeight.toFloat())
        targetIndex(letter)?.let { ti -> scope.launch { listState.scrollToItem(ti + leadingItemCount) } }
    }

    Box(modifier) {
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
                        IntOffset(x = offsetX, y = (pointerY - bubblePx / 2f).roundToInt())
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

        Column(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(30.dp)
                .onSizeChanged { railHeight = it.height }
                .pointerInput(apps) {
                    detectVerticalDragGestures(
                        onDragStart = { offset -> isDragging = true; updateFromY(offset.y) },
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false },
                        onVerticalDrag = { change, _ -> change.consume(); updateFromY(change.position.y) },
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

// ---- Overlays -------------------------------------------------------------

@Composable
private fun Scrim(onDismiss: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x99000000))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onDismiss() },
        content = content,
    )
}

@Composable
private fun BoxScope.MenuCard(content: @Composable ColumnScope.() -> Unit) {
    val palette = LocalPalette.current
    Column(
        modifier = Modifier
            .align(Alignment.Center)
            .fillMaxWidth(0.82f)
            .clip(RoundedCornerShape(22.dp))
            .background(palette.background)
            // Swallow taps so touching the card doesn't dismiss via the scrim.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {}
            .padding(horizontal = 22.dp, vertical = 18.dp),
        content = content,
    )
}

@Composable
private fun MenuRow(text: String, onClick: () -> Unit) {
    val palette = LocalPalette.current
    BasicText(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 14.dp),
        style = TextStyle(color = palette.text, fontSize = 18.sp),
    )
}

@Composable
private fun AppActionMenu(
    app: AppEntry,
    isFavorite: Boolean,
    canAdd: Boolean,
    isHidden: Boolean,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
    onToggleHide: () -> Unit,
    onAppInfo: () -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalPalette.current
    Scrim(onDismiss) {
        MenuCard {
            BasicText(
                text = app.label,
                modifier = Modifier.padding(bottom = 6.dp),
                style = TextStyle(color = palette.muted, fontSize = 13.sp, letterSpacing = 1.sp),
            )
            when {
                isFavorite -> MenuRow("remove from home", onRemove)
                canAdd -> MenuRow("add to home", onAdd)
                else -> BasicText(
                    text = "home is full (max $MAX_FAVORITES)",
                    modifier = Modifier.padding(vertical = 14.dp),
                    style = TextStyle(color = palette.muted, fontSize = 18.sp),
                )
            }
            MenuRow(if (isHidden) "unhide app" else "hide app", onToggleHide)
            MenuRow("app info", onAppInfo)
        }
    }
}

@Composable
private fun HomeMenu(
    showClock: Boolean,
    onToggleClock: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalPalette.current
    Scrim(onDismiss) {
        MenuCard {
            BasicText(
                text = "home",
                modifier = Modifier.padding(bottom = 6.dp),
                style = TextStyle(color = palette.muted, fontSize = 13.sp, letterSpacing = 1.sp),
            )
            MenuRow(if (showClock) "hide clock" else "show clock", onToggleClock)
            MenuRow("settings", onOpenSettings)
        }
    }
}

@Composable
private fun AppPicker(
    apps: List<AppEntry>,
    side: ShortcutSide,
    onPick: (AppEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalPalette.current
    Scrim(onDismiss) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.86f)
                .fillMaxHeight(0.72f)
                .clip(RoundedCornerShape(22.dp))
                .background(palette.background)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {}
                .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            BasicText(
                text = "choose ${side.name.lowercase()} shortcut",
                modifier = Modifier.padding(bottom = 10.dp),
                style = TextStyle(color = palette.muted, fontSize = 13.sp, letterSpacing = 1.sp),
            )
            LazyColumn(Modifier.weight(1f)) {
                items(apps, key = { it.packageName + "/" + it.activityName }) { app ->
                    BasicText(
                        text = app.label,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(app) }
                            .padding(vertical = 12.dp),
                        style = TextStyle(color = palette.text, fontSize = 18.sp),
                    )
                }
            }
        }
    }
}
