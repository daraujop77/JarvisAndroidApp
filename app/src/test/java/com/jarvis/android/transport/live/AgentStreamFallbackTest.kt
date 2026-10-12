package com.jarvis.android.transport.live

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
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
            "event: progress\ndata: {\"phase\":\"thinking\"}\n\n" +
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
        val phases = mutableListOf<String>()
        val reply = session.writingRoomAutoChatStream(
            "prj_story", "Alexander", "Hola",
            onProgress = { phases.add(it) },
            onDelta = { deltas.add(it) },
        ).getOrThrow()
        assertEquals(listOf("thinking"), phases)
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

    @Test fun capabilityProbeNeverRunsModelAndReturnsTrueOnlyWhenEnabled() =
        runBlocking(Dispatchers.IO) {
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    if (request.path == "/api/app/writing-room/agent/latest") {
                        MockResponse().setBody(
                            """{"schema":"jarvis.writing-room.copilot.task.v1",
                            "project_id":"prj_story","state":"NONE","steps":[],"results":[]}""",
                        )
                    } else MockResponse().setResponseCode(404)
            }
            assertTrue(session.writingRoomAdaptiveReady("prj_story"))
            val request = server.takeRequest()
            assertEquals("/api/app/writing-room/agent/latest", request.path)
            assertTrue(request.body.readUtf8().contains("conversation_id"))
        }

    @Test fun agentConfirmationCarriesTheFrozenActionHash() =
        runBlocking(Dispatchers.IO) {
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    if (request.path == "/api/app/writing-room/agent/confirm") {
                        MockResponse().setBody(
                            """{"schema":"jarvis.writing-room.copilot.task.v1",
                            "task_id":"ar_0123456789abcdef01234567",
                            "project_id":"prj_story","conversation_id":"wrc_ab",
                            "request_mode":"local_only","state":"DONE",
                            "requires_confirmation":false,"steps":[],"results":[]}""",
                        )
                    } else MockResponse().setResponseCode(500)
            }
            val hash = "a".repeat(64)
            val result = session.writingRoomCopilotTaskRun(
                projectId = "prj_story",
                taskId = "ar_0123456789abcdef01234567",
                confirmed = true,
                conversationId = "wrc_ab", actionHash = hash, mode = "local_only",
            ).getOrThrow()
            assertEquals("DONE", result.state)
            val request = server.takeRequest()
            assertEquals("/api/app/writing-room/agent/confirm", request.path)
            val body = request.body.readUtf8()
            assertTrue(body.contains(hash))
            assertTrue(body.contains("local_only"))
            assertTrue(body.contains("wrc_ab"))
        }

    @Test fun globalProjectAgentUsesExplicitProjectAndExistingChatScope() =
        runBlocking(Dispatchers.IO) {
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    if (request.path == "/api/app/chat/agent/project/turn") {
                        MockResponse().setBody(
                            """{"schema":"jarvis.chat.turn.v1","route":"adaptive_agent",
                            "response":{"text":"Canon localizado","provider":"jarvis-adaptive"}}""",
                        )
                    } else MockResponse().setResponseCode(500)
            }
            val result = session.chatGlobalProjectAgentTurn(
                projectId = "prj_story", conversationId = "chat-project-a",
                requestId = "req-1234", prompt = "Busca el canon",
                mode = "local_only",
            ).getOrThrow()
            assertEquals("Canon localizado", result.response.text)
            val request = server.takeRequest()
            assertEquals("/api/app/chat/agent/project/turn", request.path)
            val body = request.body.readUtf8()
            assertTrue(body.contains("\\"project_id\\":\\"prj_story\\""))
            assertTrue(body.contains("\\"conversation_id\\":\\"chat-project-a\\""))
            assertTrue(body.contains("\\"mode\\":\\"local_only\\""))
        }

    @Test fun projectAgentBindingIsExplicitPerAccountAndConversation() {
        assertEquals(null, session.chatAgentProjectFor("chat-a"))
        session.setChatAgentProjectFor("chat-a", "prj_story")
        assertEquals("prj_story", session.chatAgentProjectFor("chat-a"))
        assertEquals(null, session.chatAgentProjectFor("chat-b"))
        session.setChatAgentProjectFor("chat-a", null)
        assertEquals(null, session.chatAgentProjectFor("chat-a"))
        session.setChatAgentProjectFor("chat-a", "prj_story")
        store.put(mapOf(JarvisAppSession.KEY_USER_ID to "child"))
        assertEquals(null, session.chatAgentProjectFor("chat-a"))
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
