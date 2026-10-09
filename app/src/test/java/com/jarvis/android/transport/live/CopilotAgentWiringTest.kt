package com.jarvis.android.transport.live

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CopilotAgentWiringTest {
    private fun code(path: String): String {
        return listOf(File(path), File("..", path)).firstOrNull { it.isFile }
            ?.readText() ?: error("Missing source file: $path")
    }

    @Test
    fun copilotUsesServerOwnedPlanAndHumanGatedProposals() {
        val api = code("app/src/main/java/com/jarvis/android/transport/live/WritingRoomApi.kt")
        val vm = code("app/src/main/java/com/jarvis/android/ui/JarvisViewModel.kt")
        val screen = code("app/src/main/java/com/jarvis/android/ui/screens/WritingWorkspaceV1Screen.kt")

        assertTrue(api.contains("val copilot_task: CopilotTaskState? = null"))
        assertTrue(api.contains("toolMode: Boolean = false"))
        assertTrue(api.contains("if (toolMode) put(\"tool_mode\", \"plan\")"))
        assertTrue(api.contains("/api/app/writing-room/copilot/tasks/latest"))
        assertTrue(api.contains("/api/app/writing-room/copilot/tasks/status"))
        assertTrue(api.contains("/api/app/writing-room/copilot/tasks/run"))
        assertTrue(api.contains("put(\"confirmed\", confirmed)"))

        assertTrue(vm.contains("recoverWritingCopilotTask(projectId)"))
        assertTrue(vm.contains("fun updateWritingCopilotTask("))
        assertTrue(vm.contains("task.state != \"READY\" || !task.requires_confirmation"))
        assertTrue(vm.contains("copilotAgentTask = response.copilot_task ?: state.copilotAgentTask"))
        assertTrue(vm.contains("copilotRecoveryEpoch == epoch"))
        assertTrue(screen.contains("Agente con herramientas"))
        assertTrue(screen.contains("Confirmar y guardar propuesta"))
        assertTrue(screen.contains("Actualizar estado desde el VPS"))
        assertTrue(screen.contains("task.requires_confirmation"))
    }
}
