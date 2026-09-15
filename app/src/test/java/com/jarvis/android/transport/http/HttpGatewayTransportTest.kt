package com.jarvis.android.transport.http

import com.jarvis.android.contract.MobileRequest
import com.jarvis.android.contract.webv1.WebV1
import com.jarvis.android.transport.AuthProvider
import com.jarvis.android.transport.LinkState
import com.jarvis.android.transport.TransportException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
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

class HttpGatewayTransportTest {

    private lateinit var server: MockWebServer
    private lateinit var scope: CoroutineScope
    private val posted = mutableListOf<String>()
    private val authSeen = mutableListOf<String?>()
    private val eventQueries = mutableListOf<String?>()

    @Before
    fun setUp() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                authSeen += request.getHeader("Authorization")
                return when {
                    path == "/api/v1/health" -> MockResponse().setBody(
                        """{"status":"ok","protocol_version":"1.0","contract_fingerprint":"web-v1-1.0","capabilities":["streaming"]}""",
                    )
                    path == "/api/v1/capabilities" -> MockResponse().setBody(
                        """{"schema":"jarvis.web.capabilities.v1","protocol_version":"1.0","server_version":"pc-a-test","contract_fingerprint":"${WebV1.FINGERPRINT}","capabilities":["conversation","streaming","cancel","replay","approvals","action_proposals"],"future_flag":true}""",
                    )
                    path.startsWith("/api/v1/events") -> {
                        val after = path.substringAfter("after=", "").ifBlank { null }
                            ?.let { java.net.URLDecoder.decode(it, Charsets.UTF_8) }
                        eventQueries += after
                        val cursor = after ?: "tok_0"
                        MockResponse().setBody(
                            """
                            [{
                              "schema":"jarvis.web.event.v1",
                              "protocol_version":"1.0",
                              "contract_fingerprint":"${WebV1.FINGERPRINT}",
                              "event_id":"evt_http_1",
                              "sequence":1,
                              "cursor":"$cursor-next",
                              "type":"connection.ready",
                              "timestamp_utc":"2026-09-10T08:00:00Z",
                              "user_id":"owner-001",
                              "device_id":"device-001",
                              "session_id":"session-001",
                              "conversation_id":"c1",
                              "request_id":null,
                              "trace_id":"trace-001",
                              "task_id":null,
                              "run_id":null,
                              "action_id":null,
                              "optional":false,
                              "payload":{}
                            }]
                            """.trimIndent(),
                        )
                    }
                    request.method == "POST" && path == "/api/v1/requests" -> {
                        posted += request.body.readUtf8()
                        MockResponse().setBody("""{"ok":true}""")
                    }
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

    private fun transport(auth: AuthProvider = AuthProvider { emptyMap() }) = HttpGatewayTransport(
        scope = scope,
        baseUrlProvider = { server.url("/").toString().trimEnd('/') },
        auth = auth,
        pollIntervalMs = 10_000,
    )

    private fun <T> runBlockingShort(block: suspend kotlinx.coroutines.CoroutineScope.() -> T): T =
        kotlinx.coroutines.runBlocking(Dispatchers.IO, block = block)

    @Test
    fun healthAndCapabilitiesUseWebV1Paths() {
        val t = transport()
        runBlockingShort {
            t.connect()
            val h = t.health()
            assertEquals("ok", h.status)
            assertEquals(WebV1.VERSION, h.protocolVersion)
            val caps = t.capabilities()
            assertEquals("pc-a-test", caps.serverVersion)
            assertTrue(caps.capabilities.contains("streaming"))
            assertTrue(caps.operations.contains("submit"))
            t.disconnect()
        }
    }

    @Test
    fun submitPostsWebV1EnvelopeAndReplayUsesOpaqueCursor() {
        val t = transport()
        runBlockingShort {
            t.connect()
            t.send(MobileRequest.SendMessage("r1", "c1", "hello"))
            t.send(MobileRequest.Replay("not-a-number:abc+/="))
            delay(50)
            t.disconnect()
        }
        assertTrue(posted.any { it.contains("\"operation\":\"submit\"") && it.contains("\"request_id\":\"r1\"") })
        assertTrue(posted.any { it.contains("\"operation\":\"resume\"") && it.contains("not-a-number:abc+/=") })
        assertTrue(eventQueries.any { it == "not-a-number:abc+/=" })
        assertTrue(t.lastCursor().startsWith("not-a-number:abc+/="))
    }

    @Test
    fun authProviderHeadersAreAttachedAndAbsentMeansUnauthenticated() {
        val authed = transport(AuthProvider { mapOf("Authorization" to "Bearer test-token") })
        runBlockingShort {
            authed.connect()
            authed.health()
            authed.disconnect()
            delay(50)
        }
        val bearerCount = authSeen.count { it == "Bearer test-token" }
        assertTrue(bearerCount > 0)

        val closed = transport()
        runBlockingShort {
            closed.connect()
            closed.health()
            closed.disconnect()
            delay(50)
        }
        assertEquals(bearerCount, authSeen.count { it == "Bearer test-token" })
        assertTrue(authSeen.any { it == null })
    }

    @Test
    fun connectReachesConnectedWithoutLivePcA() {
        val t = transport()
        runBlockingShort {
            t.connect()
            withTimeout(2_000) { while (t.linkState.value != LinkState.CONNECTED) delay(10) }
            t.disconnect()
        }
        assertEquals(LinkState.CLOSED, t.linkState.value)
    }

    @Test
    fun pollFailureDowngradesLinkToReconnecting() {
        // Hardening: health OK at connect, then events 500 -> the poller must
        // flip the link to RECONNECTING (so the session layer's bounded
        // backoff takes over) instead of spinning silently as CONNECTED.
        val flaky = MockWebServer()
        var eventsHit = 0
        flaky.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path == "/api/v1/health" -> MockResponse().setBody(
                    """{"status":"ok","protocol_version":"1.0","capabilities":[]}""",
                )
                request.path?.startsWith("/api/v1/events") == true -> {
                    eventsHit++
                    MockResponse().setResponseCode(500).setBody("boom")
                }
                else -> MockResponse().setResponseCode(404)
            }
        }
        flaky.start()
        try {
            val t = HttpGatewayTransport(
                scope = scope,
                baseUrlProvider = { flaky.url("/").toString().trimEnd('/') },
                pollIntervalMs = 20,
            )
            runBlockingShort {
                t.connect()
                withTimeout(3_000) { while (t.linkState.value != LinkState.RECONNECTING) delay(10) }
                t.disconnect()
            }
            assertTrue("poller actually attempted events", eventsHit >= 1)
        } finally {
            flaky.shutdown()
        }
    }

    @Test
    fun publicHostIsRejectedBeforeAnyRequest() {
        // Lane D security rule: the HTTP adapter carries a real bearer when a
        // session exists, so a public/typo'd base URL must fail closed before any
        // socket is opened (no token downgrade). requireBase() runs first in
        // connect(), so the default client never issues a request.
        val t = HttpGatewayTransport(
            scope = scope,
            baseUrlProvider = { "https://jarvis.example.com" },
            auth = AuthProvider { mapOf("Authorization" to "Bearer secret-token") },
        )
        val err = runBlockingShort { runCatching { t.connect() }.exceptionOrNull() }
        assertTrue(err is com.jarvis.android.transport.TransportException)
        assertTrue(err!!.message!!.contains("private-network"))
        assertEquals(LinkState.FAILED, t.linkState.value)
    }

    @Test
    fun typedErrorEnvelopeIsParsedWithoutRawBody() {
        val errServer = MockWebServer()
        errServer.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path == "/api/v1/health" -> MockResponse().setBody(
                    """{"status":"ok","protocol_version":"1.0","capabilities":[]}""",
                )
                request.method == "POST" && request.path == "/api/v1/requests" -> MockResponse()
                    .setResponseCode(503)
                    .setBody(
                        """{"schema":"jarvis.web.error.v1","protocol_version":"1.0","contract_fingerprint":"${WebV1.FINGERPRINT}","status_code":503,"error":"gateway_unavailable","message":"busy","retryable":true,"request_id":"req-http-err","trace_id":"trace-http-err","token":"secret-should-not-leak"}""",
                    )
                else -> MockResponse().setResponseCode(404)
            }
        }
        errServer.start()
        try {
            val t = HttpGatewayTransport(
                scope = scope,
                baseUrlProvider = { errServer.url("/").toString().trimEnd('/') },
                pollIntervalMs = 10_000,
            )
            val err = runBlockingShort {
                t.connect()
                runCatching { t.send(MobileRequest.SendMessage("r1", "c1", "hello")) }.exceptionOrNull()
            }
            assertTrue(err is TransportException)
            val te = err as TransportException
            assertEquals("gateway_unavailable", te.code)
            assertEquals(503, te.statusCode)
            assertEquals("req-http-err", te.requestId)
            assertEquals("trace-http-err", te.traceId)
            assertTrue(te.retryable)
            assertFalse(te.message!!.contains("secret"))
            assertFalse(te.message!!.contains("token"))
        } finally {
            errServer.shutdown()
        }
    }

    @Test
    fun capabilitiesMissingServerVersionFailsClosed() {
        val bad = MockWebServer()
        bad.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path == "/api/v1/health" -> MockResponse().setBody(
                    """{"status":"ok","protocol_version":"1.0","capabilities":[]}""",
                )
                request.path == "/api/v1/capabilities" -> MockResponse().setBody(
                    """{"schema":"jarvis.web.capabilities.v1","protocol_version":"1.0","contract_fingerprint":"${WebV1.FINGERPRINT}","capabilities":["streaming"]}""",
                )
                else -> MockResponse().setResponseCode(404)
            }
        }
        bad.start()
        try {
            val t = HttpGatewayTransport(
                scope = scope,
                baseUrlProvider = { bad.url("/").toString().trimEnd('/') },
                pollIntervalMs = 10_000,
            )
            val err = runBlockingShort {
                t.connect()
                runCatching { t.capabilities() }.exceptionOrNull()
            }
            assertTrue(err is TransportException)
            assertTrue((err as TransportException).message!!.contains("server_version"))
        } finally {
            bad.shutdown()
        }
    }
}
