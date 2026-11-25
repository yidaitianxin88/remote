package ai.venice.remote

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class ControlService : AccessibilityService() {
    override fun onServiceConnected() { Hub.control = this }
    override fun onAccessibilityEvent(e: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    fun tap(nx: Float, ny: Float) {
        val p = Path().apply { moveTo(nx * Hub.width, ny * Hub.height) }
        dispatchGesture(GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(p, 0, 60)).build(), null, null)
    }

    fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, ms: Long) {
        val p = Path().apply { moveTo(x1 * Hub.width, y1 * Hub.height); lineTo(x2 * Hub.width, y2 * Hub.height) }
        dispatchGesture(GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(p, 0, ms)).build(), null, null)
    }

    fun text(s: String) {
        val cm = getSystemService(ClipboardManager::class.java)
        cm.setPrimaryClip(ClipData.newPlainText("k", s))
        val n = findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return
        n.performAction(AccessibilityNodeInfo.ACTION_PASTE)
    }
}