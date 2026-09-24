package com.jarvis.android.ui.chat

import com.jarvis.android.data.state.RequestState
import com.jarvis.android.data.state.RequestStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationActivityTest {

    @Test
    fun activeRequestIsScopedToOpenConversation() {
        val a = RequestState(
            clientRequestId = "req-a",
            conversationId = "conv-a",
            userText = "A",
            status = RequestStatus.Streaming,
            startedAtMs = 10,
        )
        val b = RequestState(
            clientRequestId = "req-b",
            conversationId = "conv-b",
            userText = "B",
            status = RequestStatus.Accepted,
            startedAtMs = 20,
        )

        assertEquals("req-a", ConversationActivity.activeRequest(listOf(a, b), "conv-a")?.clientRequestId)
        assertEquals("req-b", ConversationActivity.activeRequest(listOf(a, b), "conv-b")?.clientRequestId)
    }

    @Test
    fun completedRequestDoesNotMakeConversationBusy() {
        val completed = RequestState(
            clientRequestId = "req-a",
            conversationId = "conv-a",
            userText = "A",
            status = RequestStatus.Completed,
            startedAtMs = 10,
        )

        assertNull(ConversationActivity.activeRequest(listOf(completed), "conv-a"))
    }

    @Test
    fun activeConversationSetDoesNotIncludeTerminalRequests() {
        val requests = listOf(
            RequestState(
                clientRequestId = "req-a",
                conversationId = "conv-a",
                userText = "A",
                status = RequestStatus.Streaming,
            ),
            RequestState(
                clientRequestId = "req-b",
                conversationId = "conv-b",
                userText = "B",
                status = RequestStatus.Cancelled,
            ),
            RequestState(
                clientRequestId = "req-c",
                conversationId = "conv-c",
                userText = "C",
                status = RequestStatus.Pending,
            ),
        )

        val active = ConversationActivity.activeConversationIds(requests)

        assertEquals(setOf("conv-a", "conv-c"), active)
        assertTrue("conv-b" !in active)
    }

    @Test
    fun newestNonTerminalRequestWinsInsideSameConversation() {
        val older = RequestState(
            clientRequestId = "req-old",
            conversationId = "conv-a",
            userText = "old",
            status = RequestStatus.Streaming,
            startedAtMs = 10,
        )
        val newer = RequestState(
            clientRequestId = "req-new",
            conversationId = "conv-a",
            userText = "new",
            status = RequestStatus.Cancelling,
            startedAtMs = 30,
        )

        assertEquals(
            "req-new",
            ConversationActivity.activeRequest(listOf(older, newer), "conv-a")?.clientRequestId,
        )
    }
}
