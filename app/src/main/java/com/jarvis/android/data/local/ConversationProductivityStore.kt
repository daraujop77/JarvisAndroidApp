package com.jarvis.android.data.local

import com.jarvis.android.data.repo.ConversationSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Device-only conversation chrome: pin, local title, last-opened recency and
 * hide-on-this-device. None of these rows are a server conversation, unread
 * count, or delete. Archive only sets [ConversationLocalMetaEntity.archived];
 * `conversations` and `messages` are never removed here.
 */
class ConversationProductivityStore(
    private val dao: JarvisDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    val maxLocalTitleChars: Int = MAX_LOCAL_TITLE_CHARS

    fun observeMeta(): Flow<Map<String, ConversationLocalMetaEntity>> =
        dao.observeLocalMeta().map { rows -> rows.associateBy { it.conversationId } }

    suspend fun setPinned(conversationId: String, pinned: Boolean) {
        val current = dao.localMeta(conversationId) ?: ConversationLocalMetaEntity(conversationId)
        dao.upsertLocalMeta(current.copy(pinned = pinned))
    }

    /**
     * Hide or unhide on this device only. Does not delete Room history and
     * does not send anything to the server.
     */
    suspend fun setArchived(conversationId: String, archived: Boolean) {
        val current = dao.localMeta(conversationId) ?: ConversationLocalMetaEntity(conversationId)
        dao.upsertLocalMeta(current.copy(archived = archived))
    }

    suspend fun setArchived(conversationIds: Collection<String>, archived: Boolean) {
        conversationIds.forEach { setArchived(it, archived) }
    }

    /**
     * Overlay title for the list and chat header. Blank [title] clears the
     * overlay so the stored conversation title is shown again. This is never
     * written onto [ConversationEntity.title].
     */
    suspend fun setLocalTitle(conversationId: String, title: String) {
        val current = dao.localMeta(conversationId) ?: ConversationLocalMetaEntity(conversationId)
        dao.upsertLocalMeta(current.copy(localTitle = title.trim().take(maxLocalTitleChars)))
    }

    suspend fun markOpened(conversationId: String) {
        val current = dao.localMeta(conversationId) ?: ConversationLocalMetaEntity(conversationId)
        dao.upsertLocalMeta(current.copy(lastOpenedAtMs = clock()))
    }

    fun decorate(
        conversations: List<ConversationSummary>,
        meta: Map<String, ConversationLocalMetaEntity>,
    ): List<ConversationListItem> = conversations.map { summary ->
        val row = meta[summary.conversationId]
        ConversationListItem(
            conversationId = summary.conversationId,
            storedTitle = summary.title,
            displayTitle = row?.localTitle?.takeIf { it.isNotBlank() } ?: summary.title,
            updatedAtMs = summary.updatedAtMs,
            lastOpenedAtMs = row?.lastOpenedAtMs ?: 0L,
            pinned = row?.pinned == true,
            archived = row?.archived == true,
            locallyRenamed = !row?.localTitle.isNullOrBlank(),
        )
    }

    /**
     * Search matches the stored title and the local overlay title. Hidden
     * rows are omitted unless [showHidden]. Pins sort first; otherwise local
     * last-opened (when set) outranks stored [ConversationSummary.updatedAtMs]
     * without claiming a server unread feed.
     */
    fun overlay(
        conversations: List<ConversationSummary>,
        meta: Map<String, ConversationLocalMetaEntity>,
        query: String = "",
        showHidden: Boolean = false,
    ): List<ConversationListItem> {
        val items = decorate(conversations, meta)
        val visible = if (showHidden) items.filter { it.archived } else items.filterNot { it.archived }
        val matched = filter(visible, query)
        return matched.sortedWith(
            compareByDescending<ConversationListItem> { it.pinned }
                .thenByDescending { it.sortRecencyMs },
        )
    }

    fun filter(items: List<ConversationListItem>, query: String): List<ConversationListItem> {
        val needle = query.trim()
        if (needle.isEmpty()) return items
        return items.filter { item ->
            item.displayTitle.contains(needle, ignoreCase = true) ||
                item.storedTitle.contains(needle, ignoreCase = true)
        }
    }

    private companion object {
        const val MAX_LOCAL_TITLE_CHARS = 80
    }
}

/**
 * List-row presentation. [activitySinceOpen] is local last-opened vs stored
 * updatedAt — not a server unread flag.
 */
data class ConversationListItem(
    val conversationId: String,
    val storedTitle: String,
    val displayTitle: String,
    val updatedAtMs: Long,
    val lastOpenedAtMs: Long,
    val pinned: Boolean,
    val archived: Boolean,
    val locallyRenamed: Boolean,
) {
    val sortRecencyMs: Long get() = lastOpenedAtMs.takeIf { it > 0L } ?: updatedAtMs

    val activitySinceOpen: Boolean
        get() = lastOpenedAtMs > 0L && updatedAtMs > lastOpenedAtMs
}
