package com.jarvis.android.ui

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class VisualAssetLoadingTest {
    @Test fun stalledFetchEndsAndCanBeRetried() = runTest {
        var staged = false
        val timeout = loadVisualAsset<String, String>(
            timeoutMillis = 100,
            fetch = { awaitCancellation() },
            stage = { staged = true; it },
        )
        assertTrue(timeout.isFailure)
        assertTrue(timeout.exceptionOrNull()!!.message!!.contains("tardó"))
        assertFalse(staged)
        val retry = loadVisualAsset(fetch = { Result.success("bytes") }, stage = { it })
        assertEquals("bytes", retry.getOrThrow())
    }

    @Test fun failedDownloadNeverStagesAndRetainsTheReason() = runTest {
        val failure = IllegalStateException("Drive unavailable")
        var staged = false
        val result = loadVisualAsset<String, String>(
            fetch = { Result.failure(failure) },
            stage = { staged = true; it },
        )
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        assertEquals(failure.message, result.exceptionOrNull()?.message)
        assertFalse(staged)
    }

    @Test fun invalidImageFinishesWithFailure() = runTest {
        val result = loadVisualAsset<String, String>(
            fetch = { Result.success("invalid image") },
            stage = { null },
        )
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("leer"))
    }

    @Test fun cancellationIsPropagatedInsteadOfShownAsADownloadError() = runTest {
        val cancellation = CancellationException("scope changed")
        try {
            loadVisualAsset<String, String>(
                fetch = { throw cancellation },
                stage = { it },
            )
            fail("Cancellation must propagate")
        } catch (actual: CancellationException) {
            assertEquals(cancellation.message, actual.message)
        }
    }

    @Test fun visualReceiptMustMatchSelectedAssetNotOnlyBeValidImage() {
        val listed = com.jarvis.android.transport.live.VisualStudioAsset(
            asset_id = "va_master",
            project_id = "prj_story",
            kind = "PRIMARY_REFERENCE",
            status = "APPROVED",
            character_ids = listOf("character:alexander"),
            sha256 = "a".repeat(64),
            mime_type = "image/png",
        )
        val fetched = com.jarvis.android.transport.live.WritingVisualAssetContent(
            asset_id = "va_master", sha256 = "A".repeat(64),
            mime_type = "image/png", image_base64 = "fixture",
        )
        assertTrue(matchesListedVisualAssetReceipt("prj_story", listed, fetched))
        assertFalse(matchesListedVisualAssetReceipt("prj_other", listed, fetched))
        assertFalse(matchesListedVisualAssetReceipt(
            "prj_story", listed, fetched.copy(asset_id = "va_unrelated"),
        ))
        assertFalse(matchesListedVisualAssetReceipt(
            "prj_story", listed, fetched.copy(sha256 = "b".repeat(64)),
        ))
        assertFalse(matchesListedVisualAssetReceipt(
            "prj_story", listed, fetched.copy(mime_type = "image/jpeg"),
        ))
        assertFalse(matchesListedVisualAssetReceipt(
            "prj_story", listed.copy(sha256 = "not-a-digest"), fetched,
        ))
    }

    @Test fun paidEditRejectsAlteredOrOtherCharacterOriginalBeforeDispatch() {
        val valid = "synthetic-approved-original".toByteArray()
        val hash = java.security.MessageDigest.getInstance("SHA-256")
            .digest(valid).joinToString("") { "%02x".format(it) }
        val parent = com.jarvis.android.transport.live.VisualStudioAsset(
            project_id = "prj_story",
            asset_id = "va_master",
            kind = "PRIMARY_REFERENCE",
            status = "APPROVED",
            character_ids = listOf("character:alexander"),
            sha256 = hash, mime_type = "image/png",
        )
        assertTrue(verifiedCharacterEditParent(
            "prj_story", "character:alexander", parent, valid,
        ))
        assertFalse(verifiedCharacterEditParent(
            "prj_story", "character:alexander", parent, "changed".toByteArray(),
        ))
        assertFalse(verifiedCharacterEditParent(
            "prj_story", "character:doom", parent, valid,
        ))
        assertFalse(verifiedCharacterEditParent(
            "prj_other", "character:alexander", parent, valid,
        ))
        assertFalse(verifiedCharacterEditParent(
            "prj_story", "character:alexander", parent.copy(status = "CANDIDATE"), valid,
        ))
    }

    @Test fun editedChildMustBeAnImmutableUnapprovedDescendantOfExactParent() {
        val parent = com.jarvis.android.transport.live.VisualStudioAsset(
            asset_id = "va_master", project_id = "prj_story",
            kind = "PRIMARY_REFERENCE", perspective = "front",
            status = "APPROVED", character_ids = listOf("character:alexander"),
            sha256 = "a".repeat(64), mime_type = "image/png",
        )
        val child = parent.copy(
            asset_id = "va_candidate_02", status = "CANDIDATE",
            sha256 = "b".repeat(64), parent_asset_id = parent.asset_id,
            parent_sha256 = parent.sha256, derivation = "EDIT",
        )
        assertTrue(verifiedCharacterEditChild(parent, child, "image/png"))
        assertFalse(verifiedCharacterEditChild(parent, null, "image/png"))
        assertFalse(verifiedCharacterEditChild(parent, child.copy(status = "APPROVED"), "image/png"))
        assertFalse(verifiedCharacterEditChild(parent, child.copy(project_id = "prj_other"), "image/png"))
        assertFalse(verifiedCharacterEditChild(parent, child.copy(character_ids = listOf("character:doom")), "image/png"))
        assertFalse(verifiedCharacterEditChild(parent, child.copy(parent_asset_id = "va_other"), "image/png"))
        assertFalse(verifiedCharacterEditChild(parent, child.copy(parent_sha256 = "c".repeat(64)), "image/png"))
        assertFalse(verifiedCharacterEditChild(parent, child.copy(derivation = "REGENERATION"), "image/png"))
        assertFalse(verifiedCharacterEditChild(parent, child.copy(asset_id = parent.asset_id), "image/png"))
        assertFalse(verifiedCharacterEditChild(parent, child.copy(mime_type = "image/jpeg"), "image/png"))
        assertFalse(verifiedCharacterEditChild(parent, child.copy(sha256 = "bad"), "image/png"))
    }
}
