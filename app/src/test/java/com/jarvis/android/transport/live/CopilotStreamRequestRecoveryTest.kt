package com.jarvis.android.transport.live

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Deliberately broken SSE streams, with a read-only exact-request recovery. */
class CopilotStreamRequestRecoveryTest {
    private lateinit var server: MockWebServer
    private lateinit var session: JarvisAppSession

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        val store = JarvisAppSession.MemoryStore()
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
    @After fun tearDown() { server.shutdown() }

    private fun json(request: RecordedRequest) =
        Json.parseToJsonElement(request.body.readUtf8()).jsonObject

    @Test fun lostCompletionRecoversExactFullAnswerWithoutResendingPrompt() =
        runBlocking(Dispatchers.IO) {
            var originalId = ""
            val conversationId = session.writingRoomConversationId("prj_story")
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val body = json(request)
                    return when (request.path) {
                        "/api/app/writing-room/agent/stream" -> {
                            originalId = body["request_id"]!!.jsonPrimitive.content
                            MockResponse().setHeader("Content-Type", "text/event-stream")
                                .setBody("event: ready\ndata: {}\n\n")
                        }
                        "/api/app/writing-room/agent/request-status" -> {
                            assertEquals(originalId, body["request_id"]!!.jsonPrimitive.content)
                            assertEquals(conversationId, body["conversation_id"]!!.jsonPrimitive.content)
                            MockResponse().setBody(
                                """{"schema":"jarvis.writing-room.copilot.task.v1",
                                   "task_id":"ar_0123456789abcdef01234567",
                                   "request_id":"$originalId",
                                   "project_id":"prj_story","conversation_id":"$conversationId",
                                   "state":"DONE","agent_run_state":"SUCCEEDED",
                                   "reply_text":"Esta es la respuesta completa recuperada",
                                   "results":[],"steps":[]}""",
                            )
                        }
                        else -> MockResponse().setResponseCode(500)
                    }
                }
            }
            val answer = session.writingRoomAutoChatStream(
                "prj_story", "Alexander", "Consulta el canon", onDelta = {},
            ).getOrThrow()
            assertTrue(answer.recovered_from_interruption)
            assertEquals("Esta es la respuesta completa recuperada", answer.turn.response.text)
            assertEquals(originalId, answer.copilot_task!!.request_id)
            assertEquals(2, server.requestCount)
            assertTrue(originalId.isNotBlank())
        }

    @Test fun runningAgentIsNotMistakenForCompletedAfterStreamBreak() =
        runBlocking(Dispatchers.IO) {
            var originalId = ""
            val conversationId = session.writingRoomConversationId("prj_story")
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val body = json(request)
                    return when (request.path) {
                        "/api/app/writing-room/agent/stream" -> {
                            originalId = body["request_id"]!!.jsonPrimitive.content
                            MockResponse().setHeader("Content-Type", "text/event-stream")
                                .setBody("event: ready\ndata: {}\n\n")
                        }
                        "/api/app/writing-room/agent/request-status" -> MockResponse().setBody(
                            """{"schema":"jarvis.writing-room.copilot.task.v1",
                               "task_id":"ar_0123456789abcdef01234567",
                               "request_id":"$originalId",
                               "project_id":"prj_story","conversation_id":"$conversationId",
                               "state":"RUNNING","agent_run_state":"RUNNING",
                               "reply_text":"","results":[],"steps":[]}""",
                        )
                        else -> MockResponse().setResponseCode(500)
                    }
                }
            }
            val answer = session.writingRoomAutoChatStream(
                "prj_story", "Alexander", "Consulta el canon", onDelta = {},
            ).getOrThrow()
            assertTrue(answer.recovered_from_interruption)
            assertEquals("RUNNING", answer.copilot_task!!.state)
            assertTrue(answer.turn.response.text.contains("sigue trabajando"))
            assertEquals(2, server.requestCount)
        }

    @Test fun unavailableRecoveryFailsWithoutSecondAgentDispatch() =
        runBlocking(Dispatchers.IO) {
            server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream")
                .setBody("event: ready\ndata: {}\n\n"))
            server.enqueue(MockResponse().setResponseCode(404))
            val outcome = session.writingRoomAutoChatStream(
                "prj_story", "Alexander", "Crea una escena", onDelta = {},
            )
            assertTrue(outcome.isFailure)
            assertEquals("/api/app/writing-room/agent/stream", server.takeRequest().path)
            assertEquals("/api/app/writing-room/agent/request-status", server.takeRequest().path)
            assertEquals(2, server.requestCount)
        }

    @Test fun mismatchedRecoveredScopeIsNeverPresentedAsSuccessfulReply() {
        val card = CopilotTaskState(
            project_id = "prj_private", conversation_id = "chat-a",
            request_id = "req-a", task_id = "ar_0123456789abcdef01234567",
            state = "DONE", reply_text = "Private information",
        )
        assertNull(recoveredCopilotChat("prj_story", "chat-a", "req-a", card))
        assertNull(recoveredCopilotChat("prj_private", "chat-b", "req-a", card))
        assertNull(recoveredCopilotChat("prj_private", "chat-a", "req-b", card))
    }
}
