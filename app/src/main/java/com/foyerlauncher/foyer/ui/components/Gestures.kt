package com.foyerlauncher.foyer.ui.components

import android.content.Context
import com.foyerlauncher.foyer.data.expandNotifications
import com.foyerlauncher.foyer.data.openBrowser

/**
 * What a home-screen gesture does. Configured per gesture in settings; the
 * launcher-UI targets (drawer / ask me) are injected by [GestureUi] because
 * they live in compose state, not intents.
 */
enum class GestureAction(
    val label: String,
) {
    NONE("none"),
    DRAWER("app drawer"),
    NOTIFICATIONS("notifications"),
    QUICK_SETTINGS("quick settings"),
    BROWSER("browser"),
    VOICE("ask me"),
    ;

    fun next(): GestureAction = entries[(ordinal + 1) % entries.size]
}

/** The launcher-internal destinations a gesture can open. */
class GestureUi(
    val openDrawer: () -> Unit,
    val openVoice: () -> Unit,
)

fun runGestureAction(
    context: Context,
    action: GestureAction,
    ui: GestureUi,
) {
    when (action) {
        GestureAction.NONE -> Unit
        GestureAction.DRAWER -> ui.openDrawer()
        GestureAction.VOICE -> ui.openVoice()
        GestureAction.NOTIFICATIONS -> expandNotifications(context)
        GestureAction.QUICK_SETTINGS -> expandQuickSettings(context)
        GestureAction.BROWSER -> openBrowser(context)
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
