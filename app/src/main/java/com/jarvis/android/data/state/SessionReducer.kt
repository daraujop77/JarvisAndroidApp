package com.jarvis.android.data.state

import com.jarvis.android.contract.EventCodec

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

    fun reduce(state: SessionState, rawFrame: String): Pair<SessionState, FrameResult> {
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
}
