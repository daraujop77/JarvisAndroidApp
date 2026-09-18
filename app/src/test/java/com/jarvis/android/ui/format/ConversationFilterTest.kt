package com.jarvis.android.ui.format

import com.jarvis.android.data.repo.ConversationSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationFilterTest {

    private val items = listOf(
        ConversationSummary("a", "Garage door", 2L),
        ConversationSummary("b", "Chapter outline", 1L),
    )

    @Test
    fun blankQueryKeepsOrder() {
        assertEquals(items, ConversationFilter.matching(items, "  "))
    }

    @Test
    fun matchIsCaseInsensitiveAndDropsTheRest() {
        assertEquals(listOf("a"), ConversationFilter.matching(items, "GARAGE").map { it.conversationId })
    }

    @Test
    fun nothingMatchesShowsEmpty() {
        assertEquals(emptyList<ConversationSummary>(), ConversationFilter.matching(items, "zzz"))
    }
}
