package com.jarvis.android.transport

/**
 * Injectable authenticated-request/header provider (PCB-R3).
 *
 * Real token / device binding waits for PC-A. Until then implementations must
 * fail closed: return empty headers rather than inventing credentials.
 */
fun interface AuthProvider {
    /** Header name → value pairs attached to HTTP calls. Empty = unauthenticated. */
    fun headers(): Map<String, String>
}

object NoAuthProvider : AuthProvider {
    override fun headers(): Map<String, String> = emptyMap()
}
