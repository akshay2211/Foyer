package com.foyerlauncher.foyer.marketing

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import com.foyerlauncher.foyer.data.canUninstall
import com.foyerlauncher.foyer.data.loadLaunchableApps
import com.foyerlauncher.foyer.data.rememberLauncherStore
import com.foyerlauncher.foyer.ui.screens.AppActionMenu
import com.foyerlauncher.foyer.ui.screens.AppearancePage
import com.foyerlauncher.foyer.ui.screens.AssistantPage
import com.foyerlauncher.foyer.ui.screens.DrawerPage
import com.foyerlauncher.foyer.ui.theme.FoyerMaterialTheme
import kotlinx.coroutines.delay

/**
 * Debug-only: renders one real screen full-bleed for a marketing screenshot,
 * chosen by the `screen` intent extra (sheet | drawer | appearance | ai).
 * Reuses the actual production composables (made `internal` for this) over
 * the real on-device [rememberLauncherStore] and installed-app list — no
 * mock data, no fake PackageManager. Never compiled into a release build
 * (lives under `src/debug`).
 */
class MarketingPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val screen = intent.getStringExtra("screen") ?: "drawer"
        setContent {
            FoyerMaterialTheme {
                Box(Modifier.fillMaxSize()) {
                    when (screen) {
                        "sheet" -> SheetPreview()
                        "drawer" -> DrawerPreview()
                        "appearance" -> AppearancePreview()
                        "ai" -> AiPreview()
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetPreview() {
    val context = LocalContext.current
    val store = rememberLauncherStore()
    val apps = remember { loadLaunchableApps(context) }
    // A real installed app with a few taught aliases, so the sheet shows the
    // full row set (remove from home, hide, distracting, limit, aliases,
    // app info, uninstall) instead of the sparse default state.
    val subject = remember(apps) { apps.firstOrNull { it.label.equals("WhatsApp", ignoreCase = true) } ?: apps.firstOrNull() }
    subject?.let { app ->
        var hidden by remember { mutableStateOf(false) }
        var distracting by remember { mutableStateOf(true) }
        AppActionMenu(
            app = app,
            isFavorite = store.isFavorite(app),
            canAdd = true,
            isHidden = hidden,
            isDistracting = distracting,
            canUninstall = canUninstall(context, app),
            onAdd = {},
            onRemove = {},
            onToggleHide = { hidden = !hidden },
            onToggleDistracting = { distracting = !distracting },
            onAppInfo = {},
            onUninstall = {},
            aliases = listOf("bro"),
            onAddAlias = {},
            onRemoveAlias = {},
            limitMs = 30 * 60_000L,
            onCycleLimit = {},
            onDismiss = {},
        )
    }
}

@Composable
private fun DrawerPreview() {
    val context = LocalContext.current
    val store = rememberLauncherStore()
    val apps = remember { loadLaunchableApps(context) }
    // DrawerPage auto-focuses the search field on becoming active (real UX);
    // for a clean screenshot, let that happen and then release it so the
    // list fades back to full opacity, exactly like tapping away.
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) {
        delay(600)
        focusManager.clearFocus(force = true)
    }
    DrawerPage(
        apps = apps,
        store = store,
        onLaunchApp = {},
        onLongPressApp = {},
        onOpenSettings = {},
        active = true,
        backEnabled = false,
        onNavigateHome = {},
    )
}

@Composable
private fun AppearancePreview() {
    val store = rememberLauncherStore()
    AppearancePage(store, onBack = {})
}

@Composable
private fun AiPreview() {
    val store = rememberLauncherStore()
    AssistantPage(store, onShowGuide = {}, onBack = {})
}
