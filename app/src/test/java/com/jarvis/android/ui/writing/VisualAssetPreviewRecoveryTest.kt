package com.jarvis.android.ui.writing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The attachment ID is intentionally stable after retry. The Compose decoder
 * keys on both this identifier and loading, so the same file can be decoded
 * again after a read or download failure without generating another image.
 */
class VisualAssetPreviewRecoveryTest {
    @Test fun cannotDecodeWhileReDownloadingOriginal() {
        assertFalse(shouldDecodeVisualPreview("same-approved-receipt", true))
    }

    @Test fun retriesDecodeForTheSameAttachmentAfterDownloadFinishes() {
        val id = "same-approved-receipt"
        assertTrue(shouldDecodeVisualPreview(id, false))
        assertFalse(shouldDecodeVisualPreview(id, true))
        assertTrue(shouldDecodeVisualPreview(id, false))
    }

    @Test fun missingAttachmentNeverAttemptsToDecode() {
        assertFalse(shouldDecodeVisualPreview(null, false))
        assertFalse(shouldDecodeVisualPreview("", false))
        assertFalse(shouldDecodeVisualPreview("  ", false))
    }
}
