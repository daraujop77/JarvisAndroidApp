package com.jarvis.android.ui.writing

import com.jarvis.android.transport.live.CopilotVisualApproval
import com.jarvis.android.transport.live.VisualStudioAsset

/**
 * An approval button needs the preview to have decoded on screen. A server status
 * or attachment ID alone does not prove the owner can see the image.
 */
internal fun canApproveVisualPreview(
    storageState: String,
    previewDecoded: Boolean,
    requiresStored: Boolean = true,
): Boolean = previewDecoded && (!requiresStored || storageState == "stored")

private val exactVisualShaPattern = Regex("^[a-fA-F0-9]{64}$")
private val exactVisualAssetPattern = Regex("^va_[A-Za-z0-9_]{6,80}$")

/**
 * The Android action must be bound to the CURRENT project's CURRENT
 * character candidate. The VPS independently reauthorizes the owner and SHA;
 * a stale/cross-project card must not even send an approval request.
 *
 * [localAttachmentId] proves only that bytes were staged in this account's
 * private cache. The Compose button separately requires a decoded preview.
 */
internal fun canSubmitCharacterVisualApproval(
    projectId: String,
    characterId: String,
    candidate: VisualStudioAsset,
    listedAssets: List<VisualStudioAsset>,
    localAttachmentId: String?,
): Boolean =
    projectId.isNotBlank() && characterId.startsWith("character:") &&
    exactVisualAssetPattern.matches(candidate.asset_id) &&
    exactVisualShaPattern.matches(candidate.sha256) &&
    candidate.project_id == projectId &&
    candidate.status == "CANDIDATE" &&
    candidate.storage.state == "stored" &&
    characterId in candidate.character_ids &&
    !localAttachmentId.isNullOrBlank() &&
    listedAssets.singleOrNull { it.asset_id == candidate.asset_id } == candidate

/**
 * Never show an approval success or reload the approved gallery on a
 * mismatched/partial backend response. A network timeout may leave an
 * UNKNOWN result; the owner must reconcile the exact asset, not blindly retry.
 */
internal fun isMatchingCharacterVisualApprovalReceipt(
    projectId: String,
    characterId: String,
    candidate: VisualStudioAsset,
    expectedRevision: Int,
    approved: VisualStudioAsset,
): Boolean =
    expectedRevision > 0 &&
    approved.asset_id == candidate.asset_id &&
    approved.project_id == projectId &&
    candidate.project_id == projectId &&
    approved.sha256.equals(candidate.sha256, ignoreCase = true) &&
    exactVisualShaPattern.matches(approved.sha256) &&
    approved.kind == candidate.kind &&
    approved.perspective == candidate.perspective &&
    approved.visual_revision == expectedRevision &&
    approved.status == "APPROVED" &&
    approved.storage.state == "stored" &&
    characterId in approved.character_ids

/** Ensure Wiki linkage and the *same* character/bytes were confirmed by the
 * authenticated primary-master endpoint, never trust status text alone. */
internal fun isMatchingCopilotPortraitApprovalReceipt(
    projectId: String,
    characterId: String,
    assetId: String,
    expectedSha256: String,
    result: CopilotVisualApproval,
): Boolean {
    val approved = result.asset
    return projectId.isNotBlank() &&
        result.schema == "jarvis.visual-studio.character-primary-approval.v1" &&
        result.project_id == projectId &&
        result.character_id == characterId &&
        result.wiki_link?.entry_id == characterId &&
        exactVisualAssetPattern.matches(assetId) &&
        exactVisualShaPattern.matches(expectedSha256) &&
        approved.project_id == projectId &&
        approved.asset_id == assetId &&
        approved.sha256.equals(expectedSha256, ignoreCase = true) &&
        approved.status == "APPROVED" &&
        approved.storage.state == "stored" &&
        approved.kind == "PRIMARY_REFERENCE" &&
        approved.perspective == "front" &&
        approved.character_ids == listOf(characterId)
}
