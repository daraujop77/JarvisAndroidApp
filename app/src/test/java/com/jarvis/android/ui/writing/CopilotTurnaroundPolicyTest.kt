package com.jarvis.android.ui.writing

import com.jarvis.android.transport.live.VisualCharacterBatch
import com.jarvis.android.transport.live.VisualCharacterBatchItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CopilotTurnaroundPolicyTest {
    private val sha = "A".repeat(64)
    private val candidate = VisualCharacterBatchItem(
        perspective = "back",
        status = "CANDIDATE",
        candidate_asset_id = "va_back",
        candidate_sha256 = sha,
    )
    private fun batch(
        status: String = "READY_FOR_REVIEW",
        pending: Int = 0,
        blocked: Int = 0,
        items: List<VisualCharacterBatchItem> = listOf(candidate),
    ) = VisualCharacterBatch(
        project_id = "prj_story",
        character_id = "character:alexander",
        batch_id = "batch_1",
        status = status,
        pending_count = pending,
        blocked_count = blocked,
        items = items,
    )

    @Test fun requestsSixSingleViewAnglesAfterApprovedFrontMaster() {
        assertEquals(
            listOf("left_three_quarter", "left_profile", "right_three_quarter",
                "right_profile", "back", "full_body"),
            COPILOT_TURNAROUND_PERSPECTIVES,
        )
        assertFalse(COPILOT_TURNAROUND_PERSPECTIVES.contains("front"))
        assertEquals(6, COPILOT_TURNAROUND_PERSPECTIVES.distinct().size)
    }

    @Test fun requiresLocalVerifiedPreviewOfEveryCandidate() {
        assertTrue(copilotApprovableViewIds(batch(), emptyMap()).isEmpty())
        assertEquals(listOf("va_back"), copilotApprovableViewIds(batch(), mapOf("va_back" to "file_1")))
        val two = listOf(candidate, candidate.copy(
            perspective = "left_profile", candidate_asset_id = "va_left",
        ))
        assertTrue(copilotApprovableViewIds(batch(items = two), mapOf("va_back" to "file_1")).isEmpty())
        assertEquals(listOf("va_back", "va_left"), copilotApprovableViewIds(
            batch(items = two), mapOf("va_back" to "file_1", "va_left" to "file_2"),
        ))
    }

    @Test fun refusesIncompleteBlockedWrongOrDuplicateBatch() {
        val verified = mapOf("va_back" to "file_1")
        assertTrue(copilotApprovableViewIds(batch(status = "RUNNING"), verified).isEmpty())
        assertTrue(copilotApprovableViewIds(batch(pending = 1), verified).isEmpty())
        assertTrue(copilotApprovableViewIds(batch(blocked = 1), verified).isEmpty())
        assertTrue(copilotApprovableViewIds(batch(items = listOf(candidate, candidate)), verified).isEmpty())
        assertTrue(copilotApprovableViewIds(batch(items = listOf(candidate.copy(candidate_sha256 = "invalid"))), verified).isEmpty())
    }

    @Test fun runningAndAmbiguousBatchesMustNotLaunchNewPaidWork() {
        assertTrue(copilotBatchCanStartNew(null))
        for (status in listOf("RUNNING", "READY", "READY_FOR_REVIEW", "BLOCKED", "COMPLETED")) {
            assertFalse(copilotBatchCanStartNew(batch(status = status)))
        }
    }
}
