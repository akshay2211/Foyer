package com.foyerlauncher.foyer

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

/**
 * What a home-screen gesture does. Configured per gesture in settings; the
 * launcher-UI targets (drawer / ask me) are injected by [GestureUi] because
 * they live in compose state, not intents.
 */
enum class GestureAction(val label: String) {
    NONE("none"),
    DRAWER("app drawer"),
    NOTIFICATIONS("notifications"),
    QUICK_SETTINGS("quick settings"),
    BROWSER("browser"),
    VOICE("ask me"),
    LOCK_SCREEN("lock screen"),
    ;

    fun next(): GestureAction = entries[(ordinal + 1) % entries.size]
}

/** The launcher-internal destinations a gesture can open. */
class GestureUi(
    val openDrawer: () -> Unit,
    val openVoice: () -> Unit,
)

fun runGestureAction(context: Context, action: GestureAction, ui: GestureUi) {
    when (action) {
        GestureAction.NONE -> Unit
        GestureAction.DRAWER -> ui.openDrawer()
        GestureAction.VOICE -> ui.openVoice()
        GestureAction.NOTIFICATIONS -> expandNotifications(context)
        GestureAction.QUICK_SETTINGS -> expandQuickSettings(context)
        GestureAction.BROWSER -> openBrowser(context)
        GestureAction.LOCK_SCREEN ->
            if (!LockService.lock()) {
                // Service not running (or pre-P): take the user to enable it.
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }
    }
}

/** Opens the quick-settings shade (hidden StatusBarManager API, like the notification pull). */
fun expandQuickSettings(context: Context) {
    runCatching {
        val service = context.getSystemService("statusbar")
        val cls = Class.forName("android.app.StatusBarManager")
        cls.getMethod("expandSettingsPanel").invoke(service)
    }
}

/**
 * Tiny accessibility service whose only job is the lock-screen gesture
 * (GLOBAL_ACTION_LOCK_SCREEN needs one). Processes no events, reads nothing;
 * the user enables it knowingly from system accessibility settings.
 */
class LockService : AccessibilityService() {

    override fun onServiceConnected() {
        instance = this
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    companion object {
        private var instance: LockService? = null

        /** True if the screen was locked; false when the service isn't enabled. */
        fun lock(): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
            return instance?.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) ?: false
        }

        fun isEnabled(): Boolean = instance != null
    }
}
