package com.jarvis.android.transport.http

import com.jarvis.android.contract.HealthResponse
import com.jarvis.android.contract.MobileRequest
import com.jarvis.android.contract.webv1.WebV1
import com.jarvis.android.contract.webv1.WebV1Adapter
import com.jarvis.android.contract.webv1.WebV1Capabilities
import com.jarvis.android.contract.webv1.WebV1CapabilitiesDecode
import com.jarvis.android.contract.webv1.WebV1Codec
import com.jarvis.android.contract.webv1.WebV1Errors
import com.jarvis.android.contract.webv1.WebV1Health
import com.jarvis.android.contract.webv1.WebV1Identity
import com.jarvis.android.transport.live.JarvisAppSession
import com.jarvis.android.transport.AuthProvider
import com.jarvis.android.transport.GatewayTransport
import com.jarvis.android.transport.LinkState
import com.jarvis.android.transport.NoAuthProvider
import com.jarvis.android.transport.TransportException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * PC-A Web V1 HTTP adapter (PCB-R3).
 *
 * Paths match the frozen contract:
 *  - GET  /api/v1/health
 *  - GET  /api/v1/capabilities
 *  - POST /api/v1/requests
 *  - GET  /api/v1/events?after=<opaque_cursor>
 *
 * Auth is an injectable [AuthProvider]. Absent credentials fail closed on the
 * server; this client never invents tokens. No live PC-A call is required to
 * unit-test this adapter — inject [OkHttpClient] pointed at a local fixture.
 *
 * The legacy WSS `/mobile/events` adapter remains behind [GatewayTransport]
 * but is not a production requirement.
 */
class HttpGatewayTransport(
    private val scope: CoroutineScope,
    private val baseUrlProvider: () -> String,
    private val auth: AuthProvider = NoAuthProvider,
    private val identity: () -> WebV1Identity = { WebV1Identity() },
    private val client: OkHttpClient = defaultClient(),
    private val pollIntervalMs: Long = 750,
) : GatewayTransport {

    private val _frames = Channel<String>(Channel.UNLIMITED)
    override val frames: Flow<String> = _frames.receiveAsFlow()

    private val _linkState = MutableStateFlow(LinkState.IDLE)
    override val linkState: StateFlow<LinkState> = _linkState.asStateFlow()

    @Volatile
    private var afterCursor: String = ""
    private var pollJob: Job? = null

    override suspend fun connect() {
        if (_linkState.value == LinkState.CONNECTED || _linkState.value == LinkState.CONNECTING) return
        _linkState.value = LinkState.CONNECTING
        try {
            val base = requireBase()
            get(base, "/api/v1/health")
            _linkState.value = LinkState.CONNECTED
            startPolling(base)
        } catch (t: Throwable) {
            _linkState.value = LinkState.FAILED
            // Keep the original fail-closed reason (e.g. private-host guard);
            // only wrap foreign exceptions.
            throw if (t is TransportException) t else TransportException("http connect failed: ${t.message}", t)
        }
    }

    override suspend fun disconnect() {
        pollJob?.cancel()
        pollJob = null
        _linkState.value = LinkState.CLOSED
    }

    override suspend fun send(request: MobileRequest) {
        if (_linkState.value != LinkState.CONNECTED) {
            throw TransportException("http link not connected (${_linkState.value})")
        }
        val wire = when (request) {
            is MobileRequest.Replay -> {
                afterCursor = request.sinceCursor
                WebV1Adapter.outboundReplay(request.sinceCursor, identity(), request.conversationId)
            }
            else -> WebV1Adapter.outbound(request, identity())
        }
        val body = WebV1Codec.encodeRequest(wire)
        post(requireBase(), "/api/v1/requests", body)
        if (request is MobileRequest.Replay) {
            pollOnce(requireBase())
        }
    }

    override suspend fun health(): HealthResponse {
        val raw = get(requireBase(), "/api/v1/health")
        val h = WebV1.json.decodeFromString(WebV1Health.serializer(), raw)
        return HealthResponse(
            status = h.status,
            protocolVersion = h.protocolVersion,
            capabilities = h.capabilities,
        )
    }

    suspend fun capabilities(): WebV1Capabilities {
        val raw = get(requireBase(), "/api/v1/capabilities")
        return when (val decoded = WebV1Codec.decodeCapabilities(raw)) {
            is WebV1CapabilitiesDecode.Ok -> decoded.capabilities
            is WebV1CapabilitiesDecode.ProtocolMismatch ->
                throw TransportException(decoded.reason, code = "protocol_version_mismatch", retryable = false)
            is WebV1CapabilitiesDecode.Malformed ->
                throw TransportException(decoded.reason, code = "invalid_response", retryable = false)
        }
    }

    fun lastCursor(): String = afterCursor

    private fun startPolling(base: String) {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive && _linkState.value == LinkState.CONNECTED) {
                // Wait the interval BEFORE the first poll: an immediate poll
                // races disconnect() (an in-flight request may or may not be
                // server-observed), making behavior and tests non-deterministic.
                // Replay sends and connect-time sends poll explicitly.
                delay(pollIntervalMs)
                if (!isActive || _linkState.value != LinkState.CONNECTED) break
                runCatching { pollOnce(base) }
                    .onFailure {
                        if (_linkState.value == LinkState.CONNECTED) {
                            _linkState.value = LinkState.RECONNECTING
                        }
                    }
            }
        }
    }

    private fun pollOnce(base: String) {
        val path = if (afterCursor.isBlank()) {
            "/api/v1/events"
        } else {
            "/api/v1/events?after=${java.net.URLEncoder.encode(afterCursor, "UTF-8")}"
        }
        val raw = get(base, path)
        val element = WebV1.json.parseToJsonElement(raw)
        val frames = when (element) {
            is kotlinx.serialization.json.JsonArray -> element.map { it.toString() }
            is kotlinx.serialization.json.JsonObject -> listOf(element.toString())
            else -> emptyList()
        }
        for (frame in frames) {
            val decoded = WebV1Codec.decodeEvent(frame)
            if (decoded is com.jarvis.android.contract.webv1.WebV1Decode.Ok) {
                afterCursor = decoded.event.cursor
            }
            _frames.trySend(frame)
        }
    }

    private fun requireBase(): String {
        val base = baseUrlProvider().trim().trimEnd('/')
        if (base.isBlank()) throw TransportException("gateway base URL not configured")
        if (base.toHttpUrlOrNull() == null) throw TransportException("invalid gateway base URL")
        // Lane D security rule: this adapter carries the real PC-A bearer when a
        // session exists, so it must fail closed on public hosts exactly like the
        // LIVE transport — a stray/typo'd URL can never downgrade the token to a
        // public endpoint.
        if (!JarvisAppSession.isAllowedLiveHost(base)) {
            throw TransportException("gateway host must be a private-network address (Tailscale/LAN)")
        }
        return base
    }

    private fun get(base: String, pathAndQuery: String): String {
        val url = base + pathAndQuery
        val builder = Request.Builder().url(url).get()
        auth.headers().forEach { (k, v) -> builder.header(k, v) }
        return execute(builder.build())
    }

    private fun post(base: String, path: String, json: String): String {
        val builder = Request.Builder()
            .url(base + path)
            .post(json.toRequestBody(JSON))
        auth.headers().forEach { (k, v) -> builder.header(k, v) }
        return execute(builder.build())
    }

    private fun execute(request: Request): String {
        client.newCall(request).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw WebV1Errors.fromHttp(resp.code, body)
            }
            return body
        }
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}
