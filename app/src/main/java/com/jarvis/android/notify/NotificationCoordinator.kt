package com.jarvis.android.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.jarvis.android.MainActivity
import com.jarvis.android.R
import com.jarvis.android.contract.ApprovalOutcome
import com.jarvis.android.data.repo.SessionSnapshot
import com.jarvis.android.data.state.ConnectionState

/**
 * AND-W8 notification shell: platform-compliant, category'd notifications for
 * approvals (high importance, actionable) and completed replies while the app
 * is backgrounded. No foreground WebSocket service yet (V1 scope stops here).
 *
 * Dedup is by approvalId / requestId so background/foreground transitions
 * never repeat actions (plan AND-W8 gate).
 */
class NotificationCoordinator(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    private val notifiedApprovals = mutableSetOf<String>()
    private val resolvedNotified = mutableSetOf<String>()
    private var lastRequestCount = 0
    private var appForeground = false

    init {
        createChannels()
    }

    fun setForeground(foreground: Boolean) {
        appForeground = foreground
    }

    /** Called for every snapshot; emits only *new* meaningful transitions. */
    fun onSnapshot(snap: SessionSnapshot) {
        if (!hasPermission()) return

        for ((id, a) in snap.session.approvals) {
            if (a.outcome == null && id !in notifiedApprovals) {
                notifiedApprovals += id
                notifyApproval(a.approvalId, a.title, a.description ?: "Open JARVIS to review", id)
            }
            if (a.outcome != null && a.approvalId !in resolvedNotified) {
                resolvedNotified += a.approvalId
                notifiedApprovals -= a.approvalId
                cancelApproval(a.approvalId)
            }
        }

        // New completions while backgrounded -> summary notification
        val completed = snap.session.requests.values.count {
            it.status == com.jarvis.android.data.state.RequestStatus.Completed
        }
        if (completed > lastRequestCount && !appForeground && completed > 0) {
            lastRequestCount = completed
            val newest = snap.session.requests.values
                .lastOrNull { it.status == com.jarvis.android.data.state.RequestStatus.Completed }
            notifyCompletion(newest?.text?.take(120) ?: "Reply ready")
        }
        lastRequestCount = if (completed < lastRequestCount) completed else lastRequestCount
    }

    fun notifyRevoked() {
        if (!hasPermission()) return
        notify(
            channelId = CHANNEL_STATE,
            id = NOTIF_REVOKED,
            builder = baseNotification(CHANNEL_STATE)
                .setContentTitle("Device revoked")
                .setContentText("This phone was revoked on the Jarvis PC. Re-pair to continue.")
                .setOngoing(false),
        )
    }

    fun notifyAuthExpired() {
        if (!hasPermission()) return
        notify(
            channelId = CHANNEL_STATE,
            id = NOTIF_AUTH,
            builder = baseNotification(CHANNEL_STATE)
                .setContentTitle("Session expired")
                .setContentText("Re-pair this phone from the Jarvis PC to restore access.")
                .setOngoing(false),
        )
    }

    // ---- internals -------------------------------------------------------------

    private fun notifyApproval(approvalId: String, title: String, body: String, key: String) {
        val notify = baseNotification(CHANNEL_APPROVALS)
            .setContentTitle("Approval needed: $title")
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .addAction(
                0, "Approve",
                approvalActionIntent(approvalId, ApprovalOutcome.APPROVED),
            )
            .addAction(
                0, "Deny",
                approvalActionIntent(approvalId, ApprovalOutcome.DENIED),
            )
        notify(CHANNEL_APPROVALS, notifIdFor(approvalId), notify)
    }

    private fun notifyCompletion(body: String) {
        notify(
            CHANNEL_COMPLETIONS,
            NOTIF_COMPLETION_BASE + (System.currentTimeMillis() % 1000).toInt(),
            baseNotification(CHANNEL_COMPLETIONS)
                .setContentTitle("JARVIS replied")
                .setContentText(body)
                .setAutoCancel(true)
                .setContentIntent(openAppIntent()),
        )
    }

    private fun cancelApproval(approvalId: String) {
        runCatching { manager.cancel(notifIdFor(approvalId)) }
    }

    private fun notifIdFor(approvalId: String): Int = 1000 + (approvalId.hashCode() % 900)

    private fun baseNotification(channel: String) =
        NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_jarvis)
            .setPriority(
                if (channel == CHANNEL_APPROVALS) NotificationCompat.PRIORITY_HIGH
                else NotificationCompat.PRIORITY_DEFAULT,
            )

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun approvalActionIntent(approvalId: String, outcome: ApprovalOutcome): PendingIntent {
        val intent = Intent(context, ApprovalActionReceiver::class.java).apply {
            putExtra(ApprovalActionReceiver.EXTRA_APPROVAL_ID, approvalId)
            putExtra(ApprovalActionReceiver.EXTRA_OUTCOME, outcome.name)
        }
        return PendingIntent.getBroadcast(
            context, (approvalId + outcome).hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun notify(channelId: String, id: Int, builder: NotificationCompat.Builder) {
        runCatching { manager.notify(id, builder.build()) }
            .onFailure { /* POST_NOTIFICATIONS race on <33 handled by permission check */ }
    }

    private fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_APPROVALS, "Approvals", NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "PC actions waiting for your approval"
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_COMPLETIONS, "Replies", NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "Completed assistant replies" },
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_STATE, "Connection", NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "Connection, revocation and expiry status" },
        )
    }

    companion object {
        const val CHANNEL_APPROVALS = "jarvis_approvals"
        const val CHANNEL_COMPLETIONS = "jarvis_completions"
        const val CHANNEL_STATE = "jarvis_state"
        private const val NOTIF_REVOKED = 9001
        private const val NOTIF_AUTH = 9002
        private const val NOTIF_COMPLETION_BASE = 2000
    }
}

/** Handles Approve/Deny actions from notification buttons (idempotent via repo). */
class ApprovalActionReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? com.jarvis.android.JarvisApp ?: return
        val approvalId = intent.getStringExtra(EXTRA_APPROVAL_ID) ?: return
        val outcome = runCatching {
            ApprovalOutcome.valueOf(intent.getStringExtra(EXTRA_OUTCOME) ?: "")
        }.getOrNull() ?: return
        app.container.session.resolveApproval(approvalId, outcome)
        val nm = NotificationManagerCompat.from(context)
        nm.cancel(1000 + (approvalId.hashCode() % 900))
    }

    companion object {
        const val EXTRA_APPROVAL_ID = "approval_id"
        const val EXTRA_OUTCOME = "outcome"
    }
}
