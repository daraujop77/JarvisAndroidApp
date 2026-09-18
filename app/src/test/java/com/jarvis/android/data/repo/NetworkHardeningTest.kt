package com.jarvis.android.data.repo

import com.jarvis.android.contract.HealthResponse
import com.jarvis.android.contract.MobileRequest
import com.jarvis.android.data.state.ConnectionState
import com.jarvis.android.transport.GatewayTransport
import com.jarvis.android.transport.LinkState
import com.jarvis.android.transport.TransportException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * Lane B: daily-use network hardening invariants, deterministic (no real
 * sockets). A scripted transport counts connection attempts so the bounded
 * retry ("no storm") guarantee is asserted, not just documented.
 */
class NetworkHardeningTest {

    private lateinit var job: Job
    private lateinit var scope: CoroutineScope

    @Before fun setUp() {
        job = SupervisorJob()
        scope = CoroutineScope(job + Dispatchers.Default)
    }

    @After fun tearDown() = scope.cancel()

    private class ScriptedTransport(
        /** true = connect() succeeds and the link reports CONNECTED. */
        var connectSucceeds: Boolean = false,
    ) : GatewayTransport {
        private val _frames = kotlinx.coroutines.channels.Channel<String>(kotlinx.coroutines.channels.Channel.UNLIMITED)
        override val frames: Flow<String> = _frames.receiveAsFlow()
        private val _linkState = MutableStateFlow(LinkState.IDLE)
        override val linkState: StateFlow<LinkState> = _linkState.asStateFlow()
        val connectCalls = AtomicInteger(0)

        override suspend fun connect() {
            connectCalls.incrementAndGet()
            if (!connectSucceeds) {
                _linkState.value = LinkState.FAILED
                throw TransportException("gateway unavailable")
            }
            _linkState.value = LinkState.CONNECTED
            // Deliberately does NOT emit connection.state: socket-connect alone
            // must never make the session look ONLINE.
        }

        override suspend fun disconnect() {
            _linkState.value = LinkState.CLOSED
        }

        override suspend fun send(request: MobileRequest) {
            if (_linkState.value != LinkState.CONNECTED) throw TransportException("offline")
        }

        override suspend fun health(): HealthResponse = throw TransportException("n/a")

        fun pushFrame(json: String) {
            _frames.trySend(json)
        }
    }

    private fun settle(millis: Long = 250) =
        runBlocking(Dispatchers.Default) { delay(millis) }

    @Test
    fun neverShowsOnlineUnlessServerConfirmsUsable() {
        // Link connects at the socket level, but the server never sends
        // connection.ready -> phase must NOT become READY / ONLINE.
        val transport = ScriptedTransport(connectSucceeds = true)
        val repo = JarvisSessionRepository(transport, scope, backoffMs = listOf(5L))
        repo.start()
        settle(300)
        assertNotEquals(SessionPhase.READY, repo.snapshot.value.phase)
        assertNotEquals(ConnectionState.ONLINE, repo.snapshot.value.session.connection)

        // Once the server does confirm, READY follows.
        transport.pushFrame(
            """{"cursor":1,"eventId":"e1","version":"1.0",""" +
                """"event":{"type":"connection.state","payload":{"state":"ONLINE"}}}""",
        )
        settle(200)
        assertEquals(SessionPhase.READY, repo.snapshot.value.phase)
        assertEquals(ConnectionState.ONLINE, repo.snapshot.value.session.connection)
        repo.stop()
    }

    @Test
    fun reconnectBackoffIsBoundedAndNeverStorms() {
        // Gateway permanently unavailable: attempts must equal 1 initial +
        // MAX_ATTEMPTS scheduled retries and then STOP.
        val transport = ScriptedTransport(connectSucceeds = false)
        val repo = JarvisSessionRepository(transport, scope, backoffMs = listOf(2L, 2L, 2L, 2L, 2L))
        repo.start()
        // 1 from start(); up to 5 from the bounded loop (delays are ~2ms).
        settle(400)
        val observed = transport.connectCalls.get()
        assertTrue("bounded: got $observed", observed in 2..6)
        // It must stop: the count no longer grows once the budget is spent.
        settle(300)
        assertEquals(observed, transport.connectCalls.get())
        repo.stop()
    }

    @Test
    fun revokedDeviceFailsClosedWithoutReconnectStorm() {
        val transport = ScriptedTransport(connectSucceeds = false)
        val repo = JarvisSessionRepository(transport, scope, backoffMs = listOf(3L, 3L, 3L, 3L, 3L))
        repo.start()
        settle(20) // allow the backoff loop to start
        // Server revokes the device; phase must latch to REVOKED and stay there.
        transport.pushFrame(
            """{"cursor":1,"eventId":"e1","version":"1.0",""" +
                """"event":{"type":"connection.state","payload":{"state":"DEVICE_REVOKED"}}}""",
        )
        settle(500) // any in-flight loop iterations must observe REVOKED and stop
        assertEquals(SessionPhase.REVOKED, repo.snapshot.value.phase)
        val revokedCalls = transport.connectCalls.get()

        // Foreground resume of a fail-closed device must NOT restart reconnects.
        repo.setForeground(false)
        repo.setForeground(true)
        settle(200)
        assertEquals("revoked device never auto-retries", revokedCalls, transport.connectCalls.get())
        repo.stop()
    }

    @Test
    fun backgroundWithNoWorkDefersReconnectAndForegroundResumes() {
        val transport = ScriptedTransport(connectSucceeds = false)
        val repo = JarvisSessionRepository(transport, scope, backoffMs = listOf(10L))
        repo.start()
        settle(60) // initial attempt + maybe one scheduled
        val backgrounded = transport.connectCalls.get()

        repo.setForeground(false)
        settle(120)
        // Backgrounded with nothing in flight: no new connection churn.
        assertEquals(backgrounded, transport.connectCalls.get())

        repo.setForeground(true)
        settle(120)
        // Returning to foreground resumes bounded reconnection.
        assertTrue(transport.connectCalls.get() > backgrounded)
        repo.stop()
    }

    @Test
    fun authExpiredWhileBackgroundedNeverRetriesOnForeground() {
        val transport = ScriptedTransport(connectSucceeds = true)
        val repo = JarvisSessionRepository(transport, scope, backoffMs = listOf(3L, 3L, 3L, 3L, 3L))
        repo.start()
        settle(80)
        transport.pushFrame(
            """{"cursor":1,"eventId":"e-exp","version":"1.0",""" +
                """"event":{"type":"connection.state","payload":{"state":"AUTH_EXPIRED"}}}""",
        )
        settle(80)
        assertEquals(SessionPhase.AUTH_EXPIRED, repo.snapshot.value.phase)
        val afterExpiry = transport.connectCalls.get()

        repo.setForeground(false)
        settle(80)
        repo.setForeground(true)
        settle(200)
        assertEquals(SessionPhase.AUTH_EXPIRED, repo.snapshot.value.phase)
        assertEquals("expired session never auto-retries", afterExpiry, transport.connectCalls.get())
        repo.stop()
    }

    @Test
    fun protocolMismatchFailsClosedWithoutReconnectStorm() {
        val transport = ScriptedTransport(connectSucceeds = true)
        val repo = JarvisSessionRepository(transport, scope, backoffMs = listOf(3L, 3L, 3L, 3L, 3L))
        repo.start()
        settle(80)
        transport.pushFrame(
            """{"cursor":1,"eventId":"e-mm","version":"2.0",""" +
                """"event":{"type":"connection.state","payload":{"state":"ONLINE"}}}""",
        )
        settle(80)
        assertEquals(SessionPhase.MISMATCH, repo.snapshot.value.phase)
        assertEquals(ConnectionState.PROTOCOL_MISMATCH, repo.snapshot.value.session.connection)
        val afterMismatch = transport.connectCalls.get()

        repo.setForeground(false)
        repo.setForeground(true)
        settle(200)
        assertEquals(SessionPhase.MISMATCH, repo.snapshot.value.phase)
        assertEquals("mismatch never auto-retries", afterMismatch, transport.connectCalls.get())
        repo.stop()
    }

    @Test
    fun foregroundFlipWhileBackoffIsRunningDoesNotOpenASecondConnect() {
        val transport = ScriptedTransport(connectSucceeds = false)
        val repo = JarvisSessionRepository(transport, scope, backoffMs = listOf(400L, 400L))
        repo.start()
        settle(80)
        val before = transport.connectCalls.get()

        repo.setForeground(false)
        repo.setForeground(true)
        settle(80)

        assertEquals(before, transport.connectCalls.get())
        assertTrue(repo.snapshot.value.reconnectAttempt < repo.snapshot.value.reconnectBudget)
        repo.stop()
    }
}
