package com.jarvis.android.ui.writing

import com.jarvis.android.transport.live.CopilotVisualAssets
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CopilotPortraitRecoveryPolicyTest {
    private val sha = "A".repeat(64)
    private fun candidate(
        id: String,
        model: String = "gpt-image-2-medium",
        origin: String = "copilot",
        status: String = "CANDIDATE",
        characters: String = """["character:naruto"]""",
        storage: String = "stored",
    ) = """{
        "asset_id":"$id",
        "sha256":"$sha",
        "status":"$status",
        "kind":"PRIMARY_REFERENCE",
        "perspective":"front",
        "character_ids":$characters,
        "storage":{"state":"$storage"},
        "provenance":{
            "requested_from":"$origin",
            "operation":"generation",
            "model":"$model",
            "fallback_used":false
        }
    }""".trimIndent()

    private fun parse(vararg assets: String) = Json {
        ignoreUnknownKeys = true
    }.decodeFromString<CopilotVisualAssets>(
        """{"schema":"jarvis.visual.assets.v1","project_id":"prj_story","assets":[${assets.joinToString(",")}]}""",
    ).assets

    @Test fun selectsExistingCopilotCandidatesWithoutGenerating() {
        val selected = recoverableCopilotPortraitCandidates(
            parse(candidate("va_good"), candidate("va_non_copilot", origin = "image_studio")),
        )
        assertEquals(listOf("va_good"), selected.map { it.asset_id })
        assertEquals("character:naruto", selected.single().character_ids.single())
    }

    @Test fun excludesOtherModelsApprovedAndMixedCharacters() {
        val selected = recoverableCopilotPortraitCandidates(
            parse(
                candidate("va_other_model", model = "grok-imagine-image"),
                candidate("va_approved", status = "APPROVED"),
                candidate("va_multiple", characters = """["character:naruto","character:sasuke"]"""),
                candidate("va_wanted"),
            ),
        )
        assertEquals(listOf("va_wanted"), selected.map { it.asset_id })
    }

    @Test fun pendingDriveStorageIsVisibleButNotAutomaticallyApproved() {
        val selected = recoverableCopilotPortraitCandidates(
            parse(candidate("va_pending", storage = "pending_upload")),
        )
        assertEquals("pending_upload", selected.single().storage.state)
    }

    @Test fun recoveryIsBounded() {
        val selected = recoverableCopilotPortraitCandidates(
            parse(*(1..15).map { candidate("va_$it") }.toTypedArray()), limit = 6,
        )
        assertEquals(6, selected.size)
        assertEquals("va_1", selected.first().asset_id)
    }

    @Test fun malformedProvenanceNeverCrashesRecovery() {
        val malformed = candidate("va_wrong").replace(
            "\"requested_from\":\"copilot\"", "\"requested_from\":{}",
        )
        val selected = recoverableCopilotPortraitCandidates(
            parse(malformed, candidate("va_good")),
        )
        assertEquals(1, selected.size)
        assertTrue(selected.single().asset_id == "va_good")
    }
}
