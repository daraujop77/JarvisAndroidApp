package com.jarvis.android.data.repo

/**
 * Lane B: bounded reconnection policy, extracted so the two network-hardening
 * invariants are directly testable:
 *  1. Backoff is bounded — retries stop after [MAX_ATTEMPTS], never a storm.
 *  2. Fail-closed phases (revoked / protocol mismatch / auth expiry) never
 *     auto-retry; only an explicit user action (re-pair, manual reconnect) or a
 *     foreground resume of a *recoverable* disconnect may reconnect.
 *
 * A successful/usable connection resets the attempt counter to zero.
 */
object ReconnectPolicy {
    val BACKOFF_MS = listOf(500L, 1_000L, 2_000L, 5_000L, 10_000L)
    const val MAX_ATTEMPTS = 5

    /** Delay before attempt [attempts] (0-based). Null once the budget is spent. */
    fun nextDelayMs(attempts: Int, caps: List<Long> = BACKOFF_MS): Long? = caps.getOrNull(attempts)

    /** Terminal server states that must not silently retry. */
    fun isFailClosed(phase: SessionPhase): Boolean = when (phase) {
        SessionPhase.REVOKED, SessionPhase.MISMATCH, SessionPhase.AUTH_EXPIRED -> true
        else -> false
    }

    /** Whether another reconnect attempt is allowed given phase + attempts. */
    fun shouldRetry(phase: SessionPhase, attempts: Int, caps: List<Long> = BACKOFF_MS): Boolean =
        !isFailClosed(phase) && attempts < caps.size
}
