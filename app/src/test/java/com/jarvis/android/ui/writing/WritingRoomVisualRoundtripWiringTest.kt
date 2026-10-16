package com.jarvis.android.ui.writing

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Static navigation coverage complements MockWebServer image/recovery tests.
 * It cannot replace actual on-device Compose gestures before an APK release.
 */
class WritingRoomVisualRoundtripWiringTest {
    private fun source(path: String) =
        listOf(File(path), File("../$path"), File("../../$path"))
            .firstOrNull { it.isFile }?.readText()
            ?: error("Missing source $path")

    @Test fun copilotNavigatesToExactCharacterAndOwnedEditor() {
        val writing = source("app/src/main/java/com/jarvis/android/ui/screens/WritingWorkspaceV1Screen.kt")
        val visual = source("app/src/main/java/com/jarvis/android/ui/writing/VisualStudioScreen.kt")
        val characters = source("app/src/main/java/com/jarvis/android/ui/writing/CharacterStudioScreen.kt")

        assertTrue(writing.contains("onOpenCharacterStudio(portrait.characterId)"))
        assertTrue(writing.contains("visualCharacterOpenId = characterId"))
        assertTrue(writing.contains("openCharacterRequest = visualCharacterOpenRequest"))
        assertTrue(visual.contains("requestedCharacterId = requestedCharacterId"))
        assertTrue(visual.contains("pending") || visual.contains("openCharacterRequest = openCharacterRequest"))
        assertTrue(characters.contains("pendingCopilotCharacterSelection("))
        assertTrue(characters.contains("onEditInImageStudio(attachmentId, selectedCharacterId, asset)"))
        assertTrue(visual.contains("ImageStudioScreen("))
        assertTrue(visual.contains("vm.editCharacterVisualInStudio("))
    }

    @Test fun historicalChatImagesDecodeOnIoAndNeverShowPreviousAssetForApproval() {
        val writing = source("app/src/main/java/com/jarvis/android/ui/screens/WritingWorkspaceV1Screen.kt")
        val visualStart = writing.indexOf("val decodedCandidate by produceState(")
        val approval = writing.indexOf("candidatePreviewBitmap != null", visualStart)
        assertTrue(visualStart >= 0 && approval > visualStart)
        val block = writing.substring(visualStart, approval)
        assertTrue(block.contains("key1 = turn.generatedPreviewAttachmentId"))
        assertTrue(block.contains("withContext(Dispatchers.IO)"))
        assertTrue(block.contains("vm.attachmentStore.decodeThumbnail(id, maxSize = 768)"))
        assertTrue(block.contains("decodedCandidate.first == turn.generatedPreviewAttachmentId"))
        // A synchronous BitmapFactory decode in composition freezes the chat.
        assertTrue(!block.contains("remember(turn.generatedPreviewAttachmentId) {"))

        val dialog = writing.substringAfter("expandedVisual?.let { imageId ->")
            .substringBefore("    Row(\n        modifier = Modifier.fillMaxWidth(),")
        assertTrue(dialog.contains("val decodedDialog by produceState("))
        assertTrue(dialog.contains("key1 = imageId"))
        assertTrue(dialog.contains("withContext(Dispatchers.IO)"))
        assertTrue(dialog.contains("decodeThumbnail(imageId, maxSize = 1600)"))
        assertTrue(dialog.contains("decodedDialog.first == imageId"))
        assertTrue(dialog.contains("if (fullImage != null)"))
        assertTrue(!dialog.contains("remember(imageId) {"))
    }

    @Test fun gallerySupportsZoomAndReadOnlyRecoveryWithoutGeneratingAgain() {
        val writing = source("app/src/main/java/com/jarvis/android/ui/screens/WritingWorkspaceV1Screen.kt")
        val characters = source("app/src/main/java/com/jarvis/android/ui/writing/CharacterStudioScreen.kt")
        val preview = source("app/src/main/java/com/jarvis/android/ui/writing/VisualAssetPreview.kt")
        val vm = source("app/src/main/java/com/jarvis/android/ui/JarvisViewModel.kt")

        assertTrue(writing.contains("onDecodedChange = { previewDecoded = it }"))
        assertTrue(preview.contains("Dialog("))
        assertTrue(preview.contains("onRetry =") || preview.contains("onClick = onRetry"))
        assertTrue(preview.contains("store.decodeThumbnail(attachmentId, 2048)"))
        assertTrue(characters.contains("VisualAssetPreview("))
        assertTrue(vm.contains("matchesListedVisualAssetReceipt(projectId, listedAsset, content)"))
        assertTrue(vm.contains("verifiedCharacterEditParent("))
        val edit = vm.substring(vm.indexOf("fun editCharacterVisualInStudio("))
        assertTrue(edit.contains("!settings.value.isOwner"))
        assertTrue(edit.indexOf("verifiedCharacterEditParent(") <
            edit.indexOf("container.liveSession.editVisualAssetImage("))
        assertTrue(vm.contains("recoverWritingCopilotPortraits(projectId)"))
    }
}
