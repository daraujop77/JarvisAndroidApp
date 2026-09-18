package com.jarvis.android.ui.recovery

import com.jarvis.android.data.repo.ChatMessage
import com.jarvis.android.data.repo.SessionPhase
import com.jarvis.android.data.repo.SessionSnapshot
import com.jarvis.android.data.state.ConnectionState
import com.jarvis.android.data.state.RequestState
import com.jarvis.android.data.state.RequestStatus
import com.jarvis.android.data.state.SessionState
import com.jarvis.android.notify.NotifyPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryUxTest {

    private fun snap(conn: ConnectionState, phase: SessionPhase = SessionPhase.DISCONNECTED) =
        SessionSnapshot(phase = phase, session = SessionState(connection = conn))

    private fun request(id: String, status: RequestStatus, text: String = "ping") =
        RequestState(clientRequestId = id, conversationId = "c", userText = text, status = status, startedAtMs = 1)

    @Test
    fun airplaneIsOfflineAndNotARetry() {
        val link = RecoveryUx.link(snap(ConnectionState.OFFLINE), reconnectAttempt = 0, backoffCap = 5)
        assertEquals(RecoveryUx.LinkKind.AIRPLANE, link.kind)
        assertEquals("OFFLINE", link.headline)
        assertTrue(link.detail.contains("No connection"))
        assertTrue(link.showManualRetry)
    }

    @Test
    fun reconnectingShowsAttemptAndHidesManualRetry() {
        val link = RecoveryUx.link(snap(ConnectionState.RECONNECTING), reconnectAttempt = 2, backoffCap = 5)
        assertEquals(RecoveryUx.LinkKind.RECONNECTING, link.kind)
        assertTrue(link.detail.contains("2 of 5"))
        assertFalse(link.showManualRetry)
    }

    @Test
    fun exhaustedBackoffSaysSoAndOffersOneManualRetry() {
        val link = RecoveryUx.link(snap(ConnectionState.DISCONNECTED), reconnectAttempt = 5, backoffCap = 5)
        assertEquals(RecoveryUx.LinkKind.BACKOFF_EXHAUSTED, link.kind)
        assertTrue(link.detail.contains("paused"))
        assertTrue(link.showManualRetry)
    }

    @Test
    fun failClosedIsNeverARetry() {
        val link = RecoveryUx.link(
            snap(ConnectionState.DEVICE_REVOKED, SessionPhase.REVOKED),
            reconnectAttempt = 0,
            backoffCap = 5,
        )
        assertEquals(RecoveryUx.LinkKind.FAIL_CLOSED, link.kind)
        assertFalse(link.showManualRetry)
    }

    @Test
    fun onlineIsQuiet() {
        val link = RecoveryUx.link(snap(ConnectionState.ONLINE, SessionPhase.READY), 0, 5)
        assertEquals(RecoveryUx.LinkKind.ONLINE, link.kind)
        assertFalse(link.showManualRetry)
        assertEquals("", link.detail)
    }

    @Test
    fun pendingOutboundStaysUntilTheRequestFinishes() {
        val pending = RecoveryUx.pendingOutbound(
            listOf(
                request("a", RequestStatus.Pending),
                request("b", RequestStatus.Accepted),
                request("c", RequestStatus.Completed),
                request("d", RequestStatus.Streaming),
            ),
        )
        assertEquals(listOf("a", "b", "d"), pending.map { it.clientRequestId })
        assertEquals("Sending", pending.first().label)
    }

    @Test
    fun recoveredCompletedTurnIsShownOnce() {
        val persisted = listOf(
            ChatMessage("r1", "user", "hi", RequestStatus.Completed, 1),
            ChatMessage("r1", "assistant", "hello", RequestStatus.Completed, 2),
        )
        val live = request("r1", RequestStatus.Completed).copy(text = "hello")
        val rows = RecoveryUx.visibleTurns(persisted, live)
        assertEquals(2, rows.size)
        assertEquals(1, rows.count { it.role == "assistant" })
        assertEquals("hello", rows.single { it.role == "assistant" }.text)
        assertTrue(rows.single { it.role == "assistant" }.recovered)
    }

    @Test
    fun alreadyShownCompletedIdIsNotAddedAgain() {
        val shown = setOf("r1")
        assertTrue(RecoveryUx.alreadyShown(shown, "r1", completed = true))
        assertFalse(RecoveryUx.alreadyShown(shown, "r2", completed = true))
        assertFalse(RecoveryUx.alreadyShown(shown, "r1", completed = false))
    }

    @Test
    fun inFlightRequestStillRendersWhileUnfinished() {
        val live = request("r1", RequestStatus.Pending).copy(text = "")
        val rows = RecoveryUx.visibleTurns(emptyList(), live)
        assertEquals(1, rows.size)
        assertFalse(rows.single().recovered)
    }

    @Test
    fun foregroundTransitionDoesNotAuthorizeASecondRequest() {
        assertFalse(RecoveryUx.foregroundAllowsNewRequest(explicitSend = false))
        assertTrue(RecoveryUx.foregroundAllowsNewRequest(explicitSend = true))
    }

    @Test
    fun dedupeStatusIsHiddenUntilTheLedgerHasEntries() {
        assertFalse(RecoveryUx.dedupeStatus(null).visible)
        assertFalse(RecoveryUx.dedupeStatus(NotifyPolicy.State()).visible)

        val state = NotifyPolicy.State()
        state.notifiedCompletions.addLast("r1")
        val shown = RecoveryUx.dedupeStatus(state)
        assertTrue(shown.visible)
        assertEquals(1, shown.trackedCompletions)
        assertTrue(shown.label.contains("1 replies"))
    }

    @Test
    fun dedupeStatusRoundTripsThroughTheExistingLedger() {
        val state = NotifyPolicy.State()
        state.notifiedApprovals.addLast("a1")
        val restored = NotifyPolicy.State.fromPersisted(state.toPersisted())
        val shown = RecoveryUx.dedupeStatus(restored)
        assertTrue(shown.visible)
        assertEquals(1, shown.trackedApprovals)
    }
}
