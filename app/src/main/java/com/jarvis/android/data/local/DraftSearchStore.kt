package com.jarvis.android.data.local

import com.jarvis.android.data.repo.ConversationSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Local composer drafts and local conversation search.
 *
 * A draft is one bounded text per conversation. It is written only when the
 * composer changes and it is never turned into an outbound request here: the
 * existing explicit Send action is the only thing that sends. Search matches
 * the already-persisted conversation title and never reads message bodies or
 * raw request payloads, and it never talks to the server.
 */
class DraftSearchStore(
    private val dao: JarvisDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /** Matches the existing chat input bound (composer maxLines = 4). */
    val maxDraftChars: Int = MAX_DRAFT_CHARS

    fun observeDraft(conversationId: String): Flow<String> =
        dao.observeDraft(conversationId).map { it.orEmpty() }

    /**
     * Persists [text] for exactly [conversationId]. Blank text deletes the row
     * so an emptied composer leaves no stale draft. Text past the bound is
     * truncated, never rejected, so typing cannot throw.
     */
    suspend fun saveDraft(conversationId: String, text: String) {
        val bounded = text.take(maxDraftChars)
        if (bounded.isBlank()) {
            dao.deleteDraft(conversationId)
        } else {
            dao.upsertDraft(
                ConversationDraftEntity(
                    conversationId = conversationId,
                    text = bounded,
                    updatedAtMs = clock(),
                ),
            )
        }
    }

    /**
     * Titles only, case-insensitive substring. An empty query returns the list
     * in its existing order, which the caller already sorted by recency.
     */
    fun filter(conversations: List<ConversationSummary>, query: String): List<ConversationSummary> {
        val needle = query.trim()
        if (needle.isEmpty()) return conversations
        return conversations.filter { it.title.contains(needle, ignoreCase = true) }
    }

    private companion object {
        const val MAX_DRAFT_CHARS = 4000
    }
}
