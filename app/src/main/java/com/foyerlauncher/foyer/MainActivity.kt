package com.foyerlauncher.foyer

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // As a home app, Back should keep the user on the launcher.
        onBackPressedDispatcher.addCallback(this) { /* no-op */ }

        setContent {
            val store = rememberLauncherStore()

            // Wallpaper mode (#17): the system renders the user's wallpaper
            // (static or live) behind the window; Foyer never picks or stores
            // one. The home root draws a scrim instead of an opaque fill.
            LaunchedEffect(store.showWallpaper) {
                if (store.showWallpaper) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
                    window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
                }
            }

            LauncherTheme(store.themeMode, store.accent, store.font, store.backgroundTone) {
                LauncherScreen(store)
            }
        }
    }
}