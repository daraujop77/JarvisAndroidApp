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
        assertSame(failure, result.exceptionOrNull())
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
            assertSame(cancellation, actual)
        }
    }
}
