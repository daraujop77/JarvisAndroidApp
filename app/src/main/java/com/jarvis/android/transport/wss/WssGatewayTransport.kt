package com.jarvis.android.transport.wss

import android.content.Context
import com.jarvis.android.contract.HealthResponse
import com.jarvis.android.contract.JarvisJson
import com.jarvis.android.contract.MobileRequest
import com.jarvis.android.data.prefs.SettingsStore
import com.jarvis.android.transport.GatewayTransport
import com.jarvis.android.transport.LinkState
import com.jarvis.android.transport.TransportException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Production-shaped WSS/HTTPS transport adapter (plan AND-W4 skeleton).
 *
 * PCB-R3: Web V1 production path is HTTP (`HttpGatewayTransport`). This WSS
 * adapter stays behind [GatewayTransport] for a future PC-A socket, but
 * `/mobile/events` is **not** a production requirement. Auth remains fail-closed.
 */
class WssGatewayTransport(
    private val context: Context,
    private val scope: CoroutineScope,
) : GatewayTransport {

    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // WebSocket: no read timeout
        .build()

    private val _frames = Channel<String>(Channel.UNLIMITED)
    override val frames: Flow<String> = _frames.receiveAsFlow()

    private val _linkState = MutableStateFlow(LinkState.IDLE)
    override val linkState: StateFlow<LinkState> = _linkState.asStateFlow()

    @Volatile
    private var socket: WebSocket? = null

    override suspend fun connect() {
        if (_linkState.value == LinkState.CONNECTED || _linkState.value == LinkState.CONNECTING) return
        _linkState.value = LinkState.CONNECTING
        val base = baseUrl()
        if (base.isBlank()) {
            _linkState.value = LinkState.FAILED
            throw TransportException("gateway base URL not configured")
        }
        val wsUrl = base.replace("http", "ws").trimEnd('/') + "/mobile/events"
        val request = Request.Builder()
            .url(wsUrl)
            .apply { authHeaders()?.let { (k, v) -> header(k, v) } }
            .build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _linkState.value = LinkState.CONNECTED
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                _frames.trySend(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _linkState.value = LinkState.RECONNECTING
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                if (_linkState.value != LinkState.RECONNECTING) _linkState.value = LinkState.CLOSED
            }
        })
    }

    override suspend fun disconnect() {
        socket?.close(1000, "client disconnect")
        socket = null
        _linkState.value = LinkState.CLOSED
    }

    override suspend fun send(request: MobileRequest) {
        val ws = socket ?: throw TransportException("not connected")
        val json = JarvisJson.default.encodeToString(MobileRequest.serializer(), request)
        val sent = ws.send(json)
        if (!sent) throw TransportException("socket buffer full or closed")
    }

    override suspend fun health(): HealthResponse {
        val base = baseUrl()
        if (base.isBlank()) throw TransportException("gateway base URL not configured")
        val url = base.trimEnd('/') + "/health"
        return suspendCancellableCoroutine { cont ->
            val httpRequest = Request.Builder().url(url).apply {
                authHeaders()?.let { (k, v) -> header(k, v) }
            }.build()
            val call = client.newCall(httpRequest)
            cont.invokeOnCancellation { call.cancel() }
            scope.launch {
                try {
                    call.execute().use { resp ->
                        if (!resp.isSuccessful) {
                            cont.resumeWithException(IOException("health ${resp.code}"))
                        } else {
                            val body = resp.body?.string().orEmpty()
                            cont.resume(JarvisJson.default.decodeFromString(HealthResponse.serializer(), body))
                        }
                    }
                } catch (t: Throwable) {
                    cont.resumeWithException(t)
                }
            }
        }
    }

    private suspend fun baseUrl(): String = context.let { SettingsStore(it).settings.first().gatewayBaseUrl }

    /**
     * AND-W3 will replace this with Keystore-backed device identity + pairing
     * session credentials. Until the pairing contract freezes, no credential is
     * attached (fail closed server-side).
     */
    private fun authHeaders(): Pair<String, String>? = null
}
