package com.jarvis.android.transport

import com.jarvis.android.contract.HealthResponse
import com.jarvis.android.contract.MobileRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Transport-agnostic Gateway connection (plan §23 PrivateLinkProvider abstraction).
 *
 * The app never talks to Tailscale/LAN/WSS/fixture directly — it talks to this
 * interface, so changing transport must not change conversation/request semantics.
 */
interface GatewayTransport {

    /** Raw event frames (JSON strings) as they arrive. Replay/dup semantics live in the reducer. */
    val frames: Flow<String>

    /** Transport lifecycle (independent of server-reported connection.state). */
    val linkState: StateFlow<LinkState>

    /** Establish the underlying link. Idempotent when already connected. */
    suspend fun connect()

    suspend fun disconnect()

    /** Send a mobile request. Throws [TransportException] when the link is down. */
    suspend fun send(request: MobileRequest)

    suspend fun health(): HealthResponse
}

enum class LinkState {
    IDLE,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    CLOSED,
    FAILED,
}

class TransportException(message: String, cause: Throwable? = null) : Exception(message, cause)
