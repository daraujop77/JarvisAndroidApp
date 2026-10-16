package com.jarvis.android.ui.writing

import com.jarvis.android.transport.live.JarvisAppSession
import com.jarvis.android.transport.live.WritingCopilotHistory
import com.jarvis.android.transport.live.WritingCopilotHistoryTurn
import com.jarvis.android.transport.live.WritingRoomAutoChat
import com.jarvis.android.ui.JarvisViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CopilotConversationHistoryTest {
    private fun history(vararg ids: String, project: String = "story") =
        WritingCopilotHistory(
            schema = "jarvis.writing-room.agent.history.v1",
            project_id = project, conversation_id = "writing-room-story",
            turns = ids.map { id -> WritingCopilotHistoryTurn(
                request_id = id, task_id = "ar_$id",
                user_text = "Pregunta $id", assistant_text = "Respuesta $id",
                state = "SUCCEEDED",
            ) },
        )

    @Test fun processRestartRebuildsOrderedTextOnlyWithoutNewGeneration() {
        val turns = mergeCopilotConversationHistory(
            "story", history("a", "b", "c"), emptyList(),
        )
        assertEquals(listOf("Pregunta a", "Pregunta b", "Pregunta c"),
            turns.map { it.prompt })
        assertEquals(listOf("Respuesta a", "Respuesta b", "Respuesta c"),
            turns.map { it.response?.turn?.response?.text })
        assertTrue(turns.all { it.candidateAssetId == null })
        assertTrue(turns.all { it.historicalRequestId != null })
    }

    @Test fun newerRichBubbleWinsAndPendingLocalTurnSurvives() {
        val rich = JarvisViewModel.WritingWorkspaceChatTurn(
            prompt = "Pregunta b", historicalRequestId = "b",
            response = WritingRoomAutoChat(
                copilot_task = com.jarvis.android.transport.live.CopilotTaskState(
                    task_id = "ar_b", project_id = "story",
                    request_id = "b", conversation_id = "writing-room-story",
                ),
                turn = JarvisAppSession.WritingRoomTurn(
                    response = JarvisAppSession.WritingRoomText("Respuesta rica"),
                ),
            ), candidateAssetId = "va_verified",
        )
        val pending = JarvisViewModel.WritingWorkspaceChatTurn(prompt = "Nueva pregunta")
        val restored = mergeCopilotConversationHistory(
            "story", history("a", "b"), listOf(rich, pending),
        )
        assertEquals(3, restored.size)
        assertEquals("Respuesta rica", restored[1].response?.turn?.response?.text)
        assertEquals("va_verified", restored[1].candidateAssetId)
        assertEquals("Nueva pregunta", restored.last().prompt)
        assertEquals(null, restored.last().response)
    }

    @Test fun replayNeverMixesAnotherProjectOrOverwritesVisibleLocalData() {
        val local = listOf(JarvisViewModel.WritingWorkspaceChatTurn(
            prompt = "Privado", historicalRequestId = "private",
        ))
        assertEquals(local, mergeCopilotConversationHistory(
            "story", history("x", project = "another-story"), local,
        ))
        assertEquals(local, mergeCopilotConversationHistory(
            "story", history("x").copy(conversation_id = ""), local,
        ))
    }

    @Test fun malformedOrHugeTurnCannotAppearAsRestoredMessage() {
        val good = history("ok")
        val altered = good.copy(turns = good.turns + listOf(
            WritingCopilotHistoryTurn(
                request_id = "bad", user_text = "x".repeat(6001),
                assistant_text = "untrusted",
            ),
        ))
        assertEquals(1, mergeCopilotConversationHistory(
            "story", altered, emptyList(),
        ).size)
    }

    @Test fun archivedVisualStaysOnItsOriginalTurnNotLatestTaskOrGlobalGallery() {
        val original = history("first", "second")
        val a = original.turns.first().copy(
            visual_asset = com.jarvis.android.transport.live.WritingCopilotHistoryVisual(
                asset_id = "va_" + "a".repeat(32),
                sha256 = "b".repeat(64),
                status = "CANDIDATE", storage_state = "stored",
                kind = "PRIMARY_REFERENCE",
                character_id = "character:alexander", perspective = "front",
            ),
        )
        val result = mergeCopilotConversationHistory(
            "story", original.copy(turns = listOf(a, original.turns.last())),
            emptyList(),
        )
        assertEquals(a.visual_asset?.asset_id, result.first().candidateAssetId)
        assertEquals(a.visual_asset?.sha256, result.first().candidateSha256)
        assertEquals("first", result.first().historicalRequestId)
        assertEquals(null, result.last().candidateAssetId)
        assertEquals(null, result.first().generatedPreviewAttachmentId)
    }

    @Test fun maliciousOrMalformedArchiveNeverCreatesAVisualApprovalOrDownload() {
        val original = history("good")
        val bad = original.turns.single().copy(
            visual_asset = com.jarvis.android.transport.live.WritingCopilotHistoryVisual(
                asset_id = "va_notvalid", sha256 = "unknown",
                status = "APPROVED", storage_state = "stored",
                kind = "PRIMARY_REFERENCE", character_id = "character:alexander",
            ),
        )
        val merged = mergeCopilotConversationHistory(
            "story", original.copy(turns = listOf(bad)), emptyList(),
        )
        assertEquals(null, merged.single().candidateAssetId)
        assertEquals(null, merged.single().generatedPreviewAttachmentId)
    }
}
