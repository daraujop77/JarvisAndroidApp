package com.jarvis.android.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Regression: a Wiki outage cannot prevent read-only recovery of saved portraits. */
class WritingCopilotIndependentRecoveryWiringTest {
    @Test
    fun portraitRecoveryStartsBeforeOptionalWikiAndTimelineRequests() {
        val source = listOf(
            File("app/src/main/java/com/jarvis/android/ui/JarvisViewModel.kt"),
            File("../app/src/main/java/com/jarvis/android/ui/JarvisViewModel.kt"),
        ).firstOrNull { it.isFile }?.readText()
            ?: error("Missing checked-in ViewModel source")

        val begin = source.indexOf("    fun refreshWritingWorkspace(projectId: String) {")
        val end = source.indexOf("    fun recoverWritingCopilotPortraits(projectId: String)", begin)
        assertTrue(begin >= 0 && end > begin)
        val loader = source.substring(begin, end)

        val recovery = loader.indexOf("        recoverWritingCopilotPortraits(projectId)")
        val optionalWiki = loader.indexOf("val wikiHome = ")
        val optionalTimeline = loader.indexOf("val wikiTimeline = ")
        assertTrue(recovery >= 0)
        assertTrue(optionalWiki > recovery && optionalTimeline > recovery)
        assertEquals(
            1,
            Regex("""(?m)^        recoverWritingCopilotPortraits\(projectId\)$""")
                .findAll(loader).count(),
        )
        assertTrue(loader.indexOf("if (publicFailure != null)") > recovery)

        // A project switch is an authorization boundary. Recovered portraits
        // are private staged image bytes, not just a harmless display list.
        assertTrue(loader.contains(
            "_writingWorkspace.value.copilotRecoveredPortraits.mapNotNull"
        ))
        assertTrue(loader.contains("it.previewAttachmentId"))
        assertTrue(loader.indexOf(
            "_writingWorkspace.value.copilotRecoveredPortraits.mapNotNull"
        ) < loader.indexOf("WritingWorkspaceState(chatProjectId = projectId)"))
    }

    @Test
    fun galleryDisposesUnpublishedCopiesWhenAsyncRecoveryLosesItsProject() {
        val viewModel = listOf(
            File("app/src/main/java/com/jarvis/android/ui/JarvisViewModel.kt"),
            File("../app/src/main/java/com/jarvis/android/ui/JarvisViewModel.kt"),
        ).first { it.isFile }.readText()
        val code = viewModel.substringAfter("fun recoverWritingCopilotPortraits(projectId: String)")
            .substringBefore("fun refreshKnowledgeAtlas(")
        assertTrue(code.contains("val stagedThisRecovery = mutableSetOf<String>()"))
        assertTrue(code.contains("if (!published)"))
        assertTrue(code.contains("deleteVisualStudioAttachments(stagedThisRecovery)"))
        assertTrue(code.contains("replacedCopilotPortraitCopies("))
        assertTrue(code.contains("deleteVisualStudioAttachments(disposableIds)"))
        assertTrue(code.contains("staged.mimeType == content.mime_type"))
        assertTrue(code.contains("content.mime_type in setOf(\"image/png\", \"image/jpeg\", \"image/webp\")"))
    }

    @Test
    fun archivedVisualDownloadsAreScopedToTheirOriginalMessageAndCleanedOnProjectSwitch() {
        val source = listOf(
            File("app/src/main/java/com/jarvis/android/ui/JarvisViewModel.kt"),
            File("../app/src/main/java/com/jarvis/android/ui/JarvisViewModel.kt"),
        ).first { it.isFile }.readText()
        val start = source.indexOf("fun recoverCopilotHistoricalImage(")
        val end = source.indexOf("fun recoverWritingCopilotPortraits(", start)
        assertTrue(start > 0 && end > start)
        val body = source.substring(start, end)
        assertTrue(body.contains("it.historicalRequestId == requestId"))
        assertTrue(body.contains("it.candidateAssetId == assetId"))
        assertTrue(body.contains("fetched.sha256.equals(source.candidateSha256"))
        assertTrue(body.contains("stageVisualAssetBase64("))
        assertTrue(body.contains("deleteVisualStudioAttachments(listOf(staged.attachmentId))"))
        assertTrue(body.contains("generatedPreviewAttachmentId = staged.attachmentId"))
        assertTrue(body.contains("copilotRecoveredPortraits = now.copilotRecoveredPortraits.filterNot"))
        assertTrue(!body.contains("editVisualAssetImage("))
        assertTrue(!body.contains("generateImage("))
        val refresh = source.substring(
            source.indexOf("fun refreshWritingWorkspace(projectId: String)"),
            source.indexOf("fun recoverCopilotHistoricalImage("),
        )
        assertTrue(refresh.contains(
            "_writingWorkspace.value.chatHistory.mapNotNull"
        ))
        assertTrue(refresh.indexOf("_writingWorkspace.value.chatHistory.mapNotNull")
            < refresh.indexOf("WritingWorkspaceState(chatProjectId = projectId)"))
    }
}
