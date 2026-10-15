package com.jarvis.android.ui.writing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualApprovalGateTest {
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
}
