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
        assertTrue(vm.contains("asset.status == \"APPROVED\""))
        assertTrue(vm.contains("asset.storage_state == \"stored\""))
        assertTrue(vm.contains("approvedVisualAssetIds = visibleApprovedIds"))
    }

    @Test fun paidInspectionCardNeverClaimsToSaveCanon() {
        val screen = code("app/src/main/java/com/jarvis/android/ui/screens/WritingWorkspaceV1Screen.kt")
        assertTrue(screen.contains("it.tool == \"inspect_approved_character_image\""))
        assertTrue(screen.contains("inspectsApprovedVisuals -> \"Analizar imágenes aprobadas\""))
        assertTrue(screen.contains("No genera imágenes nuevas ni cambia el canon"))
    }
}
