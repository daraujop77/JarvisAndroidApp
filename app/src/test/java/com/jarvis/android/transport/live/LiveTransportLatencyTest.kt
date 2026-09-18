package com.jarvis.android.transport.live

import com.jarvis.android.transport.LinkState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * Deterministic baseline for live connect cost. Not a device measurement.
 * Each connect revalidates /api/app/session. Skipping that on reconnect is
 * not safe: disconnect clears the validated flag, and a stale token must
 * still fail closed.
 */
class LiveTransportLatencyTest {

    private val servers = mutableListOf<MockWebServer>()

    @After
    fun tearDown() {
        servers.forEach { runCatching { it.shutdown() } }
    }

    @Test
    fun baselineConnectRepeatsSessionValidation() {
        val sessionCalls = AtomicInteger(0)
        val server = server(sessionCalls)
        val store = JarvisAppSession.MemoryStore()
        store.put(
            mapOf(
                JarvisAppSession.KEY_TOKEN to "test-token",
                JarvisAppSession.KEY_BASE to server.url("/").toString().trimEnd('/'),
                JarvisAppSession.KEY_USER_ID to "u1",
                JarvisAppSession.KEY_EXPIRES to "2099-01-01T00:00:00Z",
            ),
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val transport = LiveAppGatewayTransport(JarvisAppSession(store), scope)
        try {
            val started = System.nanoTime()
            runBlocking { transport.connect() }
            val firstMs = (System.nanoTime() - started) / 1_000_000
            assertEquals(LinkState.CONNECTED, transport.linkState.value)
            assertEquals("first connect validates the session once", 1, sessionCalls.get())

            runBlocking { transport.disconnect() }
            val reconnectStarted = System.nanoTime()
            runBlocking { transport.connect() }
            val reconnectMs = (System.nanoTime() - reconnectStarted) / 1_000_000
            assertEquals(LinkState.CONNECTED, transport.linkState.value)
            assertTrue(
                "reconnect repeated the session round trip: calls=${sessionCalls.get()} firstMs=$firstMs reconnectMs=$reconnectMs",
                sessionCalls.get() == 2,
            )
        } finally {
            scope.cancel()
        }
    }

    private fun server(sessionCalls: AtomicInteger): MockWebServer {
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.path == "/api/app/session") {
                    sessionCalls.incrementAndGet()
                    Thread.sleep(25)
                    return MockResponse().setBody(
                        """{"schema":"jarvis.app.session.v1","authenticated":true,"user":{"id":"u1","username":"alex","role":"owner"}}""",
                    )
                }
                return MockResponse().setResponseCode(404)
            }
        }
        server.start()
        servers += server
        return server
    }
}
