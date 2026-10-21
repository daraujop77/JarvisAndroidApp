package com.jarvis.android.ui.writing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A failed/restarted gallery cannot leak old media or delete active chat bytes. */
class CopilotPortraitAttachmentCleanupTest {
    @Test fun newGalleryReplacesPreviousLocalCopiesWithoutAccumulatingDuplicates() {
        assertEquals(
            setOf("visual_old_1", "visual_old_2"),
            replacedCopilotPortraitCopies(
                previousIds = setOf("visual_old_1", "visual_old_2"),
                newlyStagedIds = setOf("visual_fresh_1"),
                visibleIds = setOf("visual_fresh_1"),
                otherReferencedIds = emptySet(),
            ),
        )
    }

    @Test fun newlyDownloadedAssetInOriginatingChatDoesNotKeepSecondGalleryCopy() {
        assertEquals(
            setOf("visual_duplicate"),
            replacedCopilotPortraitCopies(
                previousIds = emptySet(),
                newlyStagedIds = setOf("visual_duplicate"),
                visibleIds = emptySet(),
                otherReferencedIds = setOf("visual_chat_copy"),
            ),
        )
    }

    @Test fun activeChatOrAnotherVisualPanelKeepsSharedAttachment() {
        assertEquals(
            setOf("visual_replaced"),
            replacedCopilotPortraitCopies(
                previousIds = setOf("visual_chat", "visual_replaced"),
                newlyStagedIds = setOf("visual_current", "visual_scene"),
                visibleIds = setOf("visual_current"),
                otherReferencedIds = setOf("visual_chat", "visual_scene"),
            ),
        )
    }

    @Test fun noMediaPresentNeverInventsFilesToDelete() {
        assertTrue(replacedCopilotPortraitCopies(
            previousIds = emptySet(),
            newlyStagedIds = emptySet(),
            visibleIds = emptySet(),
            otherReferencedIds = emptySet(),
        ).isEmpty())
    }
}
