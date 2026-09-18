package com.jarvis.android.ui.format

import com.jarvis.android.data.repo.ConversationSummary

/** Title search for the chat list. Blank query returns everything, case-insensitive. */
object ConversationFilter {
    fun matching(items: List<ConversationSummary>, query: String): List<ConversationSummary> {
        val q = query.trim()
        if (q.isEmpty()) return items
        return items.filter { it.title.contains(q, ignoreCase = true) }
    }
}
