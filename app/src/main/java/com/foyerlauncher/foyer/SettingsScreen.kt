package com.foyerlauncher.foyer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(store: LauncherStore, onClose: () -> Unit) {
    val context = LocalContext.current
    val palette = LocalPalette.current
    val ff = LocalFontFamily.current
    val tagStyle = TextStyle(color = palette.muted, fontSize = 13.sp, letterSpacing = 2.sp, fontFamily = ff)

    var showAiGuide by remember { mutableStateOf(false) }

    // Backup: user-controlled file via the system picker (no server, no account).
    var backupNote by remember { mutableStateOf<String?>(null) }
    var importPreview by remember { mutableStateOf<LauncherStore.BackupSummary?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        backupNote = if (uri == null) {
            null
        } else {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(store.exportBackup().toByteArray()) }
            }.fold({ "settings exported ✓" }, { "export failed: ${it.message}" })
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val raw = runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            }.getOrNull()
            if (raw == null) {
                backupNote = "couldn't read that file"
            } else {
                store.parseBackup(raw)
                    .onSuccess { importPreview = it; backupNote = null }
                    .onFailure { backupNote = it.message }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
    Column(
        Modifier
            .fillMaxSize()
            .background(palette.background)
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 28.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            BasicText("settings", style = tagStyle)
            BasicText("close", modifier = Modifier.clickable { onClose() }, style = tagStyle)
        }

        BasicText(
            text = "foyer",
            modifier = Modifier.padding(top = 20.dp, bottom = 36.dp),
            style = TextStyle(color = palette.text, fontSize = 64.sp, fontWeight = FontWeight.Thin, fontFamily = ff),
        )

        // Re-entry point for the onboarding prompt, visible only while another
        // launcher is the default home.
        var isDefault by remember { mutableStateOf(isDefaultLauncher(context)) }
        val roleRequest = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            isDefault = isDefaultLauncher(context)
        }
        if (!isDefault) {
            SettingRow("default launcher", "set foyer") {
                runCatching { roleRequest.launch(defaultLauncherIntent(context)) }
            }
        }

        SettingRow("theme", store.themeMode.label) { store.cycleTheme() }

        val scope = rememberCoroutineScope()
        SettingRow("on-device ai", if (store.aiEnabled) "on" else "off") { store.toggleAi() }
        if (store.aiEnabled) {
            // Capability first: know whether nano exists before offering it.
            var nanoReady by remember { mutableStateOf<Boolean?>(null) }
            LaunchedEffect(Unit) {
                nanoReady = aiSupported() && AiIntentEngine(context).prepare() == AiStatus.READY
            }
            SettingRow("brain", store.activeBackend.name.lowercase()) {
                store.setBackend(if (store.activeBackend == Backend.NANO) Backend.GEMMA else Backend.NANO)
            }
            when (store.activeBackend) {
                Backend.NANO -> when (nanoReady) {
                    null -> ModelNote("checking for gemini nano…")
                    true -> ModelNote("gemini-nano · ready")
                    false -> {
                        // Lead the user to the backend that can work here,
                        // with the reason stated — never a dead toggle.
                        ModelNote(
                            if (!aiSupported()) "nano needs android 12+ — this device can't run it"
                            else "this device has no aicore, so nano can't run here",
                        )
                        Box(Modifier.padding(vertical = 10.dp)) {
                            ActionButton("set up gemma instead") { store.setBackend(Backend.GEMMA) }
                        }
                    }
                }
                Backend.GEMMA -> when {
                    store.modelDownloaded() -> {
                        ModelNote(
                            when {
                                store.isSelfTesting -> "gemma · installed — testing…"
                                store.selfTestResult != null -> "gemma · installed — ${store.selfTestResult}"
                                else -> "gemma · installed"
                            },
                        )
                        if (!store.isSelfTesting) {
                            Box(Modifier.padding(vertical = 8.dp)) {
                                ActionButton("test model") { scope.launch { store.selfTestModel() } }
                            }
                        }
                    }
                    store.isDownloading -> {
                        val pct = if (store.totalBytes > 0L) store.downloadedBytes * 100 / store.totalBytes else 0
                        ModelNote("downloading… $pct%  (${mb(store.downloadedBytes)} / ${mb(store.totalBytes)})")
                    }
                    else -> {
                        // Step 1: pick a model. The recommended preset needs no
                        // token and no decisions; custom is for the adventurous.
                        SettingRow(
                            "model",
                            if (store.usingPresetModel) "gemma 3 1b · ~555 mb" else "custom url",
                        ) {
                            if (store.usingPresetModel) store.setModelUrl("") else store.usePresetModel()
                        }
                        if (store.usingPresetModel) {
                            ModelNote("recommended · no token or account needed")
                        } else {
                            ModelNote("paste a direct link to a mediapipe .task file")
                            ModelUrlField(store)
                            ModelTokenField(store)
                        }
                        // Step 2: download (storage is pre-checked), then a
                        // self-test proves the model answers.
                        Box(Modifier.padding(vertical = 10.dp)) {
                            ActionButton("download & set up") { scope.launch { store.downloadModel() } }
                        }
                        store.downloadError?.let { ModelNote("error: $it") }
                    }
                }
            }
            SettingRow("how the ai works", "guide") { showAiGuide = true }
        }

        ChoiceLabel("gestures")
        SettingRow("swipe up", store.swipeUpAction.label) { store.cycleSwipeUp() }
        SettingRow("swipe down", store.swipeDownAction.label) { store.cycleSwipeDown() }
        SettingRow("double tap", store.doubleTapAction.label) { store.cycleDoubleTap() }
        if (store.swipeUpAction == GestureAction.LOCK_SCREEN ||
            store.swipeDownAction == GestureAction.LOCK_SCREEN ||
            store.doubleTapAction == GestureAction.LOCK_SCREEN
        ) {
            if (!LockService.isEnabled()) {
                ModelNote("lock screen needs foyer's accessibility service — tap to enable")
                BasicText(
                    text = "enable in accessibility settings ↗",
                    modifier = Modifier
                        .padding(top = 6.dp, bottom = 4.dp)
                        .clickable {
                            runCatching {
                                context.startActivity(
                                    android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        },
                    style = TextStyle(color = palette.accent, fontSize = 14.sp, fontFamily = ff),
                )
            } else {
                ModelNote("lock screen service · enabled")
            }
        }

        ChoiceLabel("accent color")
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            AccentChoice.entries.forEach { choice ->
                val selected = store.accent == choice
                // The currently selected accent is exactly palette.accent.
                OptionText(
                    label = choice.label,
                    selected = selected,
                    color = if (selected) palette.accent else palette.muted,
                    family = ff,
                ) { store.chooseAccent(choice) }
            }
        }

        if (store.aliases.isNotEmpty()) {
            ChoiceLabel("aliases")
            store.aliases.entries.sortedBy { it.key }.forEach { (alias, key) ->
                SettingRow("“$alias” → ${key.substringBefore('/').substringAfterLast('.')}", "remove") {
                    store.removeAlias(alias)
                }
            }
        }

        ChoiceLabel("font")
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            FontChoice.entries.forEach { choice ->
                val selected = store.font == choice
                OptionText(
                    label = choice.label,
                    selected = selected,
                    color = if (selected) palette.text else palette.muted,
                    family = choice.family,
                ) { store.chooseFont(choice) }
            }
        }

        ChoiceLabel("backup")
        SettingRow("export settings", "to a file you choose") { exportLauncher.launch("foyer-backup.json") }
        SettingRow("import settings", "from a backup file") { importLauncher.launch(arrayOf("*/*")) }
        backupNote?.let { ModelNote(it) }

        // Leaves the app — the ↗ marks it as the device's settings, not foyer's.
        BasicText(
            text = "android settings ↗",
            modifier = Modifier
                .padding(top = 30.dp)
                .clickable { openSettings(context) },
            style = TextStyle(color = palette.text, fontSize = 22.sp, fontFamily = ff),
        )
        BasicText(
            text = "about",
            modifier = Modifier
                .padding(top = 22.dp)
                .clickable { openAppInfo(context, context.packageName) },
            style = TextStyle(color = palette.text, fontSize = 22.sp, fontFamily = ff),
        )
        BasicText(
            text = "reset all",
            modifier = Modifier
                .padding(top = 22.dp)
                .clickable { store.resetAll(); onClose() },
            style = TextStyle(color = palette.accent, fontSize = 22.sp, fontFamily = ff),
        )
    }

    // Import confirmation: show what the file restores before touching state.
    importPreview?.let { preview ->
        FoyerSheet(title = "restore from backup?", onDismiss = { importPreview = null }) {
            SheetNote(
                "restores ${preview.favorites} favorites, ${preview.hidden} hidden apps, " +
                    "${preview.distracting} distracting marks, ${preview.aliases} aliases, " +
                    "plus theme and gesture preferences. current settings are replaced.",
            )
            SheetRow("restore") {
                store.applyBackup(preview)
                importPreview = null
                backupNote = "settings restored ✓"
            }
            SheetRow("cancel") { importPreview = null }
        }
    }

    if (showAiGuide) {
        AiGuideScreen(onClose = { showAiGuide = false })
    }
    }
}

@Composable
private fun SettingRow(label: String, value: String, onClick: () -> Unit) {
    val palette = LocalPalette.current
    val ff = LocalFontFamily.current
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(label, style = TextStyle(color = palette.text, fontSize = 22.sp, fontFamily = ff))
        BasicText(value, style = TextStyle(color = palette.muted, fontSize = 16.sp, fontFamily = ff))
    }
}

@Composable
private fun ChoiceLabel(text: String) {
    val palette = LocalPalette.current
    val ff = LocalFontFamily.current
    BasicText(
        text = text,
        modifier = Modifier.padding(top = 26.dp, bottom = 10.dp),
        style = TextStyle(color = palette.text, fontSize = 22.sp, fontFamily = ff),
    )
}

@Composable
private fun OptionText(
    label: String,
    selected: Boolean,
    color: Color,
    family: FontFamily,
    onClick: () -> Unit,
) {
    BasicText(
        text = label,
        modifier = Modifier.clickable { onClick() }.padding(vertical = 4.dp),
        style = TextStyle(
            color = color,
            fontSize = 16.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            fontFamily = family,
        ),
    )
}

@Composable
private fun ModelNote(text: String) {
    val palette = LocalPalette.current
    val ff = LocalFontFamily.current
    BasicText(
        text = text,
        style = TextStyle(color = palette.muted, fontSize = 12.sp, letterSpacing = 1.sp, fontFamily = ff),
    )
}

@Composable
private fun ModelUrlField(store: LauncherStore) {
    val palette = LocalPalette.current
    val ff = LocalFontFamily.current
    var url by remember { mutableStateOf(store.gemmaModelUrl) }
    Column(Modifier.padding(top = 8.dp)) {
        BasicText(
            text = "model url",
            style = TextStyle(color = palette.muted, fontSize = 11.sp, letterSpacing = 1.sp, fontFamily = ff),
        )
        BasicTextField(
            value = url,
            onValueChange = { url = it; store.setModelUrl(it) },
            singleLine = true,
            textStyle = TextStyle(color = palette.text, fontSize = 13.sp, fontFamily = ff),
            cursorBrush = SolidColor(palette.accent),
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(palette.muted.copy(alpha = 0.25f)),
        )
    }
}

@Composable
private fun ModelTokenField(store: LauncherStore) {
    val palette = LocalPalette.current
    val ff = LocalFontFamily.current
    var token by remember { mutableStateOf(store.hfToken) }
    Column(Modifier.padding(top = 8.dp)) {
        BasicText(
            text = "hf token (only for gated models)",
            style = TextStyle(color = palette.muted, fontSize = 11.sp, letterSpacing = 1.sp, fontFamily = ff),
        )
        BasicTextField(
            value = token,
            onValueChange = { token = it; store.updateHfToken(it) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            textStyle = TextStyle(color = palette.text, fontSize = 13.sp, fontFamily = ff),
            cursorBrush = SolidColor(palette.accent),
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            decorationBox = { inner ->
                if (token.isEmpty()) {
                    BasicText(
                        text = "hf_…",
                        style = TextStyle(color = palette.muted, fontSize = 13.sp, fontFamily = ff),
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
}

private fun mb(bytes: Long): String = if (bytes <= 0L) "…" else "${bytes / 1_000_000} MB"
