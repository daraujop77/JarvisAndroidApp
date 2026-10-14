package com.jarvis.android.ui.writing

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CopilotApprovedVisualRevisionWiringTest {
    private fun code(path: String): String {
        val candidates = listOf(File(path), File("../$path"), File("../../$path"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Missing checked-in source: $path")
    }

    @Test fun approvedVisualHintsAreBoundedAndProjectScoped() {
        val api = code("app/src/main/java/com/jarvis/android/transport/live/WritingRoomApi.kt")
        val vm = code("app/src/main/java/com/jarvis/android/ui/JarvisViewModel.kt")
        assertTrue(api.contains("approved_visual_asset_ids"))
        assertTrue(api.contains("approvedVisualAssetIds.filter"))
        assertTrue(api.contains(".distinct().take(6)"))
        assertTrue(vm.contains("existingHistory.asReversed()"))
        val helper = code("app/src/main/java/com/jarvis/android/ui/writing/CopilotTaskVisualRecovery.kt")
        assertTrue(vm.contains("approvedVisualIdsForCopilotContext("))
        assertTrue(helper.contains("it.status == \"APPROVED\""))
        assertTrue(helper.contains("it.storage_state == \"stored\""))
        assertTrue(helper.contains(".distinct()"))
        assertTrue(helper.contains(".take(6)"))
        assertTrue(vm.contains("approvedVisualAssetIds = visibleApprovedIds"))
    }

    @Test fun candidateApprovalRequiresDecodedVisiblePreview() {
        val screen = code("app/src/main/java/com/jarvis/android/ui/screens/WritingWorkspaceV1Screen.kt")
        // An attachment ID alone is not evidence that an image loaded.
        assertTrue(screen.contains("val candidatePreviewBitmap = remember(turn.generatedPreviewAttachmentId)"))
        assertTrue(screen.contains("val image = candidatePreviewBitmap"))
        assertTrue(screen.contains("turn.candidatePerspective == \"front\""))
        assertTrue(screen.contains("candidatePreviewBitmap != null"))
        assertTrue(screen.contains("turn.candidateCharacterId.isNullOrBlank()").not())
        assertTrue(screen.contains("isApprovableProjectReference("))
        assertTrue(screen.contains("turn.candidateStorageState, turn.candidateCharacterId,\n                            candidatePreviewBitmap != null"))
        assertTrue(!screen.contains("!turn.generatedPreviewAttachmentId.isNullOrBlank()"))
    }

    @Test fun paidInspectionCardNeverClaimsToSaveCanon() {
        val screen = code("app/src/main/java/com/jarvis/android/ui/screens/WritingWorkspaceV1Screen.kt")
        assertTrue(screen.contains("it.tool == \"inspect_approved_character_image\""))
        assertTrue(screen.contains("inspectsApprovedVisuals -> \"Analizar imágenes aprobadas\""))
        assertTrue(screen.contains("No genera imágenes nuevas ni cambia el canon"))
    }
}
