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
    fun selectedGlobalProjectRequiresExactScopedMediaCapability() {
        val vm = code("app/src/main/java/com/jarvis/android/ui/JarvisViewModel.kt")
        val selection = vm.substringAfter("fun selectGlobalChatAgentProject(")
            .substringBefore("val snapshot: StateFlow")
        assertTrue(selection.contains("capability.effect == \"READ_ONLY\""))
        assertTrue(selection.contains("capability.supports_attachments"))
        assertTrue(selection.contains(
            "capability.attachment_policy == \"one_image_or_document_read_only_no_web\""
        ))
        assertFalse(selection.contains("!capability.supports_attachments"))
        assertTrue(selection.contains("setChatAgentProjectFor(id, projectId)"))
    }

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
    fun copilotIsDefaultAndDraftsAreScopedByProject() {
        val ui = code("app/src/main/java/com/jarvis/android/ui/screens/WritingWorkspaceV1Screen.kt")
        assertTrue(ui.contains("var tab by rememberSaveable(projectId) { mutableStateOf(WorkspaceTab.CHAT) }"))
        assertTrue(ui.contains("var prompt by rememberSaveable(projectId) { mutableStateOf(\"\") }"))
        assertTrue(ui.contains("var draft by rememberSaveable(projectId) { mutableStateOf(\"\") }"))
        assertTrue(ui.contains("var councilPrompt by rememberSaveable(projectId) { mutableStateOf(\"\") }"))
        assertTrue(ui.contains("var query by rememberSaveable(projectId) { mutableStateOf(\"\") }"))
        assertTrue(ui.contains("var selectedCharacterId by rememberSaveable(projectId)"))
        assertTrue(ui.contains("var planningReply by rememberSaveable(projectId, active?.chapter_id)"))
        assertTrue(ui.contains("var currentSubTab by rememberSaveable(projectId, character.id)"))
        assertTrue(ui.contains("CharactersDirectory(\n            projectId = projectId,"))
    }

    @Test
    fun singleAndBatchPaidGenerationNeedsExplicitConfirmation() {
        val studio = code("app/src/main/java/com/jarvis/android/ui/writing/CharacterStudioScreen.kt")
        val scene = code("app/src/main/java/com/jarvis/android/ui/writing/SceneBuilderScreen.kt")
        assertTrue(studio.contains("pendingSingleGeneration = \"master\""))
        assertTrue(studio.contains("pendingSingleGeneration = \"angle\""))
        assertTrue(studio.contains("pendingTurnaroundGeneration = true"))
        assertTrue(studio.contains("Confirmar imagen de pago"))
        assertTrue(studio.contains("Confirmar generación de vistas"))
        assertTrue(studio.contains("vm.generateCharacterVisual("))
        assertTrue(studio.contains("vm.completeCharacterViews("))
        assertTrue(studio.contains("bulkMasterConfirmation"))
        assertTrue(scene.contains("pendingDirectorRequest = directorRequest.trim()"))
        assertTrue(scene.contains("pendingDirectorEvidenceId = candidate.evidence_id"))
        assertTrue(scene.contains("Confirmar generación desde canon"))
        assertTrue(scene.contains("Confirmar imagen Cloud"))
        assertTrue(scene.contains("if (generationEngine == \"cloud\")"))
        assertTrue(scene.contains("pendingManualCloudGeneration = true"))
        assertTrue(scene.contains("Generar imagen con este pasaje"))
    }

    @Test
    fun sceneAndTurnaroundAsyncResultsAreProjectScoped() {
        val vm = code("app/src/main/java/com/jarvis/android/ui/JarvisViewModel.kt")
        val scene = vm.substringAfter("fun generateSceneVisual(").substringBefore("\n    fun ", "")
        assertTrue(scene.contains("scopeVersion != sceneBuilderScopeVersion"))
        assertTrue(scene.contains("staged?.attachmentId?.let { deleteVisualStudioAttachments(listOf(it)) }"))
        val batch = vm.substringAfter("fun completeCharacterViews(").substringBefore("fun approveCharacterViewBatch(")
        assertTrue(batch.contains("scopeVersion != characterStudioScopeVersion"))
        assertTrue(batch.contains("current.projectId != projectId"))
        assertTrue(batch.contains("current.characterId != characterId"))
        assertTrue(batch.contains("!settings.value.isOwner"))
    }

    @Test
    fun approvedImagesAreShaVerifiedBeforeInlineCopilotPreview() {
        val api = code("app/src/main/java/com/jarvis/android/transport/live/WritingRoomApi.kt")
        val vm = code("app/src/main/java/com/jarvis/android/ui/JarvisViewModel.kt")
        val ui = code("app/src/main/java/com/jarvis/android/ui/screens/WritingWorkspaceV1Screen.kt")
        assertTrue(api.contains("data class CopilotApprovedVisualAsset("))
        assertTrue(api.contains("val visual_assets: List<CopilotApprovedVisualAsset>"))
        assertTrue(api.contains("recentMessages: List<CopilotRecentMessage>")) // contract evolution guard
        assertTrue(api.contains("put(\"history\", kotlinx.serialization.json.buildJsonArray"))
        assertTrue(vm.contains("loadApprovedCopilotVisuals(projectId, response.visual_assets)"))
        assertTrue(vm.contains("asset.status != \"APPROVED\""))
        assertTrue(vm.contains("asset.storage_state != \"stored\""))
        assertTrue(vm.contains("stageVisualAssetBase64("))
        assertTrue(vm.contains("copilotRecoveryEpoch == epoch"))
        assertTrue(vm.contains("deleteVisualStudioAttachments(_writingWorkspace.value.approvedVisualAttachments.values)"))
        assertTrue(ui.contains("chat.visual_assets.take(6).forEach"))
        assertTrue(ui.contains("REFERENCIA APROBADA"))
    }

    @Test
    fun copilotChatHistoryRemainsBoundedAndProjectScoped() {
        val vm = code("app/src/main/java/com/jarvis/android/ui/JarvisViewModel.kt")
        val api = code("app/src/main/java/com/jarvis/android/transport/live/WritingRoomApi.kt")
        assertTrue(vm.contains("current.busy || current.chatProjectId != projectId"))
        assertTrue(vm.contains("val existingHistory = current.chatHistory"))
        assertTrue(vm.contains("existingHistory.takeLast(6)"))
        assertTrue(vm.contains("recentMessages = recent"))
        assertTrue(api.contains("recentMessages.takeLast(12)"))
        assertTrue(api.contains("msg.content.take(1200)"))
    }

    @Test
    fun unsupportedProjectMutationsAreNotAdvertisedInTheUi() {
        val projects = code("app/src/main/java/com/jarvis/android/ui/screens/ProjectsScreen.kt")
        assertFalse(projects.contains("vm.createProject("))
        assertFalse(projects.contains("vm.renameProject("))
        assertFalse(projects.contains("vm.deleteProject("))
        assertTrue(projects.contains("strings.projectMutationsUnavailable"))
        assertFalse(projects.contains("contrato seguro de provisión"))
    }

    @Test
    fun modelFirstPortraitIntentNeverDispatchesCloudGenerationInAndroid() {
        val vm = code("app/src/main/java/com/jarvis/android/ui/JarvisViewModel.kt")
        val screen = code("app/src/main/java/com/jarvis/android/ui/screens/WritingWorkspaceV1Screen.kt")
        val api = code("app/src/main/java/com/jarvis/android/transport/live/WritingRoomApi.kt")
        val autoChat = vm.substringAfter("fun runWritingRoomAutoChat(")
            .substringBefore("        _writingWorkspace.value = current.copy(")
        // Android sends the user's request once, not a locally detected paid
        // operation. The authenticated backend owns policy, tools and receipts.
        assertFalse(autoChat.contains("looksLikeCopilotPortraitCommand(clean)"))
        assertFalse(autoChat.contains("looksLikeCopilotAppearanceCommand(clean)"))
        assertFalse(autoChat.contains("generateCopilotPortrait("))
        assertTrue(screen.contains("vm.runWritingRoomAutoChat("))
        assertTrue(screen.contains("referenceAttachmentId = state.copilotPendingReferenceId"))
        assertTrue(api.contains("/api/app/writing-room/agent/stream"))
        assertTrue(api.contains("Copilot requiere un backend AdaptiveAgent compatible"))
        assertFalse(api.contains("endpoint = \"/api/app/writing-room/chat/stream\""))
        assertTrue(api.contains("stableRequestId"))
        // Explicit visual-studio confirmation buttons remain available for
        // existing saved drafts and older flows; they are not text routers.
        assertTrue(vm.contains("fun confirmWritingCopilotPortrait(projectId: String)"))
        assertTrue(vm.contains("fun cancelWritingCopilotPortrait(projectId: String)"))
        assertTrue(screen.contains("state.pendingPortrait?.let { request ->"))
    }
}
