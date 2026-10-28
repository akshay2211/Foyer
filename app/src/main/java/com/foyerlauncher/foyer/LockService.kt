package com.foyerlauncher.foyer

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.view.accessibility.AccessibilityEvent

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
