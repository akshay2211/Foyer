package com.foyerlauncher.foyer

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Build
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
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

/**
 * A launchable app: just what we need to show a name and start it. [userSerial]
 * identifies which profile the app lives in: 0 = the launcher's own (personal)
 * profile, otherwise the work/private profile's stable serial number — the same
 * package can be installed in both, and they are distinct entries.
 */
data class AppEntry(
    val label: String,
    val packageName: String,
    val activityName: String,
    val userSerial: Long = 0L,
) {
    /**
     * Android's declared app category (ApplicationInfo.CATEGORY_*), or -1 if
     * unknown/pre-API-26. Kept out of the primary constructor so it stays out of
     * equals/hashCode/copy and the persisted (favorites/shortcuts) encoding —
     * it's populated only on the live list from [loadLaunchableApps].
     */
    var category: Int = -1
}

/**
 * Enumerates launchable apps across *all* profiles (personal + work + private
 * space) via [LauncherApps] — plain PackageManager queries only see the personal
 * profile. Locked/quiet profiles simply don't return activities, so their apps
 * disappear while paused, which is the system-consistent behavior.
 */
fun loadLaunchableApps(context: Context): List<AppEntry> {
    val la = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    val um = context.getSystemService(Context.USER_SERVICE) as UserManager
    val self = context.packageName
    val me = Process.myUserHandle()
    return um.userProfiles.flatMap { user ->
        val serial = if (user == me) 0L else um.getSerialNumberForUser(user)
        la.getActivityList(null, user).mapNotNull { ai ->
            if (ai.applicationInfo.packageName == self) return@mapNotNull null
            val label = ai.label.toString().trim()
            if (label.isEmpty()) return@mapNotNull null
            AppEntry(
                label = label,
                packageName = ai.componentName.packageName,
                activityName = ai.componentName.className,
                userSerial = serial,
            ).also {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) it.category = ai.applicationInfo.category
            }
        }
    }.sortedBy { it.label.lowercase() }
}

private fun sectionOf(label: String): Char {
    val c = label.firstOrNull()?.uppercaseChar() ?: '#'
    return if (c in 'A'..'Z') c else '#'
}

@Composable
fun LauncherScreen(store: LauncherStore) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var apps by remember { mutableStateOf(emptyList<AppEntry>()) }
    // Load once, then keep in sync with installs/uninstalls/profile changes in
    // any profile; on removal also drop the app's stored state (favorites etc).
    DisposableEffect(Unit) {
        fun reload() {
            scope.launch { apps = withContext(Dispatchers.IO) { loadLaunchableApps(context) } }
        }
        reload()
        val la = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val um = context.getSystemService(Context.USER_SERVICE) as UserManager
        val callback = object : LauncherApps.Callback() {
            override fun onPackageRemoved(packageName: String, user: UserHandle) {
                val serial = if (user == Process.myUserHandle()) 0L else um.getSerialNumberForUser(user)
                store.pruneApp(packageName, serial)
                reload()
            }
            override fun onPackageAdded(packageName: String, user: UserHandle) = reload()
            override fun onPackageChanged(packageName: String, user: UserHandle) = reload()
            override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = reload()
            override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = reload()
        }
        la.registerCallback(callback)
        onDispose { la.unregisterCallback(callback) }
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
    var guardApp by remember { mutableStateOf<AppEntry?>(null) }
    var aliasApp by remember { mutableStateOf<AppEntry?>(null) }

    val overlayOpen = menuApp != null || pickerTarget != null || showHomeMenu ||
        showSettings || showVoice || guardApp != null || aliasApp != null
    BackHandler(enabled = overlayOpen) {
        menuApp = null
        pickerTarget = null
        showHomeMenu = false
        showSettings = false
        showVoice = false
        guardApp = null
        aliasApp = null
    }

    // Launch, but pause on time-sinks that are already over today's limit.
    val requestLaunch: (AppEntry) -> Unit = { app ->
        val overLimit = UsageTracker.hasAccess(context) && store.isDistracting(app) &&
            UsageTracker.usageTodayMs(context, app.packageName) >= DISTRACTION_THRESHOLD_MS
        if (overLimit) {
            guardApp = app
        } else {
            store.recordLaunch(app)
            launchApp(context, app)
        }
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
                    onLaunchApp = requestLaunch,
                    onLongPressApp = { menuApp = it },
                    onOpenSettings = { showSettings = true },
                    active = onDrawer,
                    backEnabled = onDrawer && !overlayOpen,
                    onNavigateHome = { scope.launch { pagerState.animateScrollToPage(1) } },
                )

                else -> HomePage(
                    store = store,
                    onLaunchFavorite = requestLaunch,
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
                isDistracting = store.isDistracting(app),
                canUninstall = remember(app) { canUninstall(context, app) },
                onAdd = { store.addFavorite(app); menuApp = null },
                onRemove = { store.removeFavorite(app); menuApp = null },
                onToggleHide = { store.toggleHidden(app); menuApp = null },
                onToggleDistracting = { store.toggleDistracting(app); menuApp = null },
                onAppInfo = { openAppInfo(context, app.packageName); menuApp = null },
                onUninstall = { requestUninstall(context, app); menuApp = null },
                aliases = store.aliases.filterValues { it == app.key }.keys.sorted(),
                onAddAlias = { menuApp = null; aliasApp = app },
                onRemoveAlias = { store.removeAlias(it) },
                onDismiss = { menuApp = null },
            )
        }

        aliasApp?.let { app ->
            AliasPrompt(
                app = app,
                onSave = { alias -> store.addAlias(alias, app); aliasApp = null },
                onDismiss = { aliasApp = null },
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
            VoiceScreen(store = store, onClose = { showVoice = false })
        }

        guardApp?.let { app ->
            DistractionNudge(
                app = app,
                usedMs = UsageTracker.usageTodayMs(context, app.packageName),
                onOpen = { store.recordLaunch(app); launchApp(context, app); guardApp = null },
                onDismiss = { guardApp = null },
            )
        }

        // First launch only: pitch + default-launcher prompt, above everything.
        if (!store.onboarded) {
            OnboardingScreen(onDone = { store.markOnboarded() })
        }
    }
}

@Composable
private fun DistractionNudge(
    app: AppEntry,
    usedMs: Long,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalPalette.current
    val ff = LocalFontFamily.current
    FoyerSheet(title = "${app.label.lowercase()} · ${formatDuration(usedMs)} today", onDismiss = onDismiss) {
        BasicText(
            text = "you've already spent a while here today. open anyway?",
            modifier = Modifier.padding(top = 6.dp, bottom = 10.dp),
            style = TextStyle(color = palette.text, fontSize = 20.sp, fontFamily = ff),
        )
        SheetRow("not now", onClick = onDismiss)
        SheetRow("open anyway", onClick = onOpen)
    }
}

// ---- Home page ------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomePage(
    store: LauncherStore,
    onLaunchFavorite: (AppEntry) -> Unit,
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

        // Today's screen time (the minimalist nudge). Taps to grant access.
        val hasUsage = UsageTracker.hasAccess(context)
        var screenMs by remember { mutableLongStateOf(0L) }
        LaunchedEffect(hasUsage) {
            while (hasUsage) {
                screenMs = UsageTracker.screenTimeTodayMs(context)
                delay(60_000)
            }
        }
        BasicText(
            text = if (hasUsage) "${formatDuration(screenMs)} today" else "track screen time",
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 28.dp, top = 152.dp)
                .clickable { if (!hasUsage) UsageTracker.openAccessSettings(context) },
            style = TextStyle(color = palette.muted, fontSize = 13.sp, letterSpacing = 1.sp, fontFamily = ff),
        )

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
                                onClick = { onLaunchFavorite(app) },
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
    onLaunchApp: (AppEntry) -> Unit,
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

    val onLaunch: (AppEntry) -> Unit = onLaunchApp

    // Browse list excludes hidden apps; search reveals everything (incl. hidden).
    val browse = apps.filterNot { store.isHidden(it) }
    val searchResults = if (searching) {
        // User-taught aliases rank first, then label prefixes.
        val aliased = store.aliases
            .filterKeys { it.startsWith(q, ignoreCase = true) }
            .values
            .mapNotNull { key -> apps.firstOrNull { it.key == key } }
        (aliased + apps.filter { it.label.startsWith(q, ignoreCase = true) }).distinct()
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

    // The typed command bar also routes through the active on-device model.
    val scope = rememberCoroutineScope()
    val assistant = rememberAssistant(store)
    var aiThinking by remember { mutableStateOf(false) }
    var aiResult by remember { mutableStateOf<Command?>(null) }

    // Any edit to the query invalidates a previous AI answer.
    LaunchedEffect(q) { aiThinking = false; aiResult = null }

    fun askAi(text: String) {
        scope.launch {
            aiThinking = true
            aiResult = null
            val plan = assistant?.decide(text, apps) ?: listOf(AiIntent.Web(text))
            val command = plan.toCommand(context)
            aiThinking = false
            if (command.kind == CommandKind.ACTION && !command.confirm) {
                command.run()      // confident action → just do it
                aiResult = null
            } else {
                aiResult = command // fuzzy action (button) or answer (text)
            }
        }
    }

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
                                if (app != null) {
                                    focusManager.clearFocus(); onLaunch(app)
                                } else if (q.isNotEmpty()) {
                                    focusManager.clearFocus(); askAi(q)
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
                    if (aiThinking) {
                        item(key = "ai-thinking") {
                            BasicText(
                                text = "thinking…",
                                modifier = Modifier.padding(vertical = 11.dp),
                                style = TextStyle(color = palette.muted, fontSize = 18.sp, fontFamily = ff),
                            )
                        }
                    } else {
                        aiResult?.let { cmd ->
                            item(key = "ai-result") {
                                when {
                                    cmd.options.isNotEmpty() -> Column(Modifier.padding(vertical = 6.dp)) {
                                        // Disambiguation: the question, then one button per candidate.
                                        BasicText(
                                            text = cmd.title,
                                            modifier = Modifier.padding(vertical = 6.dp),
                                            style = TextStyle(color = palette.muted, fontSize = 15.sp, fontFamily = ff),
                                        )
                                        cmd.options.forEach { opt ->
                                            Box(Modifier.padding(vertical = 6.dp)) {
                                                ActionButton(
                                                    label = "${opt.title}${opt.subtitle?.let { "  ·  $it" } ?: ""}",
                                                ) { focusManager.clearFocus(); opt.run() }
                                            }
                                        }
                                    }
                                    cmd.kind == CommandKind.ACTION -> Box(Modifier.padding(vertical = 10.dp)) {
                                        ActionButton(
                                            label = if (cmd.confirm) "did you mean: ${cmd.title}?" else cmd.title,
                                        ) { focusManager.clearFocus(); cmd.run() }
                                    }
                                    else -> CommandRow(cmd) { focusManager.clearFocus() }
                                }
                            }
                        }
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
                    if (aiThinking || aiResult != null) {
                        item(key = "ai-tag") {
                            BasicText(
                                text = "on-device · private",
                                modifier = Modifier.padding(top = 16.dp),
                                style = TextStyle(color = palette.muted, fontSize = 11.sp, letterSpacing = 1.sp, fontFamily = ff),
                            )
                        }
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
        text = buildAnnotatedString {
            append(app.label.lowercase())
            // Same app can exist in two profiles; mark the work/private copy.
            if (app.userSerial != 0L) {
                withStyle(SpanStyle(color = palette.muted, fontSize = 13.sp)) { append("  · work") }
            }
        },
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

/** Outlined, tappable action chip used for AI-proposed actions. */
@Composable
fun ActionButton(label: String, onClick: () -> Unit) {
    val palette = LocalPalette.current
    val ff = LocalFontFamily.current
    BasicText(
        text = label,
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .border(1.dp, palette.muted.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 12.dp),
        style = TextStyle(color = palette.text, fontSize = 17.sp, fontFamily = ff),
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
            .border(1.dp, palette.muted.copy(alpha = 0.6f), RoundedCornerShape(22.dp))
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
    isDistracting: Boolean,
    canUninstall: Boolean,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
    onToggleHide: () -> Unit,
    onToggleDistracting: () -> Unit,
    onAppInfo: () -> Unit,
    onUninstall: () -> Unit,
    aliases: List<String>,
    onAddAlias: () -> Unit,
    onRemoveAlias: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    FoyerSheet(title = app.label.lowercase(), onDismiss = onDismiss) {
        when {
            isFavorite -> SheetRow("remove from home", active = true, onClick = onRemove)
            canAdd -> SheetRow("add to home", onClick = onAdd)
            else -> SheetNote("home is full (max $MAX_FAVORITES)")
        }
        SheetRow(if (isHidden) "unhide app" else "hide app", active = isHidden, onClick = onToggleHide)
        SheetRow(
            if (isDistracting) "unmark distracting" else "mark distracting",
            active = isDistracting,
            onClick = onToggleDistracting,
        )
        // Taught nicknames: tap one to forget it, or teach a new one.
        aliases.forEach { alias ->
            SheetRow("alias “$alias”  ·  remove", active = true, onClick = { onRemoveAlias(alias) })
        }
        SheetRow("add alias", onClick = onAddAlias)
        SheetRow("app info", onClick = onAppInfo)
        if (canUninstall) SheetRow("uninstall", onClick = onUninstall)
    }
}

/**
 * Top-anchored alias input (top, because the IME would cover a bottom sheet —
 * the window uses adjustNothing). Enter saves, scrim/back dismisses.
 */
@Composable
private fun AliasPrompt(
    app: AppEntry,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalPalette.current
    val ff = LocalFontFamily.current
    var text by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(60)
        runCatching { focusRequester.requestFocus() }
    }
    Scrim(onDismiss) {
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 120.dp)
                .fillMaxWidth(0.82f)
                .clip(RoundedCornerShape(22.dp))
                .background(palette.background)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {}
                .padding(horizontal = 22.dp, vertical = 18.dp),
        ) {
            BasicText(
                text = "alias for ${app.label.lowercase()}",
                modifier = Modifier.padding(bottom = 10.dp),
                style = TextStyle(color = palette.muted, fontSize = 13.sp, letterSpacing = 1.sp, fontFamily = ff),
            )
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = TextStyle(color = palette.text, fontSize = 20.sp, fontFamily = ff),
                cursorBrush = SolidColor(palette.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank()) onSave(text) }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .padding(vertical = 6.dp),
                decorationBox = { inner ->
                    if (text.isEmpty()) {
                        BasicText(
                            text = "e.g. bank",
                            style = TextStyle(color = palette.muted, fontSize = 20.sp, fontFamily = ff),
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
            BasicText(
                text = "“${text.trim().lowercase().ifEmpty { "…" }}” will open ${app.label.lowercase()}",
                modifier = Modifier.padding(top = 10.dp),
                style = TextStyle(color = palette.muted, fontSize = 13.sp, fontFamily = ff),
            )
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
    FoyerSheet(title = "home", onDismiss = onDismiss) {
        SheetRow(if (showClock) "hide clock" else "show clock", active = showClock, onClick = onToggleClock)
        SheetRow("settings", onClick = onOpenSettings)
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
    FoyerSheet(title = "choose ${side.name.lowercase()} shortcut", onDismiss = onDismiss) {
        LazyColumn(Modifier.fillMaxHeight(0.62f)) {
            items(apps, key = { it.key }) { app ->
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
