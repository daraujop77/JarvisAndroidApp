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
}
