package com.jarvis.android.ui.writing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Opening or refreshing a project must not trigger paid image generation. */
class CharacterStudioPaidBatchResumeTest {
    private fun checkedIn(path: String): String =
        listOf(File(path), File("../$path"), File("../../$path"))
            .firstOrNull { it.isFile }?.readText() ?: error("Source unavailable: $path")

    @Test fun reloadIsReadOnlyForReadyOrFailedBatch() {
        val vm = checkedIn("app/src/main/java/com/jarvis/android/ui/JarvisViewModel.kt")
        val first = vm.indexOf("private suspend fun reloadCharacterStudio(")
        val last = vm.indexOf("private fun startCharacterBatchPolling(", first)
        assertTrue(first >= 0 && last > first)
        val reload = vm.substring(first, last)
        assertFalse(reload.contains("completeCharacterViews("))
        assertTrue(reload.contains("loadedBatch?.status == \"RUNNING\""))
        assertTrue(reload.contains("batch = if (batchResult.isSuccess) loadedBatch"))
    }

    @Test fun resumeRequiresExplicitOwnerDialogAndDurableBatchId() {
        val screen = checkedIn("app/src/main/java/com/jarvis/android/ui/writing/CharacterStudioScreen.kt")
        assertTrue(screen.contains("if (pendingTurnaroundGeneration)"))
        assertTrue(screen.contains("settings.isOwner && master != null"))
        assertTrue(screen.contains("resumeBatchId = existing?.batch_id.orEmpty()"))
        assertTrue(screen.contains("existing?.requested_perspectives"))
        assertTrue(screen.contains("Reanudar lote existente"))
        assertTrue(screen.contains("Iniciar vistas de pago"))
    }
}
