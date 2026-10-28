package com.foyerlauncher.foyer.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foyerlauncher.foyer.ui.theme.LocalFontFamily
import com.foyerlauncher.foyer.ui.theme.LocalPalette

/**
 * The in-settings, fully offline "how the ai works" tutorial (#2): what runs
 * on-device, nano vs gemma, bringing your own model, and troubleshooting.
 */
@Composable
fun AiGuideScreen(onClose: () -> Unit) {
    val palette = LocalPalette.current
    val ff = LocalFontFamily.current
    BackHandler { onClose() }

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
            BasicText(
                "how the ai works",
                style = TextStyle(color = palette.muted, fontSize = 13.sp, letterSpacing = 2.sp, fontFamily = ff),
            )
            BasicText(
                "close",
                modifier = Modifier.clickable { onClose() },
                style = TextStyle(color = palette.muted, fontSize = 13.sp, letterSpacing = 2.sp, fontFamily = ff),
            )
        }

        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            Section(
                "everything stays on your phone",
                "foyer's assistant runs entirely on this device. your questions, " +
                    "your app list, your contacts — nothing is sent to a server, " +
                    "no account is needed, and it works in airplane mode. " +
                    "simple commands (“open maps”, “set an alarm for 7”) are resolved " +
                    "by plain code without any model at all; the model only runs for " +
                    "questions and ambiguous requests.",
            )
            Section(
                "two brains: nano and gemma",
                "nano is google's gemini nano, built into some phones (pixel 8 and later, " +
                    "recent galaxy flagships) via aicore. nothing to download — if your " +
                    "phone has it, it just works.\n\n" +
                    "gemma is an open model you bring yourself and foyer runs locally. " +
                    "it works on any reasonably modern phone (android 7+, 4 gb+ ram) — " +
                    "this is the right choice when nano says “not available”.",
            )
            Section(
                "bring your own model",
                "foyer ships no model and hosts no downloads — you choose one and foyer " +
                    "sets it up. paste a direct link to any mediapipe .task model file " +
                    "under “model url” (the suggestion button fills a known-good gemma 3 " +
                    "1b link). models you fetch are under their own licenses: gated " +
                    "hugging face models need the license accepted on their page plus a " +
                    "classic read token (fine-grained tokens fail with a 401). public, " +
                    "non-gated .task files need nothing.",
            )
            Section(
                "troubleshooting",
                "“not available” on nano — your phone has no aicore; use gemma.\n" +
                    "401 or 403 during download — gated model: accept its license and " +
                    "set a classic read token, or use the recommended model.\n" +
                    "not enough storage — the download checks for ~700 mb free first; " +
                    "clear space and retry.\n" +
                    "model downloaded but silent — run “test model” in settings; if it " +
                    "fails, the file is corrupt: delete and re-download.",
            )
        }
    }
}

@Composable
private fun Section(title: String, body: String) {
    val palette = LocalPalette.current
    val ff = LocalFontFamily.current
    BasicText(
        text = title,
        modifier = Modifier.padding(top = 26.dp, bottom = 8.dp),
        style = TextStyle(color = palette.text, fontSize = 22.sp, fontFamily = ff),
    )
    BasicText(
        text = body,
        style = TextStyle(color = palette.muted, fontSize = 16.sp, fontFamily = ff, lineHeight = 24.sp),
    )
}
