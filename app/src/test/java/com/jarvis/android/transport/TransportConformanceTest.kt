package com.jarvis.android.transport

import com.jarvis.android.contract.webv1.WebV1
import com.jarvis.android.data.repo.JarvisSessionRepository
import com.jarvis.android.data.repo.SessionPhase
import com.jarvis.android.data.state.ConnectionState
import com.jarvis.android.data.state.RequestStatus
import com.jarvis.android.transport.http.HttpGatewayTransport
import com.jarvis.android.transport.live.JarvisAppSession
import com.jarvis.android.transport.live.LiveAppGatewayTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Lane G: transport conformance. For an equivalent server response, the
 * frozen-contract HTTP adapter (`/api/v1`) and the current app-session LIVE
 * adapter (`/api/app` SSE) must drive the SAME pure reducer to the SAME domain
 * outcome. Swapping transports must not change conversation semantics; when
 * PC-A flips API_V1_ACTIVE, only AppContainer's mode changes — not this suite.
 */
class TransportConformanceTest {

    private lateinit var scope: CoroutineScope
    private lateinit var job: Job
    private val servers = mutableListOf<MockWebServer>()

    @Before fun setUp() {
        job = SupervisorJob()
        scope = CoroutineScope(job + Dispatchers.Default)
    }

    @After fun tearDown() {
        scope.cancel()
        servers.forEach { runCatching { it.shutdown() } }
        servers.clear()
    }

    private fun waitUntil(timeoutMs: Long = 8_000, cond: () -> Boolean) =
        runBlocking(Dispatchers.Default) { withTimeout(timeoutMs) { while (!cond()) delay(10) } }

    // ---- frozen /api/v1 fixture ------------------------------------------------

    /**
     * Serves `connection.ready` on the first poll (so the session reaches READY),
     * then — only after a submit POST — the message events, so the optimistic
     * beginSend is always registered before accepted/delta/completed arrive.
     */
    private fun httpTransport(
        messageEvents: List<String>,
        failEvents: Boolean = false,
        /** Events served on the first poll; defaults to a valid connection.ready. */
        firstPollEvents: List<String>? = null,
    ): HttpGatewayTransport {
        val server = MockWebServer()
        var readySent = false
        var submitted = false
        var msgSent = false
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path == "/api/v1/health" -> MockResponse().setBody(
                    """{"status":"ok","protocol_version":"1.0","capabilities":[]}""",
                )
                request.path?.startsWith("/api/v1/events") == true && failEvents ->
                    MockResponse().setResponseCode(500)
                request.path?.startsWith("/api/v1/events") == true -> {
                    val body = when {
                        !readySent -> {
                            readySent = true
                            firstPollEvents ?: listOf(webEvent("c0", "e-init", "connection.ready", "{}"))
                        }
                        submitted && !msgSent -> { msgSent = true; messageEvents }
                        else -> emptyList()
                    }
                    MockResponse().setBody(body.joinToString(",", "[", "]"))
                }
                request.method == "POST" && request.path == "/api/v1/requests" -> {
                    submitted = true
                    MockResponse().setBody("{}")
                }
                else -> MockResponse().setResponseCode(404)
            }
        }
        server.start()
        servers += server
        return HttpGatewayTransport(
            scope = scope,
            baseUrlProvider = { server.url("/").toString().trimEnd('/') },
            auth = AuthProvider { mapOf("Authorization" to "Bearer conf-token") },
            pollIntervalMs = 30,
        )
    }

    private fun webEvent(
        cursor: String,
        eventId: String,
        type: String,
        payload: String,
        sequence: Long = 1,
        conversationId: String = "c1",
        requestId: String? = REQ,
        protocolVersion: String = "1.0",
        fingerprint: String = WebV1.FINGERPRINT,
    ): String {
        val req = if (requestId == null) "null" else "\"$requestId\""
        return """{"schema":"${WebV1.EVENT_SCHEMA}","protocol_version":"$protocolVersion",""" +
            """"contract_fingerprint":"$fingerprint","event_id":"$eventId","sequence":$sequence,""" +
            """"cursor":"$cursor","type":"$type","timestamp_utc":"2026-09-10T08:00:00Z",""" +
            """"user_id":"owner-001","device_id":"device-001","session_id":"session-001",""" +
            """"conversation_id":"$conversationId","request_id":$req,"trace_id":"trace-001",""" +
            """"task_id":"task-001","run_id":"run-001","action_id":null,"optional":false,""" +
            """"payload":$payload}"""
    }

    private fun webAccepted() = webEvent(
        cursor = "c1",
        eventId = "e-acc",
        type = "message.accepted",
        payload = """{"message_id":"m1"}""",
        sequence = 1,
    )

    private val happyHttpEvents: List<String> get() = listOf(
        webAccepted(),
        webEvent("c2", "e-d0", "message.delta", """{"message_id":"m1","seq":0,"delta":"Hello "}"""),
        webEvent("c3", "e-d1", "message.delta", """{"message_id":"m1","seq":1,"delta":"world"}"""),
        webEvent("c4", "e-done", "message.completed", """{"message_id":"m1","full_text":"$EXPECTED"}"""),
    )

    // ---- current /api/app fixture ----------------------------------------------

    private fun liveTransport(sseBody: String, cachedTurn: String? = null): LiveAppGatewayTransport {
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path == "/api/app/session" -> MockResponse().setBody(
                    """{"schema":"jarvis.app.session.v1","authenticated":true,""" +
                        """"user":{"id":"u1","username":"alex","role":"owner"}}""",
                )
                request.path == "/api/app/chat/stream" -> MockResponse()
                    .setHeader("Content-Type", "text/event-stream").setBody(sseBody)
                request.path?.startsWith("/api/app/chat/requests/") == true && cachedTurn != null ->
                    MockResponse().setBody(cachedTurn)
                else -> MockResponse().setResponseCode(404)
            }
        }
        server.start()
        servers += server
        val store = JarvisAppSession.MemoryStore()
        store.put(
            mapOf(
                JarvisAppSession.KEY_TOKEN to "test-token",
                JarvisAppSession.KEY_BASE to server.url("/").toString().trimEnd('/'),
                JarvisAppSession.KEY_USER_ID to "u1",
                JarvisAppSession.KEY_EXPIRES to "2099-01-01T00:00:00Z",
            ),
        )
        return LiveAppGatewayTransport(JarvisAppSession(store), scope)
    }

    private val happySse: String get() = buildString {
        append("event: ready\ndata: {}\n\n")
        append("event: delta\ndata: {\"delta\":\"Hello \"}\n\n")
        append("event: delta\ndata: {\"delta\":\"world\"}\n\n")
        append("event: complete\ndata: {\"schema\":\"jarvis.chat.turn.v1\",\"response\":{\"text\":\"$EXPECTED\"}}\n\n")
    }

    private fun JarvisSessionRepository.awaitTerminal(id: String): RequestStatus {
        waitUntil { snapshot.value.session.requests[id]?.status?.isTerminal == true }
        return snapshot.value.session.requests[id]!!.status
    }

    @Test
    fun equivalentServerResponsesProduceIdenticalDomainOutcomes() {
        val repoHttp = JarvisSessionRepository(httpTransport(happyHttpEvents), scope)
        repoHttp.start()
        waitUntil { repoHttp.snapshot.value.phase == SessionPhase.READY }
        repoHttp.sendWithId(REQ, "c1", "greeting")
        assertEquals(RequestStatus.Completed, repoHttp.awaitTerminal(REQ))
        assertEquals(EXPECTED, repoHttp.snapshot.value.session.requests[REQ]!!.text)
        repoHttp.stop()

        val repoLive = JarvisSessionRepository(liveTransport(happySse), scope)
        repoLive.start()
        waitUntil { repoLive.snapshot.value.phase == SessionPhase.READY }
        repoLive.sendWithId(REQ, "c1", "greeting")
        assertEquals(RequestStatus.Completed, repoLive.awaitTerminal(REQ))
        assertEquals(EXPECTED, repoLive.snapshot.value.session.requests[REQ]!!.text)
        repoLive.stop()
    }

    @Test
    fun duplicateWebEventsAreSuppressedById() {
        // e-d1 redelivered with a NEW cursor but the SAME event_id.
        val events = happyHttpEvents + webEvent(
            "c3b", "e-d1", "message.delta", """{"message_id":"m1","seq":1,"delta":"world"}""",
        )
        val repo = JarvisSessionRepository(httpTransport(events), scope)
        repo.start()
        waitUntil { repo.snapshot.value.phase == SessionPhase.READY }
        repo.sendWithId(REQ, "c1", "greeting")
        repo.awaitTerminal(REQ)
        assertEquals(RequestStatus.Completed, repo.snapshot.value.session.requests[REQ]!!.status)
        assertEquals(EXPECTED, repo.snapshot.value.session.requests[REQ]!!.text)
        repo.stop()
    }

    @Test
    fun midStreamDropNeverFabricatesCompletionOnEitherTransport() {
        val cached = """{"schema":"jarvis.chat.turn.v1","result":{"status":"completed"},""" +
            """"response":{"text":"$EXPECTED"}}"""

        // LIVE: SSE breaks before `complete`; GET /requests/{id} returns the
        // completed turn and the reducer settles with the authoritative text.
        val repoLive = JarvisSessionRepository(liveTransport(PARTIAL_SSE, cachedTurn = cached), scope)
        repoLive.start()
        waitUntil { repoLive.snapshot.value.phase == SessionPhase.READY }
        repoLive.sendWithId(REQ, "c1", "greeting")
        assertEquals(RequestStatus.Completed, repoLive.awaitTerminal(REQ))
        assertEquals(EXPECTED, repoLive.snapshot.value.session.requests[REQ]!!.text)
        repoLive.stop()

        // HTTP: events fail (link drops) -> the disconnect path fails the
        // in-flight request retryably; no fake "completed" is ever produced.
        val repoHttp = JarvisSessionRepository(
            httpTransport(happyHttpEvents, failEvents = true), scope, backoffMs = emptyList(),
        )
        repoHttp.start()
        repoHttp.sendWithId(REQ, "c1", "greeting")
        waitUntil {
            (repoHttp.snapshot.value.session.requests[REQ]?.status as? RequestStatus.Failed)?.retryable == true
        }
        // A broken link must never fabricate the answer: no completed text on
        // the failed request, and it stays retryable so recovery can settle it.
        val failed = repoHttp.snapshot.value.session.requests[REQ]!!.status as RequestStatus.Failed
        assertTrue("failure is retryable", failed.retryable)
        repoHttp.stop()
    }

    @Test
    fun incompatibleProtocolFailsClosedOnHttpTransport() {
        val bad = listOf(
            webEvent(
                cursor = "c1",
                eventId = "x1",
                type = "connection.ready",
                payload = "{}",
                requestId = null,
                protocolVersion = "2.0",
            ),
        )
        // Bad envelope arrives on the very first poll, before any submit.
        val repo = JarvisSessionRepository(httpTransport(emptyList(), firstPollEvents = bad), scope)
        repo.start()
        waitUntil { repo.snapshot.value.phase == SessionPhase.MISMATCH }
        assertEquals(ConnectionState.PROTOCOL_MISMATCH, repo.snapshot.value.session.connection)
        repo.stop()
    }

    companion object {
        private const val REQ = "r-conf"
        private const val EXPECTED = "Hello world"
        private const val PARTIAL_SSE =
            "event: ready\ndata: {}\n\nevent: delta\ndata: {\"delta\":\"partial \"}\n\n"
    }
}
