package com.jarvis.android.ui.writing

import com.jarvis.android.transport.live.CopilotApprovedVisualAsset
import com.jarvis.android.transport.live.CopilotTaskState
import kotlinx.serialization.json.JsonPrimitive


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
            "FAILED", "CANCELED", "PARTIAL",
        ) || task.state in setOf(
            "READY", "BLOCKED", "OUTCOME_UNKNOWN",
            "CANCEL_REQUESTED", "CANCELED",
        )
    ) return false
    // A legacy task may already be DONE while its asynchronous visual job
    // continues. Preserve polling for its pending live_progress entries.
    return task.live_progress.any {
        it.status in setOf(
            "QUEUED", "RUNNING", "WORKING", "PENDING", "PENDING_CANON_DIFF",
        )
    }
}


/** A completed character-master draft is an unapproved visual candidate.
 * Use only the immutable ID/hash from this exact project and task. This
 * projection NEVER starts a new paid provider operation.
 */
internal data class CopilotGeneratedMaster(
    val assetId: String,
    val sha256: String,
    val characterId: String,
    val status: String,
    val storageState: String,
)

internal fun completedCopilotGeneratedMaster(
    projectId: String, task: CopilotTaskState,
): CopilotGeneratedMaster? {
    if (projectId.isBlank() || task.project_id != projectId ||
        task.task_id.isBlank() || task.agent_run_state in
        setOf("OUTCOME_UNKNOWN", "BLOCKED", "FAILED", "RUNNING", "DISPATCHING") ||
        task.state in setOf("OUTCOME_UNKNOWN", "BLOCKED", "RUNNING", "READY")
    ) return null
    val results = task.results.filter { it.tool == "generate_character_master" }
    if (results.size != 1) return null
    val data = results.single().result
    fun field(name: String): String = (data[name] as? JsonPrimitive)?.content.orEmpty()
    val id = field("asset_id")
    val sha = field("sha256")
    val character = field("character_id")
    val status = field("status")
    val storage = field("storage_state")
    if (!Regex("^va_[A-Za-z0-9_]{6,80}$").matches(id) ||
        !Regex("^[a-fA-F0-9]{64}$").matches(sha) ||
        !character.startsWith("character:") ||
        status !in setOf("CANDIDATE", "APPROVED") || storage != "stored"
    ) return null
    return CopilotGeneratedMaster(id, sha, character, status, storage)
}
