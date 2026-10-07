package com.jarvis.android.transport.live

import com.jarvis.android.security.MemoryRefreshCredentialStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class JarvisAppSessionRefreshTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun loginStoresDeviceCredentialAndRefreshRotatesIt() = runBlocking(Dispatchers.IO) {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"schema":"jarvis.app.auth.v1","authenticated":true,"token":"access-1","expires_utc":"2099-01-01T00:00:00Z","refresh_token":"refresh-1","refresh_expires_utc":"2099-02-01T00:00:00Z","device_id":"android-server","user":{"id":"u1","username":"owner","role":"owner"}}"""
            )
        )
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"schema":"jarvis.app.refresh.v1","authenticated":true,"token":"access-2","expires_utc":"2099-01-02T00:00:00Z","refresh_token":"refresh-2","refresh_expires_utc":"2099-02-02T00:00:00Z","user":{"id":"u1","username":"owner","role":"owner"}}"""
            )
        )

        val store = JarvisAppSession.MemoryStore()
        val refreshStore = MemoryRefreshCredentialStore()
        val session = JarvisAppSession(store, refreshStore = refreshStore)
        val base = server.url("/").toString().trimEnd('/')

        assertTrue(session.login(base, "owner", "secret-password").isSuccess)
        val loginRequest = server.takeRequest()
        assertEquals("/api/app/login", loginRequest.path)
        assertTrue(loginRequest.body.readUtf8().contains("\"device_id\":\"${session.deviceId}\""))
        assertTrue(session.hasRefreshCredential)

        session.clearAccess()
        assertFalse(session.isAuthenticated)
        assertTrue(session.hasRefreshCredential)

        assertTrue(session.refresh().isSuccess)
        val refreshRequest = server.takeRequest()
        assertEquals("/api/app/refresh", refreshRequest.path)
        val refreshBody = refreshRequest.body.readUtf8()
        assertTrue(refreshBody.contains("\"refresh_token\":\"refresh-1\""))
        assertTrue(refreshBody.contains("\"device_id\":\"${session.deviceId}\""))
        assertEquals("access-2", session.token)
        assertEquals("refresh-2", refreshStore.load()?.token)
        assertNotEquals("refresh-1", refreshStore.load()?.token)
    }

    @Test
    fun biometricRefreshAdoptsValidSettingsGatewayWhenSessionBaseIsEmpty() =
        runBlocking(Dispatchers.IO) {
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """{"schema":"jarvis.app.refresh.v1","authenticated":true,"token":"access-new","expires_utc":"2099-01-02T00:00:00Z","refresh_token":"refresh-new","refresh_expires_utc":"2099-02-02T00:00:00Z","user":{"id":"u1","username":"owner","role":"owner"}}"""
                )
            )
            val store = JarvisAppSession.MemoryStore()
            val refreshStore = MemoryRefreshCredentialStore().apply {
                save("refresh-old", "2099-02-01T00:00:00Z")
            }
            val session = JarvisAppSession(store, refreshStore = refreshStore)
            val validGateway = server.url("/").toString().trimEnd('/')

            assertEquals("", session.baseUrl)
            val result = session.refresh(
                listOf(
                    "https://vps-8817149e.tail6eec63.ts.net:8443",
                    validGateway,
                ),
            )

            assertTrue(result.isSuccess)
            assertEquals(validGateway, session.baseUrl)
            assertEquals("/api/app/refresh", server.takeRequest().path)
            assertEquals("access-new", session.token)
            assertEquals("refresh-new", refreshStore.load()?.token)
        }

    @Test
    fun biometricRefreshWithoutAnyValidGatewayReturnsMigrationError() =
        runBlocking(Dispatchers.IO) {
            val store = JarvisAppSession.MemoryStore()
            val refreshStore = MemoryRefreshCredentialStore().apply {
                save("refresh-old", "2099-02-01T00:00:00Z")
            }
            val session = JarvisAppSession(store, refreshStore = refreshStore)

            val result = session.refresh(
                listOf("https://vps-8817149e.tail6eec63.ts.net:8443"),
            )

            assertTrue(result.isFailure)
            val message = result.exceptionOrNull()?.message.orEmpty()
            assertTrue(message.contains("Gateway route unavailable"))
            assertFalse(message.contains("invalid gateway URL"))
            assertTrue(session.hasRefreshCredential)
        }

    @Test
    fun retiredTailscaleBaseIsClearedAndCurrentGatewayCanBeAdopted() {
        val store = JarvisAppSession.MemoryStore().apply {
            put(
                mapOf(
                    JarvisAppSession.KEY_BASE to
                        "https://vps-8817149e.tail6eec63.ts.net:8443",
                ),
            )
        }
        val session = JarvisAppSession(store)

        assertEquals("", session.baseUrl)
        assertEquals(null, store.snapshot()[JarvisAppSession.KEY_BASE])

        val adopted = session.updateBaseUrl("https://jarvis.example.com/")
        assertTrue(adopted.isSuccess)
        assertEquals("https://jarvis.example.com", session.baseUrl)
        assertEquals(
            "https://jarvis.example.com",
            store.snapshot()[JarvisAppSession.KEY_BASE],
        )
    }

    @Test
    fun localAccessExpiryPreservesRefreshCredential() = runBlocking(Dispatchers.IO) {
        val store = JarvisAppSession.MemoryStore()
        val refreshStore = MemoryRefreshCredentialStore().apply {
            save("refresh-still-valid", "2099-02-01T00:00:00Z")
        }
        store.put(
            mapOf(
                JarvisAppSession.KEY_TOKEN to "expired-access",
                JarvisAppSession.KEY_BASE to "http://127.0.0.1:8788",
                JarvisAppSession.KEY_EXPIRES to "2000-01-01T00:00:00Z",
            )
        )
        val session = JarvisAppSession(store, refreshStore = refreshStore)

        val restored = session.restore()

        assertTrue(restored.isFailure)
        assertEquals(null, session.token)
        assertTrue(session.hasRefreshCredential)
        assertEquals("http://127.0.0.1:8788", session.baseUrl)
    }

    @Test
    fun rejectedRefreshClearsDeviceCredentialAndFailsClosed() = runBlocking(Dispatchers.IO) {
        server.enqueue(
            MockResponse().setResponseCode(401).setBody(
                """{"schema":"jarvis.web.error.v1","error":"refresh_token_revoked","message":"Device credential revoked."}"""
            )
        )
        val store = JarvisAppSession.MemoryStore()
        val refreshStore = MemoryRefreshCredentialStore().apply {
            save("revoked-refresh", "2099-02-01T00:00:00Z")
        }
        store.put(
            mapOf(
                JarvisAppSession.KEY_BASE to server.url("/").toString().trimEnd('/'),
                JarvisAppSession.KEY_DEVICE to "android_test",
            )
        )
        val session = JarvisAppSession(store, refreshStore = refreshStore)

        val result = session.refresh()

        assertTrue(result.isFailure)
        assertFalse(session.hasRefreshCredential)
        assertEquals(null, session.token)
    }
}
