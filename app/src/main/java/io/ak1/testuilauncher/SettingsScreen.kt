package io.ak1.testuilauncher

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
            text = "un",
            modifier = Modifier.padding(top = 20.dp, bottom = 36.dp),
            style = TextStyle(color = palette.text, fontSize = 64.sp, fontWeight = FontWeight.Thin, fontFamily = ff),
        )

        SettingRow("theme", store.themeMode.label) { store.cycleTheme() }

        val scope = rememberCoroutineScope()
        SettingRow("on-device ai", if (store.aiEnabled) "on" else "off") { store.toggleAi() }
        if (store.aiEnabled) {
            SettingRow("brain", store.activeBackend.name.lowercase()) {
                store.setBackend(if (store.activeBackend == Backend.NANO) Backend.GEMMA else Backend.NANO)
            }
            when (store.activeBackend) {
                Backend.NANO -> {
                    var nanoStatus by remember { mutableStateOf("checking…") }
                    LaunchedEffect(Unit) {
                        nanoStatus = when {
                            !aiSupported() -> "needs android 12+"
                            AiIntentEngine(context).prepare() == AiStatus.READY -> "gemini-nano · ready"
                            else -> "gemini-nano · not available"
                        }
                    }
                    ModelNote(nanoStatus)
                }
                Backend.GEMMA -> when {
                    store.modelDownloaded() -> ModelNote("gemma · installed")
                    store.isDownloading -> {
                        val pct = if (store.totalBytes > 0L) store.downloadedBytes * 100 / store.totalBytes else 0
                        ModelNote("downloading… $pct%  (${mb(store.downloadedBytes)} / ${mb(store.totalBytes)})")
                    }
                    else -> {
                        Box(Modifier.padding(vertical = 10.dp)) {
                            ActionButton("download gemma") { scope.launch { store.downloadModel() } }
                        }
                        store.downloadError?.let { ModelNote("error: $it") }
                        ModelUrlField(store)
                        ModelTokenField(store)
                    }
                }
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

        BasicText(
            text = "about",
            modifier = Modifier
                .padding(top = 30.dp)
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
