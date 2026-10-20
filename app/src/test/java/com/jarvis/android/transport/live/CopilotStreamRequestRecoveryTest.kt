package com.jarvis.android.transport.live

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit
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
    private lateinit var store: JarvisAppSession.MemoryStore

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
    @After fun tearDown() { server.shutdown() }

    private fun json(request: RecordedRequest) =
        Json.parseToJsonElement(request.body.readUtf8()).jsonObject

    /** SSE data must be one line; additional raw JSON lines are ignored
     * by the production event parser. Compact multiline test fixtures here.
     */
    private fun completedSse(json: String) = MockResponse()
        .setHeader("Content-Type", "text/event-stream")
        .setBody("event: complete\ndata: ${json.replace("\n", "").replace("\r", "")}\n\n")

    @Test fun attachedImageAndTextUseOneAuthenticatedAgentRequest() =
        runBlocking(Dispatchers.IO) {
            val pixels = java.util.Base64.getEncoder()
                .encodeToString(
                    byteArrayOf(0xFF.toByte(), 0xD8.toByte(),
                        0xFF.toByte(), 0x12, 0x34) +
                        "synthetic_jpeg_bytes".toByteArray(Charsets.UTF_8),
                )
            server.enqueue(completedSse(
                """{"schema":"jarvis.writing-room.chat.v1",
                   "turn":{"project_id":"prj_story",
                            "response":{"text":"He recibido la imagen"}}}""",
            ))
            val answer = session.writingRoomAutoChatStream(
                "prj_story", "Alexander", "Revisa esta imagen",
                attachedImageBase64 = pixels, onDelta = {},
            ).getOrThrow()
            assertEquals("He recibido la imagen", answer.turn.response.text)
            assertNull(session.pendingWritingRoomRequest("prj_story").getOrThrow())
            val request = server.takeRequest(5, TimeUnit.SECONDS)!!
            assertEquals("/api/app/writing-room/agent/stream", request.path)
            val images = json(request)["images"]!!.toString()
            assertTrue(images.contains(pixels))
            assertTrue(images.contains("image/jpeg"))
            assertEquals(1, server.requestCount)
        }

    @Test fun completeWithoutTurnIsNotMistakenForSuccessfulCopilotChat() =
        runBlocking(Dispatchers.IO) {
            server.enqueue(completedSse(
                """{"schema":"jarvis.writing-room.chat.v1",
                   "reply":"JARVIS terminó con estado FAILED.","task_state":""}""",
            ))
            val result = session.writingRoomAutoChatStream(
                "prj_story", "Alexander", "Prueba de fallo", onDelta = {},
            )
            assertTrue(result.isFailure)
            assertEquals(1, server.requestCount)
        }

    @Test fun typedFailedCopilotTaskCannotPassAsSuccess() =
        runBlocking(Dispatchers.IO) {
            server.enqueue(completedSse(
                """{"schema":"jarvis.writing-room.chat.v1",
                   "turn":{"project_id":"prj_story","response":{"text":"No pude responder."}},
                   "copilot_task":{"state":"BLOCKED","agent_run_state":"FAILED"}}""",
            ))
            val result = session.writingRoomAutoChatStream(
                "prj_story", "Alexander", "Prueba de error", onDelta = {},
            )
            assertTrue(result.isFailure)
            assertEquals(1, server.requestCount)
        }

    @Test fun oldFailedTextWithoutTypedTaskIsRejected() =
        runBlocking(Dispatchers.IO) {
            server.enqueue(completedSse(
                """{"schema":"jarvis.writing-room.chat.v1",
                   "turn":{"project_id":"prj_story",
                           "response":{"text":"JARVIS terminó con estado FAILED."}}}""",
            ))
            val result = session.writingRoomAutoChatStream(
                "prj_story", "Alexander", "Comprueba el estado", onDelta = {},
            )
            assertTrue(result.isFailure)
            assertEquals(1, server.requestCount)
        }

    @Test fun completedTurnFromAnotherProjectCannotBeShown() =
        runBlocking(Dispatchers.IO) {
            server.enqueue(completedSse(
                """{"schema":"jarvis.writing-room.chat.v1",
                   "turn":{"project_id":"prj_other","response":{"text":"Dato privado"}}}""",
            ))
            val result = session.writingRoomAutoChatStream(
                "prj_story", "Alexander", "Consulta", onDelta = {},
            )
            assertTrue(result.isFailure)
            assertEquals(1, server.requestCount)
        }

    @Test fun oldVpsWithoutAdaptiveAgentCannotSilentlyIgnoreAttachedImage() =
        runBlocking(Dispatchers.IO) {
            val pixels = java.util.Base64.getEncoder()
                .encodeToString(
                    byteArrayOf(0xFF.toByte(), 0xD8.toByte(),
                        0xFF.toByte(), 0x12, 0x34) +
                        "synthetic_jpeg_bytes".toByteArray(Charsets.UTF_8),
                )
            server.enqueue(MockResponse().setResponseCode(404))
            val result = session.writingRoomAutoChatStream(
                "prj_story", "Alexander", "Edita esta imagen",
                attachedImageBase64 = pixels, onDelta = {},
            )
            assertTrue(result.isFailure)
            assertEquals("/api/app/writing-room/agent/stream",
                server.takeRequest(5, TimeUnit.SECONDS)!!.path)
            assertEquals(1, server.requestCount)
        }

    @Test fun oversizedImageStopsBeforeAnyHttpDispatch() =
        runBlocking(Dispatchers.IO) {
            val result = session.writingRoomAutoChatStream(
                "prj_story", "Alexander", "Mira", onDelta = {},
                attachedImageBase64 = "x".repeat(5 * 1024 * 1024 + 1),
            )
            assertTrue(result.isFailure)
            assertEquals(0, server.requestCount)
        }

    @Test fun completedAgentStreamPinsOriginalRequestIdForPostApprovalRefresh() =
        runBlocking(Dispatchers.IO) {
            var originalId = ""
            val conversationId = session.writingRoomConversationId("prj_story")
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    if (request.path != "/api/app/writing-room/agent/stream")
                        return MockResponse().setResponseCode(500)
                    originalId = json(request)["request_id"]!!.jsonPrimitive.content
                    val completed = """{"schema":"jarvis.writing-room.chat.v1",
                      "turn":{"schema":"jarvis.writing-room.turn.v1",
                        "project_id":"prj_story","response":{"text":"Confirma la acción"}},
                      "copilot_task":{"schema":"jarvis.writing-room.copilot.task.v1",
                        "task_id":"ar_0123456789abcdef01234567",
                        "project_id":"prj_story","conversation_id":"$conversationId",
                        "state":"READY","agent_run_state":"WAITING_CONFIRMATION",
                        "requires_confirmation":true}}"""
                    return completedSse(completed)
                }
            }
            val chat = session.writingRoomAutoChatStream(
                "prj_story", "Alexander", "Guarda una idea", onDelta = {},
            ).getOrThrow()
            assertFalse(chat.recovered_from_interruption)
            assertEquals(originalId, chat.copilot_task!!.request_id)
            assertEquals("READY", chat.copilot_task!!.state)
            assertEquals(1, server.requestCount)
        }

    @Test fun mismatchedConversationCannotPinRequestIdFromSuccessfulStream() =
        runBlocking(Dispatchers.IO) {
            server.enqueue(completedSse(
                    """{"schema":"jarvis.writing-room.chat.v1",
                       "turn":{"project_id":"prj_story","response":{"text":"Ready"}},
                       "copilot_task":{"task_id":"ar_0123456789abcdef01234567",
                         "project_id":"prj_story","conversation_id":"other-chat",
                         "state":"READY"}}""",
            ))
            // Unexpected retry/recovery must fail promptly, not wait forever
            // on an unqueued MockWebServer response in the CI runner.
            server.enqueue(MockResponse().setResponseCode(503))
            val result = session.writingRoomAutoChatStream(
                "prj_story", "Alexander", "Consulta", onDelta = {},
            ).getOrThrow()
            assertEquals("", result.copilot_task!!.request_id)
            assertEquals(1, server.requestCount)
        }

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

    @Test fun lostRequestIdSurvivesSessionRecreationAndNeverDispatchesAnotherPost() =
        runBlocking(Dispatchers.IO) {
            var originalId = ""
            val conversationId = session.writingRoomConversationId("prj_story")
            assertTrue(conversationId.matches(Regex("wrc_[a-f0-9-]{36}")))
            val statusCalls = java.util.concurrent.atomic.AtomicInteger(0)
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
                            if (statusCalls.incrementAndGet() == 1) {
                                MockResponse().setResponseCode(503)
                            } else {
                                MockResponse().setBody(
                                    """{"schema":"jarvis.writing-room.copilot.task.v1",
                                       "task_id":"ar_0123456789abcdef01234567",
                                       "request_id":"$originalId",
                                       "project_id":"prj_story","conversation_id":"$conversationId",
                                       "state":"RUNNING","agent_run_state":"RUNNING",
                                       "reply_text":"","results":[],"steps":[]}""",
                                )
                            }
                        }
                        else -> MockResponse().setResponseCode(500)
                    }
                }
            }

            val first = session.writingRoomAutoChatStream(
                "prj_story", "Alexander", "Genera un retrato", onDelta = {},
            )
            assertTrue(first.isFailure)
            assertTrue(originalId.isNotBlank())
            assertEquals(
                JarvisAppSession.PendingWritingRoomRequest(conversationId, originalId),
                session.pendingWritingRoomRequest("prj_story").getOrThrow(),
            )

            // A new session object over the same durable store represents app
            // process death/recreation. A different prompt still only probes
            // the old request; the model stream is never POSTed a second time.
            val restarted = JarvisAppSession(store)
            assertEquals(conversationId, restarted.writingRoomConversationId("prj_story"))
            val recovered = restarted.writingRoomAutoChatStream(
                "prj_story", "Alexander", "Crea una escena distinta", onDelta = {},
            ).getOrThrow()
            assertTrue(recovered.recovered_from_interruption)
            assertEquals(originalId, recovered.copilot_task!!.request_id)
            assertEquals("RUNNING", recovered.copilot_task!!.agent_run_state)
            assertTrue(recovered.turn.response.text.contains("sigue trabajando"))
            assertEquals(3, server.requestCount)
            assertEquals(
                listOf(
                    "/api/app/writing-room/agent/stream",
                    "/api/app/writing-room/agent/request-status",
                    "/api/app/writing-room/agent/request-status",
                ),
                (1..3).map { server.takeRequest(5, TimeUnit.SECONDS)!!.path },
            )
            assertEquals(2, statusCalls.get())
            assertEquals(originalId,
                restarted.pendingWritingRoomRequest("prj_story").getOrThrow()?.requestId)
        }

    @Test fun unavailableRecoveryFailsWithoutSecondAgentDispatch() =
        runBlocking(Dispatchers.IO) {
            server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream")
                .setBody("event: ready\ndata: {}\n\n"))
            server.enqueue(MockResponse().setResponseCode(404))
            server.enqueue(MockResponse().setResponseCode(503))
            val first = session.writingRoomAutoChatStream(
                "prj_story", "Alexander", "Crea una escena", onDelta = {},
            )
            assertTrue(first.isFailure)
            val pending = session.pendingWritingRoomRequest("prj_story").getOrThrow()
            assertNotNull(pending)

            // A later attempt must first probe the same request again. A
            // status endpoint 404/503 remains unknown and cannot free the
            // durable gate or dispatch a new paid model POST.
            val second = session.writingRoomAutoChatStream(
                "prj_story", "Alexander", "Crea otra escena", onDelta = {},
            )
            assertTrue(second.isFailure)
            assertEquals(pending, session.pendingWritingRoomRequest("prj_story").getOrThrow())
            assertEquals("/api/app/writing-room/agent/stream", server.takeRequest(5, TimeUnit.SECONDS)?.path)
            assertEquals("/api/app/writing-room/agent/request-status", server.takeRequest(5, TimeUnit.SECONDS)?.path)
            assertEquals("/api/app/writing-room/agent/request-status", server.takeRequest(5, TimeUnit.SECONDS)?.path)
            assertEquals(3, server.requestCount)
        }

    @Test fun preExecutionHttp429DoesNotQueryRecoveryOrLegacyStream() =
        runBlocking(Dispatchers.IO) {
            server.enqueue(MockResponse().setResponseCode(429))
            val outcome = session.writingRoomAutoChatStream(
                "prj_story", "Alexander", "Genera una imagen", onDelta = {},
            )
            assertTrue(outcome.isFailure)
            assertEquals("/api/app/writing-room/agent/stream", server.takeRequest(5, TimeUnit.SECONDS)?.path)
            assertEquals(1, server.requestCount)
        }

    @Test fun exactRequestRecoveryRetainsOnlySuccessfulVerifiedExternalSources() {
        val url = "https://example.org/public-doom-reference"
        val ref = "web:" + java.security.MessageDigest.getInstance("SHA-256")
            .digest(url.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }.take(20)
        val ready = CopilotTaskResult(
            tool = "web_search", status = "SUCCEEDED",
            result = Json.parseToJsonElement(
                """{"status":"READY","authority":"EXTERNAL_UNTRUSTED","sources":[
                  {"url":"$url","ref":"$ref","title":"Public Doom source"},
                  {"url":"https://169.254.169.254/private","ref":"web:aaaaaaaaaaaaaaaaaaaa","title":"local"}
                ]}"""
            ).jsonObject,
        )
        val task = CopilotTaskState(
            project_id = "prj_story", conversation_id = "chat-a",
            request_id = "req-a", task_id = "ar_0123456789abcdef01234567",
            agent_run_state = "SUCCEEDED", state = "DONE",
            reply_text = "Investigación terminada", results = listOf(
                ready,
                ready.copy(tool = "search_story_wiki"),
                ready.copy(status = "UNAVAILABLE"),
                ready.copy(result = Json.parseToJsonElement(
                    """{"status":"READY","authority":"CANON","sources":[
                        {"url":"$url","ref":"$ref"}]}"""
                ).jsonObject),
            ),
        )
        val recovered = recoveredCopilotChat("prj_story", "chat-a", "req-a", task)
        assertNotNull(recovered)
        assertTrue(recovered!!.recovered_from_interruption)
        assertEquals("jarvis.chat.content.v1", recovered.content?.schema)
        assertEquals(1, recovered.content?.sources?.size)
        assertEquals(url, recovered.content?.sources?.single()?.url)
        assertEquals(ref, recovered.content?.sources?.single()?.ref)
        assertEquals("Public Doom source", recovered.content?.sources?.single()?.title)
        assertNull(recoveredCopilotChat("prj_story", "other-chat", "req-a", task))
        assertTrue(recoveredCopilotPublicSources(task.copy(
            results = listOf(ready.copy(status = "UNAVAILABLE")))).isEmpty())
    }

    @Test fun failedRecoveredAgentDoesNotBecomeSuccessfulChat() {
        val failed = CopilotTaskState(
            project_id = "prj_story", conversation_id = "chat-a",
            request_id = "req-a", task_id = "ar_0123456789abcdef01234567",
            state = "BLOCKED", agent_run_state = "FAILED",
            reply_text = "JARVIS terminó con estado FAILED.",
        )
        assertNull(recoveredCopilotChat("prj_story", "chat-a", "req-a", failed))
        assertNull(recoveredCopilotChat(
            "prj_story", "chat-a", "req-a",
            failed.copy(agent_run_state = "OUTCOME_UNKNOWN"),
        ))
        assertNull(recoveredCopilotChat(
            "prj_story", "chat-a", "req-a",
            failed.copy(agent_run_state = "PARTIAL"),
        ))
    }

    @Test fun onlyKnownTerminalRequestStatesReleaseTheDurableRecoveryGate() {
        fun task(runState: String) = CopilotTaskState(
            project_id = "prj_story", conversation_id = "chat-a",
            request_id = "req-a", task_id = "ar_0123456789abcdef01234567",
            state = runState, agent_run_state = runState,
        )
        assertTrue(!writingRoomRequestIsSettled(task("RUNNING")))
        assertTrue(writingRoomRequestIsSettled(task("SUCCEEDED")))
        assertTrue(writingRoomRequestIsSettled(task("PARTIAL")))
        assertTrue(writingRoomRequestIsSettled(task("FAILED")))
        assertTrue(!writingRoomRequestIsSettled(task("OUTCOME_UNKNOWN")))
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
