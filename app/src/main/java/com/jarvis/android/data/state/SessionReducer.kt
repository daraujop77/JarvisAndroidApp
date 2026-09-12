package com.jarvis.android.data.state

import com.jarvis.android.contract.EventCodec
import com.jarvis.android.contract.webv1.WebV1Adapter

/**
 * Result of feeding one raw transport frame through decode + reduce.
 */
sealed interface FrameResult {
    data class Applied(val outcome: ReducerOutcome) : FrameResult
    data class Malformed(val reason: String) : FrameResult
}

/**
 * Thin wrapper around the pure [Reducer]: decodes raw JSON frames and records
 * MALFORMED/DUPLICATE diagnostics without ever crashing the session (plan §6
 * malformed payload / unknown event handling).
 */
class SessionReducer(private val clock: () -> Long = System::currentTimeMillis) {

    /**
     * Prefer the frozen Web V1 envelope. If the frame is the older mobile-shaped
     * JSON (Fake Gateway path), fall back to [EventCodec] so existing tests keep
     * passing during PCB-R0.
     */
    fun reduce(state: SessionState, rawFrame: String): Pair<SessionState, FrameResult> {
        when (val inbound = WebV1Adapter.inbound(rawFrame)) {
            is WebV1Adapter.Inbound.ProtocolMismatch -> {
                val s = state.copy(connection = ConnectionState.PROTOCOL_MISMATCH)
                    .withDiagnostic(DiagnosticEntry.Kind.PROTOCOL_MISMATCH, inbound.reason)
                return s to FrameResult.Applied(ReducerOutcome.ProtocolMismatch(s, inbound.reason))
            }
            is WebV1Adapter.Inbound.IgnoreOptional -> {
                val s = state.copy(lastCursorToken = inbound.opaqueCursor)
                    .withDiagnostic(DiagnosticEntry.Kind.UNKNOWN_EVENT, "optional type=${inbound.type}")
                return s to FrameResult.Applied(ReducerOutcome.Applied(s))
            }
            is WebV1Adapter.Inbound.Domain -> {
                val outcome = Reducer.applyWebV1(state, inbound.envelope, inbound.opaqueCursor, clock())
                return finish(outcome, inbound.envelope.eventId)
            }
            is WebV1Adapter.Inbound.Malformed -> {
                // Not Web V1 — try the in-repo mobile envelope (Fake Gateway).
            }
        }
        return when (val decoded = EventCodec.decodeEnvelope(rawFrame)) {
            is EventCodec.DecodedEnvelope.Malformed -> {
                val s = state.withDiagnostic(DiagnosticEntry.Kind.MALFORMED_EVENT, decoded.reason)
                s to FrameResult.Malformed(decoded.reason)
            }
            is EventCodec.DecodedEnvelope.Ok -> {
                val outcome = Reducer.apply(state, decoded.envelope, clock())
                if (outcome is ReducerOutcome.Duplicate) {
                    val s = outcome.state.withDiagnostic(
                        DiagnosticEntry.Kind.DUPLICATE_EVENT,
                        "eventId=${decoded.envelope.eventId}",
                    )
                    s to FrameResult.Applied(ReducerOutcome.Duplicate(s))
                } else {
                    outcome.state to FrameResult.Applied(outcome)
                }
            }
        }
    }

    private fun finish(outcome: ReducerOutcome, eventId: String): Pair<SessionState, FrameResult> {
        return if (outcome is ReducerOutcome.Duplicate) {
            val s = outcome.state.withDiagnostic(DiagnosticEntry.Kind.DUPLICATE_EVENT, "eventId=$eventId")
            s to FrameResult.Applied(ReducerOutcome.Duplicate(s))
        } else {
            outcome.state to FrameResult.Applied(outcome)
        }
    }
}
