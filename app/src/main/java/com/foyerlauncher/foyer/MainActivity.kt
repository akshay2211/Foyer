package com.foyerlauncher.foyer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.foyerlauncher.foyer.data.rememberLauncherStore
import com.foyerlauncher.foyer.ui.screens.LauncherScreen
import com.foyerlauncher.foyer.ui.theme.LauncherTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // As a home app, Back should keep the user on the launcher.
        onBackPressedDispatcher.addCallback(this) { /* no-op */ }

        setContent {
            val store = rememberLauncherStore()
            LauncherTheme(store.themeMode, store.accent, store.font, store.backgroundTone) {
                LauncherScreen(store)
            }
        }
    }
}
