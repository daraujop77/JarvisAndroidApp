package com.jarvis.android.ui.writing

import com.jarvis.android.transport.live.CopilotPortraitWikiLink
import com.jarvis.android.transport.live.CopilotVisualApproval
import com.jarvis.android.transport.live.CopilotVisualCandidate
import com.jarvis.android.transport.live.CopilotVisualStorage
import com.jarvis.android.transport.live.VisualStudioAsset
import com.jarvis.android.transport.live.VisualStudioStorage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualApprovalGateTest {
    private val project = "prj_approved_story"
    private val character = "character:alexander"
    private val assetId = "va_0123456789abcdef"
    private val sha = "a".repeat(64)

    private fun candidate() = VisualStudioAsset(
        project_id = project,
        asset_id = assetId,
        sha256 = sha,
        kind = "PRIMARY_REFERENCE",
        perspective = "front",
        status = "CANDIDATE",
        storage = VisualStudioStorage(state = "stored"),
        character_ids = listOf(character),
    )

    private fun primaryReceipt(): CopilotVisualApproval = CopilotVisualApproval(
        schema = "jarvis.visual-studio.character-primary-approval.v1",
        project_id = project,
        character_id = character,
        asset = CopilotVisualCandidate(
            asset_id = assetId, sha256 = sha, status = "APPROVED",
            kind = "PRIMARY_REFERENCE", perspective = "front",
            storage = CopilotVisualStorage(state = "stored"),
            character_ids = listOf(character),
        ),
        wiki_link = CopilotPortraitWikiLink(entry_id = character),
    )

    @Test fun decodedStoredPreviewIsApprovable() {
        assertTrue(canApproveVisualPreview("stored", previewDecoded = true))
    }

    @Test fun undecodedPreviewIsNotApprovableEvenWhenStored() {
        assertFalse(canApproveVisualPreview("stored", previewDecoded = false))
    }

    @Test fun decodedPreviewWithPendingStorageIsNotApprovableByDefault() {
        assertFalse(canApproveVisualPreview("pending", previewDecoded = true))
    }

    @Test fun decodedPreviewIsApprovableWhenStorageIsNotRequired() {
        assertTrue(canApproveVisualPreview("pending", previewDecoded = true, requiresStored = false))
    }

    @Test fun undecodedPreviewIsNotApprovableWhenStorageIsNotRequired() {
        assertFalse(canApproveVisualPreview("stored", previewDecoded = false, requiresStored = false))
    }

    @Test fun candidateActionMustBelongToCurrentCharacterProjectAndCachedBytes() {
        val listed = candidate()
        assertTrue(canSubmitCharacterVisualApproval(project, character, listed,
            listOf(listed), "visual_verified_local"))
        assertFalse(canSubmitCharacterVisualApproval("prj_another", character, listed,
            listOf(listed), "visual_verified_local"))
        assertFalse(canSubmitCharacterVisualApproval(project, "character:other", listed,
            listOf(listed), "visual_verified_local"))
        assertFalse(canSubmitCharacterVisualApproval(project, character, listed,
            listOf(listed), null))
        assertFalse(canSubmitCharacterVisualApproval(project, character, listed,
            emptyList(), "visual_verified_local"))
        assertFalse(canSubmitCharacterVisualApproval(project, character,
            listed.copy(sha256 = "b".repeat(64)), listOf(listed), "visual_verified_local"))
        assertFalse(canSubmitCharacterVisualApproval(project, character,
            listed.copy(status = "APPROVED"), listOf(listed), "visual_verified_local"))
        assertFalse(canSubmitCharacterVisualApproval(project, character,
            listed.copy(storage = VisualStudioStorage(state = "pending")),
            listOf(listed), "visual_verified_local"))
        assertFalse(canSubmitCharacterVisualApproval(project, character,
            listed.copy(asset_id = "va_bad"), listOf(listed), "visual_verified_local"))
        assertFalse(canSubmitCharacterVisualApproval(project, character, listed,
            listOf(listed, listed), "visual_verified_local"))
    }

    @Test fun characterApprovalReceiptRequiresSameProjectRevisionCharacterAndBytes() {
        val before = candidate()
        val after = before.copy(status = "APPROVED", visual_revision = 1)
        assertTrue(isMatchingCharacterVisualApprovalReceipt(
            project, character, before, 1, after))
        for (bad in listOf(
            after.copy(project_id = "prj_another"),
            after.copy(asset_id = "va_deadbeefaaaa"),
            after.copy(sha256 = "b".repeat(64)),
            after.copy(kind = "REFERENCE"),
            after.copy(perspective = "side"),
            after.copy(visual_revision = 2),
            after.copy(status = "CANDIDATE"),
            after.copy(storage = VisualStudioStorage(state = "pending")),
            after.copy(character_ids = listOf("character:other")),
        )) {
            assertFalse(isMatchingCharacterVisualApprovalReceipt(
                project, character, before, 1, bad))
        }
        assertFalse(isMatchingCharacterVisualApprovalReceipt(
            project, character, before, 0, after))
    }

    @Test fun copilotPortraitReceiptMustConfirmExactOwnerScopedWikiLink() {
        val valid = primaryReceipt()
        assertTrue(isMatchingCopilotPortraitApprovalReceipt(
            project, character, assetId, sha, valid))
        for (bad in listOf(
            valid.copy(project_id = "prj_another"),
            valid.copy(schema = "invalid"),
            valid.copy(character_id = "character:other"),
            valid.copy(wiki_link = CopilotPortraitWikiLink(entry_id = "character:other")),
            valid.copy(wiki_link = null),
            valid.copy(asset = valid.asset.copy(asset_id = "va_wrongasset")),
            valid.copy(asset = valid.asset.copy(sha256 = "b".repeat(64))),
            valid.copy(asset = valid.asset.copy(kind = "REFERENCE")),
            valid.copy(asset = valid.asset.copy(perspective = "back")),
            valid.copy(asset = valid.asset.copy(character_ids = listOf("character:other"))),
            valid.copy(asset = valid.asset.copy(storage = CopilotVisualStorage(state = "pending"))),
            valid.copy(asset = valid.asset.copy(status = "CANDIDATE")),
        )) {
            assertFalse(isMatchingCopilotPortraitApprovalReceipt(
                project, character, assetId, sha, bad))
        }
        assertFalse(isMatchingCopilotPortraitApprovalReceipt(
            project, character, assetId, "wrong-sha", valid))
    }
}
