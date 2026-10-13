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
