package com.foyerlauncher.foyer.ui.screens

import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.BitmapFactory
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
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
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
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
import com.foyerlauncher.foyer.data.GridSize
import com.foyerlauncher.foyer.data.HomeWidget
import com.foyerlauncher.foyer.data.LabelMatch
import com.foyerlauncher.foyer.data.LauncherStore
import com.foyerlauncher.foyer.data.MAX_FAVORITES
import com.foyerlauncher.foyer.data.ResizeEdge
import com.foyerlauncher.foyer.data.ShortcutSide
import com.foyerlauncher.foyer.data.UsageTracker
import com.foyerlauncher.foyer.data.WallpaperStore
import com.foyerlauncher.foyer.data.WidgetSpec
import com.foyerlauncher.foyer.data.canUninstall
import com.foyerlauncher.foyer.data.defaultSpec
import com.foyerlauncher.foyer.data.formatDuration
import com.foyerlauncher.foyer.data.key
import com.foyerlauncher.foyer.data.launchApp
import com.foyerlauncher.foyer.data.loadLaunchableApps
import com.foyerlauncher.foyer.data.matchLabel
import com.foyerlauncher.foyer.data.openAppInfo
import com.foyerlauncher.foyer.data.openClockApp
import com.foyerlauncher.foyer.data.playSearch
import com.foyerlauncher.foyer.data.requestUninstall
import com.foyerlauncher.foyer.data.sectionOf
import com.foyerlauncher.foyer.data.snapMove
import com.foyerlauncher.foyer.data.snapResize
import com.foyerlauncher.foyer.data.wallpaperFile
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
import com.foyerlauncher.foyer.ui.components.GridDots
import com.foyerlauncher.foyer.ui.components.GridLines
import com.foyerlauncher.foyer.ui.components.IconAction
import com.foyerlauncher.foyer.ui.components.LocalSharedTransitionScope
import com.foyerlauncher.foyer.ui.components.LockClockWidget
import com.foyerlauncher.foyer.ui.components.MinimalClockWidget
import com.foyerlauncher.foyer.ui.components.SheetNote
import com.foyerlauncher.foyer.ui.components.SheetRow
import com.foyerlauncher.foyer.ui.components.WidgetEditFrame
import com.foyerlauncher.foyer.ui.components.runGestureAction
import com.foyerlauncher.foyer.ui.components.sharedLabel
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

@OptIn(ExperimentalSharedTransitionApi::class)
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
    // Shared-label key of the row whose long-press opened the menu — the morph
    // must know *which* row, since the same app can sit in both "frequently
    // used" and the A–Z list. Only meaningful while menuApp != null.
    var menuAnchor by remember { mutableStateOf<String?>(null) }
    var pickerTarget by remember { mutableStateOf<ShortcutSide?>(null) }
    var showHomeMenu by remember { mutableStateOf(false) }
    // Pixel-style home edit mode: drag/resize the clock and favorites widgets.
    var editingHome by remember { mutableStateOf(false) }
    var showWallpaperMenu by remember { mutableStateOf(false) }
    // Quiet failure line inside the wallpaper sheet ("couldn't read that photo").
    var wallpaperNote by remember { mutableStateOf<String?>(null) }
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
            showWallpaperMenu ||
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

    // Declared before the overlay handler so an open sheet closes first.
    BackHandler(enabled = editingHome) { editingHome = false }

    BackHandler(enabled = overlayOpen) {
        menuApp = null
        pickerTarget = null
        showHomeMenu = false
        showWallpaperMenu = false
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

    // Spans the pager and the sheet overlays (it stacks children like a Box)
    // so a pressed row's label can morph into its sheet header and back.
    SharedTransitionLayout(
        Modifier
            .fillMaxSize()
            .background(LocalPalette.current.background)
            .matteGrain(store.backgroundTone == BackgroundTone.MATTE),
    ) {
        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
            // Paging must yield to widget drags while editing, or every
            // horizontal move would swipe to the drawer instead.
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize(), userScrollEnabled = !editingHome) { page ->
                when (page) {
                    0 ->
                        DrawerPage(
                            apps = store.focusFiltered(apps),
                            store = store,
                            onLaunchApp = requestLaunch,
                            onLongPressApp = { app, anchor ->
                                menuApp = app
                                menuAnchor = anchor
                            },
                            menuAnchor = if (menuApp != null) menuAnchor else null,
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
                            onLongPressFavorite = {
                                menuApp = it
                                menuAnchor = "fav/" + it.key
                            },
                            menuAnchor = if (menuApp != null) menuAnchor else null,
                            onLongPressShortcut = { pickerTarget = it },
                            onLongPressBackground = { showHomeMenu = true },
                            onOpenVoice = { showVoice = true },
                            onSwipeUp = { runGestureAction(context, store.swipeUpAction, gestureUi) },
                            onSwipeDown = { runGestureAction(context, store.swipeDownAction, gestureUi) },
                            onDoubleTap = { runGestureAction(context, store.doubleTapAction, gestureUi) },
                            onOpenUsage = { showUsage = true },
                            editing = editingHome,
                            onEnterEdit = { editingHome = true },
                            onExitEdit = { editingHome = false },
                        )
                    }
                }
            }

            menuApp?.let { app ->
                AppActionMenu(
                    app = app,
                    sharedKey = menuAnchor,
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
                    onEditHome = {
                        showHomeMenu = false
                        editingHome = true
                    },
                    onWallpaper = {
                        showHomeMenu = false
                        wallpaperNote = null
                        showWallpaperMenu = true
                    },
                    onOpenSettings = {
                        showHomeMenu = false
                        showSettings = true
                    },
                    onDismiss = { showHomeMenu = false },
                )
            }

            if (showWallpaperMenu) {
                val wallpaperPicker =
                    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                        if (uri != null) {
                            scope.launch {
                                WallpaperStore
                                    .install(context, uri)
                                    .onSuccess {
                                        store.setWallpaperInstalled()
                                        showWallpaperMenu = false
                                    }.onFailure { wallpaperNote = it.message ?: "couldn't use that photo" }
                            }
                        }
                    }
                FoyerSheet(title = "wallpaper", onDismiss = { showWallpaperMenu = false }) {
                    SheetRow("choose from photos", icon = R.drawable.ic_tabler_photo) {
                        wallpaperNote = null
                        wallpaperPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                    if (store.wallpaperVersion != 0L) {
                        SheetRow("remove wallpaper", icon = R.drawable.ic_tabler_x) {
                            store.clearWallpaper()
                            showWallpaperMenu = false
                        }
                    }
                    wallpaperNote?.let { SheetNote(it) }
                }
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
    menuAnchor: String?,
    onLongPressShortcut: (ShortcutSide) -> Unit,
    onLongPressBackground: () -> Unit,
    onOpenVoice: () -> Unit,
    onSwipeUp: () -> Unit,
    onSwipeDown: () -> Unit,
    onDoubleTap: () -> Unit,
    onOpenUsage: () -> Unit,
    editing: Boolean,
    onEnterEdit: () -> Unit,
    onExitEdit: () -> Unit,
) {
    val context = LocalContext.current
    val palette = LocalPalette.current
    val ff = LocalFontFamily.current
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 110.dp.toPx() }
    // The widget a tap selected while editing — only it shows resize handles.
    var selectedWidget by remember(editing) { mutableStateOf<HomeWidget?>(null) }

    // Decode off-main, keyed on the install marker so re-picking invalidates.
    val wallpaper by produceState<ImageBitmap?>(initialValue = null, store.wallpaperVersion) {
        value =
            if (store.wallpaperVersion == 0L) {
                null
            } else {
                withContext(Dispatchers.IO) {
                    BitmapFactory.decodeFile(wallpaperFile(context).path)?.asImageBitmap()
                }
            }
    }

    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                val img = wallpaper ?: return@drawBehind
                // Centre-crop: take the largest source window matching the
                // canvas aspect ratio, so the image fills without distortion.
                val dstAspect = size.width / size.height
                val srcAspect = img.width / img.height.toFloat()
                val srcW = if (srcAspect > dstAspect) (img.height * dstAspect).roundToInt() else img.width
                val srcH = if (srcAspect > dstAspect) img.height else (img.width / dstAspect).roundToInt()
                drawImage(
                    image = img,
                    srcOffset = IntOffset((img.width - srcW) / 2, (img.height - srcH) / 2),
                    srcSize = IntSize(srcW, srcH),
                    dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                )
                // Readability scrim: text sits straight on the photo, so pull
                // it toward the theme background in both light and dark.
                drawRect(palette.background.copy(alpha = 0.30f))
            }.drawBehind {
                // Extra dim while editing so the chrome reads over any wallpaper.
                if (editing) drawRect(palette.background.copy(alpha = 0.35f))
            }.pointerInput(editing) {
                if (editing) return@pointerInput
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
            }.pointerInput(editing) {
                detectTapGestures(
                    onTap = { if (editing) selectedWidget = null },
                    onLongPress = { if (!editing) onLongPressBackground() },
                    onDoubleTap = { if (!editing) onDoubleTap() },
                )
            }.windowInsetsPadding(WindowInsets.systemBars),
    ) {
        // The home widgets, absolutely positioned on the cell grid. The bottom
        // band stays reserved for "ask me" and the corner shortcuts.
        BoxWithConstraints(Modifier.fillMaxSize().padding(start = 8.dp, end = 8.dp, bottom = 72.dp)) {
            val grid = store.gridSize
            val cellW = maxWidth / grid.cols
            val cellH = maxHeight / grid.rows
            val cellWPx = with(density) { cellW.toPx() }
            val cellHPx = with(density) { cellH.toPx() }
            val clockSpec = store.homeLayout.getValue(HomeWidget.CLOCK)
            val favSpec = store.homeLayout.getValue(HomeWidget.FAVORITES)
            val clockDefault = defaultSpec(HomeWidget.CLOCK, grid.cols, grid.rows)

            // While a finger is moving a widget, the full grid fades in so the
            // snap targets are visible; it recedes to the dots on release.
            var dragActive by remember(editing) { mutableStateOf(false) }
            val gridLineAlpha by animateFloatAsState(if (dragActive) 1f else 0f, label = "grid lines")
            if (editing) {
                GridDots(grid.cols, grid.rows, Modifier.matchParentSize())
                GridLines(grid.cols, grid.rows, gridLineAlpha, Modifier.matchParentSize())
            }

            EditableWidget(
                widget = HomeWidget.CLOCK,
                spec = clockSpec,
                grid = grid,
                cellWPx = cellWPx,
                cellHPx = cellHPx,
                editing = editing,
                selected = selectedWidget == HomeWidget.CLOCK,
                onSelect = { selectedWidget = HomeWidget.CLOCK },
                onCommit = { store.moveWidget(HomeWidget.CLOCK, it) },
                onInteracting = { dragActive = it },
            ) { liveSize ->
                ClockLockupWidget(
                    store = store,
                    onOpenUsage = onOpenUsage,
                    boxSize = liveSize,
                    baseSize = DpSize(cellW * clockDefault.spanW, cellH * clockDefault.spanH),
                    interactive = !editing,
                    onLongPressWidget = onEnterEdit,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            EditableWidget(
                widget = HomeWidget.FAVORITES,
                spec = favSpec,
                grid = grid,
                cellWPx = cellWPx,
                cellHPx = cellHPx,
                editing = editing,
                selected = selectedWidget == HomeWidget.FAVORITES,
                onSelect = { selectedWidget = HomeWidget.FAVORITES },
                onCommit = { store.moveWidget(HomeWidget.FAVORITES, it) },
                onInteracting = { dragActive = it },
            ) { _ ->
                FavoritesWidget(
                    store = store,
                    menuAnchor = menuAnchor,
                    onLaunchFavorite = onLaunchFavorite,
                    onLongPressFavorite = onLongPressFavorite,
                    interactive = !editing,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        if (editing) {
            // Edit mode swaps "ask me" for the grid chooser + done pill.
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GridSize.entries.forEach { size ->
                        val active = store.gridSize == size
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(50))
                                .border(
                                    width = 1.dp,
                                    color = if (active) palette.accent else palette.muted.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(50),
                                ).semantics {
                                    role = Role.Button
                                    if (active) stateDescription = "on"
                                }.clickable(onClickLabel = "grid ${size.label}") { store.chooseGridSize(size) }
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                        ) {
                            BasicText(
                                size.label,
                                style =
                                    TextStyle(
                                        color = if (active) palette.accent else palette.muted,
                                        fontSize = 13.sp,
                                        fontFamily = ff,
                                    ),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(palette.accent)
                        .clickable(onClickLabel = "finish editing") { onExitEdit() }
                        .padding(horizontal = 26.dp, vertical = 10.dp),
                ) {
                    BasicText("done", style = TextStyle(color = palette.background, fontSize = 15.sp, fontFamily = ff))
                }
            }
        } else {
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
        }

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

/**
 * One grid-positioned home widget and its edit mechanics. Normally it just
 * places [content] at its spec's cells. While editing it adds the frame and
 * handles, and owns the gestures: body drags move with a live raw-pixel
 * offset, handle drags resize with a live box preview; release snaps to the
 * nearest cells via the store (which refuses overlaps — the box then springs
 * back instead).
 */
@Composable
private fun EditableWidget(
    widget: HomeWidget,
    spec: WidgetSpec,
    grid: GridSize,
    cellWPx: Float,
    cellHPx: Float,
    editing: Boolean,
    selected: Boolean,
    onSelect: () -> Unit,
    onCommit: (WidgetSpec) -> Boolean,
    onInteracting: (Boolean) -> Unit,
    content: @Composable (DpSize) -> Unit,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val dragDelta = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    var resizeEdge by remember(editing) { mutableStateOf<ResizeEdge?>(null) }
    var resizeDelta by remember(editing) { mutableStateOf(Offset.Zero) }

    val baseX = cellWPx * spec.col
    val baseY = cellHPx * spec.row
    val baseW = cellWPx * spec.spanW
    val baseH = cellHPx * spec.spanH

    // Live geometry: a resize-in-progress moves the dragged edge freely; the
    // snap happens on release. Half a cell is the visual floor so the box can
    // never invert under the finger.
    val liveX = baseX + if (resizeEdge == ResizeEdge.LEFT) resizeDelta.x else 0f
    val liveY = baseY + if (resizeEdge == ResizeEdge.TOP) resizeDelta.y else 0f
    val liveW =
        (
            baseW +
                when (resizeEdge) {
                    ResizeEdge.LEFT -> -resizeDelta.x
                    ResizeEdge.RIGHT -> resizeDelta.x
                    else -> 0f
                }
        ).coerceAtLeast(cellWPx / 2f)
    val liveH =
        (
            baseH +
                when (resizeEdge) {
                    ResizeEdge.TOP -> -resizeDelta.y
                    ResizeEdge.BOTTOM -> resizeDelta.y
                    else -> 0f
                }
        ).coerceAtLeast(cellHPx / 2f)
    val liveSize = with(density) { DpSize(liveW.toDp(), liveH.toDp()) }

    val settle = spring<Offset>(dampingRatio = 0.85f)
    Box(
        Modifier
            .offset { IntOffset((liveX + dragDelta.value.x).roundToInt(), (liveY + dragDelta.value.y).roundToInt()) }
            .size(liveSize)
            .then(
                if (editing) {
                    Modifier
                        .pointerInput(widget) {
                            detectTapGestures(onTap = { onSelect() })
                        }.pointerInput(widget, spec, grid, cellWPx, cellHPx) {
                            detectDragGestures(
                                onDragStart = {
                                    onSelect()
                                    onInteracting(true)
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    scope.launch { dragDelta.snapTo(dragDelta.value + amount) }
                                },
                                onDragEnd = {
                                    onInteracting(false)
                                    val target = snapMove(widget, spec, dragDelta.value.x / cellWPx, dragDelta.value.y / cellHPx, grid.cols, grid.rows)
                                    val moved = target != spec && onCommit(target)
                                    scope.launch {
                                        if (moved) {
                                            // Hand the visual position over to the new
                                            // spec, then spring out the remainder.
                                            dragDelta.snapTo(
                                                Offset(
                                                    baseX + dragDelta.value.x - cellWPx * target.col,
                                                    baseY + dragDelta.value.y - cellHPx * target.row,
                                                ),
                                            )
                                        }
                                        dragDelta.animateTo(Offset.Zero, settle)
                                    }
                                },
                                onDragCancel = {
                                    onInteracting(false)
                                    scope.launch { dragDelta.animateTo(Offset.Zero, settle) }
                                },
                            )
                        }
                } else {
                    Modifier
                },
            ),
    ) {
        content(liveSize)
        if (editing) {
            WidgetEditFrame(
                selected = selected,
                onResizeDrag = { edge, amount ->
                    if (resizeEdge != edge) {
                        resizeEdge = edge
                        resizeDelta = Offset.Zero
                        onInteracting(true)
                    }
                    resizeDelta += amount
                },
                onResizeEnd = {
                    onInteracting(false)
                    resizeEdge?.let { edge ->
                        // snapResize takes the raw axis delta; its LEFT/TOP cases
                        // already account for the inverted grow direction.
                        val dCells =
                            when (edge) {
                                ResizeEdge.LEFT, ResizeEdge.RIGHT -> resizeDelta.x / cellWPx
                                ResizeEdge.TOP, ResizeEdge.BOTTOM -> resizeDelta.y / cellHPx
                            }
                        val target = snapResize(widget, spec, edge, dCells, grid.cols, grid.rows)
                        if (target != spec) onCommit(target)
                    }
                    resizeEdge = null
                    resizeDelta = Offset.Zero
                },
                onResizeCancel = {
                    onInteracting(false)
                    resizeEdge = null
                    resizeDelta = Offset.Zero
                },
            )
        }
    }
}

/**
 * The clock lockup widget: clock + date/screen-time line + focus countdown,
 * centered in its grid box. Content scales with the box: text clocks by font
 * scale against the default-span box, dial clocks by diameter; the info lines
 * stay 13.sp for legibility.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ClockLockupWidget(
    store: LauncherStore,
    onOpenUsage: () -> Unit,
    boxSize: DpSize,
    baseSize: DpSize,
    interactive: Boolean,
    onLongPressWidget: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val palette = LocalPalette.current
    val ff = LocalFontFamily.current

    val scale = minOf(boxSize.width / baseSize.width, boxSize.height / baseSize.height).coerceIn(0.55f, 1.6f)
    val dial = minOf(boxSize.width - 16.dp, boxSize.height - 44.dp).coerceIn(80.dp, 320.dp)

    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val clockModifier =
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .combinedClickable(
                    enabled = interactive,
                    onClick = { openClockApp(context) },
                    onClickLabel = "open clock app",
                    onLongClick = onLongPressWidget,
                    onLongClickLabel = "edit home",
                )
        when (store.clockStyle) {
            ClockStyle.DIGITAL -> DigitalClockWidget(modifier = clockModifier, scale = scale)
            ClockStyle.LOCK -> LockClockWidget(modifier = clockModifier, scale = scale)
            ClockStyle.ANALOGUE -> AnalogueClockWidget(modifier = clockModifier, diameter = dial)
            ClockStyle.MINIMAL -> MinimalClockWidget(modifier = clockModifier, diameter = dial)
            ClockStyle.OFF -> Unit
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
                    .clickable(
                        enabled = interactive,
                        onClickLabel = if (hasUsage) "open screen time summary" else "grant usage access",
                    ) {
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
}

/**
 * The favorites widget: the pinned-apps spine, top-aligned in its grid box.
 * The box height decides how many rows show before the internal scroll takes
 * over (invisible when everything fits).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FavoritesWidget(
    store: LauncherStore,
    menuAnchor: String?,
    onLaunchFavorite: (AppEntry) -> Unit,
    onLongPressFavorite: (AppEntry) -> Unit,
    interactive: Boolean,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val ff = LocalFontFamily.current
    Column(
        modifier =
            modifier
                .padding(start = 20.dp)
                .verticalScroll(rememberScrollState(), enabled = interactive),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (store.favorites.isEmpty()) {
            BasicText(
                text = "long-press an app to pin it",
                style = TextStyle(color = palette.muted, fontSize = 15.sp, fontFamily = ff),
            )
        } else {
            store.focusFiltered(store.favorites).forEach { app ->
                // Same split as AppRow: the Box is the click target, the label
                // alone is the shared element.
                val anchor = "fav/" + app.key
                Box(
                    Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .combinedClickable(
                            enabled = interactive,
                            onClick = { onLaunchFavorite(app) },
                            onClickLabel = "open",
                            onLongClick = { onLongPressFavorite(app) },
                            onLongClickLabel = "app options",
                        ).padding(vertical = 9.dp),
                ) {
                    BasicText(
                        text = app.label.lowercase(),
                        modifier = Modifier.sharedLabel(anchor, visible = menuAnchor != anchor),
                        style = TextStyle(color = palette.text, fontSize = 22.sp, fontFamily = ff),
                    )
                }
            }
        }
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
    onLongPressApp: (AppEntry, String) -> Unit,
    menuAnchor: String?,
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
    val onLongPress: (AppEntry, String) -> Unit = { app, anchor ->
        focusManager.clearFocus()
        onLongPressApp(app, anchor)
    }

    // Browse list excludes hidden apps; search reveals everything (incl. hidden).
    val browse = apps.filterNot { store.isHidden(it) }
    val searchResults =
        if (searching) {
            // User-taught aliases rank first, then fuzzy label matches in
            // tightening order: prefix, word start, substring, letters in
            // order (matchLabel) — so "in" finds linkedin and pinterest too.
            val aliased =
                store.aliases
                    .filterKeys { it.startsWith(q, ignoreCase = true) }
                    .values
                    .mapNotNull { key -> apps.firstOrNull { it.key == key } }
            val fuzzy =
                apps
                    .mapNotNull { app -> matchLabel(app.label, q)?.let { app to it } }
                    .sortedWith(
                        compareBy<Pair<AppEntry, LabelMatch>>(
                            { (_, m) -> m.rank },
                            { (_, m) -> m.ranges.first().first },
                            { (app, _) -> app.label.lowercase() },
                        ),
                    ).map { it.first }
            (aliased + fuzzy).distinct()
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
                            AppRow(app, onLaunch, onLongPress, "s/", menuAnchor, highlightQuery = q)
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
                                AppRow(app, onLaunch, onLongPress, "f/", menuAnchor, distracting = store.isDistracting(app))
                            }
                            item(key = "freq-divider") {
                                HorizontalDivider(
                                    modifier = Modifier.padding(top = 10.dp, bottom = 8.dp, end = 8.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                )
                            }
                        }
                        items(browse, key = { "a/" + it.key }) { app ->
                            AppRow(app, onLaunch, onLongPress, "a/", menuAnchor, distracting = store.isDistracting(app))
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
                                    AppRow(app, onLaunch, onLongPress, "h/", menuAnchor, ghosted = true)
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
    onLongPress: (AppEntry, String) -> Unit,
    anchorPrefix: String,
    menuAnchor: String?,
    ghosted: Boolean = false,
    distracting: Boolean = false,
    highlightQuery: String = "",
) {
    val palette = LocalPalette.current
    val ff = LocalFontFamily.current
    val label = app.label.lowercase()
    // This row's end of the shared label morph; prefixed per list section so
    // an app shown twice (frequent + A–Z) never has two visible shared keys.
    val anchor = anchorPrefix + app.key
    // Row ink: hidden apps go fully muted; apps marked distracting are dimmed
    // partway toward muted so the eye isn't pulled to them, but they stay
    // legible and distinct from the hidden look.
    val rowColor =
        when {
            ghosted -> palette.muted
            distracting -> lerp(palette.text, palette.muted, 0.55f)
            else -> palette.text
        }
    // The letters the search query hit light up like on the website: the rest
    // of the label dims, the matches keep the accent (ink, when accent is
    // mono — it still reads against the dimmed rest). Ranges index `label`.
    val match = if (highlightQuery.isNotEmpty()) matchLabel(label, highlightQuery) else null
    // Click target spans the full row; only the label text itself is the
    // shared element, so the morph tracks the word, not the whole-width strip.
    Box(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = { onLaunch(app) },
                onClickLabel = "open",
                onLongClick = { onLongPress(app, anchor) },
                onLongClickLabel = "app options",
            ).padding(vertical = 11.dp),
    ) {
        BasicText(
            text =
                buildAnnotatedString {
                    append(label)
                    if (match != null) {
                        addStyle(SpanStyle(color = palette.muted), 0, label.length)
                        match.ranges.forEach { r ->
                            addStyle(SpanStyle(color = palette.accent), r.first, r.last + 1)
                        }
                    }
                    // Same app can exist in two profiles; mark the work/private copy.
                    if (app.userSerial != 0L) {
                        withStyle(SpanStyle(color = palette.muted, fontSize = 13.sp)) { append("  · work") }
                    }
                },
            modifier = Modifier.sharedLabel(anchor, visible = menuAnchor != anchor),
            // Hidden apps show ghosted; distracting apps show dimmed (see rowColor).
            style = TextStyle(color = rowColor, fontSize = 19.sp, fontFamily = ff),
        )
    }
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
    sharedKey: String?,
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
    FoyerSheet(title = app.label.lowercase(), onDismiss = onDismiss, sharedKey = sharedKey) {
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
    onEditHome: () -> Unit,
    onWallpaper: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    FoyerSheet(title = "home", onDismiss = onDismiss) {
        SheetRow("clock", active = clockStyle != ClockStyle.OFF, value = clockStyle.label, icon = R.drawable.ic_tabler_clock, onClick = onCycleClock)
        SheetRow(if (focusActive) "end focus" else "focus", active = focusActive, icon = R.drawable.ic_tabler_focus_2, onClick = onFocus)
        SheetRow("edit home", icon = R.drawable.ic_tabler_layout_grid, onClick = onEditHome)
        SheetRow("wallpaper", icon = R.drawable.ic_tabler_photo, onClick = onWallpaper)
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
