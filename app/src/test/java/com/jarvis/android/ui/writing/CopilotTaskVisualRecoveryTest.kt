package com.jarvis.android.ui.writing

import com.jarvis.android.transport.live.CopilotApprovedVisualAsset
import com.jarvis.android.transport.live.CopilotTaskResult
import com.jarvis.android.transport.live.CopilotTaskState
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CopilotTaskVisualRecoveryTest {
    private val approved = CopilotApprovedVisualAsset(
        asset_id = "va_0123456789abcdef01234567",
        sha256 = "a".repeat(64),
        status = "APPROVED",
        storage_state = "stored",
        kind = "PRIMARY_REFERENCE",
        character_ids = listOf("character:alexander"),
    )
    private fun task(
        project: String = "prj_story",
        taskId: String = "ar_123",
        assets: List<CopilotApprovedVisualAsset> = emptyList(),
    ) = CopilotTaskState(
        project_id = project,
        task_id = taskId,
        results = if (assets.isEmpty()) emptyList()
                  else listOf(CopilotTaskResult(
                      tool = "get_approved_visual_references",
                      visual_assets = assets,
                  )),
    )

    @Test fun newApprovedAssetArrivingDuringPollingIsDownloadedOnce() {
        val initial = task()
        val completed = task(assets = listOf(approved))
        assertEquals(listOf(approved),
            newApprovedCopilotTaskVisuals("prj_story", initial, completed))
        assertTrue(newApprovedCopilotTaskVisuals(
            "prj_story", completed, completed,
        ).isEmpty())
    }

    @Test fun candidateBecomingApprovedTriggersOnlyAfterApproval() {
        val candidate = approved.copy(status = "CANDIDATE")
        val before = task(assets = listOf(candidate))
        val after = task(assets = listOf(approved))
        assertTrue(newApprovedCopilotTaskVisuals(
            "prj_story", task(), before,
        ).isEmpty())
        assertEquals(listOf(approved),
            newApprovedCopilotTaskVisuals("prj_story", before, after))
    }

    @Test fun mismatchedProjectOrMissingTaskNeverLeaksVisuals() {
        val crossProject = task(project = "prj_private", assets = listOf(approved))
        assertTrue(newApprovedCopilotTaskVisuals(
            "prj_story", task(), crossProject,
        ).isEmpty())
        assertTrue(newApprovedCopilotTaskVisuals(
            "prj_story", null, task(taskId = "", assets = listOf(approved)),
        ).isEmpty())
    }

    @Test fun invalidHashAndStorageStateAreNeverDownloaded() {
        val badDigest = approved.copy(sha256 = "xyz")
        val pending = approved.copy(storage_state = "pending")
        val otherId = approved.copy(asset_id = "not-a-visual")
        val next = task(assets = listOf(badDigest, pending, otherId))
        assertTrue(newApprovedCopilotTaskVisuals(
            "prj_story", null, next,
        ).isEmpty())
    }

    @Test fun duplicateAndChangedHashForSeenIdAreNotReFetched() {
        val before = task(assets = listOf(approved))
        val later = task(assets = listOf(
            approved.copy(sha256 = "b".repeat(64)),
            approved,
        ))
        assertTrue(newApprovedCopilotTaskVisuals(
            "prj_story", before, later,
        ).isEmpty())
        assertEquals(1, newApprovedCopilotTaskVisuals(
            "prj_story", task(), later,
        ).size)
    }

    @Test fun laterTaskDoesNotInheritEarlierTasksSeenAssets() {
        val previous = task(taskId = "ar_previous", assets = listOf(approved))
        val fresh = task(taskId = "ar_new", assets = listOf(approved))
        assertEquals(listOf(approved),
            newApprovedCopilotTaskVisuals("prj_story", previous, fresh))
    }

    @Test fun adaptiveAgentWithoutLegacyWorkerRowsMustStillPoll() {
        assertTrue(copilotTaskHasActiveRemoteWork(
            task().copy(agent_run_state = "RUNNING", state = "RUNNING"),
        ))
        assertTrue(copilotTaskHasActiveRemoteWork(
            task().copy(agent_run_state = "DISPATCHING", state = "DISPATCHING"),
        ))
    }

    @Test fun waitingApprovalOrTerminalOutcomeCannotTriggerAutomaticPolling() {
        assertTrue(!copilotTaskHasActiveRemoteWork(
            task().copy(agent_run_state = "WAITING_CONFIRMATION", state = "READY"),
        ))
        assertTrue(!copilotTaskHasActiveRemoteWork(
            task().copy(agent_run_state = "OUTCOME_UNKNOWN", state = "OUTCOME_UNKNOWN"),
        ))
        assertTrue(!copilotTaskHasActiveRemoteWork(
            task().copy(agent_run_state = "SUCCEEDED", state = "DONE"),
        ))
    }

    @Test fun oldStyleBackgroundWorkerStillRequiresPollingUntilFinished() {
        val progress = com.jarvis.android.transport.live.CopilotTaskLiveProgress(
            tool = "generate_story_scene",
            status = "RUNNING",
        )
        assertTrue(copilotTaskHasActiveRemoteWork(
            task().copy(live_progress = listOf(progress)),
        ))
        assertTrue(!copilotTaskHasActiveRemoteWork(
            task().copy(live_progress = listOf(progress.copy(status = "READY_FOR_REVIEW"))),
        ))
    }

    @Test fun terminalTaskWithQueuedLegacyVisualJobStillPolls() {
        val queued = com.jarvis.android.transport.live.CopilotTaskLiveProgress(
            tool = "complete_character_views", status = "QUEUED",
        )
        assertTrue(copilotTaskHasActiveRemoteWork(
            task().copy(state = "DONE", live_progress = listOf(queued)),
        ))
        assertTrue(copilotTaskHasActiveRemoteWork(
            task().copy(agent_run_state = "SUCCEEDED", state = "DONE",
                live_progress = listOf(queued)),
        ))
    }

    @Test fun completedAdaptivePollMakesJustFinishedApprovedReferenceAvailable() {
        val active = task().copy(agent_run_state = "RUNNING", state = "RUNNING")
        val done = task(assets = listOf(approved))
            .copy(agent_run_state = "SUCCEEDED", state = "DONE")
        assertTrue(copilotTaskHasActiveRemoteWork(active))
        assertEquals(listOf(approved),
            newApprovedCopilotTaskVisuals("prj_story", active, done))
        assertTrue(!copilotTaskHasActiveRemoteWork(done))
    }

    @Test fun completedCharacterMasterIsRecoveredWithoutRepeatGeneration() {
        val result = CopilotTaskResult(tool = "generate_character_master",
            result = buildJsonObject {
                put("asset_id", "va_gen_" + "a".repeat(32))
                put("sha256", "b".repeat(64))
                put("character_id", "character:doom")
                put("status", "CANDIDATE")
                put("storage_state", "stored")
            })
        val completed = task().copy(
            agent_run_state = "SUCCEEDED", state = "DONE", results = listOf(result),
        )
        val candidate = completedCopilotGeneratedMaster("prj_story", completed)
        assertEquals("character:doom", candidate?.characterId)
        assertEquals("CANDIDATE", candidate?.status)
        assertTrue(completedCopilotGeneratedMaster("another_project", completed) == null)
        assertTrue(completedCopilotGeneratedMaster("prj_story",
            completed.copy(state = "OUTCOME_UNKNOWN")) == null)
        assertTrue(completedCopilotGeneratedMaster("prj_story",
            completed.copy(results = listOf(result.copy(result =
                buildJsonObject { put("asset_id", "not-valid") })))) == null)
    }

}
