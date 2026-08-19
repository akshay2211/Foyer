package io.ak1.testuilauncher

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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

        val aiAvailable = remember { onDeviceAiAvailable(context) }
        SettingRow("on-device ai", if (store.aiEnabled) "on" else "off") { store.toggleAi() }
        if (store.aiEnabled && !aiAvailable) {
            BasicText(
                text = "no on-device model found — using heuristics",
                style = TextStyle(color = palette.muted, fontSize = 12.sp, fontFamily = ff),
            )
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
