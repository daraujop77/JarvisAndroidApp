package com.jarvis.android.ui

import com.jarvis.android.transport.live.JarvisAppSession
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingPaidVisualRecoveryTest {
    // Same 1x1 PNG and SHA-256 as the VisualStudioApiTest fixtures, so integrity runs for real.
    private val fixturePng = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGNgZGT8DwABDQED5NTSmwAAAABJRU5ErkJggg=="
    private val fixtureSha256 = "2f41b02bc94868f7cdd1e503c7f592ee7673dbb155eac2f267a3d1abf64aea18"

    private fun candidate(
        storageState: String = "stored",
        status: String = "CANDIDATE",
        sha256: String = fixtureSha256,
        projectId: String = "prj_story",
    ): JsonObject = buildJsonObject {
        put("asset_id", "va_master")
        put("project_id", projectId)
        put("kind", "PRIMARY_REFERENCE")
        put("status", status)
        put("sha256", sha256)
        put("mime_type", "image/png")
        put("size_bytes", 70)
        put("storage", buildJsonObject {
            put("backend", "google_drive")
            put("state", storageState)
        })
    }

    private fun receipt(
        visualAsset: JsonElement? = null,
        route: String = "/api/app/images/generations",
        data: String? = fixturePng,
    ): JsonObject = buildJsonObject {
        put("request_id", "img_recovery_0001")
        put("request_fingerprint", "a".repeat(64))
        put("project_id", "prj_story")
        put("route", route)
        put("state", "COMPLETED")
        put("dispatch_state", "COMPLETED")
        put("retryable", false)
        put("mime_type", "image/png")
        put("size_bytes", 70)
        data?.let { put("data_base64", it) }
        visualAsset?.let { put("visual_asset", it) }
    }

    private fun completed(receipt: JsonObject) =
        JarvisAppSession.PendingPaidVisualState.Completed("key-1", "img_recovery_0001", receipt)

    @Test
    fun storedVerifiedCandidateIsReleasedWithoutCopyingBytes() {
        assertEquals(
            PaidVisualRecoveryStep.RELEASE_CANDIDATE,
            paidVisualRecoveryStep(completed(receipt(candidate(storageState = "stored")))),
        )
    }

    @Test
    fun pendingStorageCandidateIsReleasedBecauseTheVpsAlreadyHoldsIt() {
        assertEquals(
            PaidVisualRecoveryStep.RELEASE_CANDIDATE,
            paidVisualRecoveryStep(completed(receipt(candidate(storageState = "pending_drive")))),
        )
    }

    @Test
    fun candidateWithBytesThatFailIntegrityIsKept() {
        assertEquals(
            PaidVisualRecoveryStep.KEEP,
            paidVisualRecoveryStep(completed(receipt(candidate(sha256 = "0".repeat(64))))),
        )
    }

    @Test
    fun candidateThatIsNotStoredOrNotCandidateOrOtherProjectIsKept() {
        assertEquals(
            PaidVisualRecoveryStep.KEEP,
            paidVisualRecoveryStep(completed(receipt(candidate(storageState = "failed")))),
        )
        assertEquals(
            PaidVisualRecoveryStep.KEEP,
            paidVisualRecoveryStep(completed(receipt(candidate(status = "DRAFT")))),
        )
        assertEquals(
            PaidVisualRecoveryStep.KEEP,
            paidVisualRecoveryStep(completed(receipt(candidate(projectId = "prj_other")))),
        )
    }

    @Test
    fun completedWithoutVisualAssetIsStagedNotReleasedDirectly() {
        assertEquals(
            PaidVisualRecoveryStep.STAGE_ORIGINAL,
            paidVisualRecoveryStep(completed(receipt(visualAsset = null))),
        )
    }

    @Test
    fun candidateErrorReceiptWithNullAssetIsStaged() {
        assertEquals(
            PaidVisualRecoveryStep.STAGE_ORIGINAL,
            paidVisualRecoveryStep(completed(receipt(visualAsset = JsonNull))),
        )
    }

    @Test
    fun unknownOutcomeIsNeverReleasedAutomatically() {
        val unknown = JarvisAppSession.PendingPaidVisualState.Unknown("key-1", "img_recovery_0001")

        assertEquals(PaidVisualRecoveryStep.KEEP, paidVisualRecoveryStep(unknown))
    }

    @Test
    fun unverifiableOutcomeIsKept() {
        assertEquals(
            PaidVisualRecoveryStep.KEEP,
            paidVisualRecoveryStep(JarvisAppSession.PendingPaidVisualState.Unverifiable("key-1")),
        )
    }

    @Test
    fun originalBytesRequireBothDataAndMime() {
        assertEquals(fixturePng to "image/png", paidVisualOriginalBytes(receipt(visualAsset = null)))
        assertNull(paidVisualOriginalBytes(receipt(visualAsset = null, data = null)))
    }

    @Test
    fun onlyGlobalGenerationRecordsIntoTheActiveChat() {
        assertTrue(paidVisualRecordsInChat(receipt(route = "/api/app/images/generations")))
        assertFalse(paidVisualRecordsInChat(receipt(route = "/api/app/images/edits")))
        assertFalse(
            paidVisualRecordsInChat(receipt(route = "/api/app/writing-room/v2/visual/scenes/generate")),
        )
    }

    @Test
    fun unresolvedCauseIsShownAsSpanishNoticeAndOtherErrorsAreUnchanged() {
        assertEquals(
            "Hay una imagen pendiente de confirmar. Revísala en el aviso superior.",
            paidVisualUserMessage("Previous paid image request is unresolved. Recover it first."),
        )
        assertEquals("Image generation failed", paidVisualUserMessage("Image generation failed"))
        assertNull(paidVisualUserMessage(null))
    }
}
