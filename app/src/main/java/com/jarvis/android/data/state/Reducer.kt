package com.jarvis.android.data.state

import com.jarvis.android.contract.ApprovalOutcome
import com.jarvis.android.contract.ConnectionPayload
import com.jarvis.android.contract.ContractVersion
import com.jarvis.android.contract.ErrorEnvelope
import com.jarvis.android.contract.EventEnvelope
import com.jarvis.android.contract.GatewayEvent
import com.jarvis.android.contract.ServerConnectionState

enum class ReducerEventKind { APPLIED, DUPLICATE, STALE, MISMATCH }

sealed interface ReducerOutcome {
    val state: SessionState

    data class Applied(override val state: SessionState) : ReducerOutcome
    data class Duplicate(override val state: SessionState) : ReducerOutcome
    data class Stale(override val state: SessionState) : ReducerOutcome
    data class ProtocolMismatch(override val state: SessionState, val reason: String) : ReducerOutcome
}

/**
 * Deterministic, pure event reducer (plan AND-W1 / AND-0002).
 *
 * Guarantees exercised by unit tests:
 *  - duplicate eventId -> dropped, semantic state unchanged
 *  - cursor < lastCursor -> stale (already applied), dropped
 *  - out-of-order deltas -> buffered, applied only when contiguous
 *  - repeated delta seq -> dropped (no duplicated text)
 *  - cancel is idempotent at every stage
 *  - unknown major protocol version -> PROTOCOL_MISMATCH, fails closed
 *  - unknown event types -> diagnostic entry, never a crash
 *
 * ID model: the Gateway echoes the client's `clientRequestId` as `requestId`
 * in message.* events (FIXTURE_FROZEN per plan §3), so requests are keyed by it.
 */
object Reducer {

    /**
     * Web V1 entry: opaque cursor is stored as-is and never compared numerically.
     * Duplicate detection remains by [EventEnvelope.eventId].
     */
    fun applyWebV1(
        state: SessionState,
        env: EventEnvelope,
        opaqueCursor: String,
        nowMs: Long,
        protocolVersion: String = "",
        fingerprint: String? = null,
    ): ReducerOutcome {
        // Opaque tokens are not ordered; skip the numeric stale check by
        // presenting a cursor that is never less than lastCursor.
        val outcome = apply(state, env.copy(cursor = state.lastCursor), nowMs)
        fun withNegotiation(s: SessionState) = s.copy(
            lastCursorToken = opaqueCursor,
            negotiatedProtocolVersion = protocolVersion.ifBlank { s.negotiatedProtocolVersion },
            negotiatedFingerprint = fingerprint ?: s.negotiatedFingerprint,
        )
        return when (outcome) {
            is ReducerOutcome.Applied -> ReducerOutcome.Applied(withNegotiation(outcome.state))
            is ReducerOutcome.Duplicate -> ReducerOutcome.Duplicate(withNegotiation(outcome.state))
            is ReducerOutcome.Stale -> ReducerOutcome.Stale(withNegotiation(outcome.state))
            is ReducerOutcome.ProtocolMismatch -> outcome
        }
    }

    fun apply(state: SessionState, env: EventEnvelope, nowMs: Long): ReducerOutcome {
        if (!isVersionCompatible(env.version)) {
            return ReducerOutcome.ProtocolMismatch(
                state.copy(connection = ConnectionState.PROTOCOL_MISMATCH)
                    .withDiagnostic(DiagnosticEntry.Kind.PROTOCOL_MISMATCH, "unsupported version ${env.version}"),
                "unsupported contract version ${env.version}; client supports major ${ContractVersion.MAJOR}",
            )
        }
        if (env.eventId in state.seenEventIds) {
            return ReducerOutcome.Duplicate(state)
        }
        if (env.cursor < state.lastCursor) {
            return ReducerOutcome.Stale(state)
        }

        val marked = state.markSeen(env.eventId).copy(lastCursor = maxOf(state.lastCursor, env.cursor))
        val next = when (val e = env.event) {
            is GatewayEvent.MessageAccepted -> onAccepted(marked, e, nowMs)
            is GatewayEvent.MessageDelta -> onDelta(marked, e)
            is GatewayEvent.MessageCompleted -> onCompleted(marked, e)
            is GatewayEvent.MessageFailed -> onFailed(marked, e)
            is GatewayEvent.ApprovalRequired -> onApprovalRequired(marked, e.payload)
            is GatewayEvent.ApprovalResolved -> onApprovalResolved(marked, e.payload, nowMs)
            is GatewayEvent.TaskUpdated -> onTask(marked, e.payload, nowMs)
            is GatewayEvent.ConnectionState -> {
                val connection = mapConnection(e.payload)
                marked.copy(
                    connection = connection,
                    // Keep the transport's reason so the banner can tell a dark PC
                    // from a dark control plane. Cleared once the link is usable.
                    connectionDetail = if (connection.isUsable) "" else e.payload.reason.orEmpty(),
                )
            }
            is GatewayEvent.AttachmentReady -> onAttachment(marked, e.payload, ready = true)
            is GatewayEvent.AttachmentFailed -> onAttachment(marked, e.payload, ready = false)
            is GatewayEvent.Unknown -> marked.withDiagnostic(
                DiagnosticEntry.Kind.UNKNOWN_EVENT, "type=${e.unknownType}",
            )
        }
        return ReducerOutcome.Applied(next.diagnosticsCapped())
    }

    /** Optimistic local command: user pressed send. Idempotent by clientRequestId. */
    fun beginSend(
        state: SessionState,
        clientRequestId: String,
        conversationId: String,
        text: String,
        nowMs: Long,
    ): SessionState {
        if (clientRequestId in state.requests) return state
        val req = RequestState(
            clientRequestId = clientRequestId,
            conversationId = conversationId,
            userText = text,
            status = RequestStatus.Pending,
            startedAtMs = nowMs,
        )
        return state.copy(requests = state.requests + (clientRequestId to req))
    }

    /**
     * Local cancel command. Idempotent: cancelling a request that is already
     * cancelling/cancelled/terminal returns the state unchanged.
     */
    fun requestCancel(state: SessionState, clientRequestId: String): SessionState {
        val req = state.requests[clientRequestId] ?: return state
        return if (req.status.isTerminal || req.status == RequestStatus.Cancelling) {
            state
        } else {
            state.copy(requests = state.requests + (clientRequestId to req.copy(status = RequestStatus.Cancelling)))
        }
    }

    /** Local resolve-approval command (marks in-flight; server event finalizes). */
    fun resolveApprovalLocally(state: SessionState, approvalId: String, outcome: ApprovalOutcome): SessionState {
        val a = state.approvals[approvalId] ?: return state
        if (a.outcome != null || a.resolutionInFlight) return state
        return state.copy(approvals = state.approvals + (approvalId to a.copy(resolutionInFlight = true, outcome = outcome)))
    }

    fun markApprovalResolutionFailed(state: SessionState, approvalId: String): SessionState {
        val a = state.approvals[approvalId] ?: return state
        return state.copy(approvals = state.approvals + (approvalId to a.copy(resolutionInFlight = false, outcome = null)))
    }

    /** Local staging: mark an attachment as uploading before/independent of the wire. */
    fun markAttachmentUploading(
        state: SessionState,
        attachmentId: String,
        mimeType: String?,
        sizeBytes: Long?,
    ): SessionState {
        val prev = state.attachments[attachmentId]
        return state.copy(
            attachments = state.attachments + (attachmentId to (prev ?: AttachmentUiState(
                attachmentId = attachmentId,
                requestId = null,
                kind = "image",
                mimeType = mimeType,
                sizeBytes = sizeBytes,
                ready = false,
                uploading = true,
            )).copy(uploading = true)),
        )
    }

    /** After transport loss: in-flight requests become retryable failures so the UI can act. */
    fun onDisconnected(state: SessionState): SessionState =
        state.copy(
            connection = ConnectionState.RECONNECTING,
            requests = state.requests.mapValues { (_, r) ->
                when (r.status) {
                    RequestStatus.Pending, RequestStatus.Accepted, RequestStatus.Streaming, RequestStatus.Cancelling ->
                        r.copy(status = RequestStatus.Failed(
                            ErrorEnvelope("disconnected", "Connection lost"),
                            retryable = true,
                        ))
                    else -> r
                }
            },
        )

    // ---- per-event handlers -------------------------------------------------

    private fun onAccepted(state: SessionState, e: GatewayEvent.MessageAccepted, nowMs: Long): SessionState {
        val req = state.requests[e.requestId]
            ?: RequestState(e.requestId, e.conversationId, "", startedAtMs = nowMs)
        val status = if (req.status == RequestStatus.Cancelling) RequestStatus.Cancelling else RequestStatus.Accepted
        return state.copy(
            requests = state.requests + (req.clientRequestId to req.copy(
                status = status,
                conversationId = e.conversationId,
                messageId = e.messageId,
            )),
        )
    }

    private fun onDelta(state: SessionState, e: GatewayEvent.MessageDelta): SessionState {
        val req = state.requests[e.requestId] ?: return state.withDiagnostic(
            DiagnosticEntry.Kind.REDUCER_NOTE, "delta for unknown request ${e.requestId}",
        )
        if (req.status.isTerminal && !req.status.revivable()) return state
        if (req.status == RequestStatus.Cancelling) return state
        if (e.seq < req.nextSeq) return state // already applied — duplicate delta body
        if (e.seq > req.nextSeq) {
            if (req.pendingDeltas.size >= MAX_BUFFERED_DELTAS) return state
            return state.copy(
                requests = state.requests + (req.clientRequestId to req.copy(
                    pendingDeltas = req.pendingDeltas + (e.seq to e.text),
                )),
            )
        }
        var next = req.copy(
            status = RequestStatus.Streaming,
            text = req.text + e.text,
            nextSeq = req.nextSeq + 1,
        )
        while (true) {
            val buffered = next.pendingDeltas[next.nextSeq] ?: break
            next = next.copy(
                text = next.text + buffered,
                nextSeq = next.nextSeq + 1,
                pendingDeltas = next.pendingDeltas - next.nextSeq,
            )
        }
        return state.copy(requests = state.requests + (next.clientRequestId to next))
    }

    private fun onCompleted(state: SessionState, e: GatewayEvent.MessageCompleted): SessionState {
        val req = state.requests[e.requestId] ?: return state
        if (req.status.isTerminal && !req.status.revivable()) return state
        val status = if (req.status == RequestStatus.Cancelling) RequestStatus.Cancelled else RequestStatus.Completed
        val finalText = e.fullText?.takeIf { it.isNotEmpty() } ?: req.text
        return state.copy(
            requests = state.requests + (req.clientRequestId to req.copy(
                status = status,
                text = finalText,
                pendingDeltas = emptyMap(),
                messageId = e.messageId,
                routeProvider = e.provider?.takeIf { it.isNotBlank() } ?: req.routeProvider,
                routeModel = e.model?.takeIf { it.isNotBlank() } ?: req.routeModel,
                routeKind = e.route?.takeIf { it.isNotBlank() } ?: req.routeKind,
                routeDestination = e.destination?.takeIf { it.isNotBlank() } ?: req.routeDestination,
            )),
        )
    }

    private fun onFailed(state: SessionState, e: GatewayEvent.MessageFailed): SessionState {
        val req = state.requests[e.requestId] ?: return state
        if (req.status == RequestStatus.Cancelled) return state // cancel wins over late failure
        return state.copy(
            requests = state.requests + (req.clientRequestId to req.copy(
                status = RequestStatus.Failed(e.error, e.retryable),
                pendingDeltas = emptyMap(),
            )),
        )
    }

    private fun onApprovalRequired(state: SessionState, p: com.jarvis.android.contract.ApprovalRequiredPayload): SessionState {
        if (p.approvalId in state.approvals) return state // re-delivery is idempotent
        return state.copy(
            approvals = state.approvals + (p.approvalId to ApprovalUiState(
                approvalId = p.approvalId,
                requestId = p.requestId,
                title = p.title,
                description = p.description,
                tier = p.tier,
                risk = p.risk,
                expiresAtMs = p.expiresAtMs,
            )),
        )
    }

    private fun onApprovalResolved(
        state: SessionState,
        p: com.jarvis.android.contract.ApprovalResolvedPayload,
        nowMs: Long,
    ): SessionState {
        val existing = state.approvals[p.approvalId] ?: return state
        // Server outcome is authoritative; if the client guessed differently, server wins.
        return state.copy(
            approvals = state.approvals + (p.approvalId to existing.copy(
                outcome = p.outcome,
                resolutionInFlight = false,
                resolvedAtMs = p.resolvedAtMs ?: nowMs,
            )),
        )
    }

    private fun onAttachment(state: SessionState, p: com.jarvis.android.contract.AttachmentPayload, ready: Boolean): SessionState {
        val prev = state.attachments[p.attachmentId]
        return state.copy(
            attachments = state.attachments + (p.attachmentId to (prev ?: AttachmentUiState(
                attachmentId = p.attachmentId,
                requestId = p.requestId,
                kind = p.kind,
                mimeType = p.mimeType,
                sizeBytes = p.sizeBytes,
                ready = false,
            )).copy(
                ready = ready && p.error == null,
                uploading = false,
                error = if (ready) null else p.error ?: ErrorEnvelope("upload_failed", "attachment failed"),
            )),
        )
    }

    private fun onTask(
        state: SessionState,
        p: com.jarvis.android.contract.TaskUpdatedPayload,
        nowMs: Long,
    ): SessionState {
        val prev = state.tasks[p.taskId]
        // Terminal task states never regress (task outlives socket/app session).
        if (prev != null && prev.status.isTerminal() && !p.status.isTerminal()) return state
        return state.copy(
            tasks = state.tasks + (p.taskId to TaskUiState(p.taskId, p.requestId, p.status, p.progress, p.label, nowMs)),
        )
    }

    // ---- helpers --------------------------------------------------------------

    private const val MAX_BUFFERED_DELTAS = 256

    private fun isVersionCompatible(version: String): Boolean {
        val major = version.substringBefore('.', version).toIntOrNull() ?: return false
        return major == ContractVersion.MAJOR
    }

    /**
     * A disconnect-failed request may be revived by server replay (reconnect
     * resumes the stream from the cursor; plan AND-W2 gate). Real failures and
     * cancels are never revived.
     */
    private fun RequestStatus.revivable(): Boolean =
        this is RequestStatus.Failed && error.code == "disconnected"

    private fun mapConnection(payload: ConnectionPayload): ConnectionState = when (payload.state) {
        ServerConnectionState.ONLINE -> ConnectionState.ONLINE
        ServerConnectionState.DEGRADED -> ConnectionState.DEGRADED
        ServerConnectionState.OFFLINE -> ConnectionState.OFFLINE
        ServerConnectionState.AUTH_EXPIRED -> ConnectionState.AUTH_EXPIRED
        ServerConnectionState.DEVICE_REVOKED -> ConnectionState.DEVICE_REVOKED
    }

    private fun com.jarvis.android.contract.TaskStatus.isTerminal(): Boolean = when (this) {
        com.jarvis.android.contract.TaskStatus.COMPLETED,
        com.jarvis.android.contract.TaskStatus.FAILED,
        com.jarvis.android.contract.TaskStatus.CANCELLED -> true
        else -> false
    }
}

internal fun SessionState.markSeen(id: String): SessionState {
    var order = seenOrder + id
    var seen = seenEventIds + id
    if (order.size > SessionState.SEEN_EVENTS_RETENTION) {
        val drop = order.take(order.size - SessionState.SEEN_EVENTS_RETENTION)
        order = order.drop(drop.size)
        seen = seen - drop.toSet()
    }
    return copy(seenEventIds = seen, seenOrder = order)
}

internal fun SessionState.withDiagnostic(kind: DiagnosticEntry.Kind, detail: String): SessionState =
    copy(diagnostics = diagnostics + DiagnosticEntry(System.currentTimeMillis(), kind, detail)).diagnosticsCapped()
