package com.foyerlauncher.foyer.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foyerlauncher.foyer.LockService
import com.foyerlauncher.foyer.data.LauncherStore
import com.foyerlauncher.foyer.data.SUGGESTED_GEMMA_URL
import com.foyerlauncher.foyer.data.SUGGESTED_QWEN_BALANCED_URL
import com.foyerlauncher.foyer.data.SUGGESTED_QWEN_SMALL_URL
import com.foyerlauncher.foyer.data.openAppInfo
import com.foyerlauncher.foyer.data.openSettings
import com.foyerlauncher.foyer.ui.components.ClockStyle
import com.foyerlauncher.foyer.ui.components.FastScrollStyle
import com.foyerlauncher.foyer.ui.components.FoyerGlyph
import com.foyerlauncher.foyer.ui.components.GestureAction
import com.foyerlauncher.foyer.ui.components.GlyphState
import com.foyerlauncher.foyer.ui.theme.AccentChoice
import com.foyerlauncher.foyer.ui.theme.BackgroundTone
import com.foyerlauncher.foyer.ui.theme.FontChoice
import com.foyerlauncher.foyer.ui.theme.FoyerMaterialTheme
import com.foyerlauncher.foyer.ui.theme.LocalPalette
import com.foyerlauncher.foyer.ui.theme.ThemeMode
import com.foyerlauncher.foyer.ui.theme.accentColor
import com.foyerlauncher.foyer.ui.theme.toneBase
import kotlinx.coroutines.launch

/** The settings hierarchy: a root hub and one page per section. */
private enum class SettingsPage {
    ROOT,
    APPEARANCE,
    GESTURES,
    ASSISTANT,
    DATA,
}

@Composable
fun SettingsScreen(
    store: LauncherStore,
    onClose: () -> Unit,
) {
    var page by remember { mutableStateOf(SettingsPage.ROOT) }
    var showAiGuide by remember { mutableStateOf(false) }

    FoyerMaterialTheme {
        BackHandler {
            when {
                showAiGuide -> showAiGuide = false
                page != SettingsPage.ROOT -> page = SettingsPage.ROOT
                else -> onClose()
            }
        }

        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            AnimatedContent(
                targetState = page,
                transitionSpec = {
                    val emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
                    if (targetState != SettingsPage.ROOT) {
                        (slideInHorizontally(tween(350, easing = emphasized)) { it / 4 } + fadeIn(tween(250)))
                            .togetherWith(slideOutHorizontally(tween(350, easing = emphasized)) { -it / 8 } + fadeOut(tween(200)))
                    } else {
                        (slideInHorizontally(tween(350, easing = emphasized)) { -it / 8 } + fadeIn(tween(250)))
                            .togetherWith(slideOutHorizontally(tween(350, easing = emphasized)) { it / 4 } + fadeOut(tween(200)))
                    }
                },
                label = "settings pages",
            ) { current ->
                when (current) {
                    SettingsPage.ROOT ->
                        RootPage(
                            store = store,
                            onOpen = { page = it },
                            onClose = onClose,
                        )
                    SettingsPage.APPEARANCE ->
                        AppearancePage(store) { page = SettingsPage.ROOT }
                    SettingsPage.GESTURES ->
                        GesturesPage(store) { page = SettingsPage.ROOT }
                    SettingsPage.ASSISTANT ->
                        AssistantPage(store, onShowGuide = { showAiGuide = true }) { page = SettingsPage.ROOT }
                    SettingsPage.DATA ->
                        DataPage(store) { page = SettingsPage.ROOT }
                }
            }

            if (showAiGuide) {
                AiGuideScreen(onClose = { showAiGuide = false })
            }
        }
    }
}

// ---- Pages -----------------------------------------------------------------

@Composable
private fun RootPage(
    store: LauncherStore,
    onOpen: (SettingsPage) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var showResetConfirm by remember { mutableStateOf(false) }

    PageScaffold(title = "settings", subtitle = "foyer", onBack = onClose, backIsClose = true) {
        // Re-entry point for the onboarding prompt, visible only while another
        // launcher is the default home.
        var isDefault by remember { mutableStateOf(isDefaultLauncher(context)) }
        val roleRequest =
            rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                isDefault = isDefaultLauncher(context)
            }
        if (!isDefault) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text("foyer isn't your home app yet", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "set it as the default launcher so the home button always opens foyer.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
                    )
                    Button(onClick = { runCatching { roleRequest.launch(defaultLauncherIntent(context)) } }) {
                        Text("set as default")
                    }
                }
            }
        }

        GroupLabel("customize")
        SettingsGroup(
            { shape ->
                NavRow(
                    shape = shape,
                    title = "appearance",
                    subtitle = "${store.themeMode.label} · ${store.accent.label} · ${store.font.label}",
                ) { onOpen(SettingsPage.APPEARANCE) }
            },
            { shape ->
                NavRow(
                    shape = shape,
                    title = "gestures",
                    subtitle = "up ${store.swipeUpAction.label} · down ${store.swipeDownAction.label}",
                ) { onOpen(SettingsPage.GESTURES) }
            },
        )

        GroupLabel("assistant")
        SettingsGroup(
            { shape ->
                val state =
                    when {
                        !store.aiEnabled -> "off"
                        store.isDownloading -> "on · downloading model…"
                        store.modelDownloaded() -> "on · model ready"
                        else -> "on · needs a model"
                    }
                NavRow(shape = shape, title = "ai assistant", subtitle = state) { onOpen(SettingsPage.ASSISTANT) }
            },
        )

        GroupLabel("data")
        SettingsGroup(
            { shape ->
                val aliasNote = if (store.aliases.isEmpty()) "" else " · ${store.aliases.size} aliases"
                NavRow(shape = shape, title = "backup & aliases", subtitle = "export, import$aliasNote") {
                    onOpen(SettingsPage.DATA)
                }
            },
        )

        GroupLabel("system")
        SettingsGroup(
            { shape ->
                NavRow(shape = shape, title = "android settings", subtitle = "opens the system app ↗", chevron = false) {
                    openSettings(context)
                }
            },
            { shape ->
                NavRow(shape = shape, title = "app info", subtitle = "version & permissions ↗", chevron = false) {
                    openAppInfo(context, context.packageName)
                }
            },
            { shape ->
                NavRow(
                    shape = shape,
                    title = "reset foyer",
                    subtitle = "clears every setting on this device",
                    chevron = false,
                    titleColor = MaterialTheme.colorScheme.error,
                ) { showResetConfirm = true }
            },
        )
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("reset foyer?") },
            text = { Text("favorites, hidden apps, aliases, limits and every preference are cleared. this can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showResetConfirm = false
                    store.resetAll()
                    onClose()
                }) { Text("reset", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showResetConfirm = false }) { Text("cancel") } },
        )
    }
}

@Composable
private fun AppearancePage(
    store: LauncherStore,
    onBack: () -> Unit,
) {
    PageScaffold(title = "appearance", onBack = onBack) {
        GroupLabel("theme")
        Segmented(ThemeMode.entries, store.themeMode, { it.label }) { store.chooseTheme(it) }

        GroupLabel("accent")
        GroupSurface(RoundedCornerShape(22.dp)) {
            Row(
                Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val palette = LocalPalette.current
                AccentChoice.entries.forEach { choice ->
                    Swatch(
                        color = accentColor(choice, palette.text),
                        selected = store.accent == choice,
                        description = "accent ${choice.label}",
                    ) { store.chooseAccent(choice) }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    store.accent.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        GroupLabel("font")
        SettingsGroup(
            *FontChoice.entries
                .map { choice ->
                    @Composable { shape: Shape ->
                        RadioRow(
                            shape = shape,
                            title = choice.label,
                            family = choice.family,
                            selected = store.font == choice,
                        ) { store.chooseFont(choice) }
                    }
                }.toTypedArray(),
        )

        GroupLabel("background")
        GroupSurface(RoundedCornerShape(22.dp)) {
            Row(
                Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val palette = LocalPalette.current
                BackgroundTone.entries.forEach { tone ->
                    Swatch(
                        color = toneBase(tone)?.first?.background ?: palette.background,
                        selected = store.backgroundTone == tone,
                        description = "background ${tone.label}",
                    ) { store.chooseTone(tone) }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    store.backgroundTone.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        GroupLabel("clock")
        Segmented(ClockStyle.entries, store.clockStyle, { it.label }) { store.chooseClockStyle(it) }

        GroupLabel("fast scroll")
        Segmented(FastScrollStyle.entries, store.fastScrollStyle, { it.label }) { store.chooseFastScroll(it) }
        Caption("how the a–z rail in the app drawer behaves while you scrub it.")
    }
}

@Composable
private fun GesturesPage(
    store: LauncherStore,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var picker by remember { mutableStateOf<String?>(null) }

    PageScaffold(title = "gestures", onBack = onBack) {
        GroupLabel("home screen")
        SettingsGroup(
            { shape ->
                NavRow(shape, "swipe up", store.swipeUpAction.label) { picker = "up" }
            },
            { shape ->
                NavRow(shape, "swipe down", store.swipeDownAction.label) { picker = "down" }
            },
            { shape ->
                NavRow(shape, "double tap", store.doubleTapAction.label) { picker = "tap" }
            },
        )

        val usesLock =
            store.swipeUpAction == GestureAction.LOCK_SCREEN ||
                store.swipeDownAction == GestureAction.LOCK_SCREEN ||
                store.doubleTapAction == GestureAction.LOCK_SCREEN
        if (usesLock) {
            if (LockService.isEnabled()) {
                Caption("lock screen service · enabled")
            } else {
                GroupLabel("lock screen")
                SettingsGroup(
                    { shape ->
                        NavRow(
                            shape = shape,
                            title = "enable accessibility service",
                            subtitle = "required for the lock gesture ↗",
                            chevron = false,
                        ) {
                            runCatching {
                                context.startActivity(
                                    android.content
                                        .Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        }
                    },
                )
                Caption("foyer's service performs the system lock action and nothing else — it reads no screen content.")
            }
        }
    }

    picker?.let { which ->
        val (title, current) =
            when (which) {
                "up" -> "swipe up" to store.swipeUpAction
                "down" -> "swipe down" to store.swipeDownAction
                else -> "double tap" to store.doubleTapAction
            }
        AlertDialog(
            onDismissRequest = { picker = null },
            title = { Text(title) },
            text = {
                Column {
                    GestureAction.entries.forEach { action ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(selected = action == current, role = androidx.compose.ui.semantics.Role.RadioButton) {
                                    when (which) {
                                        "up" -> store.chooseSwipeUp(action)
                                        "down" -> store.chooseSwipeDown(action)
                                        else -> store.chooseDoubleTap(action)
                                    }
                                    picker = null
                                }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = action == current, onClick = null)
                            Text(action.label, Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { picker = null }) { Text("cancel") } },
        )
    }
}

@Composable
private fun AssistantPage(
    store: LauncherStore,
    onShowGuide: () -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    PageScaffold(title = "ai assistant", onBack = onBack) {
        SettingsGroup(
            { shape ->
                SwitchRow(
                    shape = shape,
                    title = "on-device ai",
                    subtitle = "voice and typed commands, fully local",
                    checked = store.aiEnabled,
                ) { store.toggleAi() }
            },
        )

        if (store.aiEnabled) {
            GroupLabel("model")
            when {
                store.modelDownloaded() -> {
                    SettingsGroup(
                        { shape ->
                            NavRow(
                                shape = shape,
                                title = "model installed",
                                subtitle =
                                    when {
                                        store.isSelfTesting -> "testing…"
                                        store.selfTestResult != null -> store.selfTestResult ?: ""
                                        else -> "ready"
                                    },
                                chevron = false,
                            ) {}
                        },
                    )
                    if (!store.isSelfTesting) {
                        FilledTonalButton(
                            onClick = { scope.launch { store.selfTestModel() } },
                            modifier = Modifier.padding(top = 10.dp),
                        ) { Text("test model") }
                    }
                }

                store.isDownloading -> {
                    GroupSurface(RoundedCornerShape(22.dp)) {
                        Column(Modifier.padding(18.dp)) {
                            FoyerGlyph(
                                state = GlyphState.THINKING,
                                modifier = Modifier.padding(bottom = 12.dp).size(width = 84.dp, height = 30.dp),
                            )
                            if (store.totalBytes > 0L) {
                                val fraction = store.downloadedBytes.toFloat() / store.totalBytes
                                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                                Text(
                                    "downloading… ${(fraction * 100).toInt()}%  (${mb(store.downloadedBytes)} / ${mb(store.totalBytes)})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 10.dp),
                                )
                            } else {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                Text(
                                    "downloading…",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 10.dp),
                                )
                            }
                        }
                    }
                }

                else -> {
                    // Foyer ships no model (product decision): the user brings a
                    // third-party .task model; we only make the setup easy.
                    Caption("foyer includes no model — paste a direct link to any mediapipe .task file, or tap a suggestion.")
                    OutlinedTextField(
                        value = store.modelUrl,
                        onValueChange = { store.updateModelUrl(it) },
                        label = { Text("model url") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    )
                    var token by remember { mutableStateOf(store.hfToken) }
                    OutlinedTextField(
                        value = token,
                        onValueChange = {
                            token = it
                            store.updateHfToken(it)
                        },
                        label = { Text("hf token · gated models only") },
                        placeholder = { Text("hf_…") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    )

                    if (store.modelUrl.isBlank()) {
                        GroupLabel("suggestions")
                        SettingsGroup(
                            { shape ->
                                NavRow(shape, "qwen 2.5 · 0.5b · light", "~550 mb · no account needed", chevron = false) {
                                    store.updateModelUrl(SUGGESTED_QWEN_SMALL_URL)
                                }
                            },
                            { shape ->
                                NavRow(shape, "qwen 2.5 · 1.5b · balanced", "~1.6 gb · no account needed", chevron = false) {
                                    store.updateModelUrl(SUGGESTED_QWEN_BALANCED_URL)
                                }
                            },
                            { shape ->
                                NavRow(shape, "gemma 3 · 1b", "~555 mb · hf license + classic read token", chevron = false) {
                                    store.updateModelUrl(SUGGESTED_GEMMA_URL)
                                }
                            },
                        )
                    }

                    Button(
                        onClick = { scope.launch { store.downloadModel() } },
                        modifier = Modifier.padding(top = 14.dp),
                    ) { Text("download & set up") }
                    store.downloadError?.let { Caption("error: $it") }
                }
            }

            GroupLabel("learn")
            SettingsGroup(
                { shape ->
                    NavRow(shape, "how the ai works", "the offline guide") { onShowGuide() }
                },
            )
        }
    }
}

@Composable
private fun DataPage(
    store: LauncherStore,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var backupNote by remember { mutableStateOf<String?>(null) }
    var importPreview by remember { mutableStateOf<LauncherStore.BackupSummary?>(null) }

    val exportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            backupNote =
                if (uri == null) {
                    null
                } else {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { it.write(store.exportBackup().toByteArray()) }
                    }.fold({ "settings exported ✓" }, { "export failed: ${it.message}" })
                }
        }
    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                val raw =
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                    }.getOrNull()
                if (raw == null) {
                    backupNote = "couldn't read that file"
                } else {
                    store
                        .parseBackup(raw)
                        .onSuccess {
                            importPreview = it
                            backupNote = null
                        }.onFailure { backupNote = it.message }
                }
            }
        }

    PageScaffold(title = "backup & aliases", onBack = onBack) {
        GroupLabel("backup")
        SettingsGroup(
            { shape ->
                NavRow(shape, "export settings", "to a json file you choose", chevron = false) {
                    exportLauncher.launch("foyer-backup.json")
                }
            },
            { shape ->
                NavRow(shape, "import settings", "replaces current settings", chevron = false) {
                    importLauncher.launch(arrayOf("*/*"))
                }
            },
        )
        backupNote?.let { Caption(it) }
        Caption("backups stay in files you own — foyer has no server and no account.")

        if (store.aliases.isNotEmpty()) {
            GroupLabel("aliases you've taught")
            SettingsGroup(
                *store.aliases.entries
                    .sortedBy { it.key }
                    .map { (alias, appKey) ->
                        @Composable { shape: Shape ->
                            val appName = appKey.substringBefore('/').substringAfterLast('.')
                            RowSurface(shape = shape, onClick = null) {
                                Column(Modifier.weight(1f)) {
                                    Text("“$alias”", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "opens $appName",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                TextButton(onClick = { store.removeAlias(alias) }) { Text("remove") }
                            }
                        }
                    }.toTypedArray(),
            )
            Caption("teach new aliases from any app's long-press menu.")
        }
    }

    // Import confirmation: show what the file restores before touching state.
    importPreview?.let { preview ->
        AlertDialog(
            onDismissRequest = { importPreview = null },
            title = { Text("restore from backup?") },
            text = {
                Text(
                    "restores ${preview.favorites} favorites, ${preview.hidden} hidden apps, " +
                        "${preview.distracting} distracting marks, ${preview.aliases} aliases, " +
                        "plus theme and gesture preferences. current settings are replaced.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    store.applyBackup(preview)
                    importPreview = null
                    backupNote = "settings restored ✓"
                }) { Text("restore") }
            },
            dismissButton = { TextButton(onClick = { importPreview = null }) { Text("cancel") } },
        )
    }
}

// ---- Scaffolding & building blocks ------------------------------------------

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PageScaffold(
    title: String,
    onBack: () -> Unit,
    subtitle: String? = null,
    backIsClose: Boolean = false,
    content: @Composable () -> Unit,
) {
    val scrollBehavior =
        androidx.compose.material3.TopAppBarDefaults
            .exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(title) },
                subtitle = subtitle?.let { { Text(it) } },
                navigationIcon = {
                    if (backIsClose) {
                        TextButton(onClick = onBack) {
                            Text(
                                "back",
                                style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 2.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "back")
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            content()
            Spacer(Modifier.height(32.dp))
        }
    }
}

/** M3 Expressive grouped list: rows in one visual card, 2dp seams between them. */
@Composable
private fun SettingsGroup(vararg rows: @Composable (shape: Shape) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        rows.forEachIndexed { index, row -> row(groupShape(index, rows.size)) }
    }
}

private fun groupShape(
    index: Int,
    count: Int,
): Shape {
    val outer = 22.dp
    val inner = 6.dp
    return when {
        count == 1 -> RoundedCornerShape(outer)
        index == 0 -> RoundedCornerShape(outer, outer, inner, inner)
        index == count - 1 -> RoundedCornerShape(inner, inner, outer, outer)
        else -> RoundedCornerShape(inner)
    }
}

@Composable
private fun GroupSurface(
    shape: Shape,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
        content = content,
    )
}

@Composable
private fun RowSurface(
    shape: Shape,
    onClick: (() -> Unit)?,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    val rowContent: @Composable () -> Unit = {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(),
            content = rowContent,
        )
    } else {
        GroupSurface(shape = shape, content = rowContent)
    }
}

@Composable
private fun NavRow(
    shape: Shape,
    title: String,
    subtitle: String? = null,
    chevron: Boolean = true,
    titleColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
    onClick: () -> Unit,
) {
    RowSurface(shape = shape, onClick = onClick) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = titleColor)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (chevron) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SwitchRow(
    shape: Shape,
    title: String,
    subtitle: String?,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier =
            Modifier
                .fillMaxWidth()
                .toggleable(value = checked, role = androidx.compose.ui.semantics.Role.Switch) { onToggle() },
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            Switch(checked = checked, onCheckedChange = null)
        }
    }
}

@Composable
private fun RadioRow(
    shape: Shape,
    title: String,
    family: FontFamily,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier =
            Modifier
                .fillMaxWidth()
                .selectable(selected = selected, role = androidx.compose.ui.semantics.Role.RadioButton, onClick = onSelect),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = family),
                modifier = Modifier.weight(1f),
            )
            RadioButton(selected = selected, onClick = null)
        }
    }
}

@Composable
private fun <T> Segmented(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
            ) { Text(label(option)) }
        }
    }
}

@Composable
private fun Swatch(
    color: androidx.compose.ui.graphics.Color,
    selected: Boolean,
    description: String,
    onClick: () -> Unit,
) {
    val ring = MaterialTheme.colorScheme.primary
    val edge = MaterialTheme.colorScheme.outlineVariant
    Box(
        Modifier
            .size(40.dp)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) ring else edge,
                shape = CircleShape,
            ).padding(5.dp)
            .background(color, CircleShape)
            .semantics {
                contentDescription = description
            }.selectable(selected = selected, role = androidx.compose.ui.semantics.Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = if (color.luminance() > 0.5f) androidx.compose.ui.graphics.Color.Black else androidx.compose.ui.graphics.Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 18.dp, top = 26.dp, bottom = 10.dp),
    )
}

@Composable
private fun Caption(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 18.dp).padding(top = 10.dp),
    )
}

private fun mb(bytes: Long): String = if (bytes <= 0L) "…" else "${bytes / 1_000_000} MB"
