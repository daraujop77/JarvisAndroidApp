package com.jarvis.android.ui.writing

import com.jarvis.android.transport.live.JarvisAppSession
import com.jarvis.android.transport.live.WritingCopilotHistory
import com.jarvis.android.transport.live.WritingCopilotHistoryTurn
import com.jarvis.android.transport.live.WritingCopilotHistoryVisual
import com.jarvis.android.transport.live.WritingRoomAutoChat
import com.jarvis.android.ui.JarvisViewModel

/**
 * Archive metadata only. Never treat an ID as image proof or approve it
 * until its authenticated SHA-bound original is actually downloaded.
 */
internal fun verifiedHistoricalCopilotVisual(
    item: WritingCopilotHistoryTurn,
): WritingCopilotHistoryVisual? = item.visual_asset?.takeIf { x ->
    item.request_id.isNotBlank() &&
        x.asset_id.matches(Regex("^va_[A-Za-z0-9_]{6,80}$")) &&
        x.sha256.matches(Regex("^[a-fA-F0-9]{64}$")) &&
        x.status in setOf("CANDIDATE", "APPROVED") &&
        x.storage_state in setOf("stored", "pending") &&
        (x.character_id.isBlank() && x.kind in
            setOf("SCENE_ART", "REFERENCE", "LOCATION_REFERENCE") ||
            x.character_id.startsWith("character:"))
}

/**
 * Server snapshots own historical text. The live local turn still owns its
 * actual candidate preview, task state and original attachment references.
 * Rehydration must not replace a richer in-flight or completed local bubble.
 */
internal fun mergeCopilotConversationHistory(
    projectId: String,
    archive: WritingCopilotHistory,
    local: List<JarvisViewModel.WritingWorkspaceChatTurn>,
): List<JarvisViewModel.WritingWorkspaceChatTurn> {
    if (projectId.isBlank() || archive.project_id != projectId ||
        archive.schema != "jarvis.writing-room.agent.history.v1" ||
        archive.conversation_id.isBlank() || archive.turns.size > 80
    ) return local
    val unused = local.toMutableList()
    val restored = archive.turns.mapNotNull { item ->
        if (item.request_id.isBlank() || item.request_id.length > 160 ||
            item.user_text.isBlank() || item.user_text.length > 6000 ||
            item.assistant_text.isBlank() || item.assistant_text.length > 12000
        ) return@mapNotNull null
        val exact = unused.indexOfFirst { existing ->
            existing.historicalRequestId == item.request_id ||
                existing.response?.copilot_task?.request_id == item.request_id
        }
        // Text matching is a compatibility fallback only for UNIQUE pairs.
        // Two identical repeated prompts are distinct requests and must never
        // steal each other's visual asset provenance.
        val textPairUnique = archive.turns.count {
            it.user_text == item.user_text && it.assistant_text == item.assistant_text
        } == 1
        val candidateIndices = unused.indices.filter { n ->
            unused[n].prompt == item.user_text &&
                unused[n].response?.turn?.response?.text == item.assistant_text
        }
        val found = if (exact >= 0) exact else
            candidateIndices.singleOrNull()?.takeIf { textPairUnique } ?: -1
        val visual = verifiedHistoricalCopilotVisual(item)
        if (found >= 0) {
            val rich = unused.removeAt(found)
            val identified = rich.copy(
                historicalRequestId = rich.historicalRequestId ?: item.request_id,
            )
            // Never overwrite a live original or replace its exact media
            // provenance with a possibly older archived receipt.
            if (identified.candidateAssetId != null || visual == null)
                return@mapNotNull identified
            return@mapNotNull identified.copy(
                candidateAssetId = visual.asset_id,
                candidateSha256 = visual.sha256,
                candidateStatus = visual.status,
                candidateStorageState = visual.storage_state,
                candidateKind = visual.kind,
                candidateCharacterId = visual.character_id,
                candidatePerspective = visual.perspective,
            )
        }
        JarvisViewModel.WritingWorkspaceChatTurn(
            prompt = item.user_text,
            historicalRequestId = item.request_id,
            candidateAssetId = visual?.asset_id,
            candidateSha256 = visual?.sha256,
            candidateStatus = visual?.status,
            candidateStorageState = visual?.storage_state,
            candidateKind = visual?.kind,
            candidateCharacterId = visual?.character_id,
            candidatePerspective = visual?.perspective,
            response = WritingRoomAutoChat(
                schema = "jarvis.writing-room.chat.v1",
                selected_participant = "copilot",
                recovered_from_interruption = true,
                turn = JarvisAppSession.WritingRoomTurn(
                    project_id = projectId,
                    participant = JarvisAppSession.WritingRoomParticipant(
                        id = "copilot", label = "Copilot",
                    ),
                    response = JarvisAppSession.WritingRoomText(
                        text = item.assistant_text,
                    ),
                ),
            ),
        )
    }
    // Keep live messages, pending submissions and the richer last-task recovery
    // that arrived after the archive request. Never drop or execute them.
    return restored + unused
}
