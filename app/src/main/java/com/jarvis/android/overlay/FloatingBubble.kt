package com.jarvis.android.overlay

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.jarvis.android.MainActivity
import com.jarvis.android.R
import com.jarvis.android.data.prefs.SettingsStore
import com.jarvis.android.ui.components.JarvisBrain
import com.jarvis.android.ui.components.OrbActivity
import com.jarvis.android.ui.theme.JarvisTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The floating brain. A small bubble that stays above other apps so the owner
 * can talk to JARVIS without leaving the screen they are on.
 *
 * It is only a door. Tapping it opens the app on the conversation. It does not
 * read the screen and it does not act — that seam is [ScreenVisionService],
 * which stays dormant until an action is explicitly approved.
 *
 * Showing a window over other apps needs the SYSTEM_ALERT_WINDOW grant, which
 * only the owner can give from system Settings. The service never prompts for
 * it itself and never starts without it.
 */
class FloatingBubbleService : Service() {

    private lateinit var windowManager: WindowManager
    private var bubble: ComposeView? = null
    private var owner: OverlayOwner? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var activity by mutableStateOf(OrbActivity.IDLE)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_SET_ACTIVITY -> {
                activity = intent.getStringExtra(EXTRA_ACTIVITY)
                    ?.let { runCatching { OrbActivity.valueOf(it) }.getOrNull() }
                    ?: activity
            }
        }
        if (!canDraw(this)) {
            // The grant was revoked while we were up. Come down quietly.
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, buildNotification())
        if (bubble == null) showBubble()
        return START_STICKY
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showBubble() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 48
            y = 240
        }

        val host = OverlayOwner()
        owner = host
        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(host)
            setViewTreeViewModelStoreOwner(host)
            setViewTreeSavedStateRegistryOwner(host)
            setContent { BubbleContent(params) }
        }
        host.onCreate()
        host.onStart()
        host.onResume()
        windowManager.addView(view, params)
        bubble = view
    }

    @androidx.compose.runtime.Composable
    private fun BubbleContent(params: WindowManager.LayoutParams) {
        var expanded by androidx.compose.runtime.remember { mutableStateOf(false) }
        JarvisTheme {
            Column(horizontalAlignment = Alignment.End) {
                if (expanded) BubblePanel(onClose = { expanded = false })
                Box(
                    Modifier
                        .size(72.dp)
                        .pointerInput(Unit) {
                            detectDragGestures { change, drag ->
                                change.consume()
                                params.x += drag.x.toInt()
                                params.y += drag.y.toInt()
                                bubble?.let { windowManager.updateViewLayout(it, params) }
                            }
                        }
                        .clickable { expanded = !expanded },
                    contentAlignment = Alignment.Center,
                ) {
                    JarvisBrain(
                        size = 68.dp,
                        activity = activity,
                        contentDescription = "JARVIS",
                    )
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun BubblePanel(onClose: () -> Unit) {
        Column(
            Modifier
                .padding(bottom = 8.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xF20B1622))
                .border(1.dp, Color(0xFF23465C), RoundedCornerShape(18.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("JARVIS", color = Color(0xFF3FD3EC))
            PanelButton("Open conversation") {
                onClose()
                openApp()
            }
            PanelButton("Hide bubble") {
                scope.launch { SettingsStore(this@FloatingBubbleService).setFloatingBubble(false) }
                stopSelf()
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun PanelButton(label: String, onClick: () -> Unit) {
        Box(
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF12303E))
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text(label, color = Color(0xFFE8EEF8))
        }
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "JARVIS bubble", NotificationManager.IMPORTANCE_MIN),
            )
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("JARVIS is listening")
            .setContentText("Tap the bubble to talk")
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    override fun onDestroy() {
        bubble?.let { runCatching { windowManager.removeView(it) } }
        bubble = null
        owner?.onDestroy()
        owner = null
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.jarvis.android.overlay.START"
        const val ACTION_STOP = "com.jarvis.android.overlay.STOP"
        const val ACTION_SET_ACTIVITY = "com.jarvis.android.overlay.SET_ACTIVITY"
        const val EXTRA_ACTIVITY = "activity"

        private const val CHANNEL_ID = "jarvis_bubble"
        private const val NOTIFICATION_ID = 7101

        fun canDraw(context: Context): Boolean = Settings.canDrawOverlays(context)

        /** Raise the bubble. No-op when the owner has not granted the overlay. */
        fun start(context: Context) {
            if (!canDraw(context)) return
            val intent = Intent(context, FloatingBubbleService::class.java).setAction(ACTION_START)
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, FloatingBubbleService::class.java).setAction(ACTION_STOP),
            )
        }

        /** Drive the bubble's brain from the conversation: thinking, listening, idle. */
        fun setActivity(context: Context, activity: OrbActivity) {
            context.startService(
                Intent(context, FloatingBubbleService::class.java)
                    .setAction(ACTION_SET_ACTIVITY)
                    .putExtra(EXTRA_ACTIVITY, activity.name),
            )
        }

        /** Bring the bubble back after a reboot or an update, if it was enabled. */
        fun restoreIfEnabled(context: Context) {
            CoroutineScope(Dispatchers.IO).launch {
                val enabled = SettingsStore(context).settings.first().floatingBubbleEnabled
                if (enabled) start(context)
            }
        }
    }
}
