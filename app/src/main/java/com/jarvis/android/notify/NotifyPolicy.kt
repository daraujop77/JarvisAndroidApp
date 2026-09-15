package com.jarvis.android.notify

import com.jarvis.android.data.repo.SessionSnapshot
import com.jarvis.android.data.state.RequestStatus

/**
 * AND-W8: pure, deterministic notification decision core. The coordinator owns
 * Android APIs; this owns *what* should fire and *exactly once*, with state
 * that can be persisted so a background/foreground restart (or process death)
 * never re-notifies approvals, replies or revocations the user already saw.
 */
class NotifyPolicy(val state: State) {

    class State(
        val notifiedApprovals: ArrayDeque<String> = ArrayDeque(),
        val resolvedApprovals: ArrayDeque<String> = ArrayDeque(),
        val notifiedCompletions: ArrayDeque<String> = ArrayDeque(),
        var lastPhaseKey: String = "",
    ) {
        fun toPersisted(): String = listOf(
            notifiedApprovals.joinToString("|"),
            resolvedApprovals.joinToString("|"),
            notifiedCompletions.joinToString("|"),
            lastPhaseKey,
        ).joinToString("\n")

        companion object {
            fun fromPersisted(raw: String?): State {
                val s = State()
                if (raw.isNullOrBlank()) return s
                val parts = raw.split("\n")
                parts.getOrNull(0)?.split("|")?.filter { it.isNotBlank() }?.let { s.notifiedApprovals.addAll(it) }
                parts.getOrNull(1)?.split("|")?.filter { it.isNotBlank() }?.let { s.resolvedApprovals.addAll(it) }
                parts.getOrNull(2)?.split("|")?.filter { it.isNotBlank() }?.let { s.notifiedCompletions.addAll(it) }
                s.lastPhaseKey = parts.getOrNull(3).orEmpty()
                return s
            }
        }
    }

    sealed interface Action {
        data class NotifyApproval(val approvalId: String, val title: String, val body: String) : Action
        data class CancelApproval(val approvalId: String) : Action
        data class NotifyReplyCompleted(val clientRequestId: String, val snippet: String) : Action
        data object NotifyAuthExpired : Action
        data object NotifyRevoked : Action
    }

    /**
     * Evaluate one snapshot; returns only transitions not yet seen. Foreground
     * completions are recorded (marked notified) without emitting an action so
     * a later background transition doesn't notify stale history.
     */
    fun evaluate(snap: SessionSnapshot, foreground: Boolean): List<Action> {
        val actions = mutableListOf<Action>()

        for ((id, a) in snap.session.approvals) {
            if (a.outcome == null) {
                if (id !in state.notifiedApprovals) {
                    addCapped(state.notifiedApprovals, id)
                    actions += Action.NotifyApproval(
                        approvalId = id,
                        title = a.title,
                        body = a.description ?: "Open JARVIS to review",
                    )
                }
            } else if (id !in state.resolvedApprovals) {
                addCapped(state.resolvedApprovals, id)
                state.notifiedApprovals.remove(id)
                actions += Action.CancelApproval(id)
            }
        }

        for (r in snap.session.requests.values) {
            if (r.status == RequestStatus.Completed && r.clientRequestId !in state.notifiedCompletions) {
                addCapped(state.notifiedCompletions, r.clientRequestId)
                if (!foreground) {
                    actions += Action.NotifyReplyCompleted(
                        clientRequestId = r.clientRequestId,
                        snippet = r.text.take(120),
                    )
                }
            }
        }

        // One-shot transition notifications for fail-closed phases.
        val phaseKey = when (snap.phase) {
            com.jarvis.android.data.repo.SessionPhase.AUTH_EXPIRED -> "auth_expired"
            com.jarvis.android.data.repo.SessionPhase.REVOKED -> "revoked"
            else -> ""
        }
        if (phaseKey.isNotEmpty() && phaseKey != state.lastPhaseKey) {
            actions += if (phaseKey == "revoked") Action.NotifyRevoked else Action.NotifyAuthExpired
        }
        state.lastPhaseKey = phaseKey

        return actions
    }

    private fun addCapped(q: ArrayDeque<String>, id: String) {
        if (q.size >= MAX_TRACKED) q.removeFirst()
        if (id !in q) q.addLast(id)
    }

    companion object {
        private const val MAX_TRACKED = 200
    }
}
