package com.foyerlauncher.foyer.ui.screens

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foyerlauncher.foyer.data.LauncherStore
import com.foyerlauncher.foyer.ui.theme.LocalFontFamily
import com.foyerlauncher.foyer.ui.theme.LocalPalette

/** True when Foyer is the device's current default home app. */
fun isDefaultLauncher(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val rm = context.getSystemService(RoleManager::class.java)
        if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME)) {
            return rm.isRoleHeld(RoleManager.ROLE_HOME)
        }
    }
    val res = context.packageManager.resolveActivity(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
        PackageManager.MATCH_DEFAULT_ONLY,
    )
    return res?.activityInfo?.packageName == context.packageName
}

/**
 * The best available "make me the default home" intent: the system role dialog
 * on Android 10+, the default-home settings page otherwise.
 */
fun defaultLauncherIntent(context: Context): Intent {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val rm = context.getSystemService(RoleManager::class.java)
        if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
            return rm.createRequestRoleIntent(RoleManager.ROLE_HOME)
        }
    }
    return Intent(Settings.ACTION_HOME_SETTINGS)
}

/**
 * First-run overlay: a short privacy-first pitch and the default-launcher
 * prompt. Fully skippable; shown exactly once (see [LauncherStore.onboarded]).
 * Permissions (mic / contacts / usage access) are *not* requested here — each
 * is asked at the point of first use by the feature that needs it.
 */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val palette = LocalPalette.current
    val ff = LocalFontFamily.current

    var becameDefault by remember { mutableStateOf(isDefaultLauncher(context)) }
    val roleRequest = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        becameDefault = isDefaultLauncher(context)
        if (becameDefault) onDone()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(palette.background)
            // Swallow all input so the launcher beneath isn't reachable.
            .clickable(enabled = false) {}
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 28.dp),
    ) {
        BasicText(
            text = "foyer",
            modifier = Modifier.padding(top = 96.dp, bottom = 28.dp),
            style = TextStyle(color = palette.text, fontSize = 64.sp, fontWeight = FontWeight.Thin, fontFamily = ff),
        )
        BasicText(
            text = "a quiet home for your phone.",
            modifier = Modifier.padding(bottom = 14.dp),
            style = TextStyle(color = palette.text, fontSize = 22.sp, fontFamily = ff),
        )
        BasicText(
            text = "search, ask, launch — the ai runs on your device. nothing leaves it.",
            modifier = Modifier.padding(bottom = 14.dp),
            style = TextStyle(color = palette.muted, fontSize = 16.sp, fontFamily = ff),
        )
        BasicText(
            text = "microphone, contacts and usage access are asked only when a feature needs them — all optional.",
            style = TextStyle(color = palette.muted, fontSize = 16.sp, fontFamily = ff),
        )

        Spacer(Modifier.height(48.dp))

        if (!becameDefault) {
            Box(Modifier.padding(bottom = 22.dp)) {
                ActionButton("make foyer your home") {
                    runCatching { roleRequest.launch(defaultLauncherIntent(context)) }
                        .onFailure { onDone() }
                }
            }
        }
        BasicText(
            text = if (becameDefault) "start" else "skip for now",
            modifier = Modifier.clickable { onDone() }.padding(vertical = 10.dp),
            style = TextStyle(color = palette.muted, fontSize = 16.sp, letterSpacing = 1.sp, fontFamily = ff),
        )
    }
}
