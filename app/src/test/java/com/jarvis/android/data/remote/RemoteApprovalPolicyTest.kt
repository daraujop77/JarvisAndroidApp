package com.jarvis.android.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** POST_BASELINE_FUTURE_WORK. Local policy only — no wire contract. */
class RemoteApprovalPolicyTest {

    private val now = 1_000_000L

    private fun challenge(
        expiresAtMs: Long = now + 30_000,
        screenshot: Boolean = true,
        valid: Boolean = true,
        actionId: String = "act-1",
        capturedAtMs: Long = now - 1_000,
    ) = RemoteApprovalChallenge(
        actionId = actionId,
        operation = "click",
        targetIdentity = "desktop-l59hjk4",
        capturedAtMs = capturedAtMs,
        expiresAtMs = expiresAtMs,
        screenshotPresent = screenshot,
        metadataValid = valid,
    )

    private fun pending(c: RemoteApprovalChallenge? = challenge(), linkDown: Boolean = false) =
        RemoteApprovalUiModel(c, RemoteApprovalPhase.PENDING, linkDown = linkDown)

    @Test
    fun approveOnceThenReplayIsConsumed() {
        val first = RemoteApprovalPolicy.approveOnce(pending(), now)
        assertEquals(RemoteApprovalPhase.APPROVED, first.phase)
        assertTrue(first.decisionConsumed)
        assertEquals(RemoteApprovalPhase.CONSUMED, RemoteApprovalPolicy.displayedPhase(first, now))
        val second = RemoteApprovalPolicy.approveOnce(first, now)
        assertEquals(first, second)
        assertFalse(RemoteApprovalPolicy.canApproveOnce(first, now))
        assertFalse(RemoteApprovalPolicy.canDeny(first, now))
    }

    @Test
    fun denyIsOneShot() {
        val denied = RemoteApprovalPolicy.deny(pending(), now)
        assertEquals(RemoteApprovalPhase.DENIED, denied.phase)
        assertEquals(denied, RemoteApprovalPolicy.deny(denied, now))
        assertEquals(RemoteApprovalPhase.CONSUMED, RemoteApprovalPolicy.displayedPhase(denied, now))
    }

    @Test
    fun expiryDisablesApprove() {
        val model = pending(challenge(expiresAtMs = now))
        assertEquals(RemoteApprovalPhase.EXPIRED, RemoteApprovalPolicy.displayedPhase(model, now))
        assertFalse(RemoteApprovalPolicy.canApproveOnce(model, now))
        assertEquals(model, RemoteApprovalPolicy.approveOnce(model, now))
        assertEquals(0L, RemoteApprovalPolicy.remainingMs(model, now))
    }

    @Test
    fun disconnectDisablesApprove() {
        val model = pending(linkDown = true)
        assertEquals(RemoteApprovalPhase.DISCONNECTED, RemoteApprovalPolicy.displayedPhase(model, now))
        assertFalse(RemoteApprovalPolicy.canApproveOnce(model, now))
        assertFalse(RemoteApprovalPolicy.canDeny(model, now))
    }

    @Test
    fun staleChallengeIsError() {
        val stale = pending(challenge(capturedAtMs = now, expiresAtMs = now))
        assertEquals(RemoteApprovalPhase.ERROR, RemoteApprovalPolicy.displayedPhase(stale, now - 1))
        assertFalse(RemoteApprovalPolicy.canApproveOnce(stale, now - 1))
    }

    @Test
    fun missingScreenshotAndMalformedMetadataFailClosed() {
        assertEquals(
            RemoteApprovalPhase.ERROR,
            RemoteApprovalPolicy.displayedPhase(pending(challenge(screenshot = false)), now),
        )
        assertEquals(
            RemoteApprovalPhase.ERROR,
            RemoteApprovalPolicy.displayedPhase(pending(challenge(valid = false)), now),
        )
        assertEquals(
            RemoteApprovalPhase.ERROR,
            RemoteApprovalPolicy.displayedPhase(pending(c = null), now),
        )
    }

    @Test
    fun emergencyStopIsIdempotentAndRejectsBlankAction() = kotlinx.coroutines.runBlocking {
        val stop = FakeRemoteEmergencyStop()
        assertEquals(RemoteStopResult.ACCEPTED, stop.requestStop("act-1"))
        assertEquals(RemoteStopResult.ALREADY_STOPPED, stop.requestStop("act-1"))
        assertEquals(RemoteStopResult.REJECTED, stop.requestStop(""))
        assertEquals(listOf("act-1", "act-1", ""), stop.calls)
    }
}
