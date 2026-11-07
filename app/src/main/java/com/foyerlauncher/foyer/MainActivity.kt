package com.foyerlauncher.foyer

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.foyerlauncher.foyer.data.rememberLauncherStore
import com.foyerlauncher.foyer.ui.components.marketingClockOverrideMillis
import com.foyerlauncher.foyer.ui.screens.LauncherScreen
import com.foyerlauncher.foyer.ui.theme.LauncherTheme
import java.util.Calendar

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // As a home app, Back should keep the user on the launcher.
        onBackPressedDispatcher.addCallback(this) { /* no-op */ }

        applyPinClockExtra(intent)

        setContent {
            val store = rememberLauncherStore()
            LauncherTheme(store.themeMode, store.accent, store.font, store.backgroundTone) {
                LauncherScreen(store)
            }
        }
    }

    // The system owns this activity's lifecycle as the HOME app and can
    // relaunch it itself right after a force-stop, racing a screenshot
    // script's own `am start` — that second start lands here, not onCreate.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        applyPinClockExtra(intent)
    }

    // Screenshot aid, debuggable builds only: `adb shell am start -n
    // <app>/.MainActivity -e pinClock 1111` freezes every clock widget at
    // that instant so marketing shots don't go stale between sessions.
    private fun applyPinClockExtra(intent: Intent) {
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (!debuggable) return
        intent.getStringExtra("pinClock")?.let { hhmm ->
            if (hhmm.length == 4) {
                marketingClockOverrideMillis =
                    Calendar
                        .getInstance()
                        .apply {
                            set(Calendar.HOUR_OF_DAY, hhmm.substring(0, 2).toInt())
                            set(Calendar.MINUTE, hhmm.substring(2, 4).toInt())
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }.timeInMillis
            }
        }
    }
}
