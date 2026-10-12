package com.jarvis.android.transport.live

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AgentStreamFallbackTest {
    private lateinit var server: MockWebServer
    private lateinit var store: JarvisAppSession.MemoryStore
    private lateinit var session: JarvisAppSession
    private val chatJson = """
        {"schema":"jarvis.writing-room.chat.v1",
         "classification":{"task_class":"NORMAL"},
         "selected_participant":"copilot",
         "turn":{"project_id":"prj_story","participant":{"id":"copilot","label":"Copilot"},
                 "response":{"text":"Respuesta del agente"}},
         "visual_assets":[]}
    """.trimIndent()

    private fun sse(text: String) = MockResponse()
        .setHeader("Content-Type", "text/event-stream")
        .setBody(
            "event: ready\ndata: {\"request_id\":\"req\"}\n\n" +
            "event: delta\ndata: {\"delta\":\"$text\"}\n\n" +
            "event: complete\ndata: $chatJson\n\n",
        )

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        store = JarvisAppSession.MemoryStore()
        store.put(mapOf(
            JarvisAppSession.KEY_TOKEN to "test-token",
            JarvisAppSession.KEY_BASE to server.url("/").toString().trimEnd('/'),
            JarvisAppSession.KEY_USER_ID to "owner-a",
            JarvisAppSession.KEY_USERNAME to "author",
            JarvisAppSession.KEY_ROLE to "owner",
            JarvisAppSession.KEY_EXPIRES to "2099-01-01T00:00:00Z",
        ))
        session = JarvisAppSession(store)
    }

    @After fun tearDown() {
        server.shutdown()
    }

    @Test fun adaptiveStreamIsPreferredWhenPresent() = runBlocking(Dispatchers.IO) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.path == "/api/app/writing-room/agent/stream") sse("Agent")
                else MockResponse().setResponseCode(500)
        }
        val deltas = mutableListOf<String>()
        val reply = session.writingRoomAutoChatStream(
            "prj_story", "Alexander", "Hola", onDelta = { deltas.add(it) },
        ).getOrThrow()
        assertEquals(listOf("Agent"), deltas)
        assertEquals("Respuesta del agente", reply.turn.response.text)
        assertEquals(1, server.requestCount)
        val request = server.takeRequest()
        assertEquals("/api/app/writing-room/agent/stream", request.path)
        val json = kotlinx.serialization.json.Json.parseToJsonElement(
            request.body.readUtf8()).jsonObject
        assertTrue(json.containsKey("conversation_id"))
        assertTrue(json.containsKey("request_id"))
    }

    @Test fun disabledAgentFallsBackToLegacyOnlyOn404() = runBlocking(Dispatchers.IO) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/api/app/writing-room/agent/stream" -> MockResponse().setResponseCode(404)
                "/api/app/writing-room/chat/stream" -> sse("Legacy")
                else -> MockResponse().setResponseCode(500)
            }
        }
        val answer = session.writingRoomAutoChatStream(
            "prj_story", "Alexander", "Hola", onDelta = {},
        ).getOrThrow()
        assertEquals("Respuesta del agente", answer.turn.response.text)
        assertEquals(2, server.requestCount)
        val first = server.takeRequest()
        val second = server.takeRequest()
        assertEquals("/api/app/writing-room/agent/stream", first.path)
        assertEquals("/api/app/writing-room/chat/stream", second.path)
        val json1 = kotlinx.serialization.json.Json.parseToJsonElement(first.body.readUtf8())
        val json2 = kotlinx.serialization.json.Json.parseToJsonElement(second.body.readUtf8())
        assertEquals(json1, json2) // Same request ID; no duplicate dispatch.
    }

    @Test fun serverErrorNeverTriggersUnsafeLegacyRetry() = runBlocking(Dispatchers.IO) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                MockResponse().setResponseCode(503)
        }
        val answer = session.writingRoomAutoChatStream(
            "prj_story", "Alexander", "Haz una imagen", onDelta = {},
        )
        assertTrue(answer.isFailure)
        assertEquals(1, server.requestCount)
        assertEquals("/api/app/writing-room/agent/stream", server.takeRequest().path)
    }

    @Test fun separateUsersAndProjectsHaveDifferentConversationIds() {
        val a1 = session.writingRoomConversationId("prj_story")
        val a2 = session.writingRoomConversationId("prj_story")
        val otherProject = session.writingRoomConversationId("prj_private")
        assertEquals(a1, a2)
        assertNotEquals(a1, otherProject)
        store.put(mapOf(JarvisAppSession.KEY_USER_ID to "child"))
        val child = session.writingRoomConversationId("prj_story")
        assertNotEquals(a1, child)
    }
}
