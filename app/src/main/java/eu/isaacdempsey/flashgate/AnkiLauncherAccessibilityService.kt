package eu.isaacdempsey.flashgate

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

class AnkiLauncherAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (!isLocked) return

        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return
        // Ignore system UI (notification shade, recents overlay, status bar)
        if (pkg.contains("systemui", ignoreCase = true)) return
        // Ignore bare Android system dialogs
        if (pkg == "android") return

        startActivity(
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            }
        )
    }

    override fun onInterrupt() {}

    companion object {
        var isLocked: Boolean = false
    }
}
