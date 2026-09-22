package com.jarvis.android.overlay

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.util.Base64
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.io.ByteArrayOutputStream

/**
 * Screen vision and action, wired but dormant.
 *
 * Daily v1 parks unrestricted device control. This service is the seam for it
 * and nothing more: it can describe the current screen and it can perform a
 * single gesture, but only when an already-approved action is handed to it.
 * With no approval it reports nothing and touches nothing.
 *
 * The user must still enable it by hand under Settings > Accessibility, where
 * Android shows its own strong warning. That is deliberate — a service that
 * can read every screen should never switch itself on.
 *
 * What it can do once an action is approved:
 *  - read the screen structure (which app, which controls, their text)
 *  - tap, focus and scroll a named control
 *
 * What it never does: act on its own, store what it read, or bypass a system
 * dialog. The control plane decides an action; the owner approves it; only
 * then does [perform] run.
 */
class ScreenVisionService : AccessibilityService() {

    override fun onServiceConnected() {
        instance = this
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Deliberately empty. Events are not observed until an approved action
        // asks for a snapshot. Listening continuously would be surveillance.
    }

    override fun onInterrupt() = Unit

    /**
     * Capture exactly one owner-approved frame. Nothing is written to disk.
     * Android 14+ captures the active target window so our floating overlay is
     * not baked into the image; Android 11-13 falls back to the default display.
     */
    fun captureOnce(callback: (Result<ScreenCapture>) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            callback(Result.failure(IllegalStateException("screen capture requires Android 11 or newer")))
            return
        }

        val screenshotCallback = object : AccessibilityService.TakeScreenshotCallback {
            override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                val buffer = screenshot.hardwareBuffer
                try {
                    val hardware = Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace)
                        ?: throw IllegalStateException("Android returned an unreadable screenshot")
                    val maxEdge = maxOf(hardware.width, hardware.height).coerceAtLeast(1)
                    val scale = (1280f / maxEdge.toFloat()).coerceAtMost(1f)
                    val width = (hardware.width * scale).toInt().coerceAtLeast(1)
                    val height = (hardware.height * scale).toInt().coerceAtLeast(1)
                    val software = Bitmap.createScaledBitmap(hardware, width, height, true)
                    val bytes = ByteArrayOutputStream().use { out ->
                        if (!software.compress(Bitmap.CompressFormat.JPEG, 72, out)) {
                            throw IllegalStateException("screen JPEG encoding failed")
                        }
                        out.toByteArray()
                    }
                    if (software !== hardware) software.recycle()
                    callback(
                        Result.success(
                            ScreenCapture(
                                imageBase64 = Base64.encodeToString(bytes, Base64.NO_WRAP),
                                width = width,
                                height = height,
                            ),
                        ),
                    )
                } catch (error: Throwable) {
                    callback(Result.failure(error))
                } finally {
                    buffer.close()
                }
            }

            override fun onFailure(errorCode: Int) {
                callback(Result.failure(IllegalStateException("screen capture failed ($errorCode)")))
            }
        }

        val executor = mainExecutor
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val windowId = rootInActiveWindow?.windowId
            if (windowId == null) {
                callback(Result.failure(IllegalStateException("no active window to capture")))
                return
            }
            takeScreenshotOfWindow(windowId, executor, screenshotCallback)
        } else {
            takeScreenshot(Display.DEFAULT_DISPLAY, executor, screenshotCallback)
        }
    }

    /** A one-shot description of the screen. Empty when nothing is approved. */
    fun describe(): ScreenSnapshot {
        val root = rootInActiveWindow ?: return ScreenSnapshot.EMPTY
        return try {
            ScreenSnapshot(
                packageName = root.packageName?.toString().orEmpty(),
                controls = collect(root).take(MAX_CONTROLS),
            )
        } finally {
            root.recycle()
        }
    }

    /**
     * Run one approved action against the current screen. Returns false when
     * the target cannot be found or the platform refuses — it never retries
     * and never guesses a different control.
     */
    fun perform(action: ScreenAction): Boolean {
        val root = rootInActiveWindow ?: return false
        return try {
            when (action) {
                is ScreenAction.Observe -> false
                is ScreenAction.Capture -> false
                is ScreenAction.Tap -> actOn(root, action.targetText, AccessibilityNodeInfo.ACTION_CLICK)
                is ScreenAction.Focus -> actOn(root, action.targetText, AccessibilityNodeInfo.ACTION_FOCUS)
                is ScreenAction.ScrollForward ->
                    actOn(root, action.targetText, AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            }
        } finally {
            root.recycle()
        }
    }

    private fun actOn(root: AccessibilityNodeInfo, text: String, action: Int): Boolean {
        val node = find(root, text) ?: return false
        return try {
            node.performAction(action)
        } finally {
            node.recycle()
        }
    }

    private fun find(node: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        val own = node.text?.toString().orEmpty()
        val desc = node.contentDescription?.toString().orEmpty()
        if (text.equals(own, ignoreCase = true) || text.equals(desc, ignoreCase = true)) {
            return AccessibilityNodeInfo.obtain(node)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val hit = find(child, text)
            child.recycle()
            if (hit != null) return hit
        }
        return null
    }

    private fun collect(node: AccessibilityNodeInfo, out: MutableList<ScreenControl> = mutableListOf()): List<ScreenControl> {
        val label = node.text?.toString()?.takeIf { it.isNotBlank() }
            ?: node.contentDescription?.toString()?.takeIf { it.isNotBlank() }
        if (label != null && (node.isClickable || node.isEditable || node.isScrollable)) {
            out += ScreenControl(
                text = label.take(MAX_LABEL),
                clickable = node.isClickable,
                editable = node.isEditable,
            )
        }
        if (out.size < MAX_CONTROLS) {
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                collect(child, out)
                child.recycle()
                if (out.size >= MAX_CONTROLS) break
            }
        }
        return out
    }

    companion object {
        private const val MAX_CONTROLS = 40
        private const val MAX_LABEL = 80

        @Volatile
        private var instance: ScreenVisionService? = null

        /** False until the owner turns the service on in system Settings. */
        val isEnabled: Boolean get() = instance != null

        /**
         * Describe the screen only for an action the owner already approved.
         * Returns null when the service is off or nothing was approved.
         */
        fun describeApproved(approvalToken: ApprovalToken?): ScreenSnapshot? {
            if (approvalToken == null || !approvalToken.covers(ScreenAction.Observe)) return null
            return instance?.describe()
        }

        /**
         * Capture one frame only when the owner-approved token covers Capture.
         * The callback receives the in-memory JPEG; no screenshot is persisted.
         */
        fun captureApproved(
            approvalToken: ApprovalToken?,
            callback: (Result<ScreenCapture>) -> Unit,
        ) {
            if (approvalToken == null || !approvalToken.covers(ScreenAction.Capture)) {
                callback(Result.failure(IllegalStateException("screen capture was not approved")))
                return
            }
            val service = instance
            if (service == null) {
                callback(Result.failure(IllegalStateException("screen vision accessibility service is off")))
                return
            }
            service.captureOnce(callback)
        }

        /** Perform one approved action. False when not approved or not possible. */
        fun performApproved(approvalToken: ApprovalToken?, action: ScreenAction): Boolean {
            if (approvalToken == null || !approvalToken.covers(action)) return false
            return instance?.perform(action) ?: false
        }
    }
}

/** What the owner authorised. Single-use and specific — never "do anything". */
class ApprovalToken internal constructor(private val allowed: Set<String>) {
    fun covers(action: ScreenAction): Boolean = action.key in allowed

    companion object {
        /** Built only from an owner approval. There is no public wildcard. */
        fun forActions(actions: Set<ScreenAction>): ApprovalToken =
            ApprovalToken(actions.map { it.key }.toSet())
    }
}

sealed class ScreenAction(val key: String) {
    data object Observe : ScreenAction("observe")
    data object Capture : ScreenAction("capture")
    data class Tap(val targetText: String) : ScreenAction("tap:$targetText")
    data class Focus(val targetText: String) : ScreenAction("focus:$targetText")
    data class ScrollForward(val targetText: String) : ScreenAction("scroll:$targetText")
}

data class ScreenControl(val text: String, val clickable: Boolean, val editable: Boolean)

data class ScreenCapture(val imageBase64: String, val width: Int, val height: Int)

data class ScreenSnapshot(val packageName: String, val controls: List<ScreenControl>) {
    companion object {
        val EMPTY = ScreenSnapshot("", emptyList())
    }
}
