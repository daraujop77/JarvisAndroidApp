package com.jarvis.android.transport.live

import com.jarvis.android.contract.TaskStatus
import com.jarvis.android.data.repo.JarvisSessionRepository
import com.jarvis.android.data.repo.SessionPhase
import com.jarvis.android.transport.TransportException
import com.jarvis.android.data.state.ConnectionState
import com.jarvis.android.data.state.RequestStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * PCB-LIVE-1: the live transport against a MockWebServer that replicates the
 * PC-A `/api/app` contract (shapes mirrored from auth.js +
 * jarvis-authenticated-app.tests.ps1). No real PC-A call required.
 */
class LiveAppGatewayTransportTest {

    private lateinit var server: MockWebServer
    private lateinit var scope: CoroutineScope
    private lateinit var job: Job
    private var advertiseGeneralAgent = false
    private var rejectProjectAgentTurn = false
    private var tamperProjectAgentReplyScope = false
    private val cancelCalls = AtomicInteger(0)
    private val loginCalls = AtomicInteger(0)
    private val streamCalls = AtomicInteger(0)
    private val jsonChatCalls = AtomicInteger(0)
    private val recoveredImageDownloads = AtomicInteger(0)
    private val receiptImage = byteArrayOf(
        0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 1, 2, 3,
    )
    private val receiptSha = java.security.MessageDigest.getInstance("SHA-256")
        .digest(receiptImage).joinToString("") { "%02x".format(it) }
    private val postedBodies = java.util.Collections.synchronizedList(mutableListOf<String>())

    /** Per-test SSE behavior; default = full happy stream. */
    private var streamResponder: () -> MockResponse = {
        MockResponse()
            .setHeader("Content-Type", "text/event-stream")
            .setBody(sseBody())
            .throttleBody(96, 150, TimeUnit.MILLISECONDS)
    }

    private companion object {
        const val ANDROID_DEVICE = "android_device_stub"
        const val APP_SESSION = "and_session_stub"
    }

    @Before
    fun setUp() {
        job = SupervisorJob()
        scope = CoroutineScope(job + Dispatchers.IO)
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val auth = request.getHeader("Authorization")
                return when {
                    request.path?.startsWith("/api/app/images/requests/") == true ->
                        MockResponse().setResponseCode(404)
                            .setBody("""{"error":"image_request_not_found"}""")
                    request.path == "/api/app/login" && request.method == "POST" -> {
                        loginCalls.incrementAndGet()
                        val body = request.body.readUtf8()
                        if (body.contains("right-pass")) {
                            MockResponse().setBody(
                                """{"schema":"jarvis.app.auth.v1","authenticated":true,"token":"test-token",""" +
                                    """"expires_utc":"2099-01-01T00:00:00Z","user":{"id":"u1","username":"alex","role":"owner"}}""",
                            )
                        } else {
                            MockResponse().setResponseCode(401).setBody("""{"error":"invalid_credentials"}""")
                        }
                    }
                    request.path == "/api/app/session" && auth != "Bearer test-token" ->
                        MockResponse().setResponseCode(401)
                    request.path == "/api/app/session" -> MockResponse().setBody(
                        """{"schema":"jarvis.app.session.v1","authenticated":true,"user":{"id":"u1","username":"alex","role":"owner"}}""",
                    )
                    request.path == "/api/app/chat/agent/general/stream" && auth != "Bearer test-token" ->
                        MockResponse().setResponseCode(401)
                    request.path == "/api/app/chat/agent/general/stream" -> {
                        postedBodies += "POST /api/app/chat/agent/general/stream\\n" + request.body.readUtf8()
                        streamResponder()
                    }
                    request.path == "/api/app/chat/stream" && auth != "Bearer test-token" ->
                        MockResponse().setResponseCode(401)
                    request.path == "/api/app/chat/stream" -> {
                        streamCalls.incrementAndGet()
                        postedBodies += "POST /api/app/chat/stream\n" + request.body.readUtf8()
                        streamResponder()
                    }
                    request.path?.startsWith("/api/app/chat/agent/general/image/") == true &&
                        auth == "Bearer test-token" -> {
                        recoveredImageDownloads.incrementAndGet()
                        MockResponse().setHeader("Content-Type", "image/png")
                            .setBody(okio.Buffer().write(receiptImage))
                    }
                    request.path?.startsWith("/api/app/chat/requests/") == true && auth != "Bearer test-token" ->
                        MockResponse().setResponseCode(401)
                    request.path?.startsWith("/api/app/chat/requests/") == true -> {
                        val rid = request.path!!.substringAfterLast('/')
                        if (rid in setOf("req-lost", "req-broken", "req-media", "req-content", "req-sources")) {
                            val media = if (rid == "req-media") {
                                ""","media":{"generated_images":[{"asset_id":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","sha256":"$receiptSha","mime_type":"image/png"}]}"""
                            } else ""
                            val content = if (rid == "req-sources") {
                                ""","content":{"schema":"jarvis.chat.content.v1","blocks":[{"type":"text","text":"Sources"}],"sources":[{"ref":"web:d92dac30efd5eb76088d","title":"Verified report","url":"https://example.org/report"}]}"""
                            } else if (rid in setOf("req-media", "req-content")) {
                                ""","content":{"schema":"jarvis.chat.content.v1","blocks":[{"type":"image","asset_id":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","sha256":"$receiptSha","mime_type":"image/png"}],"sources":[]}"""
                            } else ""
                            MockResponse().setBody(
                                """{"schema":"jarvis.chat.turn.v1","result":{"status":"completed"},""" +
                                    """"response":{"text":"Recovered answer"}""" + media + content + "}",
                            )
                        } else {
                            MockResponse().setResponseCode(404)
                                .setBody("""{"schema":"jarvis.web.error.v1","error":"unknown_request"}""")
                        }
                    }
                    request.path == "/api/app/chat/agent/project/turn" -> {
                        val posted = request.body.readUtf8()
                        postedBodies += "POST /api/app/chat/agent/project/turn\n" + posted
                        if (rejectProjectAgentTurn) {
                            MockResponse().setResponseCode(409)
                                .setBody("""{"error":"agent_not_available"}""")
                        } else {
                            val reqScope = Json.parseToJsonElement(posted).jsonObject
                            val identity = kotlinx.serialization.json.buildJsonObject {
                                listOf("request_id", "conversation_id", "trace_id",
                                       "device_id", "session_id").forEach { field ->
                                    put(field, reqScope[field]!!)
                                }
                                if (tamperProjectAgentReplyScope) {
                                    put("conversation_id", "other-conversation")
                                }
                            }
                            MockResponse().setBody(
                                """{"schema":"jarvis.chat.turn.v1","route":"adaptive_agent","request":$identity,"response":{"text":"Scoped image read","provider":"jarvis-adaptive"},"content":{"schema":"jarvis.chat.content.v1","blocks":[{"type":"text","text":"Scoped image read"}],"sources":[{"ref":"web:a32f3bb71f3e05ea8971","title":"IANA official","url":"https://www.iana.org/domains/reserved"},{"ref":"web:00000000000000000000","title":"Fake local","url":"https://127.0.0.1/private"}]}}"""
                            )
                        }
                    }
                    request.path == "/api/app/chat" && request.method == "POST" -> {
                        jsonChatCalls.incrementAndGet()
                        MockResponse().setBody(
                            """{"schema":"jarvis.chat.turn.v1","result":{"status":"completed"},""" +
                                """"response":{"text":"JSON fallback answer"}}""",
                        )
                    }
                    request.path == "/api/app/chat/cancel" -> {
                        cancelCalls.incrementAndGet()
                        MockResponse().setResponseCode(202)
                            .setBody("""{"result":"cancel_requested"}""")
                    }
                    request.path == "/api/app/chat/conversations/delete" && auth != "Bearer test-token" ->
                        MockResponse().setResponseCode(401)
                    request.path == "/api/app/chat/conversations/delete" -> {
                        postedBodies += "POST /api/app/chat/conversations/delete\n" + request.body.clone().readUtf8()
                        MockResponse().setBody(
                            """{"schema":"jarvis.chat.conversation-delete.v1","deleted":true}""",
                        )
                    }
                    request.path == "/api/app/images/generations" && auth != "Bearer test-token" ->
                        MockResponse().setResponseCode(401)
                    request.path == "/api/app/images/generations" -> {
                        postedBodies += "POST /api/app/images/generations\n" + request.body.readUtf8()
                        MockResponse().setBody(
                            """{"schema":"jarvis.image.generation.v2","provider":"openai-codex","model":"gpt-image-2-medium","route":"cloud_image_generation:openai-codex","mime_type":"image/png","data_base64":"aW1hZ2U=","size_bytes":5,"requested_mode":"model_select","requested_model":"gpt-image-2-medium","fallback_used":false,"attempt_count":1,"duration_ms":1234}""",
                        )
                    }
                    request.path == "/api/app/writing-room/visual-assets/set-wiki-primary" && auth != "Bearer test-token" ->
                        MockResponse().setResponseCode(401)
                    request.path == "/api/app/writing-room/visual-assets/set-wiki-primary" -> {
                        postedBodies += "POST /api/app/writing-room/visual-assets/set-wiki-primary\n" + request.body.readUtf8()
                        MockResponse().setBody(
                            """{"schema":"jarvis.visual.wiki-primary.v1","project_id":"prj_story","character_id":"character:alexander","asset":{"asset_id":"va_alexander_primary_2","status":"APPROVED","visual_revision":2}}""",
                        )
                    }
                    request.path == "/api/app/images/edits" && auth != "Bearer test-token" ->
                        MockResponse().setResponseCode(401)
                    request.path == "/api/app/images/edits" -> {
                        postedBodies += "POST /api/app/images/edits\n" + request.body.readUtf8()
                        MockResponse().setBody(
                            """{"schema":"jarvis.image.edit.v1","provider":"xai-oauth","model":"grok-imagine-image-2.0","route":"cloud_image_edit:xai-oauth","mime_type":"image/jpeg","data_base64":"ZWRpdGVk","size_bytes":6,"requested_mode":"quality","requested_model":null,"fallback_used":false,"attempt_count":1,"duration_ms":2222}""",
                        )
                    }
                    request.path == "/api/status" -> MockResponse().setBody("""{"status":"ok"}""")
                    request.path == "/api/app/status" && auth != "Bearer test-token" ->
                        MockResponse().setResponseCode(401)
                    request.path == "/api/app/status" -> MockResponse().setBody(
                        """
                        {
                          "schema": "jarvis.app.status.v1",
                          "user": {"id":"u1","username":"alex","role":"owner"},
                          "chat": {
                            "mode": "local",
                            "general_agent": {"enabled": $advertiseGeneralAgent},
                            "access": {"owner_model_selection": true},
                            "models": [
                              {"profile":"fast","label":"Fast","model":"qwen3.5:4b","state":"installed"},
                              {"profile":"normal","label":"Normal","model":"gemma4:12b-it-qat","state":"active"},
                              {"profile":"deep","label":"Deep","model":"qwen3.8:27b","state":"installed"}
                            ]
                          },
                          "capabilities": {
                            "image_input": {"state":"ready"},
                            "image_generation": {
                              "state":"ready","default_mode":"speed","owner_model_selection":true,
                              "modes":[{"id":"speed","label":"Speed"},{"id":"quality","label":"Quality"},{"id":"model_select","label":"Model Select"}],
                              "models":[{"id":"auto","label":"Auto","provider":"jarvis","state":"ready"},{"id":"grok-imagine-image","label":"Grok Imagine","provider":"xai-oauth","state":"ready"},{"id":"gpt-image-2-medium","label":"GPT Image","provider":"openai-codex","state":"ready"}]
                            },
                            "image_edit": {
                              "state":"ready","default_mode":"quality","owner_model_selection":true,
                              "modes":[{"id":"speed","label":"Speed"},{"id":"quality","label":"Quality"},{"id":"model_select","label":"Model Select"}],
                              "models":[{"id":"auto","label":"Auto","provider":"jarvis","state":"ready"},{"id":"grok-imagine-image-2.0","label":"Grok Imagine Quality","provider":"xai-oauth","state":"ready"},{"id":"gpt-image-2-medium","label":"GPT Image","provider":"openai-codex","state":"ready"}]
                            }
                          }
                        }
                        """.trimIndent(),
                    )
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
    }

    @After
    fun tearDown() {
        scope.cancel()
        server.shutdown()
    }

    private fun sseBody(): String = buildString {
        append("event: ready\ndata: {\"request_id\":\"x\"}\n\n")
        for (i in 0 until 6) {
            append("event: delta\ndata: {\"delta\":\"tok$i \"}\n\n")
        }
        append("event: complete\ndata: {\"schema\":\"jarvis.chat.turn.v1\",\"route\":\"hermes_cloud\",\"request\":{\"resolved_destination\":\"hermes_cloud\",\"resolved_provider\":\"openai-codex\",\"resolved_model\":\"gpt-6-luna\"},\"response\":{\"text\":\"Full reply\",\"provider\":\"openai-codex\",\"model\":\"gpt-6-luna\"},\"result\":{\"status\":\"completed\"}}\n\n")
    }

    private fun baseUrl() = server.url("/").toString().trimEnd('/')

    @Test
    fun deleteConversationUsesAuthenticatedChatEndpoint() = runBlocking {
        val transport = LiveAppGatewayTransport(JarvisAppSession(seededStore()), scope)

        assertTrue(transport.deleteConversation("conversation-a"))

        val request = server.takeRequest(2, TimeUnit.SECONDS) ?: error("delete request was not sent")
        assertEquals("POST", request.method)
        assertEquals("/api/app/chat/conversations/delete", request.path)
        assertEquals("Bearer test-token", request.getHeader("Authorization"))
        assertEquals("{\"conversation_id\":\"conversation-a\"}", request.body.readUtf8())
    }

    private fun seededStore(expired: Boolean = false): JarvisAppSession.MemoryStore {
        val store = JarvisAppSession.MemoryStore()
        store.put(
            mapOf(
                JarvisAppSession.KEY_TOKEN to "test-token",
                JarvisAppSession.KEY_BASE to baseUrl(),
                JarvisAppSession.KEY_USER_ID to "u1",
                JarvisAppSession.KEY_USERNAME to "alex",
                JarvisAppSession.KEY_ROLE to "owner",
                JarvisAppSession.KEY_EXPIRES to if (expired) "2000-01-01T00:00:00Z" else "2099-01-01T00:00:00Z",
            ),
        )
        return store
    }

    private fun await(timeoutMs: Long = 10_000, block: () -> Boolean) {
        runBlocking(Dispatchers.Default) {
            withTimeout(timeoutMs) { while (!block()) delay(20) }
        }
    }

    @Test
    fun sseIgnoresUnknownAndMalformedFramesButStillCompletes() {
        // Hardening (no contract change): junk the server might interleave must
        // not kill the turn — unknown event names, garbage JSON, comment lines.
        streamResponder = {
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(
                    "event: ready\ndata: {}\n\n" +
                        ": keepalive comment\n\n" +
                        "event: ui.future_hint\ndata: {\"anything\":true}\n\n" +
                        "event: delta\ndata: {not valid json\n\n" +
                        "event: delta\ndata: {\"delta\":\"ok \"}\n\n" +
                        "event: complete\ndata: {\"schema\":\"jarvis.chat.turn.v1\",\"response\":{\"text\":\"Recovered answer\"}}\n\n",
                )
        }
        val transport = LiveAppGatewayTransport(JarvisAppSession(seededStore()), scope)
        val repo = JarvisSessionRepository(transport, scope)
        repo.start()
        await { repo.snapshot.value.phase == SessionPhase.READY }
        val cid = repo.send("c1", "stress me")
        await { repo.snapshot.value.session.requests[cid]?.status?.isTerminal == true }
        val req = repo.snapshot.value.session.requests[cid]!!
        assertEquals(RequestStatus.Completed, req.status)
        // malformed delta dropped; valid one applied; complete overrides with full text
        assertEquals("Recovered answer", req.text)
    }

    @Test
    fun sseDeliversAuthenticatedTypedCitationToOriginalTurn() {
        streamResponder = {
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(
                    "event: ready\ndata: {}\n\n" +
                    "event: complete\ndata: " +
                    """{"schema":"jarvis.chat.turn.v1","response":{"text":"Research"},""" +
                    """"content":{"schema":"jarvis.chat.content.v1","blocks":[{"type":"text","text":"Research"}],""" +
                    """"sources":[{"ref":"web:d92dac30efd5eb76088d","title":"Report","url":"https://example.org/report"}]}}""" +
                    "\n\n"
                )
        }
        val appSession = JarvisAppSession(seededStore())
        val delivered = mutableListOf<Triple<String, String, String>>()
        appSession.onAgentSourcesReady = { conversation, request, sources ->
            delivered += Triple(conversation, request, sources)
        }
        val transport = LiveAppGatewayTransport(appSession, scope)
        val repo = JarvisSessionRepository(transport, scope)
        repo.start()
        await { repo.snapshot.value.phase == SessionPhase.READY }
        val requestId = repo.send("c1", "Cita tu investigación")
        await { repo.snapshot.value.session.requests[requestId]?.status?.isTerminal == true }
        assertEquals(RequestStatus.Completed,
            repo.snapshot.value.session.requests[requestId]!!.status)
        assertEquals(1, delivered.size)
        assertEquals("c1", delivered.single().first)
        assertEquals(requestId, delivered.single().second)
        assertEquals("https://example.org/report",
            com.jarvis.android.data.repo.ChatSourcesCodec.decode(
                delivered.single().third).single().url)
        assertEquals(1, streamCalls.get())
        assertEquals(0, jsonChatCalls.get())
    }

    @Test
    fun loginPersistsOnlyTheShortLivedToken() = runBlocking(Dispatchers.IO) {
        val store = JarvisAppSession.MemoryStore()
        val session = JarvisAppSession(store)
        val ok = session.login(baseUrl(), "alex", "right-pass")
        assertTrue(ok.isSuccess)
        assertEquals("alex", ok.getOrThrow().username)
        assertEquals("Bearer test-token", session.authHeader())
        assertTrue(session.isAuthenticated)
        assertEquals(1, loginCalls.get())

        val persisted = store.snapshot().values.joinToString("|")
        assertFalse("password must never be persisted", persisted.contains("right-pass"))

        val bad = JarvisAppSession(JarvisAppSession.MemoryStore())
        assertTrue(bad.login(baseUrl(), "alex", "wrong-pass").isFailure)
    }

    @Test
    fun loginRejectsPublicHostsWithoutAnyRequest() = runBlocking(Dispatchers.IO) {
        val session = JarvisAppSession(JarvisAppSession.MemoryStore())
        val result = session.login("https://jarvis.example.com", "alex", "pw")
        assertTrue(result.isFailure)
        assertEquals(0, loginCalls.get())
    }

    @Test
    fun connectStreamsThroughReducerToCompleted() {
        val transport = LiveAppGatewayTransport(JarvisAppSession(seededStore()), scope)
        val repo = JarvisSessionRepository(transport, scope)
        repo.start()
        await { repo.snapshot.value.phase == SessionPhase.READY }

        val cid = repo.send("c1", "hola")
        await { repo.snapshot.value.session.requests[cid]?.status?.isTerminal == true }
        val req = repo.snapshot.value.session.requests[cid]!!
        assertEquals(RequestStatus.Completed, req.status)
        // deltas streamed in order, final complete text wins
        assertEquals("Full reply", req.text)
        assertEquals("openai-codex", req.routeProvider)
        assertEquals("gpt-6-luna", req.routeModel)
        assertEquals("hermes_cloud", req.routeKind)
        assertEquals("hermes_cloud", req.routeDestination)
    }

    @Test
    fun cancelIsIdempotentAndHitsTheScopedCancelEndpoint() {
        val transport = LiveAppGatewayTransport(JarvisAppSession(seededStore()), scope)
        val repo = JarvisSessionRepository(transport, scope)
        repo.start()
        await { repo.snapshot.value.phase == SessionPhase.READY }

        val cid = repo.send("c1", "hola")
        await { repo.snapshot.value.session.requests[cid]?.status == RequestStatus.Streaming }
        repeat(4) { repo.cancel(cid) } // repeated Stop presses

        await { repo.snapshot.value.session.requests[cid]?.status?.isTerminal == true }
        val status = repo.snapshot.value.session.requests[cid]!!.status
        assertTrue("expected terminal cancel/complete, got $status",
            status == RequestStatus.Cancelled || status == RequestStatus.Completed)
        // Reducer.requestCancel is idempotent -> at most one wire command
        assertTrue(cancelCalls.get() <= 1)
    }

    @Test
    fun expiredSessionFailsClosedAtTransportBoundary() {
        // The local expiry guard fails closed *before* any network call: no
        // token is ever sent to the front door once the stored session is past
        // `expires_utc`. (Downstream AUTH_EXPIRED phase mapping is exercised by
        // the Fake transport suite; asserted here at the transport contract.)
        val session = JarvisAppSession(seededStore(expired = true))
        assertTrue(session.expired)
        val transport = LiveAppGatewayTransport(session, scope)
        val err = runBlocking(Dispatchers.IO) {
            runCatching { transport.connect() }.exceptionOrNull()
        }
        assertTrue("expected connect to fail closed", err != null)
        assertFalse("expired token must not be sent", session.isAuthenticated)
        val frames = mutableListOf<String>()
        runBlocking(Dispatchers.IO) {
            val collect = scope.launch { transport.frames.toList(frames) }
            delay(150)
            collect.cancel()
        }
        // connect() emitted a terminal AUTH_EXPIRED frame rather than a stream.
        assertTrue(frames.any { it.contains("AUTH_EXPIRED") })
    }

    @Test
    fun unauthenticatedLinkStaysDisconnected() {
        val empty = JarvisAppSession(JarvisAppSession.MemoryStore())
        val transport = LiveAppGatewayTransport(empty, scope)
        val repo = JarvisSessionRepository(transport, scope)
        repo.start()
        runBlocking(Dispatchers.Default) { delay(300) }
        assertFalse(repo.snapshot.value.isReady)
        assertFalse(repo.snapshot.value.session.connection.isUsable)
    }

    @Test
    fun submitSendsBoundedConversationContext() {
        val transport = LiveAppGatewayTransport(JarvisAppSession(seededStore()), scope)
        val repo = JarvisSessionRepository(transport, scope)
        repo.start()
        await { repo.snapshot.value.phase == SessionPhase.READY }

        val cid = repo.send(
            "c1", "what did I ask before?",
            contextProvider = {
                listOf(
                    com.jarvis.android.contract.ChatTurn("user", "first question"),
                    com.jarvis.android.contract.ChatTurn("assistant", "first answer"),
                )
            },
        )
        await { repo.snapshot.value.session.requests[cid]?.status?.isTerminal == true }
        val streamBody = postedBodies.last { it.contains("/api/app/chat/stream") }
        assertTrue(streamBody.contains("first question"))
        assertTrue(streamBody.contains("first answer"))
        assertTrue(streamBody.contains("what did I ask before?"))
    }

    @Test
    fun recoverCompletedTurnSettlesWithoutSecondInference() {
        val store = seededStore()
        // Persist a turn scope as if the app died mid-stream.
        store.put(mapOf(JarvisAppSession.KEY_TURN_IDS to "req-lost"))
        store.put(
            mapOf(
                JarvisAppSession.KEY_TURN_PREFIX + "req-lost" to
                    listOf("c1", "trace-lost", ANDROID_DEVICE, APP_SESSION).joinToString("\u001f"),
            ),
        )
        val transport = LiveAppGatewayTransport(JarvisAppSession(store), scope)
        val frames = mutableListOf<String>()
        runBlocking(Dispatchers.IO) {
            val collect = scope.launch { transport.frames.toList(frames) }
            delay(80)
            val recovered = transport.recoverCompletedTurn("req-lost", "c1")
            assertTrue("server had a completed turn cached", recovered)
            delay(80)
            collect.cancel()
        }
        // Recovery emits accepted+completed for the same id and NO extra stream POST.
        assertTrue(frames.any { it.contains("message.accepted") && it.contains("req-lost") })
        assertTrue(frames.any { it.contains("message.completed") && it.contains("req-lost") })
        assertEquals(0, streamCalls.get())
        assertEquals(null, store.get(JarvisAppSession.KEY_TURN_PREFIX + "req-lost"))
    }

    @Test
    fun frozenProjectReceiptSurvivesRestartAndRefusesRescope() {
        val store = seededStore()
        val session = JarvisAppSession(store)
        session.setChatAgentProjectFor("private-conv", "prj_story")
        val saved = session.rememberTurn(
            "private-req", "private-conv", "trace-original", "device-original",
            "session-original",
        )
        assertEquals("prj_story", saved.projectId)
        assertTrue(saved.scopeKnown)
        val afterRestart = JarvisAppSession(store)
        val reused = afterRestart.rememberTurn(
            "private-req", "private-conv", "new-trace", "different-device",
            "different-session",
        )
        assertEquals("trace-original", reused.traceId)
        assertEquals("device-original", reused.deviceId)
        assertEquals("session-original", reused.sessionId)

        afterRestart.setChatAgentProjectFor("private-conv", null)
        try {
            afterRestart.rememberTurn(
                "private-req", "private-conv", "unsafe-trace", "other-device",
                "other-session",
            )
            throw AssertionError("Project-scoped request must not become unscoped")
        } catch (expected: TransportException) {
            assertTrue(expected.message.orEmpty().contains("project changed"))
        }
        assertEquals("prj_story", afterRestart.loadTurn("private-req")?.projectId)
        assertEquals("trace-original", afterRestart.loadTurn("private-req")?.traceId)
    }

    @Test
    fun projectReplayNeverFallsBackAfterSelectionIsCleared() {
        val store = seededStore()
        val session = JarvisAppSession(store)
        session.setChatAgentProjectFor("private-conv", "prj_story")
        session.rememberTurn(
            "private-replay", "private-conv", "trace-original", "device-original",
            "session-original",
        )
        session.setChatAgentProjectFor("private-conv", null)
        val transport = LiveAppGatewayTransport(JarvisAppSession(store), scope)
        val repo = JarvisSessionRepository(transport, scope)
        repo.start()
        await { repo.snapshot.value.phase == SessionPhase.READY }
        repo.sendWithId("private-replay", "private-conv", "Sensitive project request")
        await { repo.snapshot.value.session.requests["private-replay"]?.status?.isTerminal == true }
        assertTrue(repo.snapshot.value.session.requests["private-replay"]?.status
            is RequestStatus.Failed)
        assertTrue(postedBodies.none { it.startsWith("POST /api/app/chat/") })
        assertEquals("prj_story", JarvisAppSession(store).loadTurn("private-replay")?.projectId)
    }

    @Test
    fun recoveryRefusesAnotherConversationWithoutFetchingCachedTurn() {
        val store = seededStore()
        val session = JarvisAppSession(store)
        session.setChatAgentProjectFor("private-conv", "prj_story")
        session.rememberTurn(
            "req-lost", "private-conv", "trace-original", ANDROID_DEVICE,
            APP_SESSION,
        )
        val transport = LiveAppGatewayTransport(session, scope)
        assertFalse(runBlocking(Dispatchers.IO) {
            transport.recoverCompletedTurn("req-lost", "unrelated-conversation")
        })
        assertEquals("prj_story", session.loadTurn("req-lost")?.projectId)
        assertTrue(postedBodies.none { it.contains("/api/app/chat/requests/req-lost") })
    }

    @Test
    fun forgedProjectReplyScopeCannotBeRenderedOrPersistSources() {
        tamperProjectAgentReplyScope = true
        val session = JarvisAppSession(seededStore())
        session.setChatAgentProjectFor("private-conv", "prj_story")
        val sources = mutableListOf<String>()
        session.onAgentSourcesReady = { _, _, receipt -> sources += receipt }
        val transport = LiveAppGatewayTransport(session, scope)
        val repo = JarvisSessionRepository(transport, scope)
        repo.start()
        await { repo.snapshot.value.phase == SessionPhase.READY }
        val turn = repo.send("private-conv", "Check source identity")
        await { repo.snapshot.value.session.requests[turn]?.status?.isTerminal == true }
        assertTrue(repo.snapshot.value.session.requests[turn]?.status is RequestStatus.Failed)
        assertTrue(sources.isEmpty())
        assertEquals(1, postedBodies.count {
            it.startsWith("POST /api/app/chat/agent/project/turn")
        })
        assertEquals(0, streamCalls.get())
        assertEquals(0, jsonChatCalls.get())
    }

    @Test
    fun projectRetryPostsExactlyTheOriginalPersistentTraceAndDeviceScope() {
        val store = seededStore()
        val session = JarvisAppSession(store)
        session.setChatAgentProjectFor("private-conv", "prj_story")
        session.rememberTurn(
            "req-persisted", "private-conv", "original-trace",
            "original-device", "original-client-session",
        )
        val transport = LiveAppGatewayTransport(JarvisAppSession(store), scope)
        val repo = JarvisSessionRepository(transport, scope)
        repo.start()
        await { repo.snapshot.value.phase == SessionPhase.READY }
        repo.sendWithId("req-persisted", "private-conv", "Inspect this project")
        await { repo.snapshot.value.session.requests["req-persisted"]?.status?.isTerminal == true }
        assertEquals(RequestStatus.Completed,
                     repo.snapshot.value.session.requests["req-persisted"]?.status)
        val sent = postedBodies.single { it.startsWith(
            "POST /api/app/chat/agent/project/turn"
        ) }.substringAfter(10.toChar())
        val body = Json.parseToJsonElement(sent).jsonObject
        assertEquals("prj_story", body["project_id"]?.jsonPrimitive?.content)
        assertEquals("private-conv", body["conversation_id"]?.jsonPrimitive?.content)
        assertEquals("req-persisted", body["request_id"]?.jsonPrimitive?.content)
        assertEquals("original-trace", body["trace_id"]?.jsonPrimitive?.content)
        assertEquals("original-device", body["device_id"]?.jsonPrimitive?.content)
        assertEquals("original-client-session", body["session_id"]?.jsonPrimitive?.content)
        assertEquals(0, streamCalls.get())
        assertEquals(0, jsonChatCalls.get())
    }

    @Test
    fun projectAgentTypedMediaNeverDropsProjectScope() {
        val session = JarvisAppSession(seededStore())
        val imageArray = kotlinx.serialization.json.Json.parseToJsonElement(
            """[{"mime_type":"image/png","data_base64":"iVBORw0KGgo="}]"""
        ) as kotlinx.serialization.json.JsonArray
        val reply = runBlocking(Dispatchers.IO) {
            session.chatGlobalProjectAgentTurn(
                projectId = "prj_story", conversationId = "project-chat-1",
                requestId = "req-image-scope", prompt = "Describe this picture",
                images = imageArray,
            )
        }
        assertTrue(reply.isSuccess)
        val saved = postedBodies.single { it.startsWith("POST /api/app/chat/agent/project/turn") }
        val posted = kotlinx.serialization.json.Json.parseToJsonElement(
            saved.substringAfter('\n')
        ).jsonObject
        assertEquals("prj_story", posted["project_id"]?.jsonPrimitive?.content)
        assertEquals("project-chat-1", posted["conversation_id"]?.jsonPrimitive?.content)
        assertEquals("req-image-scope", posted["request_id"]?.jsonPrimitive?.content)
        val source = posted["images"]?.jsonArray?.single()?.jsonObject
        assertEquals("image/png", source?.get("mime_type")?.jsonPrimitive?.content)
        assertEquals("iVBORw0KGgo=", source?.get("data_base64")?.jsonPrimitive?.content)
        assertEquals(0, streamCalls.get())
        assertEquals(0, jsonChatCalls.get())
    }

    @Test
    fun selectedProjectTurnKeepsSignedWebSourcesOnOriginalMessage() {
        val session = JarvisAppSession(seededStore())
        session.setChatAgentProjectFor("project-chat-sources", "prj_story")
        val delivered = java.util.Collections.synchronizedList(
            mutableListOf<Triple<String, String, String>>()
        )
        session.onAgentSourcesReady = { conversationId, requestId, sourcesJson ->
            delivered += Triple(conversationId, requestId, sourcesJson)
        }
        val transport = LiveAppGatewayTransport(session, scope)
        val repo = JarvisSessionRepository(transport, scope)
        repo.start()
        await { repo.snapshot.value.phase == SessionPhase.READY }
        val turn = repo.send("project-chat-sources", "Find the official IANA page")
        await { repo.snapshot.value.session.requests[turn]?.status?.isTerminal == true }
        assertEquals(RequestStatus.Completed,
                     repo.snapshot.value.session.requests[turn]?.status)
        val sent = postedBodies.single {
            it.startsWith("POST /api/app/chat/agent/project/turn")
        }
        val request = Json.parseToJsonElement(sent.substringAfter(10.toChar())).jsonObject
        assertEquals("prj_story", request["project_id"]?.jsonPrimitive?.content)
        assertEquals("project-chat-sources",
                     request["conversation_id"]?.jsonPrimitive?.content)
        assertEquals(turn, request["request_id"]?.jsonPrimitive?.content)
        assertEquals(0, streamCalls.get())
        assertEquals(0, jsonChatCalls.get())
        assertEquals(1, delivered.size)
        assertEquals("project-chat-sources", delivered.single().first)
        assertEquals(turn, delivered.single().second)
        val citations = com.jarvis.android.data.repo.ChatSourcesCodec.decode(
            delivered.single().third
        )
        assertEquals(1, citations.size)
        assertEquals("https://www.iana.org/domains/reserved", citations.single().url)
        assertEquals("web:a32f3bb71f3e05ea8971", citations.single().ref)
    }

    @Test
    fun selectedProjectServerRejectionNeverFallsBackToUnscopedChat() {
        rejectProjectAgentTurn = true
        val session = JarvisAppSession(seededStore())
        session.setChatAgentProjectFor("project-private", "prj_story")
        val transport = LiveAppGatewayTransport(session, scope)
        val repo = JarvisSessionRepository(transport, scope)
        repo.start()
        await { repo.snapshot.value.phase == SessionPhase.READY }
        val turn = repo.send("project-private", "Check this private story")
        await { repo.snapshot.value.session.requests[turn]?.status?.isTerminal == true }
        assertTrue(
            repo.snapshot.value.session.requests[turn]?.status is RequestStatus.Failed
        )
        assertEquals(1, postedBodies.count {
            it.startsWith("POST /api/app/chat/agent/project/turn")
        })
        assertEquals(0, streamCalls.get())
        assertEquals(0, jsonChatCalls.get())
    }

    @Test
    fun cachedTurnRestoresImageReceiptWithoutPostingNewGeneration() {
        val store = seededStore()
        store.put(mapOf(JarvisAppSession.KEY_TURN_IDS to "req-media"))
        store.put(mapOf(
            JarvisAppSession.KEY_TURN_PREFIX + "req-media" to
                listOf("c1", "trace-media", ANDROID_DEVICE, APP_SESSION).joinToString("\u001f"),
        ))
        val appSession = JarvisAppSession(store)
        val missingStaging = AtomicInteger(0)
        appSession.onAgentImageError = { missingStaging.incrementAndGet() }
        val transport = LiveAppGatewayTransport(appSession, scope)
        runBlocking(Dispatchers.IO) {
            assertTrue(transport.recoverCompletedTurn("req-media", "c1"))
        }
        assertEquals(1, recoveredImageDownloads.get())
        // This fixture has no AttachmentStore. The recovery nevertheless
        // attempts the authenticated download instead of dropping its media.
        assertEquals(1, missingStaging.get())
        assertEquals(0, jsonChatCalls.get())
        assertEquals(0, streamCalls.get())
    }

    @Test
    fun typedContentOnlyImageRestoresWithoutLegacyMediaOrSecondInference() {
        val store = seededStore()
        store.put(mapOf(JarvisAppSession.KEY_TURN_IDS to "req-content"))
        store.put(mapOf(
            JarvisAppSession.KEY_TURN_PREFIX + "req-content" to
                listOf("c1", "trace-content", ANDROID_DEVICE, APP_SESSION)
                    .joinToString("\u001f"),
        ))
        val appSession = JarvisAppSession(store)
        val imageEvents = AtomicInteger(0)
        appSession.onAgentImageError = { imageEvents.incrementAndGet() }
        val transport = LiveAppGatewayTransport(appSession, scope)
        runBlocking(Dispatchers.IO) {
            assertTrue(transport.recoverCompletedTurn("req-content", "c1"))
        }
        assertEquals(1, recoveredImageDownloads.get())
        assertEquals(1, imageEvents.get())
        assertEquals(0, streamCalls.get())
        assertEquals(0, jsonChatCalls.get())
    }

    @Test
    fun cachedTurnDeliversTypedSourcesUnderOriginalRequestWithoutNewInference() {
        val store = seededStore()
        store.put(mapOf(JarvisAppSession.KEY_TURN_IDS to "req-sources"))
        store.put(mapOf(
            JarvisAppSession.KEY_TURN_PREFIX + "req-sources" to
                listOf("c1", "trace-citations", ANDROID_DEVICE, APP_SESSION)
                    .joinToString("\u001f"),
        ))
        val appSession = JarvisAppSession(store)
        val received = mutableListOf<Triple<String, String, String>>()
        appSession.onAgentSourcesReady = { conversationId, requestId, sources ->
            received += Triple(conversationId, requestId, sources)
        }
        val transport = LiveAppGatewayTransport(appSession, scope)
        runBlocking(Dispatchers.IO) {
            assertTrue(transport.recoverCompletedTurn("req-sources", "c1"))
        }
        assertEquals(1, received.size)
        assertEquals("c1", received.single().first)
        assertEquals("req-sources", received.single().second)
        val sources = com.jarvis.android.data.repo.ChatSourcesCodec.decode(
            received.single().third)
        assertEquals("https://example.org/report", sources.single().url)
        assertEquals(0, recoveredImageDownloads.get())
        assertEquals(0, streamCalls.get())
        assertEquals(0, jsonChatCalls.get())
    }

    @Test
    fun recoverReturnsFalseWhenServerHasNoCompletedTurn() {
        val transport = LiveAppGatewayTransport(JarvisAppSession(seededStore()), scope)
        val recovered = runBlocking(Dispatchers.IO) {
            transport.recoverCompletedTurn("req-never-ran", "c1")
        }
        assertFalse(recovered)
    }

    @Test
    fun missingSseRouteFallsBackToJsonChat() {
        streamResponder = { MockResponse().setResponseCode(404) }
        val transport = LiveAppGatewayTransport(JarvisAppSession(seededStore()), scope)
        val repo = JarvisSessionRepository(transport, scope)
        repo.start()
        await { repo.snapshot.value.phase == SessionPhase.READY }

        val cid = repo.send("c1", "hello")
        await { repo.snapshot.value.session.requests[cid]?.status?.isTerminal == true }
        val req = repo.snapshot.value.session.requests[cid]!!
        assertEquals(RequestStatus.Completed, req.status)
        assertEquals("JSON fallback answer", req.text)
        assertEquals(1, streamCalls.get())
        assertEquals(1, jsonChatCalls.get())
    }

    @Test
    fun brokenStreamRecoversCompletedTurnWithoutSecondInference() {
        // SSE opens, sends deltas, then dies before `complete` (tailnet drop).
        streamResponder = {
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody("event: ready\ndata: {}\n\nevent: delta\ndata: {\"delta\":\"partial \"}\n\n")
        }
        val store = seededStore()
        val transport = LiveAppGatewayTransport(JarvisAppSession(store), scope)
        val repo = JarvisSessionRepository(transport, scope)
        repo.start()
        await { repo.snapshot.value.phase == SessionPhase.READY }

        val cid = repo.sendWithId("req-broken", "c1", "hello")
        await { repo.snapshot.value.session.requests[cid]?.status?.isTerminal == true }
        val req = repo.snapshot.value.session.requests[cid]!!
        assertEquals(RequestStatus.Completed, req.status)
        // server-side cache wins over the partial stream
        assertEquals("Recovered answer", req.text)
        // exactly one stream attempt; recovery used GET /requests/{id}, no re-run
        assertEquals(1, streamCalls.get())
        assertEquals(null, store.get(JarvisAppSession.KEY_TURN_PREFIX + "req-broken"))
    }

    private fun errorFrame(errorJson: String): () -> MockResponse = {
        MockResponse()
            .setHeader("Content-Type", "text/event-stream")
            .setBody("event: ready\ndata: {}\n\nevent: error\ndata: $errorJson\n\n")
    }

    private fun generalAgentRepo(
        store: JarvisAppSession.MemoryStore = seededStore(),
    ): Pair<JarvisAppSession, JarvisSessionRepository> {
        advertiseGeneralAgent = true
        val session = JarvisAppSession(store)
        runBlocking(Dispatchers.IO) { session.fetchChatAccess() }
        assertTrue(session.generalChatAgentEnabled)
        val repo = JarvisSessionRepository(LiveAppGatewayTransport(session, scope), scope)
        repo.start()
        await { repo.snapshot.value.phase == SessionPhase.READY }
        return session to repo
    }

    @Test
    fun errorFrameRecoversCompletedGeneralTurnBeforeOfferingRetry() {
        streamResponder = errorFrame("""{"error":"general_agent_empty","retry_safe":false}""")
        val store = seededStore()
        val (appSession, repo) = generalAgentRepo(store)
        val missingStaging = AtomicInteger(0)
        appSession.onAgentImageError = { missingStaging.incrementAndGet() }

        val turn = repo.sendWithId("req-media", "c1", "hello")
        await { repo.snapshot.value.session.requests[turn]?.status?.isTerminal == true }
        assertEquals(RequestStatus.Completed, repo.snapshot.value.session.requests[turn]!!.status)
        assertEquals("Recovered answer", repo.snapshot.value.session.requests[turn]!!.text)
        assertEquals(TaskStatus.COMPLETED, repo.snapshot.value.session.tasks["chat_$turn"]?.status)
        // The JVM fixture has no AttachmentStore, so only the verified download is observable here.
        assertEquals(1, recoveredImageDownloads.get())
        assertEquals(1, missingStaging.get())
        await { store.get(JarvisAppSession.KEY_TURN_PREFIX + turn) == null }
        assertEquals(1, postedBodies.count { it.startsWith("POST /api/app/chat/agent/general/stream") })
        assertEquals(0, jsonChatCalls.get())
    }

    @Test
    fun errorFrameWithoutRetrySafeIsFinalWhenNoTurnIsCached() {
        streamResponder = errorFrame("""{"error":"agent_unavailable"}""")
        val (_, repo) = generalAgentRepo()
        val turn = repo.sendWithId("req-error-plain", "c1", "hello")
        await { repo.snapshot.value.session.requests[turn]?.status?.isTerminal == true }
        val failed = repo.snapshot.value.session.requests[turn]?.status as? RequestStatus.Failed
        assertEquals("agent_unavailable", failed?.error?.code)
        assertEquals(false, failed?.retryable)
        assertEquals(TaskStatus.FAILED, repo.snapshot.value.session.tasks["chat_$turn"]?.status)
        assertEquals(1, postedBodies.count { it.startsWith("POST /api/app/chat/agent/general/stream") })
    }

    @Test
    fun errorFrameWithRetrySafeIsRetryableWhenNoTurnIsCached() {
        streamResponder = errorFrame("""{"error":"agent_unavailable","retry_safe":true}""")
        val (_, repo) = generalAgentRepo()
        val turn = repo.sendWithId("req-error-safe", "c1", "hello")
        await { repo.snapshot.value.session.requests[turn]?.status?.isTerminal == true }
        val failed = repo.snapshot.value.session.requests[turn]?.status as? RequestStatus.Failed
        assertEquals("agent_unavailable", failed?.error?.code)
        assertEquals(true, failed?.retryable)
        assertEquals(1, postedBodies.count { it.startsWith("POST /api/app/chat/agent/general/stream") })
    }

    @Test
    fun errorFrameKeepsUnresolvedPaidCodesFinalEvenWithRetrySafe() {
        streamResponder = errorFrame("""{"error":"image_result_unknown","retry_safe":true}""")
        val (_, repo) = generalAgentRepo()
        val turn = repo.sendWithId("req-error-unknown", "c1", "hello")
        await { repo.snapshot.value.session.requests[turn]?.status?.isTerminal == true }
        val failed = repo.snapshot.value.session.requests[turn]?.status as? RequestStatus.Failed
        assertEquals("image_result_unknown", failed?.error?.code)
        assertEquals(false, failed?.retryable)
    }

    @Test
    fun serverAdvertisedGeneralAgentUsesAuthenticatedSseWithoutChangingLegacyDefault() {
        advertiseGeneralAgent = true
        val session = JarvisAppSession(seededStore())
        val access = runBlocking(Dispatchers.IO) { session.fetchChatAccess() }
        assertTrue(access != null)
        assertTrue(session.generalChatAgentEnabled)

        val transport = LiveAppGatewayTransport(session, scope)
        val repo = JarvisSessionRepository(transport, scope)
        repo.start()
        await { repo.snapshot.value.phase == SessionPhase.READY }
        val turn = repo.send("general-agent-conv", "Necesito buscar información")
        await { repo.snapshot.value.session.requests[turn]?.status?.isTerminal == true }
        assertEquals(RequestStatus.Completed,
                     repo.snapshot.value.session.requests[turn]?.status)
        assertTrue(postedBodies.any { it.startsWith(
            "POST /api/app/chat/agent/general/stream\\n"
        ) })
        assertTrue(postedBodies.none { it.startsWith("POST /api/app/chat/stream\\n") })
    }

    @Test
    fun ownerProfileSelectionIsServerDrivenAndSentWithTurn() {
        val session = JarvisAppSession(seededStore())
        val access = runBlocking(Dispatchers.IO) { session.fetchChatAccess() }
        assertTrue("server confirms owner role", access?.isOwner == true)
        assertTrue("server grants owner selection", access?.ownerModelSelection == true)
        assertEquals(listOf("fast", "normal", "deep"), access?.entries?.map { it.profile })
        assertEquals("speed", access?.imageGeneration?.defaultMode)
        assertTrue(access?.imageGeneration?.ownerModelSelection == true)
        assertEquals(listOf("speed", "quality", "model_select"), access?.imageGeneration?.modes?.map { it.id })
        assertEquals(listOf("auto", "grok-imagine-image", "gpt-image-2-medium"), access?.imageGeneration?.models?.map { it.id })
        assertEquals("quality", access?.imageEdit?.defaultMode)
        assertTrue(access?.imageEdit?.ownerModelSelection == true)
        assertEquals(listOf("speed", "quality", "model_select"), access?.imageEdit?.modes?.map { it.id })
        assertEquals(listOf("auto", "grok-imagine-image-2.0", "gpt-image-2-medium"), access?.imageEdit?.models?.map { it.id })

        val transport = LiveAppGatewayTransport(session, scope)
        val repo = JarvisSessionRepository(transport, scope)
        repo.start()
        await { repo.snapshot.value.phase == SessionPhase.READY }
        val cid = repo.send("c1", "hi")
        await { repo.snapshot.value.session.requests[cid]?.status?.isTerminal == true }
        assertTrue(postedBodies.last().contains("\"profile\":\"normal\""))

        // owner picks another server profile for THIS conversation; the next
        // turn on the same conversation carries it.
        session.setChatProfileFor("c1", "deep")
        val cid2 = repo.send("c1", "again")
        await { repo.snapshot.value.session.requests[cid2]?.status?.isTerminal == true }
        assertTrue(postedBodies.last().contains("\"profile\":\"deep\""))
    }

    @Test
    fun paidImageNeverDispatchesIfDurableRequestIdWriteFails() {
        val original = seededStore()
        val unsafeDisk = object : JarvisAppSession.Store by original {
            override fun putDurable(values: Map<String, String?>): Boolean = false
        }
        val session = JarvisAppSession(unsafeDisk)
        val result = runBlocking(Dispatchers.IO) {
            session.generateImage("do not charge me", "speed", null)
        }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("cannot be persisted"))
        assertTrue(postedBodies.none {
            it.startsWith("POST /api/app/images/generations")
        })
    }

    @Test
    fun logoutRetainsOnlyOpaqueUnsettledPaidIntentNotOtherSessionData() {
        val memory = seededStore()
        val key = "jarvis.paid.visual.account-hash.generations"
        memory.put(mapOf(key to "img_original_1234\\u001fdigest",
                         "chat.private.sample" to "SECRET"))
        memory.clear()
        assertEquals("img_original_1234\\u001fdigest", memory.get(key))
        assertEquals(null, memory.get(JarvisAppSession.KEY_TOKEN))
        assertEquals(null, memory.get(JarvisAppSession.KEY_USER_ID))
        assertEquals(null, memory.get("chat.private.sample"))
    }

    @Test
    fun exactImageModelSelectionIsSentAndMetadataIsParsed() {
        val session = JarvisAppSession(seededStore())
        val result = runBlocking(Dispatchers.IO) {
            session.generateImage("A blue holographic sphere", "model_select", "gpt-image-2-medium")
        }
        assertTrue(result.isSuccess)
        val reply = result.getOrThrow()
        assertEquals("openai-codex", reply.provider)
        assertEquals("gpt-image-2-medium", reply.model)
        assertEquals("model_select", reply.requestedMode)
        assertEquals("gpt-image-2-medium", reply.requestedModel)
        assertEquals(false, reply.fallbackUsed)
        assertEquals(1234L, reply.durationMs)
        val body = postedBodies.last { it.startsWith("POST /api/app/images/generations") }
        assertTrue(body.contains("\"mode\":\"model_select\""))
        assertTrue(body.contains("\"model\":\"gpt-image-2-medium\""))
    }

    @Test
    fun imageEditSendsReferencePolicyAndParsesMetadata() {
        val session = JarvisAppSession(seededStore())
        val result = runBlocking(Dispatchers.IO) {
            session.editImage(
                imageBase64 = "cmVmZXJlbmNl",
                mimeType = "image/jpeg",
                instruction = "Change only the armor.",
                mode = "quality",
                preserveIdentity = "high",
                aspectRatio = "portrait",
            )
        }
        assertTrue(result.isSuccess)
        val reply = result.getOrThrow()
        assertEquals("xai-oauth", reply.provider)
        assertEquals("grok-imagine-image-2.0", reply.model)
        assertEquals("quality", reply.requestedMode)
        assertEquals(false, reply.fallbackUsed)
        assertEquals(2222L, reply.durationMs)
        val body = postedBodies.last { it.startsWith("POST /api/app/images/edits") }
        assertTrue(body.contains("\"image_base64\":\"cmVmZXJlbmNl\""))
        assertTrue(body.contains("\"instruction\":\"Change only the armor.\""))
        assertTrue(body.contains("\"preserve_identity\":\"high\""))
        assertTrue(body.contains("\"aspect_ratio\":\"portrait\""))
    }

    @Test
    fun setWikiPrimaryReferencePostsStructuredVisualAction() {
        val session = JarvisAppSession(seededStore())
        val result = runBlocking(Dispatchers.IO) {
            session.setWikiPrimaryReference(
                imageBase64 = "aW1hZ2U=",
                mimeType = "image/jpeg",
                projectId = "prj_story",
                characterId = "character:alexander",
                alt = "Alexander canonical visual",
            )
        }
        assertTrue(result.isSuccess)
        val reply = result.getOrThrow()
        assertEquals("va_alexander_primary_2", reply.assetId)
        assertEquals("character:alexander", reply.characterId)
        assertEquals(2, reply.visualRevision)
        assertEquals("APPROVED", reply.status)
        val body = postedBodies.last {
            it.startsWith("POST /api/app/writing-room/visual-assets/set-wiki-primary")
        }
        assertTrue(body.contains("\"character_id\":\"character:alexander\""))
        assertTrue(body.contains("\"source\":\"MANUAL_UPLOAD\""))
        assertTrue(body.contains("\"perspective\":\"front\""))
    }

    @Test
    fun profileSelectionIsConversationLocal() {
        val store = JarvisAppSession.MemoryStore()
        val session = JarvisAppSession(store)
        assertEquals("normal", session.chatProfileFor("cA"))
        session.setChatProfileFor("cA", "fast")
        assertEquals("fast", session.chatProfileFor("cA"))
        // PA-7M: conversation B keeps its own default; no cross-selection leak.
        assertEquals("normal", session.chatProfileFor("cB"))
        // Same store after a restart still shows A's choice, B's default.
        assertEquals("fast", JarvisAppSession(store).chatProfileFor("cA"))
        assertEquals("normal", JarvisAppSession(store).chatProfileFor("cB"))
    }

    @Test
    fun concurrentConversationsCarryIndependentProfilesAndContexts() {
        val session = JarvisAppSession(seededStore())
        session.setChatProfileFor("cA", "fast")
        session.setChatProfileFor("cB", "deep")
        val transport = LiveAppGatewayTransport(session, scope)
        val repo = JarvisSessionRepository(transport, scope)
        repo.start()
        await { repo.snapshot.value.phase == SessionPhase.READY }

        val idA = repo.send("cA", "alpha question", contextProvider = {
            listOf(com.jarvis.android.contract.ChatTurn("user", "only in A"))
        })
        val idB = repo.send("cB", "beta question", contextProvider = {
            listOf(com.jarvis.android.contract.ChatTurn("user", "only in B"))
        })
        await {
            repo.snapshot.value.session.requests[idA]?.status?.isTerminal == true &&
                repo.snapshot.value.session.requests[idB]?.status?.isTerminal == true
        }
        assertEquals(RequestStatus.Completed, repo.snapshot.value.session.requests[idA]?.status)
        assertEquals(RequestStatus.Completed, repo.snapshot.value.session.requests[idB]?.status)
        val bodyA = postedBodies.single { it.contains(idA) }
        val bodyB = postedBodies.single { it.contains(idB) }
        assertTrue(bodyA.contains("\"profile\":\"fast\""))
        assertTrue(bodyB.contains("\"profile\":\"deep\""))
        assertTrue(bodyA.contains("only in A") && !bodyA.contains("only in B"))
        assertTrue(bodyB.contains("only in B") && !bodyB.contains("only in A"))
        // different conversation ids on the wire
        assertTrue(bodyA.contains("\"conversation_id\":\"cA\""))
        assertTrue(bodyB.contains("\"conversation_id\":\"cB\""))
    }

    @Test
    fun approvalAndUploadRefuseExplicitlyUntilPcAGates() {
        // Lane H seam: the live surface must fail closed with a clear error
        // (PA-5/6 + upload freeze), never silently drop or invent behavior.
        val transport = LiveAppGatewayTransport(JarvisAppSession(seededStore()), scope)
        val repo = JarvisSessionRepository(transport, scope)
        repo.start()
        await { repo.snapshot.value.phase == SessionPhase.READY }

        val err = runBlocking(Dispatchers.IO) {
            runCatching {
                transport.send(
                    com.jarvis.android.contract.MobileRequest.ResolveApproval(
                        "a1", com.jarvis.android.contract.ApprovalOutcome.APPROVED, "res-1",
                    ),
                )
            }.exceptionOrNull()
        }
        assertTrue(err is TransportException)
        assertTrue(err!!.message!!.contains("approvals not enabled"))

        val upErr = runBlocking(Dispatchers.IO) {
            runCatching {
                transport.send(
                    com.jarvis.android.contract.MobileRequest.UploadAttachment(
                        "att1", "c1", "p.jpg", "image/jpeg", 10L,
                    ),
                )
            }.exceptionOrNull()
        }
        assertTrue(upErr is TransportException)
        assertTrue(upErr!!.message!!.contains("upload contract not frozen"))
    }
}


class ImageGenerationErrorFormattingTest {
    @Test
    fun serverImageErrorUsesReadableMessageInsteadOfRawJson() {
        val error = try {
            requireImageGenerationOk(
                502 to """{"schema":"jarvis.web.error.v1","error":"image_generation_selected_model_failed","message":"GPT Image failed via openai-codex. No fallback was used."}"""
            )
            null
        } catch (caught: TransportException) {
            caught
        }
        assertTrue(error != null)
        assertEquals(
            "GPT Image failed via openai-codex. No fallback was used.",
            error?.message,
        )
        assertFalse(error?.message.orEmpty().contains("{\"schema\""))
    }
}
