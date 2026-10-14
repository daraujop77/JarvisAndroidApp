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
    if (task.agent_run_state == "WAITING_CONFIRMATION" ||
        (task.state == "READY" && task.requires_confirmation)
    ) return false
    if (task.agent_run_state in active || task.state in active) return true
    // An accepted, owner-scoped worker can outlive the agent's final reply.
    // Poll its EXISTING job only, including after a PARTIAL/FAILED text run;
    // never resubmit a provider operation or treat acceptance as completion.
    if (task.live_progress.any {
            it.status in setOf(
                "QUEUED", "RUNNING", "WORKING", "PENDING", "PENDING_CANON_DIFF",
            )
        }) return true
    return false
}


/** The server is the source of a missing chat bubble after process death.
 * A latest-task response is NOT authority to show another project's transcript.
 * Recovery never invokes a model, provider, or visual generation.
 */
internal fun canRebuildCopilotTurn(
    projectId: String, original: CopilotTaskState, recovered: CopilotTaskState,
): Boolean = projectId.isNotBlank() &&
    original.project_id == projectId && recovered.project_id == projectId &&
    original.task_id.isNotBlank() && original.task_id == recovered.task_id &&
    original.request_id.isNotBlank() &&
    original.request_id == recovered.request_id &&
    original.conversation_id.isNotBlank() &&
    original.conversation_id == recovered.conversation_id &&
    recovered.request_text.isNotBlank() &&
    recovered.reply_text.isNotBlank() &&
    recovered.agent_run_state !in setOf("RUNNING", "DISPATCHING", "NEW")


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
    // An image receipt is independent of the model's final text/run state.
    // Even after an uncertain final turn, a hash-bound candidate belonging to
    // this exact task can be inspected without approving or regenerating it.
    if (projectId.isBlank() || task.project_id != projectId ||
        task.task_id.isBlank()
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
        status !in setOf("CANDIDATE", "APPROVED") ||
        storage !in setOf("stored", "pending")
    ) return null
    return CopilotGeneratedMaster(id, sha, character, status, storage)
}
