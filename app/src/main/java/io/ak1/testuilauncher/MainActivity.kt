package io.ak1.testuilauncher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // As a home app, Back should keep the user on the launcher.
        onBackPressedDispatcher.addCallback(this) { /* no-op */ }

        setContent {
            val store = rememberLauncherStore()

            LauncherTheme(store.themeMode, store.accent, store.font) {
                LauncherScreen(store)
            }
        }
    }
}