package com.foyerlauncher.foyer.ui.screens

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foyerlauncher.foyer.LimitNudgeReceiver
import com.foyerlauncher.foyer.R
import com.foyerlauncher.foyer.ai.engine.rememberAssistant
import com.foyerlauncher.foyer.ai.router.AiIntent
import com.foyerlauncher.foyer.ai.router.Command
import com.foyerlauncher.foyer.ai.router.CommandKind
import com.foyerlauncher.foyer.ai.router.toCommand
import com.foyerlauncher.foyer.data.AppEntry
import com.foyerlauncher.foyer.data.DISTRACTION_THRESHOLD_MS
import com.foyerlauncher.foyer.data.LauncherStore
import com.foyerlauncher.foyer.data.MAX_FAVORITES
import com.foyerlauncher.foyer.data.ShortcutSide
import com.foyerlauncher.foyer.data.UsageTracker
import com.foyerlauncher.foyer.data.canUninstall
import com.foyerlauncher.foyer.data.formatDuration
import com.foyerlauncher.foyer.data.key
import com.foyerlauncher.foyer.data.launchApp
import com.foyerlauncher.foyer.data.loadLaunchableApps
import com.foyerlauncher.foyer.data.openAppInfo
import com.foyerlauncher.foyer.data.openClockApp
import com.foyerlauncher.foyer.data.playSearch
import com.foyerlauncher.foyer.data.requestUninstall
import com.foyerlauncher.foyer.data.sectionOf
import com.foyerlauncher.foyer.data.webSearch
import com.foyerlauncher.foyer.ui.components.AnalogueClockWidget
import com.foyerlauncher.foyer.ui.components.AppIconCache
import com.foyerlauncher.foyer.ui.components.ClockStyle
import com.foyerlauncher.foyer.ui.components.DigitalClockWidget
import com.foyerlauncher.foyer.ui.components.FastScrollStyle
import com.foyerlauncher.foyer.ui.components.FoyerGlyph
import com.foyerlauncher.foyer.ui.components.FoyerSheet
import com.foyerlauncher.foyer.ui.components.GestureUi
import com.foyerlauncher.foyer.ui.components.GlyphState
import com.foyerlauncher.foyer.ui.components.IconAction
import com.foyerlauncher.foyer.ui.components.LockClockWidget
import com.foyerlauncher.foyer.ui.components.MinimalClockWidget
import com.foyerlauncher.foyer.ui.components.SheetNote
import com.foyerlauncher.foyer.ui.components.SheetRow
import com.foyerlauncher.foyer.ui.components.runGestureAction
import com.foyerlauncher.foyer.ui.theme.BackgroundTone
import com.foyerlauncher.foyer.ui.theme.FoyerMaterialTheme
import com.foyerlauncher.foyer.ui.theme.LocalFontFamily
import com.foyerlauncher.foyer.ui.theme.LocalPalette
import com.foyerlauncher.foyer.ui.theme.matteGrain
import com.foyerlauncher.foyer.ui.theme.tracked
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun LauncherScreen(store: LauncherStore) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val notifPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

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
        val callback =
            object : LauncherApps.Callback() {
                override fun onPackageRemoved(
                    packageName: String,
                    user: UserHandle,
                ) {
                    val serial = if (user == Process.myUserHandle()) 0L else um.getSerialNumberForUser(user)
                    store.pruneApp(packageName, serial)
                    AppIconCache.invalidate(packageName)
                    reload()
                }

                override fun onPackageAdded(
                    packageName: String,
                    user: UserHandle,
                ) = reload()

                override fun onPackageChanged(
                    packageName: String,
                    user: UserHandle,
                ) {
                    // An update can change the icon itself (redesign, adaptive
                    // icon tweak) — the cache has no other way to find out.
                    AppIconCache.invalidate(packageName)
                    reload()
                }

                override fun onPackagesAvailable(
                    packageNames: Array<out String>,
                    user: UserHandle,
                    replacing: Boolean,
                ) = reload()

                override fun onPackagesUnavailable(
                    packageNames: Array<out String>,
                    user: UserHandle,
                    replacing: Boolean,
                ) = reload()
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
    // Voice's "set up model" prompt opens settings directly on the ai page.
    var settingsStartAtAi by remember { mutableStateOf(false) }
    var showVoice by remember { mutableStateOf(false) }
    var guardApp by remember { mutableStateOf<AppEntry?>(null) }
    var aliasApp by remember { mutableStateOf<AppEntry?>(null) }
    var showUsage by remember { mutableStateOf(false) }
    var showFocusPicker by remember { mutableStateOf(false) }
    var focusBlockedApp by remember { mutableStateOf<AppEntry?>(null) }

    val overlayOpen =
        menuApp != null ||
            pickerTarget != null ||
            showHomeMenu ||
            showSettings ||
            showVoice ||
            guardApp != null ||
            aliasApp != null ||
            showUsage ||
            showFocusPicker ||
            focusBlockedApp != null

    // Whenever the first overlay opens, drop the keyboard — a sheet must never
    // sit under the IME. Fires only on the closed→open edge, so sheet-to-sheet
    // transitions (app menu → alias dialog, which focuses its own field) keep
    // their focus.
    val overlayFocusManager = LocalFocusManager.current
    LaunchedEffect(overlayOpen) {
        if (overlayOpen) overlayFocusManager.clearFocus()
    }

    BackHandler(enabled = overlayOpen) {
        menuApp = null
        pickerTarget = null
        showHomeMenu = false
        showSettings = false
        settingsStartAtAi = false
        showVoice = false
        guardApp = null
        aliasApp = null
        showUsage = false
        showFocusPicker = false
        focusBlockedApp = null
    }

    // Launch, but pause on apps that are over their daily budget (a custom
    // per-app limit, or the global default for distracting-marked apps) —
    // unless a conscious five-minute bypass is still running.
    val requestLaunch: (AppEntry) -> Unit = { app ->
        val limit = store.limitFor(app.packageName)
        val overLimit =
            limit != null &&
                UsageTracker.hasAccess(context) &&
                UsageTracker.usageTodayMs(context, app.packageName) >= limit &&
                !store.bypassActive(app.packageName)
        when {
            store.isFocusActive() && store.isDistracting(app) -> focusBlockedApp = app
            overLimit -> guardApp = app
            else -> {
                store.recordLaunch(app)
                // Arm the "limit reached" nudge for the moment today's budget
                // runs out; the receiver re-checks usage before notifying.
                if (limit != null && UsageTracker.hasAccess(context)) {
                    LimitNudgeReceiver.schedule(
                        context,
                        app,
                        limit,
                        UsageTracker.usageTodayMs(context, app.packageName),
                    )
                }
                launchApp(context, app)
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(LocalPalette.current.background)
            .matteGrain(store.backgroundTone == BackgroundTone.MATTE),
    ) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                0 ->
                    DrawerPage(
                        apps = store.focusFiltered(apps),
                        store = store,
                        onLaunchApp = requestLaunch,
                        onLongPressApp = { menuApp = it },
                        onOpenSettings = { showSettings = true },
                        active = onDrawer,
                        backEnabled = onDrawer && !overlayOpen,
                        onNavigateHome = { scope.launch { pagerState.animateScrollToPage(1) } },
                    )

                else -> {
                    val gestureUi =
                        remember {
                            GestureUi(
                                openDrawer = { scope.launch { pagerState.animateScrollToPage(0) } },
                                openVoice = { showVoice = true },
                            )
                        }
                    HomePage(
                        store = store,
                        onLaunchFavorite = requestLaunch,
                        onLongPressFavorite = { menuApp = it },
                        onLongPressShortcut = { pickerTarget = it },
                        onLongPressBackground = { showHomeMenu = true },
                        onOpenVoice = { showVoice = true },
                        onSwipeUp = { runGestureAction(context, store.swipeUpAction, gestureUi) },
                        onSwipeDown = { runGestureAction(context, store.swipeDownAction, gestureUi) },
                        onDoubleTap = { runGestureAction(context, store.doubleTapAction, gestureUi) },
                        onOpenUsage = { showUsage = true },
                    )
                }
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
                onAdd = {
                    store.addFavorite(app)
                    menuApp = null
                },
                onRemove = {
                    store.removeFavorite(app)
                    menuApp = null
                },
                onToggleHide = {
                    store.toggleHidden(app)
                    menuApp = null
                },
                onToggleDistracting = {
                    store.toggleDistracting(app)
                    menuApp = null
                },
                onAppInfo = {
                    openAppInfo(context, app.packageName)
                    menuApp = null
                },
                onUninstall = {
                    requestUninstall(context, app)
                    menuApp = null
                },
                aliases =
                    store.aliases
                        .filterValues { it == app.key }
                        .keys
                        .sorted(),
                onAddAlias = {
                    menuApp = null
                    aliasApp = app
                },
                onRemoveAlias = { store.removeAlias(it) },
                limitMs = store.appLimits[app.packageName],
                onCycleLimit = {
                    store.cycleLimit(app.packageName)
                    // Point-of-use: the nudge needs notifications on 13+.
                    if (store.limitFor(app.packageName) != null &&
                        android.os.Build.VERSION.SDK_INT >= 33 &&
                        context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                        android.content.pm.PackageManager.PERMISSION_GRANTED
                    ) {
                        notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
                onDismiss = { menuApp = null },
            )
        }

        aliasApp?.let { app ->
            AliasPrompt(
                app = app,
                onSave = { alias ->
                    store.addAlias(alias, app)
                    aliasApp = null
                },
                onDismiss = { aliasApp = null },
            )
        }

        pickerTarget?.let { side ->
            AppPicker(
                apps = apps,
                side = side,
                onPick = {
                    store.setShortcut(side, it)
                    pickerTarget = null
                },
                onDismiss = { pickerTarget = null },
            )
        }

        if (showHomeMenu) {
            HomeMenu(
                clockStyle = store.clockStyle,
                focusActive = store.isFocusActive(),
                onCycleClock = { store.chooseClockStyle(store.clockStyle.next()) },
                onFocus = {
                    showHomeMenu = false
                    if (store.isFocusActive()) store.endFocus() else showFocusPicker = true
                },
                onOpenSettings = {
                    showHomeMenu = false
                    showSettings = true
                },
                onDismiss = { showHomeMenu = false },
            )
        }

        if (showFocusPicker) {
            FoyerSheet(title = "focus — hide distracting apps", onDismiss = { showFocusPicker = false }) {
                SheetRow("30 minutes") {
                    store.startFocus(30 * 60_000L)
                    showFocusPicker = false
                }
                SheetRow("1 hour") {
                    store.startFocus(60 * 60_000L)
                    showFocusPicker = false
                }
                SheetRow("2 hours") {
                    store.startFocus(120 * 60_000L)
                    showFocusPicker = false
                }
                SheetRow("until i turn it off") {
                    store.startFocus(null)
                    showFocusPicker = false
                }
            }
        }

        focusBlockedApp?.let { app ->
            FoyerSheet(title = app.label.lowercase(), onDismiss = { focusBlockedApp = null }) {
                BasicText(
                    text = "${app.label.lowercase()} is paused — ${focusLabel(store.focusUntil)}",
                    modifier = Modifier.padding(top = 6.dp, bottom = 10.dp),
                    style = TextStyle(color = LocalPalette.current.text, fontSize = 20.sp),
                )
                SheetRow("keep focusing") { focusBlockedApp = null }
                SheetRow("end focus") {
                    store.endFocus()
                    store.recordLaunch(app)
                    launchApp(context, app)
                    focusBlockedApp = null
                }
            }
        }

        if (showSettings) {
            SettingsScreen(
                store = store,
                startAtAssistant = settingsStartAtAi,
                onClose = {
                    showSettings = false
                    settingsStartAtAi = false
                },
            )
        }

        if (showVoice) {
            VoiceScreen(
                store = store,
                onClose = { showVoice = false },
                onOpenAiSettings = {
                    showVoice = false
                    settingsStartAtAi = true
                    showSettings = true
                },
            )
        }

        guardApp?.let { app ->
            DistractionNudge(
                app = app,
                usedMs = UsageTracker.usageTodayMs(context, app.packageName),
                limitMs = store.limitFor(app.packageName) ?: DISTRACTION_THRESHOLD_MS,
                onOpen = {
                    store.recordBypass(app.packageName)
                    store.recordLaunch(app)
                    launchApp(context, app)
                    guardApp = null
                },
                onDismiss = { guardApp = null },
            )
        }

        if (showUsage) {
            UsageSummaryScreen(apps = apps, store = store, onClose = { showUsage = false })
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
    limitMs: Long,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalPalette.current
    val ff = LocalFontFamily.current
    FoyerSheet(title = "${app.label.lowercase()} · ${formatDuration(usedMs)} today", onDismiss = onDismiss) {
        BasicText(
            text = "that's past your ${formatDuration(limitMs)} budget for today. open anyway?",
            modifier = Modifier.padding(top = 6.dp, bottom = 10.dp),
            style = TextStyle(color = palette.text, fontSize = 20.sp, fontFamily = ff),
        )
        SheetRow("not now", onClick = onDismiss)
        SheetRow("5 more minutes", onClick = onOpen)
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
    onDoubleTap: () -> Unit,
    onOpenUsage: () -> Unit,
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
                    onVerticalDrag = { change, delta ->
                        dy += delta
                        change.consume()
                    },
                    onDragEnd = {
                        if (dy < -swipeThresholdPx) {
                            onSwipeUp()
                        } else if (dy > swipeThresholdPx) {
                            onSwipeDown()
                        }
                    },
                    onDragCancel = { dy = 0f },
                )
            }.pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { onLongPressBackground() },
                    onDoubleTap = { onDoubleTap() },
                )
            }.windowInsetsPadding(WindowInsets.systemBars),
    ) {
        // One flowing column: a centred watch-face lockup (clock + date line +
        // focus) over the left-spine favorites — spacing adapts to whichever
        // clock is shown, so nothing can overlap or hide.
        Column(Modifier.fillMaxSize().padding(start = 28.dp)) {
            Column(
                Modifier.fillMaxWidth().padding(end = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when (store.clockStyle) {
                    ClockStyle.DIGITAL ->
                        DigitalClockWidget(
                            modifier =
                                Modifier
                                    .padding(top = 56.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable(onClickLabel = "open clock app") { openClockApp(context) },
                        )
                    ClockStyle.LOCK ->
                        LockClockWidget(
                            modifier =
                                Modifier
                                    .padding(top = 56.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable(onClickLabel = "open clock app") { openClockApp(context) },
                        )
                    ClockStyle.ANALOGUE ->
                        AnalogueClockWidget(
                            modifier =
                                Modifier
                                    .padding(top = 56.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable(onClickLabel = "open clock app") { openClockApp(context) },
                        )
                    ClockStyle.MINIMAL ->
                        MinimalClockWidget(
                            modifier =
                                Modifier
                                    .padding(top = 56.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable(onClickLabel = "open clock app") { openClockApp(context) },
                        )
                    ClockStyle.OFF -> Spacer(Modifier.height(56.dp))
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
                val dateFmt = remember { SimpleDateFormat("EEEE, MMM d", Locale.getDefault()) }
                val dateText = dateFmt.format(Date()).lowercase()
                BasicText(
                    text =
                        if (hasUsage) {
                            "$dateText · ${formatDuration(screenMs)} today"
                        } else {
                            "$dateText · track screen time"
                        },
                    modifier =
                        Modifier
                            .padding(top = 14.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClickLabel = if (hasUsage) "open screen time summary" else "grant usage access") {
                                if (hasUsage) onOpenUsage() else UsageTracker.openAccessSettings(context)
                            },
                    style = TextStyle(color = palette.muted, fontSize = 13.sp, letterSpacing = tracked(1.sp), fontFamily = ff),
                )

                // Focus countdown — the minute tick also re-evaluates the focus filters
                // so distracting favorites reappear on their own when focus expires.
                var focusTick by remember { mutableIntStateOf(0) }
                LaunchedEffect(store.focusUntil) {
                    while (store.isFocusActive()) {
                        delay(30_000)
                        focusTick++
                    }
                }
                if (store.isFocusActive() && focusTick >= 0) {
                    BasicText(
                        text = focusLabel(store.focusUntil),
                        modifier = Modifier.padding(top = 8.dp),
                        style = TextStyle(color = palette.accent, fontSize = 13.sp, letterSpacing = tracked(1.sp), fontFamily = ff),
                    )
                }
            }


            // Favorites, sitting high in whatever space the header leaves —
            // a Box + BiasAlignment, not a weighted Spacer either side: a
            // weight(_, fill = false) child only ever gets pushed to the
            // *start* of its allotted share (Column's default top-alignment
            // within that slot), so unused room inside its own share sits
            // below it rather than being split between the spacers. The
            // vertical bias of -0.6 splits the free space 20% above / 80%
            // below (bias maps -1..1 onto 0..100% of the slack).
            // The bottom padding keeps it clear of "ask me" and the
            // shortcuts; the scroll is a safety net for many favorites on
            // small screens — invisible when everything fits.
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(end = 24.dp, bottom = 72.dp),
                contentAlignment = BiasAlignment(horizontalBias = -1f, verticalBias = -0.6f),
            ) {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (store.favorites.isEmpty()) {
                        BasicText(
                            text = "long-press an app to pin it",
                            style = TextStyle(color = palette.muted, fontSize = 15.sp, fontFamily = ff),
                        )
                    } else {
                        store.focusFiltered(store.favorites).forEach { app ->
                            BasicText(
                                text = app.label.lowercase(),
                                modifier =
                                    Modifier
                                        .defaultMinSize(minHeight = 48.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .combinedClickable(
                                            onClick = { onLaunchFavorite(app) },
                                            onClickLabel = "open",
                                            onLongClick = { onLongPressFavorite(app) },
                                            onLongClickLabel = "app options",
                                        ).padding(vertical = 9.dp),
                                style = TextStyle(color = palette.text, fontSize = 22.sp, fontFamily = ff),
                            )
                        }
                    }
                }
            }
        }

        // Voice / command entry, bottom-center. The glyph flips to robot-off
        // while no local model is set up, so the state is visible before tapping.
        val assistantReady = store.aiEnabled && store.modelDownloaded()
        IconAction(
            icon = if (assistantReady) R.drawable.ic_tabler_voice else R.drawable.ic_tabler_robot_off,
            label = if (assistantReady) "ask me" else "ask me — no model set up",
            tint = palette.text,
            size = 28.dp,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 18.dp),
        ) { onOpenVoice() }

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

/** Glyphs for well-known corner apps; any other assigned app keeps its text label. */
private fun shortcutIcon(app: AppEntry?): Int? {
    if (app == null) return null
    val label = app.label.lowercase()
    val pkg = app.packageName.lowercase()
    val hint = "$label $pkg"
    return when {
        "camera" in hint -> R.drawable.ic_tabler_camera
        "whatsapp" in hint -> R.drawable.ic_tabler_brand_whatsapp
        "gmail" in label || pkg == "com.google.android.gm" -> R.drawable.ic_tabler_brand_gmail
        "telegram" in hint -> R.drawable.ic_tabler_brand_telegram
        "play store" in label || pkg == "com.android.vending" -> R.drawable.ic_tabler_brand_google_play
        // Exact matches only: half of Android's packages contain "google".
        label == "google" || pkg == "com.google.android.googlequicksearchbox" -> R.drawable.ic_tabler_brand_google
        "phone" in hint || "dialer" in hint || "dialtacts" in hint -> R.drawable.ic_tabler_phone
        else -> null
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
    val icon = shortcutIcon(app)
    if (icon != null && app != null) {
        // Known role → upright glyph; same gestures as the text label
        // (tap launches, long-press reassigns), so no tooltip here.
        Image(
            painterResource(icon),
            contentDescription = app.label.lowercase(),
            colorFilter = ColorFilter.tint(palette.text),
            modifier =
                modifier
                    .padding(vertical = 24.dp, horizontal = 16.dp)
                    .clip(CircleShape)
                    .combinedClickable(
                        onClick = { launchApp(context, app) },
                        onLongClick = onLongPress,
                        onLongClickLabel = "change shortcut",
                    ).padding(8.dp)
                    .size(24.dp),
        )
    } else {
        BasicText(
            text = (app?.label ?: "set").lowercase(),
            modifier =
                modifier
                    .padding(vertical = 18.dp)
                    .rotate(rotation)
                    .clip(RoundedCornerShape(12.dp))
                    .combinedClickable(
                        onClick = { app?.let { launchApp(context, it) } ?: onLongPress() },
                        onLongClick = onLongPress,
                    ).padding(8.dp),
            style =
                TextStyle(
                    color = if (app == null) palette.muted else palette.text,
                    fontSize = 15.sp,
                    letterSpacing = tracked(1.sp),
                    fontFamily = ff,
                ),
        )
    }
}

// ---- App drawer page ------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DrawerPage(
    apps: List<AppEntry>,
    store: LauncherStore,
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

    // Icon-preview fast scroll wants every icon ready before the finger ever
    // touches the rail; warm the cache in the background as soon as the
    // drawer's app list is available. A no-op per app once already cached, so
    // this is safe to re-key on style too (switching modes mid-session).
    LaunchedEffect(apps, store.fastScrollStyle) {
        if (store.fastScrollStyle == FastScrollStyle.INTERACTIVE) {
            AppIconCache.warmAll(context, apps)
        }
    }

    // Dragging the list dismisses the IME, which otherwise covers half the
    // results. Drag interactions only: programmatic scrolls (A–Z rail jumps,
    // query changes) must not steal focus mid-typing.
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect {
            if (it is DragInteraction.Start) focusManager.clearFocus()
        }
    }

    var query by remember { mutableStateOf("") }
    var showHidden by remember { mutableStateOf(false) }
    val q = query.trim()
    val searching = q.isNotEmpty()

    val onLaunch: (AppEntry) -> Unit = onLaunchApp

    // Long-press opens the app sheet; the keyboard must not stay over it.
    val onLongPress: (AppEntry) -> Unit = {
        focusManager.clearFocus()
        onLongPressApp(it)
    }

    // Browse list excludes hidden apps; search reveals everything (incl. hidden).
    val browse = apps.filterNot { store.isHidden(it) }
    val searchResults =
        if (searching) {
            // User-taught aliases rank first, then label prefixes.
            val aliased =
                store.aliases
                    .filterKeys { it.startsWith(q, ignoreCase = true) }
                    .values
                    .mapNotNull { key -> apps.firstOrNull { it.key == key } }
            (aliased + apps.filter { it.label.startsWith(q, ignoreCase = true) }).distinct()
        } else {
            emptyList()
        }
    val frequent =
        if (!searching) {
            browse
                .filter { store.usageCount(it) > 0 }
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
    LaunchedEffect(q) {
        aiThinking = false
        aiResult = null
    }

    fun askAi(text: String) {
        scope.launch {
            aiThinking = true
            aiResult = null
            val plan = assistant?.decide(text, apps) ?: listOf(AiIntent.Web(text))
            val command = plan.toCommand(context)
            aiThinking = false
            if (command.kind == CommandKind.ACTION && !command.confirm) {
                command.run() // confident action → just do it
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
            showHidden = false // tuck the hidden section away again
        }
    }

    // M3 Expressive surfaces, themed from the Foyer palette (reverted easily:
    // this wrapper + the pill search + the frequent card block are the change).
    FoyerMaterialTheme {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconAction(
                    icon = R.drawable.ic_tabler_arrow_narrow_left_dashed,
                    label = "back",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                ) {
                    focusManager.clearFocus()
                    onNavigateHome()
                }
                IconAction(
                    icon = R.drawable.ic_tabler_settings_2,
                    label = "settings",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                ) {
                    // Drop the IME before the settings overlay comes up.
                    focusManager.clearFocus()
                    onOpenSettings()
                }
            }

            // Expressive pill search bar; still a plain BasicTextField inside so
            // focus/IME behavior is unchanged.
            androidx.compose.material3.Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            ) {
                Row(
                    Modifier.padding(start = 18.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f)) {
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = TextStyle(color = palette.text, fontSize = 18.sp, fontFamily = ff),
                            cursorBrush = SolidColor(palette.accent),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                            keyboardActions =
                                KeyboardActions(
                                    onGo = {
                                        val app = searchResults.firstOrNull()
                                        if (app != null) {
                                            focusManager.clearFocus()
                                            onLaunch(app)
                                        } else if (q.isNotEmpty()) {
                                            focusManager.clearFocus()
                                            askAi(q)
                                        }
                                    },
                                ),
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    // Own render layer: the blinking cursor invalidates
                                    // draw every ~500ms, and without a layer that
                                    // re-records the entire window's display list.
                                    .graphicsLayer()
                                    .focusRequester(focusRequester)
                                    .semantics { contentDescription = "search apps or ask the assistant" }
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
                        IconAction(
                            icon = R.drawable.ic_tabler_x,
                            label = "clear search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        ) {
                            query = ""
                            focusManager.clearFocus()
                        }
                    }
                }
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
                                Row(
                                    modifier = Modifier.padding(vertical = 11.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    FoyerGlyph(
                                        state = GlyphState.THINKING,
                                        modifier = Modifier.padding(end = 12.dp).size(width = 64.dp, height = 24.dp),
                                    )
                                    BasicText(
                                        text = "thinking…",
                                        style = TextStyle(color = palette.muted, fontSize = 18.sp, fontFamily = ff),
                                    )
                                }
                            }
                        } else {
                            aiResult?.let { cmd ->
                                item(key = "ai-result") {
                                    when {
                                        cmd.options.isNotEmpty() ->
                                            Column(Modifier.padding(vertical = 6.dp)) {
                                                // Disambiguation: the question, then one button per candidate.
                                                BasicText(
                                                    text = cmd.title,
                                                    modifier = Modifier.padding(vertical = 6.dp),
                                                    style = TextStyle(color = palette.muted, fontSize = 15.sp, fontFamily = ff),
                                                )
                                                cmd.options.forEach { opt ->
                                                    Box(Modifier.padding(vertical = 2.dp)) {
                                                        FilledTonalButton(onClick = {
                                                            focusManager.clearFocus()
                                                            opt.run()
                                                        }) {
                                                            Text("${opt.title}${opt.subtitle?.let { "  ·  $it" } ?: ""}")
                                                        }
                                                    }
                                                }
                                            }
                                        cmd.kind == CommandKind.ACTION ->
                                            Box(Modifier.padding(vertical = 8.dp)) {
                                                FilledTonalButton(onClick = {
                                                    focusManager.clearFocus()
                                                    cmd.run()
                                                }) {
                                                    Text(if (cmd.confirm) "did you mean: ${cmd.title}?" else cmd.title)
                                                }
                                            }
                                        else -> CommandRow(cmd) { focusManager.clearFocus() }
                                    }
                                }
                            }
                        }
                        items(searchResults, key = { "s/" + it.key }) { app ->
                            AppRow(app, onLaunch, onLongPress)
                        }
                        item(key = "web") {
                            FallbackRow("search web for “$q”") {
                                focusManager.clearFocus()
                                webSearch(context, q)
                            }
                        }
                        item(key = "play") {
                            FallbackRow("find “$q” in Play Store") { playSearch(context, q) }
                        }
                        if (aiThinking || aiResult != null) {
                            item(key = "ai-tag") {
                                BasicText(
                                    text = "on-device · private",
                                    modifier = Modifier.padding(top = 16.dp),
                                    style = TextStyle(color = palette.muted, fontSize = 11.sp, letterSpacing = tracked(1.sp), fontFamily = ff),
                                )
                            }
                        }
                    } else {
                        if (frequent.isNotEmpty()) {
                            item(key = "freq-header") { SectionHeader("frequently used") }
                            items(frequent, key = { "f/" + it.key }) { app ->
                                AppRow(app, onLaunch, onLongPress)
                            }
                            item(key = "freq-divider") {
                                HorizontalDivider(
                                    modifier = Modifier.padding(top = 10.dp, bottom = 8.dp, end = 8.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                )
                            }
                        }
                        items(browse, key = { "a/" + it.key }) { app ->
                            AppRow(app, onLaunch, onLongPress)
                        }

                        // Hidden apps live at the end of the list, behind one
                        // muted count row — in context, zero chrome when empty.
                        val hiddenApps = apps.filter { store.isHidden(it) }
                        if (hiddenApps.isNotEmpty()) {
                            item(key = "hidden-toggle") {
                                BasicText(
                                    text =
                                        if (showHidden) {
                                            "hidden — tap to tuck away"
                                        } else {
                                            "${hiddenApps.size} hidden"
                                        },
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .defaultMinSize(minHeight = 48.dp)
                                            .semantics { role = Role.Button }
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { showHidden = !showHidden }
                                            .padding(top = 22.dp, bottom = 8.dp),
                                    style =
                                        TextStyle(
                                            color = palette.muted,
                                            fontSize = 13.sp,
                                            letterSpacing = tracked(1.sp),
                                            fontFamily = ff,
                                        ),
                                )
                            }
                            if (showHidden) {
                                items(hiddenApps, key = { "h/" + it.key }) { app ->
                                    AppRow(app, onLaunch, onLongPress, ghosted = true)
                                }
                            }
                        }
                    }
                }

                if (!searching && browse.isNotEmpty()) {
                    AlphabetScroller(
                        apps = browse,
                        listState = listState,
                        leadingItemCount = leadingCount,
                        style = store.fastScrollStyle,
                        modifier =
                            Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight(),
                    )
                }
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
        style = TextStyle(color = palette.muted, fontSize = 12.sp, letterSpacing = tracked(2.sp), fontFamily = ff),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppRow(
    app: AppEntry,
    onLaunch: (AppEntry) -> Unit,
    onLongPress: (AppEntry) -> Unit,
    ghosted: Boolean = false,
) {
    val palette = LocalPalette.current
    val ff = LocalFontFamily.current
    BasicText(
        text =
            buildAnnotatedString {
                append(app.label.lowercase())
                // Same app can exist in two profiles; mark the work/private copy.
                if (app.userSerial != 0L) {
                    withStyle(SpanStyle(color = palette.muted, fontSize = 13.sp)) { append("  · work") }
                }
            },
        modifier =
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .clip(RoundedCornerShape(12.dp))
                .combinedClickable(
                    onClick = { onLaunch(app) },
                    onClickLabel = "open",
                    onLongClick = { onLongPress(app) },
                    onLongClickLabel = "app options",
                ).padding(vertical = 11.dp),
        // Hidden apps show ghosted: present, but visibly set aside.
        style = TextStyle(color = if (ghosted) palette.muted else palette.text, fontSize = 19.sp, fontFamily = ff),
    )
}

/** Outlined, tappable action chip used for AI-proposed actions. */
@Composable
fun ActionButton(
    label: String,
    onClick: () -> Unit,
) {
    val palette = LocalPalette.current
    val ff = LocalFontFamily.current
    BasicText(
        text = label,
        modifier =
            Modifier
                .clip(RoundedCornerShape(24.dp))
                .border(1.dp, palette.muted.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
                .clickable { onClick() }
                .padding(horizontal = 18.dp, vertical = 12.dp),
        style = TextStyle(color = palette.text, fontSize = 17.sp, fontFamily = ff),
    )
}

@Composable
private fun FallbackRow(
    text: String,
    onClick: () -> Unit,
) {
    val palette = LocalPalette.current
    val ff = LocalFontFamily.current
    BasicText(
        text = text,
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { onClick() }
                .padding(vertical = 11.dp),
        style = TextStyle(color = palette.muted, fontSize = 18.sp, fontFamily = ff),
    )
}

@Composable
private fun CommandRow(
    command: Command,
    onBeforeRun: () -> Unit,
) {
    val palette = LocalPalette.current
    val ff = LocalFontFamily.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable {
                onBeforeRun()
                command.run()
            }.padding(vertical = 10.dp),
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
    style: FastScrollStyle = FastScrollStyle.INTERACTIVE,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current

    val letters = remember { ('A'..'Z').toList() + '#' }
    val indexForLetter =
        remember(apps) {
            val map = HashMap<Char, Int>()
            apps.forEachIndexed { i, app ->
                val key = sectionOf(app.label)
                if (!map.containsKey(key)) map[key] = i
            }
            map
        }

    var railHeight by remember { mutableIntStateOf(0) }
    var isDragging by remember { mutableStateOf(false) }
    var activeLetter by remember { mutableStateOf<Char?>(null) }
    var pointerY by remember { mutableFloatStateOf(0f) }
    // Explicit "latest wins": without this, every pointer-move during a drag
    // (dozens per second) launches its own scrollToItem coroutine with
    // nothing cancelling the ones before it — relying on scrollToItem's own
    // mutex to preempt them is implicit and wasteful (allocates + cancels a
    // coroutine per stale call instead of never starting it).
    var scrollJob: Job? by remember { mutableStateOf(null) }

    // Each letter's *actual* rendered center Y, in the rail's own coordinate
    // space (same origin pointerY uses) — not a formula estimate. A Column
    // with Arrangement.SpaceBetween does not place items at even i/(n-1)
    // fractions of its height once each item's own size is accounted for;
    // that estimate was off by up to half a letter's height, worse toward
    // the rail's ends, which is exactly where the icon and the highlighted
    // letter visibly drifted apart. Measuring removes the estimate entirely.
    val letterYs = remember(letters) { mutableStateListOf(*FloatArray(letters.size).toTypedArray()) }

    // The actual app at the top of the viewport, refreshed continuously as
    // the list scrolls — not just when the touched letter bucket changes, so
    // scrubbing within one letter's many apps still updates the icon.
    // currentSection is read far more often (every letter's active check,
    // every recomposition) than it changes, so it's derived from the same
    // lookup rather than repeating the index math in a second derivedStateOf.
    val currentApp by remember(apps, leadingItemCount) {
        derivedStateOf {
            val idx = listState.firstVisibleItemIndex - leadingItemCount
            apps.getOrNull(idx.coerceAtLeast(0))
        }
    }
    val currentSection = currentApp?.let { sectionOf(it.label) }

    // The highlighted letter, the arc bulge, and the icon's vertical position
    // all key off the finger's raw position against the *measured* letter
    // centers, so they can never disagree with each other. The scroll target
    // (and so the icon's *content*) is separate: it tracks continuously
    // through the real app list, proportional to app count, not letter
    // count — this is what makes the icon change per app, not per letter,
    // even though letter density is uneven.
    fun updateFromY(y: Float) {
        if (railHeight <= 0 || apps.isEmpty()) return
        val clampedY = y.coerceIn(0f, railHeight.toFloat())
        pointerY = clampedY
        activeLetter =
            letters.getOrNull(
                letterYs.indices.minByOrNull { kotlin.math.abs(letterYs[it] - clampedY) } ?: 0,
            )
        val frac = (clampedY / railHeight).coerceIn(0f, 1f)
        val ti = (frac * apps.size).toInt().coerceIn(0, apps.size - 1)
        scrollJob?.cancel()
        scrollJob = scope.launch { listState.scrollToItem(ti + leadingItemCount) }
    }

    val context = LocalContext.current
    val ff = LocalFontFamily.current

    // peek() is instant once warmAll() has run (the common case by the time
    // the finger touches the rail); icon() is only a fallback for the rare
    // app not yet cached.
    var previewIcon by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(currentApp?.key, style) {
        previewIcon = null
        if (style == FastScrollStyle.INTERACTIVE) {
            currentApp?.let { previewIcon = AppIconCache.peek(it) ?: AppIconCache.icon(context, it) }
        }
    }

    // fillMaxWidth so BottomCenter below centers on the whole drawer, not
    // just this narrow right-edge strip — the rail itself stays pinned to
    // the edge via its own CenterEnd alignment regardless.
    Box(modifier.fillMaxWidth()) {
        val bubbleVisible = isDragging && activeLetter != null
        val bubbleAlpha by animateFloatAsState(
            targetValue = if (bubbleVisible) 1f else 0f,
            label = "bubbleAlpha",
        )
        if (bubbleAlpha > 0.01f && style == FastScrollStyle.SIMPLE) {
            // Just the letter — no icon, no app names, the rail's original look.
            val badgeHalf = with(density) { 28.dp.toPx() }
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset {
                        IntOffset(
                            x = with(density) { (-72).dp.roundToPx() },
                            y = (pointerY - badgeHalf).roundToInt().coerceAtLeast(0),
                        )
                    }.alpha(bubbleAlpha)
                    .size(56.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(palette.background)
                    .border(1.dp, palette.muted.copy(alpha = 0.35f), RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    text = (activeLetter ?: ' ').toString(),
                    style = TextStyle(color = palette.accent, fontSize = 28.sp, fontWeight = FontWeight.Medium, fontFamily = ff),
                )
            }
        }
        if (bubbleAlpha > 0.01f && style == FastScrollStyle.INTERACTIVE) {
            // Just the current app's icon — no box, no letter. Grayscale
            // keeps it in the rail's monochrome language even though the
            // source icon is full color. Fixed at bottom-center: it used to
            // track the finger up and down the rail, which meant glancing at
            // it meant chasing it: now it's one stable place to look while
            // the finger does the scrubbing.
            previewIcon?.let { icon ->
                Image(
                    bitmap = icon,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }),
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 40.dp)
                            .alpha(bubbleAlpha)
                            .size(52.dp)
                            .clip(CircleShape),
                )
            }
        }

        // Top/bottom breathing room: padding sits inside the measured+gesture
        // area (before onSizeChanged/pointerInput), so "A" at the rail's visual
        // top and the drag math agree on where the rail actually starts.
        val railModifier =
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(30.dp)
                .padding(vertical = 36.dp)
                .onSizeChanged { railHeight = it.height }
                .pointerInput(apps) {
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            // The search field's IME would otherwise sit on top
                            // of (and cover) the rows the rail is jumping to —
                            // this is the rail's own gesture, not a drag on the
                            // list itself, so the list's own dismiss-on-drag
                            // (DrawerPage's DragInteraction.Start collector)
                            // never sees it.
                            focusManager.clearFocus()
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
                }.pointerInput(apps) {
                    detectTapGestures { offset ->
                        focusManager.clearFocus()
                        updateFromY(offset.y)
                    }
                }.semantics {
                    contentDescription = "fast scroll, A to Z"
                    currentSection?.let { stateDescription = "section $it" }
                }

        // Icon-preview mode only: a fisheye arc so the touched letter and its
        // neighbors slide left, out from under the finger — the finger covers
        // the rail's right edge, so the letters need somewhere else to be
        // seen. Falloff is read *inside* graphicsLayer (deferred to the draw
        // phase), so dragging animates smoothly without recomposing all 27
        // letters on every pixel of movement.
        val arcInfluencePx = with(density) { 250.dp.toPx() }
        val arcMaxPx = with(density) { 115.dp.toPx() }
        // A symmetric ease-in-out bezier, not the page-transition "emphasized"
        // curve — that one is one-directional (slow start, hard stop), which
        // distorts a bulge that needs to rise and fall the same way on both
        // sides of the touch point. (x2, y2) mirrors (x1, y1) through
        // (0.5, 0.5), which is what keeps the two sides symmetric.
        val arcEasing = remember { CubicBezierEasing(0.42f, 0f, 0.58f, 1f) }

        // Both styles show the same always-visible A–Z column; they only
        // differ in what appears next to the finger while dragging (the
        // icon-forward bubble for INTERACTIVE, a bare letter badge for
        // SIMPLE) and, in INTERACTIVE, the arc above.
        Column(
            railModifier,
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            letters.forEachIndexed { i, c ->
                val active = if (isDragging) c == activeLetter else c == currentSection
                val enabled = indexForLetter.containsKey(c)
                val targetColor =
                    when {
                        active -> palette.accent
                        enabled -> palette.muted
                        else -> palette.muted.copy(alpha = 0.35f)
                    }
                val color by animateColorAsState(targetColor, label = "letterColor")
                val scale by animateFloatAsState(if (active) 1.5f else 1f, label = "letterScale")
                BasicText(
                    text = c.toString(),
                    modifier =
                        Modifier
                            // The letter's real rendered center, measured after
                            // layout — not a formula estimate — so the arc's
                            // falloff and updateFromY's nearest-letter search
                            // agree on the exact same position this text
                            // actually occupies on screen.
                            .onGloballyPositioned { coords ->
                                letterYs[i] = coords.positionInParent().y + coords.size.height / 2f
                            }.graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                if (style == FastScrollStyle.INTERACTIVE && isDragging) {
                                    val letterY = letterYs.getOrElse(i) { 0f }
                                    val dist = kotlin.math.abs(letterY - pointerY)
                                    val t = (1f - (dist / arcInfluencePx).coerceIn(0f, 1f))
                                    translationX = -arcEasing.transform(t) * arcMaxPx
                                }
                            },
                    style =
                        TextStyle(
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
private fun Scrim(
    onDismiss: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
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
    limitMs: Long?,
    onCycleLimit: () -> Unit,
    onDismiss: () -> Unit,
) {
    FoyerSheet(title = app.label.lowercase(), onDismiss = onDismiss) {
        when {
            isFavorite -> SheetRow("remove from home", active = true, icon = R.drawable.ic_tabler_home_minus, onClick = onRemove)
            canAdd -> SheetRow("add to home", icon = R.drawable.ic_tabler_home_plus, onClick = onAdd)
            else -> SheetNote("home is full (max $MAX_FAVORITES)")
        }
        SheetRow(
            if (isHidden) "unhide app" else "hide app",
            active = isHidden,
            icon = if (isHidden) R.drawable.ic_tabler_eye else R.drawable.ic_tabler_eye_off,
            onClick = onToggleHide,
        )
        SheetRow(
            if (isDistracting) "unmark distracting" else "mark distracting",
            active = isDistracting,
            icon = R.drawable.ic_tabler_flame,
            onClick = onToggleDistracting,
        )
        SheetRow(
            "daily limit",
            active = limitMs != null,
            value = limitMs?.let { formatDuration(it) } ?: if (isDistracting) "${formatDuration(DISTRACTION_THRESHOLD_MS)} default" else "none",
            icon = R.drawable.ic_tabler_hourglass,
            onClick = onCycleLimit,
        )
        // Taught nicknames: tap one to forget it, or teach a new one.
        aliases.forEach { alias ->
            SheetRow("alias “$alias”  ·  remove", active = true, icon = R.drawable.ic_tabler_tag_off, onClick = { onRemoveAlias(alias) })
        }
        SheetRow("add alias", icon = R.drawable.ic_tabler_tag_plus, onClick = onAddAlias)
        SheetRow("app info", icon = R.drawable.ic_tabler_info_circle, onClick = onAppInfo)
        if (canUninstall) SheetRow("uninstall", icon = R.drawable.ic_tabler_trash, onClick = onUninstall)
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
                style = TextStyle(color = palette.muted, fontSize = 13.sp, letterSpacing = tracked(1.sp), fontFamily = ff),
            )
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = TextStyle(color = palette.text, fontSize = 20.sp, fontFamily = ff),
                cursorBrush = SolidColor(palette.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank()) onSave(text) }),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .graphicsLayer() // contain cursor-blink invalidation
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
    clockStyle: ClockStyle,
    focusActive: Boolean,
    onCycleClock: () -> Unit,
    onFocus: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    FoyerSheet(title = "home", onDismiss = onDismiss) {
        SheetRow("clock", active = clockStyle != ClockStyle.OFF, value = clockStyle.label, icon = R.drawable.ic_tabler_clock, onClick = onCycleClock)
        SheetRow(if (focusActive) "end focus" else "focus", active = focusActive, icon = R.drawable.ic_tabler_focus_2, onClick = onFocus)
        SheetRow("settings", icon = R.drawable.ic_tabler_settings_2, onClick = onOpenSettings)
    }
}

/** "focus is on until 3:40 pm" / "focus is on until you turn it off". */
fun focusLabel(focusUntil: Long): String =
    if (focusUntil == Long.MAX_VALUE) {
        "focus is on until you turn it off"
    } else {
        val fmt = SimpleDateFormat("h:mm a", Locale.getDefault())
        "focus is on until ${fmt.format(Date(focusUntil)).lowercase()}"
    }

@Composable
private fun AppPicker(
    apps: List<AppEntry>,
    side: ShortcutSide,
    onPick: (AppEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalPalette.current
    val ff = LocalFontFamily.current
    var query by remember { mutableStateOf("") }
    val q = query.trim()
    // Prefix matches first, then substring matches — same feel as the drawer.
    val shown =
        if (q.isEmpty()) {
            apps
        } else {
            apps.filter { it.label.startsWith(q, ignoreCase = true) } +
                apps.filter { !it.label.startsWith(q, ignoreCase = true) && it.label.contains(q, ignoreCase = true) }
        }
    FoyerSheet(title = "choose ${side.name.lowercase()} shortcut", onDismiss = onDismiss) {
        // The same pill as the drawer's search bar, rebuilt foundation-only so
        // the sheet stays Material-free. It sits at the top of the sheet: the
        // window is adjustNothing, so the IME covers the sheet's lower half —
        // matches surface up here.
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 10.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(lerp(palette.background, palette.text, 0.16f))
                .padding(start = 18.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .padding(vertical = 12.dp),
            ) {
                if (query.isEmpty()) {
                    BasicText(
                        text = "search",
                        style = TextStyle(color = palette.muted, fontSize = 18.sp, fontFamily = ff),
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = palette.text, fontSize = 18.sp, fontFamily = ff),
                    cursorBrush = SolidColor(palette.accent),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .graphicsLayer() // contain cursor-blink invalidation
                            .semantics { contentDescription = "search apps" },
                )
            }
            if (query.isNotEmpty()) {
                IconAction(
                    icon = R.drawable.ic_tabler_x,
                    label = "clear search",
                    tint = palette.muted,
                    size = 20.dp,
                ) { query = "" }
            }
        }
        if (shown.isEmpty()) {
            SheetNote("no app matches “$q”")
        }
        LazyColumn(Modifier.fillMaxHeight(0.62f)) {
            items(shown, key = { it.key }) { app ->
                BasicText(
                    text = app.label,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onPick(app) }
                            .padding(vertical = 12.dp),
                    style = TextStyle(color = palette.text, fontSize = 18.sp, fontFamily = ff),
                )
            }
        }
    }
}
