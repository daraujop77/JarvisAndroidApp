package com.jarvis.android.ui.format

import com.jarvis.android.data.state.ConnectionState

/**
 * Wording for the connection strip. Split out of the composable so the copy is
 * covered by a plain JVM test. Presentation only — the state machine decides
 * the state, this only decides the words.
 */
object ConnectionCopy {

    fun label(state: ConnectionState): String = when (state) {
        ConnectionState.ONLINE -> "ONLINE"
        ConnectionState.DEGRADED -> "DEGRADED"
        ConnectionState.CONNECTING -> "CONNECTING"
        ConnectionState.RECONNECTING -> "RECONNECTING"
        ConnectionState.OFFLINE -> "OFFLINE"
        ConnectionState.DISCONNECTED -> "DISCONNECTED"
        ConnectionState.AUTH_EXPIRED -> "AUTH REQUIRED"
        ConnectionState.DEVICE_REVOKED -> "DEVICE REVOKED"
        ConnectionState.PROTOCOL_MISMATCH -> "UPDATE REQUIRED"
    }

    /**
     * One plain sentence for states that need an explanation. Null when the
     * label alone is enough, so the strip stays a single line while healthy.
     */
    fun detail(state: ConnectionState): String? = when (state) {
        ConnectionState.DEGRADED -> "Connected, but replies may be delayed"
        ConnectionState.RECONNECTING -> "Connection dropped — trying again"
        ConnectionState.OFFLINE -> "No link to the Jarvis PC"
        ConnectionState.DISCONNECTED -> "Not connected yet"
        ConnectionState.AUTH_EXPIRED -> "Session expired — pair again to continue"
        ConnectionState.DEVICE_REVOKED -> "This device was revoked on the Jarvis PC"
        ConnectionState.PROTOCOL_MISMATCH -> "This app is out of date — update, then pair again"
        else -> null
    }
}
