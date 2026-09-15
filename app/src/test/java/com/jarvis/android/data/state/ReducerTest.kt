package com.jarvis.android.data.state

import com.jarvis.android.contract.ApprovalOutcome
import com.jarvis.android.contract.ApprovalRequiredPayload
import com.jarvis.android.contract.ApprovalResolvedPayload
import com.jarvis.android.contract.ApprovalTier
import com.jarvis.android.contract.ConnectionPayload
import com.jarvis.android.contract.ErrorEnvelope
import com.jarvis.android.contract.EventEnvelope
import com.jarvis.android.contract.GatewayEvent
import com.jarvis.android.contract.ServerConnectionState
import com.jarvis.android.contract.TaskStatus
import com.jarvis.android.contract.TaskUpdatedPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Lane C reducer tests (plan §8): negative fixtures, duplicate/out-of-order
 * events, cancel idempotency, protocol mismatch, replay revival, approval
 * idempotency, task terminal-state protection.
 */
class ReducerTest {

    private var now = 1_000_000L
    private var cursor = 0L
    private var evt = 0

    private fun env(event: GatewayEvent, version: String = "1.0", eventId: String = "e${evt++}", cursorOverride: Long? = null) =
        EventEnvelope(
            cursor = cursorOverride ?: ++cursor,
            eventId = eventId,
            version = version,
            timestampMs = now,
            event = event,
        )

    private fun applied(state: SessionState, event: GatewayEvent, version: String = "1.0", eventId: String = "e${evt++}"): SessionState {
        val outcome = Reducer.apply(state, env(event, version, eventId), now)
        assertTrue("expected Applied but was $outcome", outcome is ReducerOutcome.Applied)
        return outcome.state
    }

    private fun started(state: SessionState = SessionState(connection = ConnectionState.ONLINE)): SessionState =
        Reducer.beginSend(state, "r1", "c1", "hello", now)

    // ---- streaming happy path -------------------------------------------------

    @Test
    fun acceptedThenDeltasThenCompleted() {
        var s = started()
        s = applied(s, GatewayEvent.MessageAccepted("r1", "c1", "m1"))
        assertEquals(RequestStatus.Accepted, s.requests["r1"]!!.status)

        s = applied(s, GatewayEvent.MessageDelta("r1", "m1", 0, "Hello "))
        s = applied(s, GatewayEvent.MessageDelta("r1", "m1", 1, "world"))
        assertEquals(RequestStatus.Streaming, s.requests["r1"]!!.status)
        assertEquals("Hello world", s.requests["r1"]!!.text)

        s = applied(s, GatewayEvent.MessageCompleted("r1", "m1"))
        assertEquals(RequestStatus.Completed, s.requests["r1"]!!.status)
        assertEquals("Hello world", s.requests["r1"]!!.text)
    }

    @Test
    fun completedWithFullTextOverridesDeltas() {
        var s = started()
        s = applied(s, GatewayEvent.MessageAccepted("r1", "c1", "m1"))
        s = applied(s, GatewayEvent.MessageDelta("r1", "m1", 0, "part"))
        s = applied(s, GatewayEvent.MessageCompleted("r1", "m1", fullText = "complete answer"))
        assertEquals("complete answer", s.requests["r1"]!!.text)
    }

    // ---- AND-W1 gate: duplicates / out-of-order -------------------------------

    @Test
    fun duplicateEventIdIsDropped() {
        var s = started()
        s = applied(s, GatewayEvent.MessageAccepted("r1", "c1", "m1"))
        val delta = env(GatewayEvent.MessageDelta("r1", "m1", 0, "text"), eventId = "dup")
        s = applied(s, GatewayEvent.MessageDelta("r1", "m1", 0, "text"), eventId = "dup")
        val outcome = Reducer.apply(s, delta, now)
        assertTrue(outcome is ReducerOutcome.Duplicate)
        // no duplicated UI text
        assertSame(s, (outcome as ReducerOutcome.Duplicate).state)
    }

    @Test
    fun duplicateDeltaSeqDoesNotDuplicateText() {
        var s = started()
        s = applied(s, GatewayEvent.MessageAccepted("r1", "c1", "m1"))
        s = applied(s, GatewayEvent.MessageDelta("r1", "m1", 0, "one "))
        // different eventId, same seq -> reducer drops by nextSeq watermark
        s = applied(s, GatewayEvent.MessageDelta("r1", "m1", 0, "one "))
        assertEquals("one ", s.requests["r1"]!!.text)
    }

    @Test
    fun outOfOrderDeltasAreBufferedThenDrained() {
        var s = started()
        s = applied(s, GatewayEvent.MessageAccepted("r1", "c1", "m1"))
        s = applied(s, GatewayEvent.MessageDelta("r1", "m1", 2, "C"))
        s = applied(s, GatewayEvent.MessageDelta("r1", "m1", 1, "B"))
        // text still empty: seq 0 missing
        assertEquals("", s.requests["r1"]!!.text)
        s = applied(s, GatewayEvent.MessageDelta("r1", "m1", 0, "A"))
        // all contiguous now -> drained in order
        assertEquals("ABC", s.requests["r1"]!!.text)
        assertEquals(3, s.requests["r1"]!!.nextSeq)
        assertTrue(s.requests["r1"]!!.pendingDeltas.isEmpty())
    }

    @Test
    fun staleCursorIsDropped() {
        var s = started()
        s = applied(s, GatewayEvent.MessageAccepted("r1", "c1", "m1"))
        val outcome = Reducer.apply(s, env(GatewayEvent.MessageCompleted("r1", "m1"), cursorOverride = 0), now)
        assertTrue(outcome is ReducerOutcome.Stale)
    }

    // ---- cancel semantics -------------------------------------------------------

    @Test
    fun cancelBeforeAcceptedThenCancelEventIdempotent() {
        var s = started()
        val s1 = Reducer.requestCancel(s, "r1")
        assertEquals(RequestStatus.Cancelling, s1.requests["r1"]!!.status)
        // second local cancel is a no-op
        assertSame(s1, Reducer.requestCancel(s1, "r1"))

        // server completes -> Cancelled
        s = applied(s1, GatewayEvent.MessageAccepted("r1", "c1", "m1"))
        assertEquals(RequestStatus.Cancelling, s.requests["r1"]!!.status) // accepted doesn't overwrite cancelling
        s = applied(s, GatewayEvent.MessageCompleted("r1", "m1"))
        assertEquals(RequestStatus.Cancelled, s.requests["r1"]!!.status)
        // late delta after cancel is dropped
        s = applied(s, GatewayEvent.MessageDelta("r1", "m1", 0, "late"))
        assertEquals("", s.requests["r1"]!!.text)
    }

    @Test
    fun cancelWhileStreamingEndsCancelled() {
        var s = started()
        s = applied(s, GatewayEvent.MessageAccepted("r1", "c1", "m1"))
        s = applied(s, GatewayEvent.MessageDelta("r1", "m1", 0, "partial "))
        s = Reducer.requestCancel(s, "r1")
        s = applied(s, GatewayEvent.MessageCompleted("r1", "m1"))
        assertEquals(RequestStatus.Cancelled, s.requests["r1"]!!.status)
        assertEquals("partial ", s.requests["r1"]!!.text)
    }

    @Test
    fun cancelOfTerminalRequestIsNoop() {
        var s = started()
        s = applied(s, GatewayEvent.MessageAccepted("r1", "c1", "m1"))
        s = applied(s, GatewayEvent.MessageCompleted("r1", "m1"))
        assertSame(s, Reducer.requestCancel(s, "r1"))
    }

    // ---- failure semantics ------------------------------------------------------

    @Test
    fun retryableFailureKeepsPartialText() {
        var s = started()
        s = applied(s, GatewayEvent.MessageAccepted("r1", "c1", "m1"))
        s = applied(s, GatewayEvent.MessageDelta("r1", "m1", 0, "half "))
        s = applied(s, GatewayEvent.MessageFailed("r1", "m1", ErrorEnvelope("gateway_degraded", "oops"), retryable = true))
        val status = s.requests["r1"]!!.status as RequestStatus.Failed
        assertTrue(status.retryable)
        assertEquals("half ", s.requests["r1"]!!.text)
    }

    @Test
    fun failureAfterCancelDoesNotOverwriteCancelled() {
        var s = started()
        s = applied(s, GatewayEvent.MessageAccepted("r1", "c1", "m1"))
        s = Reducer.requestCancel(s, "r1")
        s = applied(s, GatewayEvent.MessageCompleted("r1", "m1"))
        s = applied(s, GatewayEvent.MessageFailed("r1", "m1", ErrorEnvelope("late", "late failure"), retryable = false))
        assertEquals(RequestStatus.Cancelled, s.requests["r1"]!!.status)
    }

    // ---- protocol mismatch ------------------------------------------------------

    @Test
    fun unknownMajorVersionFailsClosed() {
        val s = started()
        val outcome = Reducer.apply(s, env(GatewayEvent.MessageAccepted("r1", "c1", "m1"), version = "2.0"), now)
        assertTrue(outcome is ReducerOutcome.ProtocolMismatch)
        assertEquals(ConnectionState.PROTOCOL_MISMATCH, outcome.state.connection)
    }

    @Test
    fun additiveMinorVersionIsTolerated() {
        val s = started()
        val next = applied(s, GatewayEvent.MessageAccepted("r1", "c1", "m1"), version = "1.4")
        assertEquals(RequestStatus.Accepted, next.requests["r1"]!!.status)
    }

    @Test
    fun unknownEventTypeRecordedAsDiagnostic() {
        val s = started()
        val next = applied(s, GatewayEvent.Unknown("widget.frobnicated"))
        assertTrue(next.diagnostics.any { it.kind == DiagnosticEntry.Kind.UNKNOWN_EVENT })
        // session keeps working
        val s2 = applied(next, GatewayEvent.MessageAccepted("r1", "c1", "m1"))
        assertEquals(RequestStatus.Accepted, s2.requests["r1"]!!.status)
    }

    // ---- approvals ---------------------------------------------------------------

    @Test
    fun approvalDeliveryAndResolutionIdempotency() {
        var s = SessionState(connection = ConnectionState.ONLINE)
        val req = GatewayEvent.ApprovalRequired(
            ApprovalRequiredPayload("a1", "r1", "Do the thing", tier = ApprovalTier.SENSITIVE, risk = "high"),
        )
        s = applied(s, req)
        assertNotNull(s.approvals["a1"])
        // re-delivery doesn't reset the approval (cursor still advances)
        val s2 = applied(s, req, eventId = "e-again")
        assertEquals(s.approvals, s2.approvals)

        s2.let { st ->
            val st2 = Reducer.resolveApprovalLocally(st, "a1", ApprovalOutcome.APPROVED)
            assertEquals(ApprovalOutcome.APPROVED, st2.approvals["a1"]!!.outcome)
            assertTrue(st2.approvals["a1"]!!.resolutionInFlight)
            // second local resolve is a no-op (idempotent)
            assertSame(st2, Reducer.resolveApprovalLocally(st2, "a1", ApprovalOutcome.DENIED))

            // server says DENIED -> server wins
            val st3 = applied(st2, GatewayEvent.ApprovalResolved(ApprovalResolvedPayload("a1", ApprovalOutcome.DENIED)))
            assertEquals(ApprovalOutcome.DENIED, st3.approvals["a1"]!!.outcome)
            assertFalse(st3.approvals["a1"]!!.resolutionInFlight)
        }
    }

    @Test
    fun approvalResolutionFailureAllowsRetry() {
        var s = SessionState(connection = ConnectionState.ONLINE)
        s = applied(s, GatewayEvent.ApprovalRequired(ApprovalRequiredPayload("a1", null, "T")))
        s = Reducer.resolveApprovalLocally(s, "a1", ApprovalOutcome.APPROVED)
        s = Reducer.markApprovalResolutionFailed(s, "a1")
        assertNull(s.approvals["a1"]!!.outcome)
        assertFalse(s.approvals["a1"]!!.resolutionInFlight)
        // retry allowed now
        s = Reducer.resolveApprovalLocally(s, "a1", ApprovalOutcome.DENIED)
        assertEquals(ApprovalOutcome.DENIED, s.approvals["a1"]!!.outcome)
    }

    @Test
    fun serverExpiredWinsOverLocalApprove() {
        var s = SessionState(connection = ConnectionState.ONLINE)
        s = applied(s, GatewayEvent.ApprovalRequired(
            ApprovalRequiredPayload("a1", null, "T", expiresAtMs = now - 1),
        ))
        // User taps Approve while the server has already expired it.
        s = Reducer.resolveApprovalLocally(s, "a1", ApprovalOutcome.APPROVED)
        s = applied(s, GatewayEvent.ApprovalResolved(
            ApprovalResolvedPayload("a1", ApprovalOutcome.EXPIRED),
        ))
        // Server is authoritative: EXPIRED, not APPROVED; not in flight.
        assertEquals(ApprovalOutcome.EXPIRED, s.approvals["a1"]!!.outcome)
        assertFalse(s.approvals["a1"]!!.resolutionInFlight)
    }

    @Test
    fun approvalResolvedForUnknownIdIsNoOp() {
        val s = SessionState(connection = ConnectionState.ONLINE)
        val s2 = applied(s, GatewayEvent.ApprovalResolved(ApprovalResolvedPayload("ghost", ApprovalOutcome.APPROVED)))
        assertTrue(s2.approvals.isEmpty())
    }

    // ---- tasks --------------------------------------------------------------------

    @Test
    fun terminalTaskStateNeverRegresses() {
        var s = SessionState(connection = ConnectionState.ONLINE)
        s = applied(s, GatewayEvent.TaskUpdated(TaskUpdatedPayload("t1", "r1", TaskStatus.RUNNING, 0.5f, "job")))
        s = applied(s, GatewayEvent.TaskUpdated(TaskUpdatedPayload("t1", "r1", TaskStatus.COMPLETED, 1f, "job")))
        // server bug: running after completed -> ignored
        s = applied(s, GatewayEvent.TaskUpdated(TaskUpdatedPayload("t1", "r1", TaskStatus.RUNNING, 0.6f, "job")))
        assertEquals(TaskStatus.COMPLETED, s.tasks["t1"]!!.status)
    }

    // ---- connection lifecycle --------------------------------------------------------

    @Test
    fun disconnectMarksInFlightRetryableAndKeepsCompleted() {
        var s = started()
        s = applied(s, GatewayEvent.MessageAccepted("r1", "c1", "m1"))
        s = applied(s, GatewayEvent.MessageDelta("r1", "m1", 0, "x"))
        var s2 = Reducer.beginSend(s, "r2", "c1", "second", now)
        s2 = applied(s2, GatewayEvent.MessageAccepted("r2", "c1", "m2"))
        s2 = applied(s2, GatewayEvent.MessageDelta("r2", "m2", 0, "done "))
        s2 = applied(s2, GatewayEvent.MessageCompleted("r2", "m2"))

        val dropped = Reducer.onDisconnected(s2)
        assertEquals(ConnectionState.RECONNECTING, dropped.connection)
        val f = dropped.requests["r1"]!!.status as RequestStatus.Failed
        assertEquals("disconnected", f.error.code)
        assertTrue(f.retryable)
        // completed untouched
        assertEquals(RequestStatus.Completed, dropped.requests["r2"]!!.status)
    }

    @Test
    fun replayRevivesDisconnectedRequest() {
        var s = started()
        s = applied(s, GatewayEvent.MessageAccepted("r1", "c1", "m1"))
        s = applied(s, GatewayEvent.MessageDelta("r1", "m1", 0, "A"))
        s = Reducer.onDisconnected(s)
        assertTrue(s.requests["r1"]!!.status is RequestStatus.Failed)

        // reconnect replay re-delivers delta seq 1 (missed) — must revive, not stay failed
        s = applied(s, GatewayEvent.MessageDelta("r1", "m1", 1, "B"))
        assertEquals(RequestStatus.Streaming, s.requests["r1"]!!.status)
        assertEquals("AB", s.requests["r1"]!!.text)

        s = applied(s, GatewayEvent.MessageCompleted("r1", "m1"))
        assertEquals(RequestStatus.Completed, s.requests["r1"]!!.status)
        assertEquals("AB", s.requests["r1"]!!.text)
    }

    @Test
    fun connectionStateEventsDriveConnection() {
        var s = SessionState()
        s = applied(s, GatewayEvent.ConnectionState(ConnectionPayload(ServerConnectionState.ONLINE)))
        assertEquals(ConnectionState.ONLINE, s.connection)
        s = applied(s, GatewayEvent.ConnectionState(ConnectionPayload(ServerConnectionState.DEGRADED)))
        assertEquals(ConnectionState.DEGRADED, s.connection)
        s = applied(s, GatewayEvent.ConnectionState(ConnectionPayload(ServerConnectionState.AUTH_EXPIRED)))
        assertEquals(ConnectionState.AUTH_EXPIRED, s.connection)
        s = applied(s, GatewayEvent.ConnectionState(ConnectionPayload(ServerConnectionState.DEVICE_REVOKED)))
        assertEquals(ConnectionState.DEVICE_REVOKED, s.connection)
    }

    @Test
    fun sendIdempotencySameClientRequestId() {
        val s = SessionState(connection = ConnectionState.ONLINE)
        val s1 = Reducer.beginSend(s, "same", "c1", "text", now)
        val s2 = Reducer.beginSend(s1, "same", "c1", "text", now)
        assertSame(s1, s2)
    }
}
