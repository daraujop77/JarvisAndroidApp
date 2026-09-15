package com.jarvis.android.data.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Lane B gate: the two network-hardening invariants the runbook demands —
 * bounded backoff (no retry storm) and fail-closed terminal phases.
 */
class ReconnectPolicyTest {

    @Test
    fun backoffIsStrictlyBounded() {
        assertEquals(500L, ReconnectPolicy.nextDelayMs(0))
        assertEquals(1_000L, ReconnectPolicy.nextDelayMs(1))
        assertEquals(2_000L, ReconnectPolicy.nextDelayMs(2))
        assertEquals(5_000L, ReconnectPolicy.nextDelayMs(3))
        assertEquals(10_000L, ReconnectPolicy.nextDelayMs(4))
        assertNull(ReconnectPolicy.nextDelayMs(5))
        assertNull(ReconnectPolicy.nextDelayMs(99))
        assertEquals(ReconnectPolicy.BACKOFF_MS.size, ReconnectPolicy.MAX_ATTEMPTS)
    }

    @Test
    fun retryStopsAfterMaxAttempts() {
        assertTrue(ReconnectPolicy.shouldRetry(SessionPhase.DISCONNECTED, ReconnectPolicy.MAX_ATTEMPTS - 1))
        assertFalse(ReconnectPolicy.shouldRetry(SessionPhase.DISCONNECTED, ReconnectPolicy.MAX_ATTEMPTS))
        assertFalse(ReconnectPolicy.shouldRetry(SessionPhase.DISCONNECTED, ReconnectPolicy.MAX_ATTEMPTS + 10))
    }

    @Test
    fun failClosedPhasesNeverAutoRetry() {
        for (p in listOf(SessionPhase.REVOKED, SessionPhase.MISMATCH, SessionPhase.AUTH_EXPIRED)) {
            assertTrue("$p must be fail-closed", ReconnectPolicy.isFailClosed(p))
            assertFalse("$p must never retry", ReconnectPolicy.shouldRetry(p, 0))
        }
    }

    @Test
    fun recoverablePhasesMayRetry() {
        assertFalse(ReconnectPolicy.isFailClosed(SessionPhase.DISCONNECTED))
        assertFalse(ReconnectPolicy.isFailClosed(SessionPhase.CONNECTING))
        assertTrue(ReconnectPolicy.shouldRetry(SessionPhase.DISCONNECTED, 0))
    }
}
