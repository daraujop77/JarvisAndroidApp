package com.jarvis.android.transport.fake

import com.jarvis.android.contract.ApprovalRequiredPayload
import com.jarvis.android.contract.ApprovalResolvedPayload
import com.jarvis.android.contract.ApprovalTier
import com.jarvis.android.contract.AttachmentPayload
import com.jarvis.android.contract.ConnectionPayload
import com.jarvis.android.contract.ContractVersion
import com.jarvis.android.contract.ErrorEnvelope
import com.jarvis.android.contract.GatewayEvent
import com.jarvis.android.contract.HealthResponse
import com.jarvis.android.contract.MobileRequest
import com.jarvis.android.contract.ServerConnectionState
import com.jarvis.android.contract.TaskStatus
import com.jarvis.android.contract.TaskUpdatedPayload
import com.jarvis.android.contract.webv1.WebV1
import com.jarvis.android.contract.webv1.WebV1Adapter
import com.jarvis.android.contract.webv1.WebV1Codec
import com.jarvis.android.contract.webv1.WebV1Event
import com.jarvis.android.transport.GatewayTransport
import com.jarvis.android.transport.LinkState
import com.jarvis.android.transport.TransportException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Deterministic, production-shaped fake transport (plan §7 / AND-0002).
 *
 * Behavior derives from the frozen Web V1 contract, never from a competing
 * backend: it emits serialized [WebV1Event] frames through the same
 * [GatewayTransport] seam the HTTP adapter implements. Per-request stream
 * drivers PAUSE while the link is down and resume on reconnect, and opaque
 * cursor replay re-emits journal frames (same event_id) so the reducer can
 * dedupe.
 *
 * Scripted knobs cover the §7 scenario matrix: streaming cadence, failure mode,
 * cancel handling, duplicate/out-of-order injection, reconnect, approvals, tasks,
 * attachments, protocol mismatch, degraded/offline/auth-expiry/revoked pushes.
 */
class FakeGateway(
    private val scope: CoroutineScope,
    var config: FakeConfig = FakeConfig(),
) : GatewayTransport {

    private val _frames = Channel<String>(Channel.UNLIMITED)
    override val frames: Flow<String> = _frames.receiveAsFlow()

    private val _linkState = MutableStateFlow(LinkState.IDLE)
    override val linkState: StateFlow<LinkState> = _linkState.asStateFlow()

    /** Journal of every Web V1 envelope produced this session, for opaque-cursor replay. */
    private val journal = ArrayDeque<WebV1Event>()
    private var cursorSeq = 0L
    private var eventSeq = 0L
    private val seqLock = Any()
    private val streamJobs = mutableMapOf<String, Job>()
    private val progressByReq = mutableMapOf<String, StreamProgress>()
    private val cancelledRequestIds = mutableSetOf<String>()

    /** Requests that reached the fake, for assertions. Written from many coroutines. */
    val receivedRequests: MutableList<MobileRequest> =
        java.util.Collections.synchronizedList(mutableListOf())
    val emittedFrames: MutableList<String> =
        java.util.Collections.synchronizedList(mutableListOf())

    var healthResponse: HealthResponse = HealthResponse(
        status = "ok",
        protocolVersion = ContractVersion.SUPPORTED,
        capabilities = listOf("streaming", "cancel", "replay", "approvals", "tasks", "attachments"),
    )

    override suspend fun connect() {
        if (_linkState.value == LinkState.CONNECTED) return
        _linkState.value = LinkState.CONNECTING
        delay(config.connectDelayMs)
        _linkState.value = LinkState.CONNECTED
        emit(GatewayEvent.ConnectionState(ConnectionPayload(ServerConnectionState.ONLINE)))
    }

    override suspend fun disconnect() {
        streamJobs.values.forEach { it.cancel() }
        streamJobs.clear()
        _linkState.value = LinkState.CLOSED
    }

    /** Simulate an abrupt transport drop and automatic re-establishment. */
    fun simulateNetworkDrop(reconnectAfterMs: Long = config.dropReconnectMs) {
        if (_linkState.value != LinkState.CONNECTED) return
        _linkState.value = LinkState.RECONNECTING
        scope.launch {
            delay(reconnectAfterMs)
            if (_linkState.value == LinkState.RECONNECTING) {
                _linkState.value = LinkState.CONNECTED
                emit(
                    GatewayEvent.ConnectionState(
                        ConnectionPayload(config.postReconnectServerState ?: ServerConnectionState.ONLINE)
                    )
                )
            }
        }
    }

    /** Server-initiated lifecycle pushes for scripted auth/revocation scenarios. */
    fun pushConnectionState(state: ServerConnectionState, reason: String? = null) {
        scope.launch { emit(GatewayEvent.ConnectionState(ConnectionPayload(state, reason))) }
    }

    override suspend fun send(request: MobileRequest) {
        if (_linkState.value != LinkState.CONNECTED) {
            throw TransportException("fake link not connected (${_linkState.value})")
        }
        receivedRequests += request
        when (request) {
            is MobileRequest.SendMessage -> handleSend(request)
            is MobileRequest.CancelRequest -> handleCancel(request)
            is MobileRequest.ResolveApproval -> handleResolveApproval(request)
            is MobileRequest.Replay -> handleReplay(request)
            is MobileRequest.UploadAttachment -> handleUploadAttachment(request)
        }
    }

    override suspend fun health(): HealthResponse {
        if (_linkState.value != LinkState.CONNECTED) throw TransportException("fake link not connected")
        return healthResponse
    }

    // ---- scenario drivers -----------------------------------------------------

    private class StreamProgress {
        var acceptedSent = false
        var timersArmed = false
        var nextWord = 0
        var approvalSent = false
        var tasksEmitted = 0
        var tailDone = false
        var settled = false
    }

    private fun handleSend(req: MobileRequest.SendMessage) {
        val rid = req.clientRequestId
        val mid = "msg_$rid"
        streamJobs[rid]?.cancel()
        streamJobs[rid] = scope.launch {
            val p = progressByReq.getOrPut(rid) { StreamProgress() }
            if (p.settled) {
                // Idempotent resend of an already-finished request (client
                // restarted after the terminal frame but before the pending
                // cleanup). A real server answers from cache, not a second
                // inference: replay this request's journal with fresh cursors
                // and the original event_ids, so a live session dedupes and a
                // fresh session re-applies the full answer.
                journal.filter { it.request_id == rid }.forEach { env ->
                    emitRaw(WebV1Codec.encodeEvent(env.copy(cursor = nextCursorToken())))
                }
                return@launch
            }
            if (!p.timersArmed) {
                p.timersArmed = true
                if (config.emitProtocolMismatchFirst) {
                    val seq = synchronized(seqLock) { ++eventSeq }
                    emitRaw(
                        WebV1Codec.encodeEvent(
                            WebV1Adapter.domainToWire(
                                event = GatewayEvent.Unknown("capability.bump"),
                                cursor = nextCursorToken(),
                                eventId = "evt_$seq",
                                protocolVersion = "2.0",
                                fingerprint = WebV1.FINGERPRINT,
                                sequence = seq,
                            ).copy(protocolVersion = "2.0"),
                        ),
                    )
                    p.settled = true
                    return@launch
                }
                config.dropAfterMs?.let { after ->
                    launch { delay(after); simulateNetworkDrop() }
                }
                config.pushState?.let { st ->
                    launch { delay(config.pushStateAfterMs); pushConnectionState(st, "scripted scenario") }
                }
            }

            delay(config.acceptDelayMs)
            if (!p.acceptedSent) {
                if (!config.dropBeforeAccepted) emit(GatewayEvent.MessageAccepted(rid, req.conversationId, mid))
                p.acceptedSent = true
                if (config.emitOptionalUnknown) {
                    val seq = synchronized(seqLock) { ++eventSeq }
                    emitRaw(
                        WebV1Codec.encodeEvent(
                            WebV1Adapter.domainToWire(
                                event = GatewayEvent.Unknown("hologram.started"),
                                cursor = nextCursorToken(),
                                eventId = "evt_opt_$seq",
                                optional = true,
                                sequence = seq,
                            ),
                        ),
                    )
                }
            }

            if (isCancelled(rid)) {
                finishCancelled(mid, rid)
                return@launch
            }

            when (config.failMode) {
                FakeFailMode.RETRYABLE -> {
                    streamWordsChunk(mid, rid, p, take = 2)
                    delay(config.failDelayMs)
                    emit(
                        GatewayEvent.MessageFailed(
                            rid, mid,
                            ErrorEnvelope("gateway_degraded", "model upstream hiccup"),
                            retryable = true,
                        )
                    )
                    p.settled = true
                    return@launch
                }
                FakeFailMode.FATAL -> {
                    delay(config.failDelayMs)
                    emit(
                        GatewayEvent.MessageFailed(
                            rid, mid,
                            ErrorEnvelope("gateway_offline", "gateway cannot serve request"),
                            retryable = false,
                        )
                    )
                    p.settled = true
                    return@launch
                }
                FakeFailMode.NONE -> Unit
            }

            if (config.requireApprovalOnRequest && !p.approvalSent) {
                p.approvalSent = true
                emit(
                    GatewayEvent.ApprovalRequired(
                        ApprovalRequiredPayload(
                            approvalId = "appr_$rid",
                            requestId = rid,
                            title = "Run PC action",
                            description = "JARVIS wants to control the main PC.",
                            tier = ApprovalTier.SENSITIVE,
                            risk = "medium",
                            expiresAtMs = System.currentTimeMillis() + 60_000,
                        )
                    )
                )
            }

            if (config.emitTaskOnRequest && p.tasksEmitted < 4) {
                val statuses = listOf(
                    TaskStatus.QUEUED, TaskStatus.STARTED, TaskStatus.RUNNING, TaskStatus.COMPLETED,
                )
                while (p.tasksEmitted < statuses.size) {
                    if (isCancelled(rid)) {
                        finishCancelled(mid, rid)
                        return@launch
                    }
                    waitForLink()
                    val i = p.tasksEmitted
                    emit(
                        GatewayEvent.TaskUpdated(
                            TaskUpdatedPayload(
                                taskId = "task_$rid", requestId = rid, status = statuses[i],
                                progress = i / 3f, label = "render job",
                            )
                        )
                    )
                    p.tasksEmitted++
                    delay(config.wordDelayMs)
                }
            }

            streamWordsChunk(mid, rid, p, take = null)

            if (isCancelled(rid)) {
                finishCancelled(mid, rid)
                return@launch
            }

            if (!p.tailDone) {
                p.tailDone = true
                if (config.emitAttachmentOnRequest) {
                    emit(
                        GatewayEvent.AttachmentReady(
                            AttachmentPayload(
                                attachmentId = "att_$rid", requestId = rid,
                                kind = "image", mimeType = "image/png", sizeBytes = 12345,
                            )
                        )
                    )
                }
                if (config.injectDuplicates) {
                    journal.lastOrNull()?.let { dup ->
                        emitRaw(WebV1Codec.encodeEvent(dup.copy(cursor = nextCursorToken())))
                    }
                }
            }

            delay(config.completeDelayMs)
            emit(GatewayEvent.MessageCompleted(rid, mid))
            p.settled = true
        }
    }

    /** Emits words from p.nextWord onward, pausing while the link is down. */
    private suspend fun streamWordsChunk(mid: String, rid: String, p: StreamProgress, take: Int?) {
        val words = if (take == null) config.replyWords else config.replyWords.take(take)
        while (p.nextWord < words.size) {
            if (isCancelled(rid) || !currentCoroutineContext().isActive) return
            waitForLink()
            if (isCancelled(rid)) return
            if (config.injectOutOfOrder && p.nextWord == config.outOfOrderFirstSeq && p.nextWord + 1 < words.size) {
                // deliver seq N+1 before N mid-stream: reducer must buffer then drain
                emitDelta(mid, rid, p.nextWord + 1, words[p.nextWord + 1])
                emitDelta(mid, rid, p.nextWord, words[p.nextWord])
                p.nextWord += 2
            } else {
                emitDelta(mid, rid, p.nextWord, words[p.nextWord])
                p.nextWord++
            }
            delay(config.wordDelayMs)
        }
    }

    /** Block until the fake link reports CONNECTED (drop/resume simulation). */
    private suspend fun waitForLink() {
        while (_linkState.value != LinkState.CONNECTED) {
            if (!currentCoroutineContext().isActive) return
            delay(30)
        }
    }

    private suspend fun finishCancelled(mid: String, rid: String) {
        waitForLink()
        if (config.cancelCompletesOfficially) {
            emit(GatewayEvent.MessageCompleted(rid, mid))
        }
    }

    private fun isCancelled(rid: String): Boolean = rid in cancelledRequestIds

    private fun handleCancel(req: MobileRequest.CancelRequest) {
        val rid = req.targetRequestId
        if (rid in cancelledRequestIds) return // idempotent — no second event
        cancelledRequestIds += rid
    }

    private fun handleResolveApproval(req: MobileRequest.ResolveApproval) {
        if (config.approveNeverResolves) return
        scope.launch {
            delay(config.approveResolveDelayMs)
            emit(
                GatewayEvent.ApprovalResolved(
                    ApprovalResolvedPayload(
                        approvalId = req.approvalId,
                        outcome = req.outcome,
                        resolvedBy = "phone",
                        resolvedAtMs = System.currentTimeMillis(),
                    )
                )
            )
        }
    }

    private fun handleUploadAttachment(req: MobileRequest.UploadAttachment) {
        scope.launch {
            delay(config.uploadDelayMs)
            if (config.failUploads) {
                emit(
                    GatewayEvent.AttachmentFailed(
                        AttachmentPayload(
                            attachmentId = req.attachmentId,
                            kind = "image",
                            mimeType = req.mimeType,
                            sizeBytes = req.sizeBytes,
                            ready = false,
                            error = ErrorEnvelope("upload_failed", "fake upload rejected (scenario)"),
                        )
                    )
                )
            } else {
                emit(
                    GatewayEvent.AttachmentReady(
                        AttachmentPayload(
                            attachmentId = req.attachmentId,
                            kind = "image",
                            mimeType = req.mimeType,
                            sizeBytes = req.sizeBytes,
                        )
                    )
                )
            }
        }
    }

    private fun handleReplay(req: MobileRequest.Replay) {
        scope.launch {
            val after = req.sinceCursor
            val start = when {
                after.isBlank() || after == "0" -> 0
                else -> {
                    val i = journal.indexOfFirst { it.cursor == after }
                    if (i < 0) 0 else i + 1
                }
            }
            journal.drop(start).toList().forEach { env ->
                emitRaw(WebV1Codec.encodeEvent(env.copy(cursor = nextCursorToken())))
            }
        }
    }

    // ---- frame production -----------------------------------------------------

    private suspend fun emit(event: GatewayEvent) {
        val seq = synchronized(seqLock) { ++eventSeq }
        val wire = WebV1Adapter.domainToWire(
            event = event,
            cursor = nextCursorToken(),
            eventId = "evt_$seq",
            sequence = seq,
        )
        journal.addLast(wire)
        emitRaw(WebV1Codec.encodeEvent(wire))
    }

    private suspend fun emitDelta(mid: String, rid: String, seq: Int, text: String) {
        emit(GatewayEvent.MessageDelta(rid, mid, seq, text))
    }

    private fun nextCursorToken(): String = synchronized(seqLock) { "tok_${++cursorSeq}" }

    private suspend fun emitRaw(json: String) {
        emittedFrames += json
        _frames.trySend(json)
    }
}

enum class FakeFailMode { NONE, RETRYABLE, FATAL }

/**
 * Named presets for the §7 scenario matrix, selectable from the Settings screen
 * (dev builds only) so a device demo can walk every client behavior live.
 */
enum class FakeScenario(val label: String, val config: FakeConfig) {
    HAPPY("Chat: accepted -> deltas -> completed", FakeConfig()),
    SLOW("Chat: slow streaming (visible cadence)", FakeConfig(wordDelayMs = 180, acceptDelayMs = 300)),
    RETRYABLE_FAIL("Failure: retryable mid-stream", FakeConfig(failMode = FakeFailMode.RETRYABLE, failDelayMs = 300, wordDelayMs = 120)),
    FATAL_FAIL("Failure: final (non-retryable)", FakeConfig(failMode = FakeFailMode.FATAL, failDelayMs = 400)),
    DROP_BEFORE_ACCEPT("Failure: drop before accepted", FakeConfig(dropBeforeAccepted = true, failMode = FakeFailMode.FATAL, failDelayMs = 800)),
    RECONNECT_STREAM("Reconnect during streaming (resume)", FakeConfig(wordDelayMs = 400, dropAfterMs = 900, dropReconnectMs = 500)),
    REPLAY("Reconnect + replay from cursor", FakeConfig(wordDelayMs = 500, dropAfterMs = 1400, dropReconnectMs = 400)),
    DUPLICATES("Duplicate event ids injected", FakeConfig(injectDuplicates = true, replyWords = List(4) { "dup$it " })),
    OUT_OF_ORDER("Out-of-order deltas", FakeConfig(injectOutOfOrder = true, outOfOrderFirstSeq = 0)),
    APPROVAL("approval.required -> resolve", FakeConfig(requireApprovalOnRequest = true)),
    APPROVAL_STUCK("approval.required (never resolves)", FakeConfig(requireApprovalOnRequest = true, approveNeverResolves = true)),
    TASKS("Task start/progress/complete", FakeConfig(emitTaskOnRequest = true)),
    ATTACHMENT("Attachment ready", FakeConfig(emitAttachmentOnRequest = true)),
    UPLOAD("attachment.upload -> ready", FakeConfig()),
    UPLOAD_FAIL("attachment.upload -> rejected", FakeConfig(failUploads = true)),
    PROTOCOL_MISMATCH("Protocol mismatch (major bump)", FakeConfig(emitProtocolMismatchFirst = true)),
    OPTIONAL_UNKNOWN("Additive optional event is ignored", FakeConfig(emitOptionalUnknown = true)),
    DEGRADED("Gateway degraded", FakeConfig(pushState = ServerConnectionState.DEGRADED)),
    OFFLINE_RECOVER("Gateway offline -> recovered", FakeConfig(pushState = ServerConnectionState.OFFLINE)),
    AUTH_EXPIRED("Auth expiry", FakeConfig(pushState = ServerConnectionState.AUTH_EXPIRED)),
    REVOKED("Device revoked", FakeConfig(pushState = ServerConnectionState.DEVICE_REVOKED)),
}

/**
 * All knobs for the §7 scenario matrix. Defaults = happy streaming path.
 */
data class FakeConfig(
    val connectDelayMs: Long = 0,
    val acceptDelayMs: Long = 0,
    val wordDelayMs: Long = 0,
    val completeDelayMs: Long = 0,
    val failDelayMs: Long = 0,
    val dropReconnectMs: Long = 50,
    val approveResolveDelayMs: Long = 0,
    val failMode: FakeFailMode = FakeFailMode.NONE,
    val cancelCompletesOfficially: Boolean = true,
    val injectDuplicates: Boolean = false,
    val injectOutOfOrder: Boolean = false,
    val outOfOrderFirstSeq: Int = 0,
    val dropBeforeAccepted: Boolean = false,
    val emitProtocolMismatchFirst: Boolean = false,
    val requireApprovalOnRequest: Boolean = false,
    val emitTaskOnRequest: Boolean = false,
    val emitAttachmentOnRequest: Boolean = false,
    val approveNeverResolves: Boolean = false,
    /** AND-W6: reject attachment.upload intents. */
    val failUploads: Boolean = false,
    val uploadDelayMs: Long = 300,
    val postReconnectServerState: ServerConnectionState? = null,
    /** Auto-drop the link N ms after a send starts (reconnect-during-streaming demo). */
    val emitOptionalUnknown: Boolean = false,
    val dropAfterMs: Long? = null,
    /** Push a connection.state event N ms after a send starts (auth/revoked/degraded demo). */
    val pushState: ServerConnectionState? = null,
    val pushStateAfterMs: Long = 1200,
    val replyWords: List<String> = List(8) { "word$it " },
)
