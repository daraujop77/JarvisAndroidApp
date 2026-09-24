package com.jarvis.android.ui.chat

import com.jarvis.android.data.state.RequestState

/**
 * Conversation-scoped view over the app-level request registry.
 *
 * The transport intentionally keeps all in-flight requests in one session map,
 * but chat UI state must never treat activity in conversation A as activity in B.
 */
object ConversationActivity {
    fun activeRequest(
        requests: Collection<RequestState>,
        conversationId: String?,
    ): RequestState? {
        if (conversationId.isNullOrBlank()) return null
        return requests
            .asSequence()
            .filter { it.conversationId == conversationId && !it.status.isTerminal }
            .maxByOrNull { it.startedAtMs }
    }

    fun activeConversationIds(requests: Collection<RequestState>): Set<String> =
        requests
            .asSequence()
            .filter { !it.status.isTerminal }
            .map { it.conversationId }
            .filter { it.isNotBlank() }
            .toSet()
}
