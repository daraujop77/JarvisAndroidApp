package com.jarvis.android.transport.live

import com.jarvis.android.contract.ConnectionPayload
import com.jarvis.android.contract.ContractVersion
import com.jarvis.android.contract.ErrorEnvelope
import com.jarvis.android.contract.EventEnvelope
import com.jarvis.android.contract.GatewayEvent
import com.jarvis.android.contract.HealthResponse
import com.jarvis.android.contract.JarvisJson
import com.jarvis.android.contract.MobileRequest
import com.jarvis.android.contract.ServerConnectionState
import com.jarvis.android.transport.GatewayTransport
import com.jarvis.android.transport.LinkState
import com.jarvis.android.transport.TransportException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * PCB-LIVE-1 transport for PC-A's authenticated app surface (the /api/app
 * login + chat endpoints) over the private Tailscale front door.
 *
 * Strategy B (runbook §5): this adapter converts PC-A's SSE chat protocol
 * (`event: ready|delta|complete|error`, JSON `data`) into the internal
 * [EventEnvelope] the existing pure reducer already consumes, so dedupe,
 * cancel-idempotency and streaming semantics are unchanged and no third
 * protocol exists. When PC-A exposes the frozen `/api/v1` Gateway, only this
 * class changes.
 *
 * Cancellation mirrors PC-A's own web client (auth.js): mark the turn, POST
 * the scoped cancel, abort the local stream, settle as Cancelled. A
 * disconnect-driven abort settles through `Reducer.onDisconnected` instead.
 */
class LiveAppGatewayTransport(
    private val session: JarvisAppSession,
    private val scope: CoroutineScope,
) : GatewayTransport {

    private val _frames = Channel<String>(Channel.UNLIMITED)
    override val frames: Flow<String> = _frames.receiveAsFlow()

    private val _linkState = MutableStateFlow(LinkState.IDLE)
    override val linkState: StateFlow<LinkState> = _linkState.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }
    private val client = JarvisAppSession.defaultClient()
    private val active = ConcurrentHashMap<String, ActiveTurn>()

    private var cursor = 0L
    private var eventSeq = 0L

    internal class TurnCtl {
        @Volatile var cancelRequested = false
        @Volatile var call: Call? = null
    }

    internal data class ActiveTurn(
        val clientRequestId: String,
        val conversationId: String,
        val traceId: String,
        val deviceId: String,
        val sessionId: String,
        val job: Job,
        val ctl: TurnCtl,
    )

    override suspend fun connect() {
        if (_linkState.value == LinkState.CONNECTED || _linkState.value == LinkState.CONNECTING) return
        val token = session.token
        if (token.isNullOrBlank()) {
            _linkState.value = LinkState.FAILED
            throw TransportException("not authenticated")
        }
        _linkState.value = LinkState.CONNECTING
        val restored = session.restore()
        if (restored.isFailure) {
            _linkState.value = LinkState.FAILED
            emit(
                GatewayEvent.ConnectionState(
                    ConnectionPayload(ServerConnectionState.AUTH_EXPIRED, restored.exceptionOrNull()?.message),
                ),
            )
            throw TransportException("session invalid: ${restored.exceptionOrNull()?.message}")
        }
        _linkState.value = LinkState.CONNECTED
        emit(GatewayEvent.ConnectionState(ConnectionPayload(ServerConnectionState.ONLINE)))
    }

    override suspend fun disconnect() {
        active.values.forEach { it.job.cancel() }
        active.clear()
        _linkState.value = LinkState.CLOSED
    }

    override suspend fun send(request: MobileRequest) {
        if (_linkState.value != LinkState.CONNECTED) throw TransportException("live link not connected")
        when (request) {
            is MobileRequest.SendMessage -> handleSend(request)
            is MobileRequest.CancelRequest -> handleCancel(request)
            is MobileRequest.Replay -> Unit // no cursor journal on the app surface yet
            is MobileRequest.ResolveApproval ->
                throw TransportException("approvals not enabled on the live app surface yet (PA-5/6 gate)")
            is MobileRequest.UploadAttachment ->
                throw TransportException("attachment upload contract not frozen yet")
        }
    }

    override suspend fun health(): HealthResponse {
        val (code, _) = withContext(Dispatchers.IO) { session.get(session.baseUrl, "/api/status", auth = null) }
        if (code !in 200..299) throw TransportException("health $code")
        return HealthResponse(
            status = "ok",
            protocolVersion = "app-session-v1",
            capabilities = listOf("chat", "stream", "cancel", "recover"),
        )
    }

    // ---- send / stream --------------------------------------------------------

    private fun handleSend(req: MobileRequest.SendMessage) {
        val turnId = req.clientRequestId
        val traceId = UUID.randomUUID().toString()
        val deviceId = session.deviceId
        val sessionId = session.appSessionId
        val ctl = TurnCtl()

        val job = scope.launch(Dispatchers.IO) {
            try {
                emit(GatewayEvent.MessageAccepted(turnId, req.conversationId, "msg_$turnId"))
                streamChat(req, traceId, deviceId, sessionId, ctl)
                emit(GatewayEvent.MessageCompleted(turnId, "msg_$turnId"))
            } catch (t: CancellationException) {
                if (ctl.cancelRequested) {
                    emitQuietly(GatewayEvent.MessageCompleted(turnId, "msg_$turnId"))
                }
                // disconnect-driven cancel: Reducer.onDisconnected owns the state
                throw t
            } catch (t: Throwable) {
                if (t is IOException && ctl.cancelRequested) {
                    emit(GatewayEvent.MessageCompleted(turnId, "msg_$turnId"))
                } else {
                    emit(
                        GatewayEvent.MessageFailed(
                            turnId,
                            "msg_$turnId",
                            ErrorEnvelope("live_error", t.message ?: "stream failed"),
                            retryable = true,
                        ),
                    )
                }
            } finally {
                active.remove(turnId)
            }
        }
        active[turnId] = ActiveTurn(turnId, req.conversationId, traceId, deviceId, sessionId, job, ctl)
    }

    private suspend fun streamChat(
        req: MobileRequest.SendMessage,
        traceId: String,
        deviceId: String,
        sessionId: String,
        ctl: TurnCtl,
    ) {
        val payload = buildJsonObject {
            put("route", "local")
            put("profile", "normal")
            put("session_id", sessionId)
            put("conversation_id", req.conversationId)
            put("device_id", deviceId)
            put("request_id", req.clientRequestId)
            put("trace_id", traceId)
            put("messages", buildJsonArray {
                addJsonObject {
                    put("role", "user")
                    put("content", req.text)
                }
            })
        }.toString()

        val request = Request.Builder()
            .url(session.baseUrl + "/api/app/chat/stream")
            .header("Accept", "text/event-stream")
            .header("Authorization", session.authHeader() ?: throw TransportException("no token"))
            .post(payload.toRequestBody(JarvisAppSession.JSON_MEDIA))
            .build()

        var seq = 0
        var completed = false
        val call = client.newCall(request)
        ctl.call = call
        call.execute().use { resp ->
            if (resp.code == 401) {
                emit(
                    GatewayEvent.ConnectionState(
                        ConnectionPayload(ServerConnectionState.AUTH_EXPIRED, "token expired"),
                    ),
                )
                throw TransportException("auth expired")
            }
            if (resp.code !in 200..299) {
                val msg = resp.body?.string()?.take(200).orEmpty()
                throw TransportException("chat stream ${resp.code} $msg")
            }
            val reader = resp.body?.charStream()?.buffered() ?: throw TransportException("empty stream body")
            var eventName = ""
            val dataLines = StringBuilder()
            while (currentCoroutineContext().isActive) {
                val line = reader.readLine() ?: break
                when {
                    line.isEmpty() -> {
                        if (eventName.isNotEmpty() || dataLines.isNotEmpty()) {
                            completed = handleSFrame(eventName, dataLines.toString(), req.clientRequestId, seq)
                                .let { completed || it }
                            if (eventName == "delta") seq++
                            eventName = ""
                            dataLines.setLength(0)
                        }
                    }
                    line.startsWith("event:") -> eventName = line.removePrefix("event:").trim()
                    line.startsWith("data:") -> {
                        if (dataLines.isNotEmpty()) dataLines.append('\n')
                        dataLines.append(line.removePrefix("data:").trimStart())
                    }
                }
            }
        }
        if (!completed && !ctl.cancelRequested) {
            throw TransportException("stream ended before completion")
        }
    }

    /** Returns true when this frame is the terminal `complete`. */
    private suspend fun handleSFrame(event: String, data: String, requestId: String, seq: Int): Boolean {
        if (data.isBlank()) return false
        when (event) {
            "ready" -> Unit // accepted already emitted when the turn started
            "delta" -> {
                val text = runCatching {
                    json.parseToJsonElement(data).jsonObject["delta"]?.jsonPrimitive?.content
                }.getOrNull().orEmpty()
                if (text.isNotEmpty()) {
                    emit(GatewayEvent.MessageDelta(requestId, "msg_$requestId", seq, text))
                }
            }
            "complete" -> {
                val full = runCatching {
                    json.parseToJsonElement(data)
                        .jsonObject["response"]?.jsonObject?.get("text")?.jsonPrimitive?.content
                }.getOrNull()
                emit(GatewayEvent.MessageCompleted(requestId, "msg_$requestId", full))
                return true
            }
            "error" -> {
                val code = runCatching {
                    json.parseToJsonElement(data).jsonObject["error"]?.jsonPrimitive?.content
                }.getOrNull()
                if (code == "chat_cancelled") {
                    emit(GatewayEvent.MessageCompleted(requestId, "msg_$requestId"))
                } else {
                    emit(
                        GatewayEvent.MessageFailed(
                            requestId,
                            "msg_$requestId",
                            ErrorEnvelope(code ?: "stream_error", "assistant stream error"),
                            retryable = code !in setOf("chat_timeout"),
                        ),
                    )
                }
                return true
            }
        }
        return false
    }

    // ---- cancel ---------------------------------------------------------------

    private fun handleCancel(req: MobileRequest.CancelRequest) {
        val turn = active.values.firstOrNull { it.clientRequestId == req.targetRequestId } ?: return
        turn.ctl.cancelRequested = true
        scope.launch(Dispatchers.IO) {
            val body = buildJsonObject {
                put("user_id", session.userId)
                put("device_id", turn.deviceId)
                put("session_id", turn.sessionId)
                put("conversation_id", turn.conversationId)
                put("request_id", turn.clientRequestId)
                put("trace_id", turn.traceId)
            }.toString()
            runCatching {
                session.post(session.baseUrl, "/api/app/chat/cancel", body, session.authHeader())
            }
            turn.ctl.call?.cancel()
            turn.job.cancel()
        }
    }

    /** Completed-turn reconciliation via `/api/app/chat/requests/{id}` (PA-8 recovery path). */
    suspend fun recoverTurn(clientRequestId: String): String? {
        val turn = active[clientRequestId] ?: return null
        val request = Request.Builder()
            .url(
                session.baseUrl + "/api/app/chat/requests/" +
                    java.net.URLEncoder.encode(clientRequestId, "UTF-8"),
            )
            .get()
            .apply {
                session.authHeader()?.let { header("Authorization", it) }
                header("X-Jarvis-User-Id", session.userId)
                header("X-Jarvis-Device-Id", turn.deviceId)
                header("X-Jarvis-Session-Id", turn.sessionId)
                header("X-Jarvis-Conversation-Id", turn.conversationId)
                header("X-Jarvis-Trace-Id", turn.traceId)
            }
            .build()
        return withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { resp ->
                if (resp.code !in 200..299) return@use null
                val body = resp.body?.string() ?: return@use null
                runCatching {
                    json.parseToJsonElement(body)
                        .jsonObject["response"]?.jsonObject?.get("text")?.jsonPrimitive?.content
                }.getOrNull()
            }
        }
    }

    // ---- frame production (internal EventEnvelope for the existing reducer) ---

    private suspend fun emit(event: GatewayEvent) {
        val env = EventEnvelope(
            cursor = ++cursor,
            eventId = "live_${eventSeq++}",
            version = ContractVersion.SUPPORTED,
            timestampMs = System.currentTimeMillis(),
            event = event,
        )
        _frames.trySend(JarvisJson.default.encodeToString(EventEnvelope.serializer(), env))
    }

    /** Non-suspending variant usable from a cancelled coroutine. */
    private fun emitQuietly(event: GatewayEvent) {
        val env = EventEnvelope(
            cursor = ++cursor,
            eventId = "live_${eventSeq++}",
            version = ContractVersion.SUPPORTED,
            timestampMs = System.currentTimeMillis(),
            event = event,
        )
        _frames.trySend(JarvisJson.default.encodeToString(EventEnvelope.serializer(), env))
    }
}
