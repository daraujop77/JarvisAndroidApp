package com.jarvis.android.ui.writing

import com.jarvis.android.transport.live.CopilotApprovedVisualAsset
import com.jarvis.android.transport.live.CopilotTaskState

/**
 * Returns only newly revealed, immutable APPROVED files for the exact task.
 *
 * The initial chat often contains no images when the backend is still
 * working. A later poll can reveal successful tool results. Polling must
 * fetch those new results without replaying already observed images,
 * crossing project/task scope, or promoting unapproved candidates.
 */
internal fun newApprovedCopilotTaskVisuals(
    projectId: String,
    previous: CopilotTaskState?,
    next: CopilotTaskState,
): List<CopilotApprovedVisualAsset> {
    if (next.project_id != projectId || next.task_id.isBlank()) return emptyList()
    val oldIds = if (
        previous != null &&
        previous.project_id == next.project_id &&
        previous.task_id == next.task_id
    ) {
        previous.results.asSequence()
            .flatMap { it.visual_assets.asSequence() }
            .filter { it.status == "APPROVED" && it.storage_state == "stored" }
            .map { it.asset_id }
            .toSet()
    } else emptySet()

    return next.results.asSequence()
        .flatMap { it.visual_assets.asSequence() }
        .filter {
            it.status == "APPROVED" &&
                it.storage_state == "stored" &&
                Regex("^va_[A-Za-z0-9_]{6,80}$").matches(it.asset_id) &&
                Regex("^[a-fA-F0-9]{64}$").matches(it.sha256)
        }
        .distinctBy { it.asset_id }
        .filterNot { it.asset_id in oldIds }
        .take(6)
        .toList()
}

/**
 * The adaptive agent has an authoritative run state even without legacy
 * worker progress rows. Do not end polling merely because live_progress
 * is empty while the model is still reasoning or dispatching tools.
 *
 * Never poll after WAITING_CONFIRMATION: it requires the user's explicit
 * action, not an automatic retry. Failures/unknown outcomes also stop.
 */
internal fun copilotTaskHasActiveRemoteWork(task: CopilotTaskState): Boolean {
    val active = setOf("RUNNING", "DISPATCHING")
    if (task.agent_run_state in active || task.state in active) return true
    if (task.agent_run_state in setOf(
            "WAITING_CONFIRMATION", "OUTCOME_UNKNOWN", "CANCEL_REQUESTED",
            "FAILED", "CANCELED", "SUCCEEDED", "PARTIAL",
        ) || task.state in setOf(
            "READY", "DONE", "BLOCKED", "OUTCOME_UNKNOWN",
            "CANCEL_REQUESTED", "CANCELED",
        )
    ) return false
    return task.live_progress.any {
        it.status in setOf(
            "QUEUED", "RUNNING", "WORKING", "PENDING", "PENDING_CANON_DIFF",
        )
    }
}
