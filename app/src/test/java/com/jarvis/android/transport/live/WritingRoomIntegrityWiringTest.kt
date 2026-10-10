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
