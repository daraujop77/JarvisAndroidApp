package com.jarvis.android.transport.live

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** UI guardrails that must remain wired even after Writing Room layout changes. */
class WritingRoomIntegrityWiringTest {
    private fun code(path: String): String =
        listOf(File(path), File("..", path)).firstOrNull { it.isFile }
            ?.readText() ?: error("Missing source: $path")

    @Test
    fun switchingProjectsClearsProtectedStateAndIgnoresLateLoads() {
        val vm = code("app/src/main/java/com/jarvis/android/ui/JarvisViewModel.kt")
        assertTrue(vm.contains("WritingWorkspaceState(chatProjectId = projectId)"))
        assertTrue(vm.contains("loadEpoch != writingWorkspaceLoadEpoch"))
        assertTrue(vm.contains("_writingWorkspace.value.chatProjectId != projectId) return@launch"))
        assertTrue(vm.contains("resetVisualStudioProtectedMedia()"))
    }

    @Test
    fun canonDashboardNeverFabricatesChapterSourcesOrProjectIdentity() {
        val screen = code("app/src/main/java/com/jarvis/android/ui/screens/WritingWorkspaceV1Screen.kt")
        assertFalse(screen.contains("STORY-001"))
        assertFalse(screen.contains("?: 37"))
        assertFalse(screen.contains("Capítulo 37: La contingencia"))
        assertFalse(screen.contains("CANON · CAP 37"))
        assertFalse(screen.contains("?: 23"))
        assertFalse(screen.contains("CANON SINCRONIZADO"))
        assertFalse(screen.contains("RAG CONECTADO"))
        assertFalse(screen.contains("Conectado al Canon de Alexander History"))
        assertTrue(screen.contains("overview.sources.total} FUENTES REGISTRADAS"))
        assertTrue(screen.contains("latestChapterNumber = home?.latest_official_chapter?.chapter_number"))
        assertTrue(screen.contains("CAPÍTULO OFICIAL POR CONFIRMAR"))
        assertTrue(screen.contains("Canon del proyecto:"))
        assertTrue(screen.contains("Hitos del canon"))
        assertTrue(screen.contains("ÚLTIMA APARICIÓN · CAP"))
    }

    @Test
    fun paidVisualOperationsIgnoreLateOtherProjectAndCharacterResults() {
        val vm = code("app/src/main/java/com/jarvis/android/ui/JarvisViewModel.kt")
        val portrait = vm.substringAfter("private fun generateCopilotPortrait(")
            .substringBefore("fun approveCopilotPortrait(")
        assertTrue(portrait.contains("val epoch = copilotRecoveryEpoch"))
        assertTrue(portrait.contains("if (copilotRecoveryEpoch != epoch ||"))
        assertTrue(portrait.contains("deleteVisualStudioAttachments(listOf(it))"))
        val approved = vm.substringAfter("fun approveCopilotPortrait(")
            .substringBefore("fun beginCopilotCharacterViews(")
        assertTrue(approved.contains("!settings.value.isOwner"))
        assertTrue(approved.contains("if (copilotRecoveryEpoch != epoch ||"))
        val generated = vm.substringAfter("fun generateCharacterVisual(")
            .substringBefore("fun uploadCharacterMaster(")
        assertTrue(generated.contains("current.projectId != projectId"))
        assertTrue(generated.contains("current.characterId != characterId"))
        assertTrue(generated.contains("scopeVersion != characterStudioScopeVersion"))
        val batch = vm.substringAfter("fun approveCharacterViewBatch(")
            .substringBefore("fun generateMissingCharacterMasters(")
        assertTrue(batch.contains("_characterStudio.value.characterId != characterId"))
        assertTrue(batch.contains("scopeVersion != characterStudioScopeVersion"))
        val pack = vm.substringAfter("fun approveCharacterReferencePack(")
            .substringBefore("fun openLocationStudio(")
        assertTrue(pack.contains("scopeVersion != characterStudioScopeVersion"))
        assertTrue(pack.contains("_characterStudio.value.projectId != projectId"))
    }

    @Test
    fun unsupportedProjectMutationsAreNotAdvertisedInTheUi() {
        val projects = code("app/src/main/java/com/jarvis/android/ui/screens/ProjectsScreen.kt")
        assertFalse(projects.contains("vm.createProject("))
        assertFalse(projects.contains("vm.renameProject("))
        assertFalse(projects.contains("vm.deleteProject("))
        assertTrue(projects.contains("contrato seguro de provisión"))
    }

    @Test
    fun portraitIntentDoesNotImmediatelyDispatchCloudGeneration() {
        val vm = code("app/src/main/java/com/jarvis/android/ui/JarvisViewModel.kt")
        val screen = code("app/src/main/java/com/jarvis/android/ui/screens/WritingWorkspaceV1Screen.kt")
        val commandStart = vm.indexOf("if (!toolMode && looksLikeCopilotPortraitCommand(clean))")
        val commandEnd = vm.indexOf("val current = _writingWorkspace.value", commandStart)
        assertTrue(commandStart >= 0 && commandEnd > commandStart)
        val handler = vm.substring(commandStart, commandEnd)
        assertTrue(handler.contains("pendingPortrait = CopilotPendingPortrait("))
        assertFalse(handler.contains("generateCopilotPortrait("))
        assertTrue(vm.contains("fun confirmWritingCopilotPortrait(projectId: String)"))
        assertTrue(vm.contains("fun cancelWritingCopilotPortrait(projectId: String)"))
        assertTrue(screen.contains("state.pendingPortrait?.let { request ->"))
        assertTrue(screen.contains("vm.confirmWritingCopilotPortrait(projectId)"))
        assertTrue(screen.contains("vm.cancelWritingCopilotPortrait(projectId)"))
        assertTrue(screen.contains("puede consumir créditos"))
    }
}
