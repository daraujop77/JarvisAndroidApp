package com.jarvis.android.data.repo

import com.jarvis.android.contract.ApprovalOutcome
import com.jarvis.android.contract.ErrorEnvelope
import com.jarvis.android.contract.HealthResponse
import com.jarvis.android.contract.MobileRequest
import com.jarvis.android.data.state.ConnectionState
import com.jarvis.android.data.state.DiagnosticEntry
import com.jarvis.android.data.state.Reducer
import com.jarvis.android.data.state.RequestState
import com.jarvis.android.data.state.RequestStatus
import com.jarvis.android.data.state.SessionReducer
import com.jarvis.android.data.state.SessionState
import com.jarvis.android.data.state.FrameResult
import com.jarvis.android.transport.GatewayTransport
import com.jarvis.android.transport.LinkState
import com.jarvis.android.transport.TransportException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

enum class SessionPhase { DISCONNECTED, CONNECTING, READY, AUTH_EXPIRED, REVOKED, MISMATCH }

data class SessionSnapshot(
    val phase: SessionPhase = SessionPhase.DISCONNECTED,
    val session: SessionState = SessionState(),
) {
    val connection: ConnectionState get() = session.connection
    val isReady: Boolean get() = phase == SessionPhase.READY
}

/**
 * Owns the transport + reducer loop (plan §8 Lane A). UI layers consume
 * [snapshot] and call the send/cancel/approve commands; no composable ever
 * touches transport logic directly.
 *
 * Reconnect behavior: on link loss, requests the server never accepted become
 * retryable failures (never silently re-sent -> no duplicate outbound), while
 * the client asks the server to replay from [SessionState.lastCursor].
 */
class JarvisSessionRepository(
    private val transport: GatewayTransport,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private val reducer = SessionReducer(clock)
    private val _snapshot = MutableStateFlow(SessionSnapshot())
    val snapshot: StateFlow<SessionSnapshot> = _snapshot.asStateFlow()

    private var collectorJob: Job? = null
    private var started = false

    fun start() {
        if (started) return
        started = true
        collectorJob?.cancel()
        collectorJob = scope.launch {
            // Observe raw frames
            transport.frames
                .onEach { frame -> onFrame(frame) }
                .launchIn(this)

            // Observe link state -> drive session phase + reconnect
            transport.linkState
                .onEach { onLink(it) }
                .launchIn(this)

            goToConnecting()
            transport.connect()
        }
    }

    fun stop() {
        collectorJob?.cancel()
        collectorJob = null
        started = false
        scope.launch { transport.disconnect() }
    }

    // ---- commands -----------------------------------------------------------

    /** Optimistic send. Returns the clientRequestId (idempotency key). */
    fun send(conversationId: String, text: String, attachmentIds: List<String> = emptyList()): String =
        sendWithId(UUID.randomUUID().toString(), conversationId, text, attachmentIds)

    /**
     * Send reusing an existing clientRequestId (process-death reconciliation).
     * Server-side idempotency on the key guarantees at-most-once delivery.
     */
    fun sendWithId(clientRequestId: String, conversationId: String, text: String, attachmentIds: List<String> = emptyList()): String {
        val cid = clientRequestId
        mutate { Reducer.beginSend(it, cid, conversationId, text, clock()) }
        scope.launch {
            try {
                transport.send(
                    MobileRequest.SendMessage(
                        clientRequestId = cid,
                        conversationId = conversationId,
                        text = text,
                        attachmentIds = attachmentIds,
                    )
                )
            } catch (t: TransportException) {
                mutate { s ->
                    val r = s.requests[cid] ?: return@mutate s
                    s.copy(requests = s.requests + (cid to r.copy(
                        status = RequestStatus.Failed(ErrorEnvelope("not_connected", t.message ?: "offline"), retryable = true),
                    )))
                }
            }
        }
        return cid
    }

    /**
     * AND-W6 upload intent. Marks the attachment uploading locally, then sends the
     * attachment.upload request. The real binary PUT is blocked on the §11 upload
     * contract freeze; the fake confirms/can reject via [failUploads].
     */
    fun uploadAttachment(attachmentId: String, conversationId: String, filename: String, mimeType: String, sizeBytes: Long) {
        mutate { Reducer.markAttachmentUploading(it, attachmentId, mimeType, sizeBytes) }
        scope.launch {
            try {
                transport.send(
                    MobileRequest.UploadAttachment(attachmentId, conversationId, filename, mimeType, sizeBytes)
                )
            } catch (t: TransportException) {
                mutate { s ->
                    val a = s.attachments[attachmentId] ?: return@mutate s
                    s.copy(attachments = s.attachments + (attachmentId to a.copy(
                        uploading = false,
                        ready = false,
                        error = ErrorEnvelope("not_connected", t.message ?: "offline"),
                    )))
                }
            }
        }
    }

    /** Cancel is idempotent: repeated presses send at most one cancel command while non-terminal. */
    fun cancel(clientRequestId: String) {
        val transitioned = mutateIfChanged { Reducer.requestCancel(it, clientRequestId) }
        if (!transitioned) return // already cancelling/cancelled/terminal
        scope.launch {
            try {
                transport.send(MobileRequest.CancelRequest(targetRequestId = clientRequestId))
            } catch (t: TransportException) {
                mutate { s -> s.withDiag(DiagnosticEntry.Kind.TRANSPORT, "cancel delivery failed: ${t.message}") }
            }
        }
    }

    /** The failed request to resend, or null when it isn't retryable. */
    fun retryableRequest(clientRequestId: String): RequestState? {
        val req = _snapshot.value.session.requests[clientRequestId] ?: return null
        val status = req.status
        return if (status is RequestStatus.Failed && status.retryable) req else null
    }

    fun resolveApproval(approvalId: String, outcome: ApprovalOutcome) {
        val transitioned = mutateIfChanged { Reducer.resolveApprovalLocally(it, approvalId, outcome) }
        if (!transitioned) return // in-flight or already resolved -> idempotent
        scope.launch {
            try {
                transport.send(
                    MobileRequest.ResolveApproval(
                        approvalId = approvalId,
                        outcome = outcome,
                        resolutionId = UUID.randomUUID().toString(),
                    )
                )
            } catch (t: TransportException) {
                mutate { Reducer.markApprovalResolutionFailed(it, approvalId) }
            }
        }
    }

    suspend fun health(): Result<HealthResponse> = runCatching { transport.health() }

    /** Manual reconnect trigger (e.g. from the offline banner). */
    fun reconnectNow() {
        scope.launch {
            goToConnecting()
            runCatching { transport.connect() }
        }
    }

    // ---- internals ------------------------------------------------------------

    /**
     * Frames arrive on the transport's dispatcher while commands mutate from
     * other coroutines, so every write goes through an atomic compare-and-set
     * ([MutableStateFlow.update]); a plain read-modify-write would silently drop
     * concurrent deltas. The reducer is pure, so a CAS retry is safe.
     */
    private fun onFrame(frame: String) {
        _snapshot.update { cur ->
            val (newSession, result) = reducer.reduce(cur.session, frame)
            cur.copy(
                phase = derivePhase(cur.phase, newSession.connection, result),
                session = newSession,
            )
        }
    }

    private fun derivePhase(
        cur: SessionPhase,
        conn: ConnectionState,
        result: FrameResult,
    ): SessionPhase {
        if (result is FrameResult.Applied && result.outcome is com.jarvis.android.data.state.ReducerOutcome.ProtocolMismatch) {
            return SessionPhase.MISMATCH
        }
        return when (conn) {
            ConnectionState.AUTH_EXPIRED -> SessionPhase.AUTH_EXPIRED
            ConnectionState.DEVICE_REVOKED -> SessionPhase.REVOKED
            ConnectionState.PROTOCOL_MISMATCH -> SessionPhase.MISMATCH
            ConnectionState.ONLINE, ConnectionState.DEGRADED ->
                if (cur == SessionPhase.MISMATCH || cur == SessionPhase.REVOKED) cur else SessionPhase.READY
            else -> cur
        }
    }

    private fun onLink(state: LinkState) {
        when (state) {
            LinkState.CONNECTED -> {
                reconnectAttempts = 0
                reconnectJob?.cancel()
                reconnectJob = null
                // Ask server to replay anything missed while down, from our cursor.
                val cursor = _snapshot.value.session.lastCursor
                scope.launch {
                    if (cursor > 0) runCatching { transport.send(MobileRequest.Replay(cursor)) }
                }
            }
            LinkState.RECONNECTING, LinkState.CLOSED, LinkState.FAILED -> {
                mutate { Reducer.onDisconnected(it) }
                _snapshot.update {
                    if (it.phase == SessionPhase.READY || it.phase == SessionPhase.CONNECTING) {
                        it.copy(phase = SessionPhase.DISCONNECTED)
                    } else it
                }
                scheduleReconnect()
            }
            LinkState.CONNECTING -> goToConnecting()
            LinkState.IDLE -> Unit
        }
    }

    private var reconnectAttempts = 0
    private var reconnectJob: Job? = null

    /** One backoff loop at a time; repeated link drops must not stack retries. */
    private fun scheduleReconnect() {
        if (reconnectJob?.isActive == true) return
        reconnectJob = scope.launch { backoffReconnect() }
    }

    private suspend fun backoffReconnect() {
        val phase = _snapshot.value.phase
        if (phase == SessionPhase.REVOKED || phase == SessionPhase.MISMATCH) return // fail closed
        val caps = listOf(500L, 1_000L, 2_000L, 5_000L, 10_000L)
        if (reconnectAttempts >= caps.size) return
        delay(caps[reconnectAttempts])
        reconnectAttempts++
        if (_snapshot.value.session.connection.isUsable) {
            reconnectAttempts = 0
            return
        }
        goToConnecting()
        runCatching { transport.connect() }
    }

    private fun goToConnecting() {
        _snapshot.update {
            it.copy(
                phase = SessionPhase.CONNECTING,
                session = it.session.copy(connection = ConnectionState.CONNECTING),
            )
        }
    }

    private inline fun mutate(crossinline f: (SessionState) -> SessionState) {
        _snapshot.update { it.copy(session = f(it.session)) }
    }

    /**
     * Applies [f] atomically and reports whether it actually changed the state,
     * so callers can decide to hit the wire exactly once (cancel/approve
     * idempotency) without a read-then-write race.
     */
    private inline fun mutateIfChanged(crossinline f: (SessionState) -> SessionState): Boolean {
        var changed = false
        _snapshot.update { cur ->
            val next = f(cur.session)
            changed = next !== cur.session
            if (changed) cur.copy(session = next) else cur
        }
        return changed
    }
}

private fun SessionState.withDiag(kind: DiagnosticEntry.Kind, detail: String): SessionState =
    copy(diagnostics = (diagnostics + DiagnosticEntry(System.currentTimeMillis(), kind, detail)).takeLast(SessionState.MAX_DIAGNOSTICS))
