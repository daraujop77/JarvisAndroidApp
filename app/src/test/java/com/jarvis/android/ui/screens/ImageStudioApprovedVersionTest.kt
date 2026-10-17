package com.jarvis.android.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verified master may be edited; a newly generated child must be approved first. */
class ImageStudioApprovedVersionTest {
    @Test fun characterEditingNeverUsesCandidateBytesWithTheOldApprovedReceipt() {
        val approvedId = "visual_approved_001"
        val candidateId = "visual_candidate_002"
        assertTrue(canEditCharacterVisualVersion(approvedId, approvedId, true))
        assertFalse(canEditCharacterVisualVersion(candidateId, approvedId, true))
        assertFalse(canEditCharacterVisualVersion("", approvedId, true))
        assertTrue(canEditCharacterVisualVersion(approvedId, approvedId, false))
        // Generic image editing keeps the existing ability to chain local versions.
        assertTrue(canEditCharacterVisualVersion(candidateId, approvedId, false))
    }
}
