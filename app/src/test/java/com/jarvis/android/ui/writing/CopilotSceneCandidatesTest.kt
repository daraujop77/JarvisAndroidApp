package com.jarvis.android.ui.writing

import com.jarvis.android.transport.live.VisualStudioAsset
import com.jarvis.android.transport.live.VisualStudioStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CopilotSceneCandidatesTest {
    private fun asset(
        id: String,
        project: String = "prj_story",
        kind: String = "SCENE_ART",
        status: String = "CANDIDATE",
        parentId: String = "",
        parentSha: String = "",
        sha: String = "A".repeat(64),
        created: String = "2026-10-09T11:00:00Z",
        storage: String = "stored",
    ) = VisualStudioAsset(
        asset_id = id, project_id = project, kind = kind, status = status,
        sha256 = sha, parent_asset_id = parentId, parent_sha256 = parentSha,
        created_utc = created, storage = VisualStudioStorage(state = storage),
    )

    @Test
    fun restoresLatestStoredDescendantOnlyWhenImmutableParentMatches() {
        val root = asset("va_root", status = "APPROVED")
        val revision = asset("va_revision", parentId = "va_root",
            parentSha = root.sha256, sha = "B".repeat(64),
            created = "2026-10-09T12:00:00Z")
        val newer = asset("va_revision2", parentId = "va_revision",
            parentSha = revision.sha256, sha = "C".repeat(64),
            created = "2026-10-09T13:00:00Z")
        val spoof = asset("va_spoof", parentId = "va_revision",
            parentSha = "D".repeat(64), created = "2026-10-09T14:00:00Z")
        val other = asset("va_cross", project = "prj_child",
            parentId = "va_revision", parentSha = revision.sha256,
            created = "2026-10-09T15:00:00Z")
        assertEquals("va_revision2", selectCopilotSceneReviewAsset(
            "prj_story", "va_root", listOf(root, revision, newer, spoof, other)
        )?.asset_id)
    }

    @Test
    fun pendingOrUnrelatedVisualsCannotReplaceAStoredCandidate() {
        val root = asset("va_root", status = "APPROVED")
        val pending = asset("va_child_pending", parentId = "va_root",
            parentSha = root.sha256, created = "2026-10-09T17:00:00Z",
            storage = "pending_upload")
        val portrait = asset("va_wrong_kind", kind = "PRIMARY_REFERENCE",
            parentId = "va_root", parentSha = root.sha256,
            created = "2026-10-09T18:00:00Z")
        assertEquals("va_root", selectCopilotSceneReviewAsset(
            "prj_story", "va_root", listOf(root, pending, portrait)
        )?.asset_id)
    }

    @Test
    fun missingRootOrInvalidHashFailsClosed() {
        val root = asset("va_root", sha = "invalid")
        assertNull(selectCopilotSceneReviewAsset(
            "prj_story", "va_root", listOf(root)
        ))
        assertNull(selectCopilotSceneReviewAsset(
            "prj_story", "va_not_present", listOf(asset("va_root"))
        ))
        assertNull(selectCopilotSceneReviewAsset(
            "prj_foreign", "va_root", listOf(asset("va_root"))
        ))
    }
}
