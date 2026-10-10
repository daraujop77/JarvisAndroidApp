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
        assertTrue(api.contains("val live_progress: List<CopilotTaskLiveProgress> = emptyList()"))
        assertTrue(api.contains("val final_asset_id: String = \"\""))
        assertTrue(api.contains("val effect: String = \"READ_ONLY\""))
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
        assertTrue(screen.contains("get_chapter_workflow"))
        assertTrue(screen.contains("prepare_visual_scene"))
        assertTrue(screen.contains("get_scene_context"))
        assertTrue(screen.contains("start_scene_generation"))
        assertTrue(screen.contains("get_scene_generation_status"))
        assertTrue(screen.contains("start_chapter_auto_review"))
        assertTrue(screen.contains("get_chapter_auto_review_status"))
        assertTrue(screen.contains("task.live_progress.forEach"))
        assertTrue(screen.contains("Sin correcciones automáticas"))
        assertTrue(screen.contains("puede generar costos"))
        assertTrue(screen.contains("Confirmar generación de imagen"))
        assertTrue(screen.contains("Iniciar revisión del capítulo"))
        assertTrue(screen.contains("step.effect == \"PROPOSED_ONLY\""))
        assertTrue(screen.contains("Preparar escena"))
        assertTrue(screen.contains("No generará imágenes ni cambiará el canon"))
        // A5.3: only the exact stored SCENE_ART from its durable job may enter
        // the chat. Preview is SHA-verified, approval is exact owner-only.
        assertTrue(vm.contains("fun loadCopilotSceneCandidate("))
        assertTrue(vm.contains("fun approveCopilotSceneCandidate("))
        assertTrue(vm.contains("fun reviseCopilotSceneCandidate("))
        assertTrue(vm.contains("followCopilotTaskProgress(projectId, updated)"))
        assertTrue(vm.contains("stageVisualAssetBase64("))
        assertTrue(vm.contains("selectCopilotSceneReviewAsset("))
        assertTrue(vm.contains("visualAssetApproveExact("))
        assertTrue(vm.contains("editSceneVisualAssetImage("))
        assertTrue(vm.contains("copilotSceneCorrectionUnknown"))
        assertTrue(screen.contains("Mostrar imagen aquí"))
        assertTrue(screen.contains("Aprobar esta imagen"))
        assertTrue(screen.contains("Generar corrección · puede tener costo"))
        assertTrue(screen.contains("it.rootAssetId == progress.final_asset_id"))
        val visualApi = code("app/src/main/java/com/jarvis/android/transport/live/VisualStudioApi.kt")
        assertTrue(screen.contains("generate_story_scene"))
        assertTrue(screen.contains("Puedes corregir esta imagen sin aprobarla"))
        assertTrue(screen.contains("scene.status in setOf(\"CANDIDATE\", \"APPROVED\")"))
        assertTrue(vm.contains("sceneJobId = if (parent.status == \"CANDIDATE\") card.jobId else null"))
        assertTrue(vm.contains("it.tool in setOf(\"start_scene_generation\", \"generate_story_scene\")"))
        assertTrue(visualApi.contains("/api/app/images/copilot-scene-edits"))
        assertTrue(visualApi.contains("if (candidateEdit) put(\"scene_job_id\", sceneJobId.orEmpty())"))
        assertTrue(visualApi.contains("(!candidateEdit && parentAsset.status != \"APPROVED\")"))
        assertTrue(screen.contains("looksLikeCopilotSceneImageRequest(clean)"))
        assertTrue(screen.contains("generate_story_scene"))
        assertTrue(vm.contains("card.status !in setOf(\"CANDIDATE\", \"APPROVED\")"))
        assertTrue(visualApi.contains("if (candidateEdit) \"/api/app/images/copilot-scene-edits\""))
        assertTrue(screen.contains("Se iniciará UNA generación"))
        assertTrue(screen.contains("Aprobar esta imagen"))
    }
}
