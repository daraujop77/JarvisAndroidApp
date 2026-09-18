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
class NotificationCoordinator(context: Context) {

    private val appContext = context.applicationContext
    private val manager = NotificationManagerCompat.from(appContext)
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val policy = NotifyPolicy(NotifyPolicy.State.fromPersisted(prefs.getString(KEY_STATE, null)))
    private var appForeground = false

    init {
        createChannels()
    }

    fun setForeground(foreground: Boolean) {
        appForeground = foreground
    }

    /**
     * The dedupe ledger already on disk, or null when this process has not
     * recorded one. Read-only: showing it must not mark anything notified.
     */
    fun persistedLedger(): String? = prefs.getString(KEY_STATE, null)

    /** Called for every snapshot; emits only *new* meaningful transitions. */
    fun onSnapshot(snap: SessionSnapshot) {
        val actions = policy.evaluate(snap, foreground = appForeground)
        if (actions.isEmpty()) return
        persistState()
        if (!hasPermission()) return
        for (a in actions) when (a) {
            is NotifyPolicy.Action.NotifyApproval ->
                notifyApproval(a.approvalId, a.title, a.body)
            is NotifyPolicy.Action.CancelApproval -> cancelApproval(a.approvalId)
            is NotifyPolicy.Action.NotifyReplyCompleted -> notifyCompletion(a.clientRequestId, a.snippet)
            NotifyPolicy.Action.NotifyAuthExpired -> notifyAuthExpired()
            NotifyPolicy.Action.NotifyRevoked -> notifyRevoked()
        }
    }

    private fun persistState() {
        prefs.edit().putString(KEY_STATE, policy.state.toPersisted()).apply()
    }

    fun notifyRevoked() {
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

    private fun notifyApproval(approvalId: String, title: String, body: String) {
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

    private fun notifyCompletion(clientRequestId: String, body: String) {
        notify(
            CHANNEL_COMPLETIONS,
            NOTIF_COMPLETION_BASE + (clientRequestId.hashCode() and 0x1FF),
            baseNotification(CHANNEL_COMPLETIONS)
                .setContentTitle("JARVIS replied")
                .setContentText(body.ifBlank { "Reply ready" })
                .setAutoCancel(true)
                .setContentIntent(openAppIntent()),
        )
    }

    private fun cancelApproval(approvalId: String) {
        runCatching { manager.cancel(notifIdFor(approvalId)) }
    }

    private fun notifIdFor(approvalId: String): Int = 1000 + (approvalId.hashCode() and 0x1FF)

    private fun baseNotification(channel: String) =
        NotificationCompat.Builder(appContext, channel)
            .setSmallIcon(R.drawable.ic_stat_jarvis)
            .setPriority(
                if (channel == CHANNEL_APPROVALS) NotificationCompat.PRIORITY_HIGH
                else NotificationCompat.PRIORITY_DEFAULT,
            )

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            appContext, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun approvalActionIntent(approvalId: String, outcome: ApprovalOutcome): PendingIntent {
        val intent = Intent(appContext, ApprovalActionReceiver::class.java).apply {
            putExtra(ApprovalActionReceiver.EXTRA_APPROVAL_ID, approvalId)
            putExtra(ApprovalActionReceiver.EXTRA_OUTCOME, outcome.name)
        }
        return PendingIntent.getBroadcast(
            appContext, (approvalId + outcome).hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun notify(channelId: String, id: Int, builder: NotificationCompat.Builder) {
        runCatching { manager.notify(id, builder.build()) }
            .onFailure { /* POST_NOTIFICATIONS race on <33 handled by permission check */ }
    }

    private fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = appContext.getSystemService(NotificationManager::class.java)
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
        private const val PREFS = "jarvis_notify_state"
        private const val KEY_STATE = "seen_ids"
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
        nm.cancel(1000 + (approvalId.hashCode() and 0x1FF))
    }

    companion object {
        const val EXTRA_APPROVAL_ID = "approval_id"
        const val EXTRA_OUTCOME = "outcome"
    }
}
