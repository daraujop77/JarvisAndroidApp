package com.jarvis.android.ui.writing

import com.jarvis.android.transport.live.JarvisAppSession
import com.jarvis.android.transport.live.WritingCopilotHistory
import com.jarvis.android.transport.live.WritingRoomAutoChat
import com.jarvis.android.ui.JarvisViewModel

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
        val found = unused.indexOfFirst { existing ->
            existing.historicalRequestId == item.request_id ||
                existing.response?.copilot_task?.request_id == item.request_id
        }.takeIf { it >= 0 } ?: unused.indexOfFirst {
            it.prompt == item.user_text &&
                it.response?.turn?.response?.text == item.assistant_text
        }
        if (found >= 0) return@mapNotNull unused.removeAt(found)
        JarvisViewModel.WritingWorkspaceChatTurn(
            prompt = item.user_text,
            historicalRequestId = item.request_id,
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
