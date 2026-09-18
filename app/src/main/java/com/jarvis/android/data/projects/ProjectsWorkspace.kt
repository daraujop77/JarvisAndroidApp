package com.jarvis.android.data.projects

import com.jarvis.android.data.state.ConnectionState
import java.time.Instant

/**
 * Pure A5 presentation rules for the Projects shell.
 *
 * Fixture-only. Never invents a live backend. Release builds must hide
 * fixture claims until a PC-A project contract is approved.
 */
object ProjectsWorkspace {

    enum class Filter { ALL, ACTIVE, ARCHIVED }

    enum class Sort { UPDATED_DESC, TITLE_ASC }

    enum class Surface { HIDDEN, LOADING, EMPTY, ERROR, OFFLINE, LOADED }

    data class Query(
        val text: String = "",
        val filter: Filter = Filter.ALL,
        val sort: Sort = Sort.UPDATED_DESC,
    )

    const val NOT_LIVE_LABEL = "Local fixtures. Not live-backed."
    const val RELEASE_HIDDEN = "Projects are not shown. This build has no live projects contract."

    fun visibleInBuild(debugBuild: Boolean): Boolean = debugBuild

    fun surface(
        debugBuild: Boolean,
        result: ProjectsResult,
        connection: ConnectionState,
    ): Surface {
        if (!debugBuild) return Surface.HIDDEN
        return when (result) {
            ProjectsResult.Loading -> Surface.LOADING
            ProjectsResult.Empty -> Surface.EMPTY
            is ProjectsResult.Error ->
                if (connection == ConnectionState.OFFLINE || connection == ConnectionState.DISCONNECTED) {
                    Surface.OFFLINE
                } else {
                    Surface.ERROR
                }
            is ProjectsResult.Loaded -> Surface.LOADED
        }
    }

    fun visibleProjects(
        debugBuild: Boolean,
        result: ProjectsResult,
        query: Query,
    ): List<ProjectSummary> {
        if (!debugBuild) return emptyList()
        val loaded = result as? ProjectsResult.Loaded ?: return emptyList()
        return apply(loaded.projects, query)
    }

    fun apply(projects: List<ProjectSummary>, query: Query): List<ProjectSummary> {
        val needle = query.text.trim()
        val filtered = projects.filter { project ->
            when (query.filter) {
                Filter.ALL -> true
                Filter.ACTIVE -> project.state == ProjectState.ACTIVE
                Filter.ARCHIVED -> project.state == ProjectState.ARCHIVED
            }
        }.filter { project ->
            needle.isEmpty() || project.title.contains(needle, ignoreCase = true)
        }
        return when (query.sort) {
            Sort.UPDATED_DESC -> filtered.sortedByDescending { it.updatedAtMs }
            Sort.TITLE_ASC -> filtered.sortedBy { it.title.lowercase() }
        }
    }

    fun groupConversations(conversations: List<ProjectConversation>): List<ProjectConversation> =
        conversations.sortedByDescending { it.updatedAtMs }

    fun searchConversations(
        conversations: List<ProjectConversation>,
        text: String,
    ): List<ProjectConversation> {
        val needle = text.trim()
        val grouped = groupConversations(conversations)
        if (needle.isEmpty()) return grouped
        return grouped.filter { it.title.contains(needle, ignoreCase = true) }
    }

    fun sanitizedId(id: String?): String {
        val trimmed = id?.trim().orEmpty()
        if (trimmed.isEmpty()) return "—"
        if (trimmed.any { it.isWhitespace() } || trimmed.contains('{') || trimmed.contains('"')) {
            return "opaque"
        }
        return if (trimmed.length <= 16) trimmed else trimmed.take(12) + "…"
    }

    fun formatTimestamp(epochMs: Long): String = Instant.ofEpochMilli(epochMs).toString()

    fun conversationCountLabel(count: Int): String = when (count) {
        0 -> "No linked chats"
        1 -> "1 linked chat"
        else -> "$count linked chats"
    }
}
