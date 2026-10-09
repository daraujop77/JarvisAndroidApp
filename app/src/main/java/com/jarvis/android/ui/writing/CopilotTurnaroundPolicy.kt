package com.jarvis.android.ui.writing

import com.jarvis.android.transport.live.VisualCharacterBatch

/** Same six independently rendered views as Character Studio; front is the approved master. */
internal val COPILOT_TURNAROUND_PERSPECTIVES = listOf(
    "left_three_quarter",
    "left_profile",
    "right_three_quarter",
    "right_profile",
    "back",
    "full_body",
)

/**
 * A batch may be approved from chat only after every candidate has verified image bytes
 * on this device. The server still checks ownership, batch identity and exact assets.
 */
internal fun copilotApprovableViewIds(
    batch: VisualCharacterBatch?,
    verifiedPreviews: Map<String, String>,
): List<String> {
    if (batch == null || batch.status != "READY_FOR_REVIEW" ||
        batch.pending_count != 0 || batch.blocked_count != 0 ||
        !batch.character_id.startsWith("character:")
    ) return emptyList()
    val candidates = batch.items.filter {
        it.status == "CANDIDATE" || it.status == "CANDIDATE_EXISTING"
    }
    if (candidates.isEmpty() || candidates.map { it.candidate_asset_id }.distinct().size != candidates.size) {
        return emptyList()
    }
    if (candidates.any {
        !it.candidate_asset_id.startsWith("va_") ||
            !Regex("^[0-9A-Fa-f]{64}$").matches(it.candidate_sha256) ||
            verifiedPreviews[it.candidate_asset_id].isNullOrBlank()
    }) return emptyList()
    return candidates.map { it.candidate_asset_id }
}

internal fun copilotBatchCanStartNew(batch: VisualCharacterBatch?): Boolean =
    batch == null || batch.status in setOf("FAILED", "CANCELLED")
