package com.jarvis.android.ui.recovery

import com.jarvis.android.data.repo.ChatMessage
import com.jarvis.android.data.repo.SessionPhase
import com.jarvis.android.data.repo.SessionSnapshot
import com.jarvis.android.data.state.ConnectionState
import com.jarvis.android.data.state.RequestState
import com.jarvis.android.data.state.RequestStatus
import com.jarvis.android.notify.NotifyPolicy

/**
 * UI-only recovery presentation. Reads session, Room-shaped rows and the
 * existing notification-dedupe ledger. Never opens a transport, never issues
 * a request, and never treats a recovered turn as a new send.
 */
object RecoveryUx {

    enum class LinkKind {
        /** Link is usable. Banner stays quiet. */
        ONLINE,
        /** No radio / no route. Truthful offline, not a retry storm. */
        AIRPLANE,
        /** Link dropped; the existing backoff loop is the only retry. */
        RECONNECTING,
        /** Backoff budget is spent. Waiting for an explicit user retry. */
        BACKOFF_EXHAUSTED,
        /** Revoked, expired, or protocol mismatch. No automatic retry. */
        FAIL_CLOSED,
    }

    data class LinkPresentation(
        val kind: LinkKind,
        val headline: String,
        val detail: String,
        /** Manual retry is only offered once automatic backoff has stopped. */
        val showManualRetry: Boolean,
    )

    data class PendingOutbound(
        val clientRequestId: String,
        val conversationId: String,
        val text: String,
        val label: String,
    )

    data class DedupeStatus(
        val visible: Boolean,
        val label: String,
        val trackedApprovals: Int,
        val trackedCompletions: Int,
    )

    /**
     * One row the chat may render for a request. A recovered completed turn
     * collapses to the single persisted assistant row.
     */
    data class TurnRow(
        val clientRequestId: String,
        val role: String,
        val text: String,
        val recovered: Boolean,
    )

    fun link(snapshot: SessionSnapshot, reconnectAttempt: Int, backoffCap: Int): LinkPresentation {
        val conn = snapshot.connection
        if (isFailClosed(snapshot.phase, conn)) {
            return LinkPresentation(
                kind = LinkKind.FAIL_CLOSED,
                headline = "SESSION CLOSED",
                detail = "This device cannot reconnect until you pair again.",
                showManualRetry = false,
            )
        }
        if (conn.isUsable && snapshot.phase == SessionPhase.READY) {
            return LinkPresentation(
                kind = LinkKind.ONLINE,
                headline = "ONLINE",
                detail = "",
                showManualRetry = false,
            )
        }
        val waiting = reconnectAttempt > 0 && reconnectAttempt < backoffCap
        val exhausted = reconnectAttempt >= backoffCap && backoffCap > 0
        return when (conn) {
            ConnectionState.CONNECTING, ConnectionState.RECONNECTING -> LinkPresentation(
                kind = LinkKind.RECONNECTING,
                headline = "RECONNECTING",
                detail = if (waiting) "Attempt $reconnectAttempt of $backoffCap" else "Waiting for the link",
                showManualRetry = false,
            )
            ConnectionState.OFFLINE, ConnectionState.DISCONNECTED -> when {
                exhausted -> LinkPresentation(
                    kind = LinkKind.BACKOFF_EXHAUSTED,
                    headline = "OFFLINE",
                    detail = "Automatic retry paused after $backoffCap attempts",
                    showManualRetry = true,
                )
                waiting -> LinkPresentation(
                    kind = LinkKind.RECONNECTING,
                    headline = "RECONNECTING",
                    detail = "Attempt $reconnectAttempt of $backoffCap",
                    showManualRetry = false,
                )
                else -> LinkPresentation(
                    kind = LinkKind.AIRPLANE,
                    headline = "OFFLINE",
                    detail = "No connection. Saved on this device.",
                    showManualRetry = true,
                )
            }
            ConnectionState.DEGRADED -> LinkPresentation(
                kind = LinkKind.ONLINE,
                headline = "DEGRADED",
                detail = "Connected, responses may be slow",
                showManualRetry = false,
            )
            else -> LinkPresentation(
                kind = LinkKind.AIRPLANE,
                headline = "OFFLINE",
                detail = "No connection. Saved on this device.",
                showManualRetry = false,
            )
        }
    }

    /**
     * Outbound that the existing send path has not finished. Terminal or
     * already-completed requests are not pending, so a recovered turn does
     * not stay in the queue.
     */
    fun pendingOutbound(requests: Collection<RequestState>): List<PendingOutbound> =
        requests
            .filter { !it.status.isTerminal && it.status != RequestStatus.Completed }
            .sortedBy { it.startedAtMs }
            .map { req ->
                PendingOutbound(
                    clientRequestId = req.clientRequestId,
                    conversationId = req.conversationId,
                    text = req.userText,
                    label = when (req.status) {
                        RequestStatus.Pending -> "Sending"
                        RequestStatus.Accepted -> "Accepted"
                        RequestStatus.Streaming -> "Responding"
                        RequestStatus.Cancelling -> "Cancelling"
                        else -> "Waiting"
                    },
                )
            }

    /**
     * A completed turn that already has a persisted assistant row is shown
     * once. The live copy is dropped so process recreation cannot paint a
     * second bubble for the same clientRequestId.
     */
    fun visibleTurns(
        persisted: List<ChatMessage>,
        live: RequestState?,
    ): List<TurnRow> {
        val rows = persisted.map {
            TurnRow(
                clientRequestId = it.clientRequestId,
                role = it.role,
                text = it.text,
                recovered = it.role == "assistant" && it.status == RequestStatus.Completed,
            )
        }.toMutableList()
        if (live == null || live.status.isTerminal) return rows
        val index = rows.indexOfFirst { it.clientRequestId == live.clientRequestId && it.role == "assistant" }
        val row = TurnRow(live.clientRequestId, "assistant", live.text, recovered = false)
        if (index >= 0) rows[index] = row else rows += row
        return rows
    }

    /** True when this completed id was already rendered and must not be added again. */
    fun alreadyShown(shownIds: Set<String>, clientRequestId: String, completed: Boolean): Boolean =
        completed && clientRequestId in shownIds

    /**
     * Foreground and background flips are presentation only. A second request
     * is allowed only when the caller is explicitly sending something new.
     */
    fun foregroundAllowsNewRequest(explicitSend: Boolean): Boolean = explicitSend

    /**
     * Notification ledger the app already keeps. Hidden when nothing has been
     * deduped yet, so an empty process does not claim a status it lacks.
     */
    fun dedupeStatus(state: NotifyPolicy.State?): DedupeStatus {
        if (state == null) {
            return DedupeStatus(visible = false, label = "", trackedApprovals = 0, trackedCompletions = 0)
        }
        val approvals = state.notifiedApprovals.size
        val completions = state.notifiedCompletions.size
        val visible = approvals > 0 || completions > 0 || state.lastPhaseKey.isNotBlank()
        val label = if (!visible) {
            ""
        } else {
            "Notifications already delivered: $approvals approvals, $completions replies"
        }
        return DedupeStatus(
            visible = visible,
            label = label,
            trackedApprovals = approvals,
            trackedCompletions = completions,
        )
    }

    private fun isFailClosed(phase: SessionPhase, conn: ConnectionState): Boolean =
        phase == SessionPhase.REVOKED ||
            phase == SessionPhase.MISMATCH ||
            phase == SessionPhase.AUTH_EXPIRED ||
            conn == ConnectionState.DEVICE_REVOKED ||
            conn == ConnectionState.PROTOCOL_MISMATCH ||
            conn == ConnectionState.AUTH_EXPIRED
}
