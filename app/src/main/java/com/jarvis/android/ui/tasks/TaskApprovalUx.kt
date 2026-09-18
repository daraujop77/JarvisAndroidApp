package com.jarvis.android.ui.tasks

import com.jarvis.android.contract.ApprovalOutcome
import com.jarvis.android.contract.ApprovalTier
import com.jarvis.android.contract.TaskStatus
import com.jarvis.android.data.state.ApprovalUiState
import com.jarvis.android.data.state.TaskUiState
import java.time.Instant
import kotlin.math.max

/**
 * Pure A4 presentation rules. Uses existing session/contract fields only.
 * A local tap is never treated as the server outcome.
 */
object TaskApprovalUx {

    enum class TaskGroup { RUNNING, COMPLETED, FAILED, CANCELLED }

    /**
     * REQUESTED: waiting for an owner action.
     * RESOLVING: local tap in flight — not authoritative.
     * EXPIRED_LOCAL: device clock passed expiresAt while the server has not
     * resolved yet. Still not a server outcome.
     * SERVER: [ApprovalUiState.outcome] from the server event.
     */
    enum class ApprovalPhase { REQUESTED, RESOLVING, EXPIRED_LOCAL, SERVER }

    val sectionOrder: List<TaskGroup> = listOf(
        TaskGroup.RUNNING,
        TaskGroup.COMPLETED,
        TaskGroup.FAILED,
        TaskGroup.CANCELLED,
    )

    fun groupOf(status: TaskStatus): TaskGroup = when (status) {
        TaskStatus.QUEUED, TaskStatus.STARTED, TaskStatus.RUNNING -> TaskGroup.RUNNING
        TaskStatus.COMPLETED -> TaskGroup.COMPLETED
        TaskStatus.FAILED -> TaskGroup.FAILED
        TaskStatus.CANCELLED -> TaskGroup.CANCELLED
    }

    fun grouped(tasks: Collection<TaskUiState>): Map<TaskGroup, List<TaskUiState>> {
        val buckets = sectionOrder.associateWith { mutableListOf<TaskUiState>() }
        tasks.sortedByDescending { it.updatedAtMs }.forEach { task ->
            buckets.getValue(groupOf(task.status)).add(task)
        }
        return buckets
    }

    /**
     * Progress shown after process recreation must start from the stored
     * value, never 0, and must not run backwards while the task is active.
     */
    fun stableProgress(previousShown: Float?, incoming: Float?, status: TaskStatus): Float? {
        if (status == TaskStatus.COMPLETED) return 1f
        val next = incoming?.coerceIn(0f, 1f)
        val held = next ?: previousShown
        if (status == TaskStatus.FAILED || status == TaskStatus.CANCELLED) return held
        if (held == null) return null
        val floor = previousShown?.coerceIn(0f, 1f)
        return if (floor == null) held else max(floor, held)
    }

    fun sanitizedId(id: String?): String {
        val trimmed = id?.trim().orEmpty()
        if (trimmed.isEmpty()) return "—"
        if (trimmed.any { it.isWhitespace() } || trimmed.contains('{') || trimmed.contains('"')) {
            return "opaque"
        }
        return if (trimmed.length <= 16) trimmed else trimmed.take(12) + "…"
    }

    fun formatTimestamp(epochMs: Long): String = Instant.ofEpochMilli(epochMs).toString()

    fun remainingSeconds(expiresAtMs: Long?, nowMs: Long): Long? {
        if (expiresAtMs == null) return null
        return ((expiresAtMs - nowMs) / 1000L).coerceAtLeast(0L)
    }

    fun locallyExpired(approval: ApprovalUiState, nowMs: Long): Boolean =
        approval.outcome == null &&
            !approval.resolutionInFlight &&
            approval.expiresAtMs != null &&
            approval.expiresAtMs <= nowMs

    fun phase(approval: ApprovalUiState, nowMs: Long): ApprovalPhase = when {
        approval.resolutionInFlight -> ApprovalPhase.RESOLVING
        approval.outcome != null -> ApprovalPhase.SERVER
        locallyExpired(approval, nowMs) -> ApprovalPhase.EXPIRED_LOCAL
        else -> ApprovalPhase.REQUESTED
    }

    /** Only REQUESTED cards may send a resolve command. */
    fun canSubmit(approval: ApprovalUiState, nowMs: Long): Boolean =
        phase(approval, nowMs) == ApprovalPhase.REQUESTED

    fun visibleApprovals(
        isOwner: Boolean,
        approvals: Collection<ApprovalUiState>,
        nowMs: Long,
    ): List<ApprovalUiState> {
        if (!isOwner) return emptyList()
        return approvals.sortedWith(
            compareBy<ApprovalUiState> { phaseRank(phase(it, nowMs)) }
                .thenByDescending { it.expiresAtMs ?: it.resolvedAtMs ?: 0L },
        )
    }

    fun groupedApprovals(
        isOwner: Boolean,
        approvals: Collection<ApprovalUiState>,
        nowMs: Long,
    ): Map<ApprovalPhase, List<ApprovalUiState>> {
        if (!isOwner) return emptyMap()
        return approvals.groupBy { phase(it, nowMs) }.mapValues { (_, rows) ->
            rows.sortedByDescending { it.expiresAtMs ?: it.resolvedAtMs ?: 0L }
        }
    }

    fun requiresBiometric(tier: ApprovalTier): Boolean =
        tier == ApprovalTier.SENSITIVE || tier == ApprovalTier.CRITICAL

    fun phaseLabel(approval: ApprovalUiState, nowMs: Long): String = when (phase(approval, nowMs)) {
        ApprovalPhase.REQUESTED -> "REQUESTED"
        ApprovalPhase.RESOLVING -> "RESOLVING — waiting for server"
        ApprovalPhase.EXPIRED_LOCAL -> "EXPIRED ON THIS DEVICE CLOCK"
        ApprovalPhase.SERVER -> when (approval.outcome) {
            ApprovalOutcome.APPROVED -> "SERVER · APPROVED"
            ApprovalOutcome.DENIED -> "SERVER · DENIED"
            ApprovalOutcome.EXPIRED -> "SERVER · EXPIRED"
            ApprovalOutcome.CANCELLED -> "SERVER · CANCELLED"
            null -> "SERVER"
        }
    }

    private fun phaseRank(phase: ApprovalPhase): Int = when (phase) {
        ApprovalPhase.REQUESTED -> 0
        ApprovalPhase.RESOLVING -> 1
        ApprovalPhase.EXPIRED_LOCAL -> 2
        ApprovalPhase.SERVER -> 3
    }
}
