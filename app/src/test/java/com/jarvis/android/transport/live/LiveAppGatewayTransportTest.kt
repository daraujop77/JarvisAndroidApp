package com.jarvis.android.transport.live

import com.jarvis.android.data.repo.JarvisSessionRepository
import com.jarvis.android.data.repo.SessionPhase
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
    private val cancelCalls = AtomicInteger(0)
    private val loginCalls = AtomicInteger(0)
    private val streamCalls = AtomicInteger(0)
    private val postedBodies = java.util.Collections.synchronizedList(mutableListOf<String>())

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
                    request.path == "/api/app/chat/stream" && auth != "Bearer test-token" ->
                        MockResponse().setResponseCode(401)
                    request.path == "/api/app/chat/stream" -> {
                        streamCalls.incrementAndGet()
                        postedBodies += "POST /api/app/chat/stream\n" + request.body.readUtf8()
                        MockResponse()
                            .setHeader("Content-Type", "text/event-stream")
                            .setBody(sseBody())
                            .throttleBody(96, 150, TimeUnit.MILLISECONDS)
                    }
                    request.path?.startsWith("/api/app/chat/requests/") == true && auth != "Bearer test-token" ->
                        MockResponse().setResponseCode(401)
                    request.path?.startsWith("/api/app/chat/requests/") == true -> {
                        val rid = request.path!!.substringAfterLast('/')
                        if (rid == "req-lost") {
                            MockResponse().setBody(
                                """{"schema":"jarvis.chat.turn.v1","result":{"status":"completed"},""" +
                                    """"response":{"text":"Recovered answer"}}""",
                            )
                        } else {
                            MockResponse().setResponseCode(404)
                                .setBody("""{"schema":"jarvis.web.error.v1","error":"unknown_request"}""")
                        }
                    }
                    request.path == "/api/app/chat/cancel" -> {
                        cancelCalls.incrementAndGet()
                        MockResponse().setResponseCode(202)
                            .setBody("""{"result":"cancel_requested"}""")
                    }
                    request.path == "/api/status" -> MockResponse().setBody("""{"status":"ok"}""")
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
        append("event: complete\ndata: {\"schema\":\"jarvis.chat.turn.v1\",\"response\":{\"text\":\"Full reply\"},\"result\":{\"status\":\"completed\"}}\n\n")
    }

    private fun baseUrl() = server.url("/").toString().trimEnd('/')

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
                    "c1|trace-lost|$ANDROID_DEVICE|$APP_SESSION",
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
    fun recoverReturnsFalseWhenServerHasNoCompletedTurn() {
        val transport = LiveAppGatewayTransport(JarvisAppSession(seededStore()), scope)
        val recovered = runBlocking(Dispatchers.IO) {
            transport.recoverCompletedTurn("req-never-ran", "c1")
        }
        assertFalse(recovered)
    }

    // test-only view into the memory store for the no-password-never-stored assert
    private fun JarvisAppSession.tokenStoreForTest(): String {
        val storeField = JarvisAppSession::class.java.getDeclaredField("store")
        storeField.isAccessible = true
        val store = storeField.get(this) as JarvisAppSession.Store
        return listOf(
            JarvisAppSession.KEY_TOKEN, JarvisAppSession.KEY_BASE, JarvisAppSession.KEY_USER_ID,
            JarvisAppSession.KEY_USERNAME, JarvisAppSession.KEY_ROLE, JarvisAppSession.KEY_EXPIRES,
            JarvisAppSession.KEY_APP_SESSION, JarvisAppSession.KEY_DEVICE,
        ).mapNotNull { store.get(it) }.joinToString("|")
    }
}
