package com.jarvis.android.ui.tasks

import com.jarvis.android.contract.ApprovalOutcome
import com.jarvis.android.contract.ApprovalTier
import com.jarvis.android.contract.TaskStatus
import com.jarvis.android.data.state.ApprovalUiState
import com.jarvis.android.data.state.TaskUiState
import com.jarvis.android.ui.screens.TaskApprovalA11y
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskApprovalUxTest {

    @Test
    fun tasksGroupByRunningCompletedFailedCancelled() {
        val tasks = listOf(
            task("run", TaskStatus.RUNNING, 0.4f, 40),
            task("queued", TaskStatus.QUEUED, null, 10),
            task("started", TaskStatus.STARTED, 0.1f, 30),
            task("done", TaskStatus.COMPLETED, 1f, 20),
            task("fail", TaskStatus.FAILED, 0.7f, 15),
            task("cancel", TaskStatus.CANCELLED, 0.2f, 5),
        )
        val grouped = TaskApprovalUx.grouped(tasks)
        assertEquals(listOf("run", "started", "queued"), grouped.getValue(TaskApprovalUx.TaskGroup.RUNNING).map { it.taskId })
        assertEquals(listOf("done"), grouped.getValue(TaskApprovalUx.TaskGroup.COMPLETED).map { it.taskId })
        assertEquals(listOf("fail"), grouped.getValue(TaskApprovalUx.TaskGroup.FAILED).map { it.taskId })
        assertEquals(listOf("cancel"), grouped.getValue(TaskApprovalUx.TaskGroup.CANCELLED).map { it.taskId })
        assertEquals(
            TaskApprovalUx.sectionOrder,
            listOf(
                TaskApprovalUx.TaskGroup.RUNNING,
                TaskApprovalUx.TaskGroup.COMPLETED,
                TaskApprovalUx.TaskGroup.FAILED,
                TaskApprovalUx.TaskGroup.CANCELLED,
            ),
        )
    }

    @Test
    fun progressSurvivesProcessRecreationAndDoesNotRunBackwards() {
        assertEquals(0.42f, TaskApprovalUx.stableProgress(null, 0.42f, TaskStatus.RUNNING))
        assertEquals(0.42f, TaskApprovalUx.stableProgress(0.42f, 0.42f, TaskStatus.RUNNING))
        assertEquals(0.5f, TaskApprovalUx.stableProgress(0.4f, 0.5f, TaskStatus.RUNNING))
        assertEquals(0.4f, TaskApprovalUx.stableProgress(0.4f, 0.2f, TaskStatus.RUNNING))
        assertEquals(1f, TaskApprovalUx.stableProgress(0.4f, 0.9f, TaskStatus.COMPLETED))
        assertEquals(0.7f, TaskApprovalUx.stableProgress(0.7f, null, TaskStatus.FAILED))
        assertNull(TaskApprovalUx.stableProgress(null, null, TaskStatus.QUEUED))
    }

    @Test
    fun taskIdentifiersAreSanitized() {
        assertEquals("task_1", TaskApprovalUx.sanitizedId("task_1"))
        assertEquals("very-long-id…", TaskApprovalUx.sanitizedId("very-long-id-that-must-truncate"))
        assertEquals("opaque", TaskApprovalUx.sanitizedId("""{"taskId":"secret"}"""))
        assertEquals("opaque", TaskApprovalUx.sanitizedId("id with space"))
        assertEquals("—", TaskApprovalUx.sanitizedId(null))
        assertEquals("—", TaskApprovalUx.sanitizedId("  "))
        assertTrue(TaskApprovalUx.formatTimestamp(1_700_000_000_000L).contains("2023"))
    }

    @Test
    fun expiryCountdownNeverGoesNegativeAndLocalExpiryIsNotServerOutcome() {
        val now = 1_000_000L
        assertEquals(5L, TaskApprovalUx.remainingSeconds(now + 5_500L, now))
        assertEquals(0L, TaskApprovalUx.remainingSeconds(now - 1L, now))
        assertNull(TaskApprovalUx.remainingSeconds(null, now))
        val pending = approval(expiresAtMs = now - 1)
        assertEquals(TaskApprovalUx.ApprovalPhase.EXPIRED_LOCAL, TaskApprovalUx.phase(pending, now))
        assertFalse(TaskApprovalUx.canSubmit(pending, now))
        assertTrue(TaskApprovalUx.phaseLabel(pending, now).contains("DEVICE CLOCK"))
        assertNull(pending.outcome)
    }

    @Test
    fun resolvingIsNotAuthoritativeAndDuplicateTapIsBlocked() {
        val now = 50L
        val requested = approval()
        assertEquals(TaskApprovalUx.ApprovalPhase.REQUESTED, TaskApprovalUx.phase(requested, now))
        assertTrue(TaskApprovalUx.canSubmit(requested, now))
        val resolving = requested.copy(resolutionInFlight = true, outcome = ApprovalOutcome.APPROVED)
        assertEquals(TaskApprovalUx.ApprovalPhase.RESOLVING, TaskApprovalUx.phase(resolving, now))
        assertFalse(TaskApprovalUx.canSubmit(resolving, now))
        assertTrue(TaskApprovalUx.phaseLabel(resolving, now).contains("waiting for server"))
        val server = requested.copy(outcome = ApprovalOutcome.DENIED, resolutionInFlight = false, resolvedAtMs = now)
        assertEquals(TaskApprovalUx.ApprovalPhase.SERVER, TaskApprovalUx.phase(server, now))
        assertFalse(TaskApprovalUx.canSubmit(server, now))
        assertEquals("SERVER · DENIED", TaskApprovalUx.phaseLabel(server, now))
        val expiredServer = requested.copy(outcome = ApprovalOutcome.EXPIRED, resolutionInFlight = false)
        assertEquals("SERVER · EXPIRED", TaskApprovalUx.phaseLabel(expiredServer, now))
    }

    @Test
    fun ownerSeesApprovalsGuestsDoNot() {
        val now = 10L
        val rows = listOf(approval("a1"), approval("a2", outcome = ApprovalOutcome.APPROVED, inFlight = false))
        assertTrue(TaskApprovalUx.visibleApprovals(isOwner = false, rows, now).isEmpty())
        assertTrue(TaskApprovalUx.groupedApprovals(isOwner = false, rows, now).isEmpty())
        val owner = TaskApprovalUx.visibleApprovals(isOwner = true, rows, now)
        assertEquals(listOf("a1", "a2"), owner.map { it.approvalId })
        val grouped = TaskApprovalUx.groupedApprovals(true, rows, now)
        assertEquals(1, grouped.getValue(TaskApprovalUx.ApprovalPhase.REQUESTED).size)
        assertEquals(1, grouped.getValue(TaskApprovalUx.ApprovalPhase.SERVER).size)
    }

    @Test
    fun biometricGateMatchesSensitiveAndCriticalOnly() {
        assertFalse(TaskApprovalUx.requiresBiometric(ApprovalTier.LOW))
        assertFalse(TaskApprovalUx.requiresBiometric(ApprovalTier.NORMAL))
        assertTrue(TaskApprovalUx.requiresBiometric(ApprovalTier.SENSITIVE))
        assertTrue(TaskApprovalUx.requiresBiometric(ApprovalTier.CRITICAL))
        assertEquals(TaskApprovalUx.requiresBiometric(ApprovalTier.SENSITIVE), com.jarvis.android.ui.shared.requiresBiometric(ApprovalTier.SENSITIVE))
        assertEquals(TaskApprovalUx.requiresBiometric(ApprovalTier.NORMAL), com.jarvis.android.ui.shared.requiresBiometric(ApprovalTier.NORMAL))
    }

    @Test
    fun a11yCopyDistinguishesRequestedResolvingAndServer() {
        assertTrue(TaskApprovalA11y.OWNER_ONLY.contains("owner"))
        assertTrue(TaskApprovalA11y.RESOLVING_NOT_AUTHORITATIVE.contains("not the result"))
        assertTrue(TaskApprovalA11y.SERVER_OUTCOME.contains("server"))
        assertTrue(TaskApprovalA11y.EXPIRED_LOCAL.contains("Waiting for the server"))
        assertTrue(TaskApprovalA11y.DUPLICATE_TAP_BLOCKED.contains("Already submitted"))
        assertTrue(TaskApprovalA11y.BIOMETRIC_REQUIRED.contains("Not approved"))
        assertEquals("Expires in 4s on this device clock", TaskApprovalA11y.remaining(4))
        assertTrue(TaskApprovalA11y.taskRow("Compile", "RUNNING").contains("RUNNING"))
    }

    private fun task(id: String, status: TaskStatus, progress: Float?, updated: Long) =
        TaskUiState(id, "req_$id", status, progress, id, updated)

    private fun approval(
        id: String = "a1",
        outcome: ApprovalOutcome? = null,
        inFlight: Boolean = false,
        expiresAtMs: Long? = 1_000L,
        tier: ApprovalTier = ApprovalTier.NORMAL,
    ) = ApprovalUiState(
        approvalId = id,
        requestId = "r1",
        title = "Run",
        description = null,
        tier = tier,
        risk = null,
        expiresAtMs = expiresAtMs,
        outcome = outcome,
        resolutionInFlight = inFlight,
    )
}