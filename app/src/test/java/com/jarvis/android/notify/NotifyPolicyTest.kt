package com.jarvis.android.notify

import com.jarvis.android.contract.ApprovalOutcome
import com.jarvis.android.contract.ApprovalRequiredPayload
import com.jarvis.android.contract.ApprovalTier
import com.jarvis.android.contract.ErrorEnvelope
import com.jarvis.android.data.repo.SessionPhase
import com.jarvis.android.data.repo.SessionSnapshot
import com.jarvis.android.data.state.ApprovalUiState
import com.jarvis.android.data.state.ConnectionState
import com.jarvis.android.data.state.RequestState
import com.jarvis.android.data.state.RequestStatus
import com.jarvis.android.data.state.SessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AND-W8 gate coverage (deterministic, JVM): background reply/approval
 * notifications, foreground suppression, duplicate suppression across
 * snapshots and across process restart (persistence round-trip).
 */
class NotifyPolicyTest {

    private fun approvalState(id: String, outcome: ApprovalOutcome? = null) =
        SessionState(
            approvals = mapOf(
                id to ApprovalUiState(
                    approvalId = id,
                    requestId = null,
                    title = "Run PC action",
                    description = "touch file",
                    tier = ApprovalTier.SENSITIVE,
                    risk = null,
                    expiresAtMs = null,
                    outcome = outcome,
                ),
            ),
        )

    private fun completedState(vararg ids: String) = SessionState(
        requests = ids.withIndex().associate { (i, rid) ->
            rid to RequestState(
                clientRequestId = rid,
                conversationId = "c1",
                userText = "hi$i",
                status = RequestStatus.Completed,
                text = "reply $rid",
                startedAtMs = i.toLong(),
            )
        },
    )

    private fun snap(session: SessionState, phase: SessionPhase = SessionPhase.READY) =
        SessionSnapshot(phase = phase, session = session)

    @Test
    fun backgroundReplyNotifiesOnceOnly() {
        val policy = NotifyPolicy(NotifyPolicy.State())
        val s = snap(completedState("r1"))

        val first = policy.evaluate(s, foreground = false)
        assertEquals(1, first.size)
        assertTrue(first[0] is NotifyPolicy.Action.NotifyReplyCompleted)

        // Same snapshot again (duplicate delivery / re-emitted frame): nothing new.
        assertTrue(policy.evaluate(s, foreground = false).isEmpty())
    }

    @Test
    fun foregroundReplyIsNotNotifiedButIsNotBackfilledOnBackground() {
        val policy = NotifyPolicy(NotifyPolicy.State())
        val s = snap(completedState("r1"))
        assertTrue(policy.evaluate(s, foreground = true).isEmpty())
        // Returning to background must NOT notify a reply the user already saw.
        assertTrue(policy.evaluate(s, foreground = false).isEmpty())
    }

    @Test
    fun approvalNotifiesThenCancelsOnResolveExactlyOnce() {
        val policy = NotifyPolicy(NotifyPolicy.State())
        val pending = snap(approvalState("a1"))
        val notify = policy.evaluate(pending, foreground = false)
        assertEquals(listOf("a1"), notify.filterIsInstance<NotifyPolicy.Action.NotifyApproval>().map { it.approvalId })
        // duplicate snapshot: no second ping
        assertTrue(policy.evaluate(pending, foreground = false).isEmpty())

        val resolved = snap(approvalState("a1", ApprovalOutcome.APPROVED))
        val cancel = policy.evaluate(resolved, foreground = false)
        assertEquals(listOf("a1"), cancel.filterIsInstance<NotifyPolicy.Action.CancelApproval>().map { it.approvalId })
        assertTrue(policy.evaluate(resolved, foreground = false).isEmpty())
    }

    @Test
    fun phaseTransitionsNotifyOnceAndRecover() {
        val policy = NotifyPolicy(NotifyPolicy.State())
        val expired = snap(SessionState(connection = ConnectionState.AUTH_EXPIRED), SessionPhase.AUTH_EXPIRED)
        assertEquals(1, policy.evaluate(expired, foreground = true).count { it == NotifyPolicy.Action.NotifyAuthExpired })
        assertTrue(policy.evaluate(expired, foreground = true).isEmpty())
        // back online clears the key so a *future* expiry can notify again
        val online = snap(SessionState(connection = ConnectionState.ONLINE))
        assertTrue(policy.evaluate(online, foreground = true).isEmpty())
        assertEquals(1, policy.evaluate(expired, foreground = true).count { it == NotifyPolicy.Action.NotifyAuthExpired })
    }

    @Test
    fun dedupeSurvivesProcessDeathViaPersistenceRoundTrip() {
        val state = NotifyPolicy.State()
        val policy = NotifyPolicy(state)
        val s = snap(completedState("r1", "r2"))
        assertEquals(2, policy.evaluate(s, foreground = false).size)

        val restored = NotifyPolicy(NotifyPolicy.State.fromPersisted(state.toPersisted()))
        assertTrue("no re-notify after restart", restored.evaluate(s, foreground = false).isEmpty())

        // A genuinely new completion still notifies after restore.
        val withThird = snap(completedState("r1", "r2", "r3"))
        val fresh = restored.evaluate(withThird, foreground = false)
        assertEquals(listOf("r3"), fresh.filterIsInstance<NotifyPolicy.Action.NotifyReplyCompleted>().map { it.clientRequestId })
    }

    @Test
    fun trackedSetsStayBounded() {
        val state = NotifyPolicy.State()
        repeat(MAX_TRACKED_FOR_TEST + 10) {
            NotifyPolicy(state).evaluate(snap(completedState("r$it")), foreground = false)
        }
        assertTrue(state.notifiedCompletions.size <= MAX_TRACKED_FOR_TEST)
    }

    companion object {
        // mirror of the private cap in NotifyPolicy for the bound assertion
        const val MAX_TRACKED_FOR_TEST = 200
    }
}
