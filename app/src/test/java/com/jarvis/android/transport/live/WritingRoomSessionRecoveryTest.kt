package com.jarvis.android.transport.live

import com.jarvis.android.security.MemoryRefreshCredentialStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Real HTTP request sequence: 401 -> device refresh -> same request once. */
class WritingRoomSessionRecoveryTest {
    private lateinit var server: MockWebServer

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun teardown() {
        server.shutdown()
    }

    @Test
    fun writingRoom401RefreshesAndRetriesWithoutUnpairing() = runBlocking(Dispatchers.IO) {
        val base = server.url("/").toString().trimEnd('/')
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            """{"authenticated":true,"token":"access-old","expires_utc":"2099-01-01T00:00:00Z","refresh_token":"pair-old","refresh_expires_utc":"2099-02-01T00:00:00Z","user":{"id":"owner","username":"owner","role":"owner"}}"""
        ))
        server.enqueue(MockResponse().setResponseCode(401).setBody("{}"))
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            """{"authenticated":true,"token":"access-new","expires_utc":"2099-01-02T00:00:00Z","refresh_token":"pair-new","refresh_expires_utc":"2099-02-02T00:00:00Z","user":{"id":"owner","username":"owner","role":"owner"}}"""
        ))
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            """{"schema":"jarvis.writing-room.overview.v1","project":{"project_id":"prj_story","title":"Story"}}"""
        ))
        val refreshStore = MemoryRefreshCredentialStore()
        val session = JarvisAppSession(JarvisAppSession.MemoryStore(), refreshStore = refreshStore)
        assertTrue(session.login(base, "owner", "secret").isSuccess)
        val result = session.writingRoomOverview("prj_story")
        assertTrue(result.isSuccess)
        assertEquals("prj_story", result.getOrThrow().project.project_id)
        assertTrue(session.hasRefreshCredential)
        assertEquals("pair-new", refreshStore.load()?.token)
        assertEquals("access-new", session.token)
        val login = server.takeRequest()
        val first = server.takeRequest()
        val refresh = server.takeRequest()
        val retried = server.takeRequest()
        assertEquals("/api/app/login", login.path)
        assertEquals("/api/app/writing-room/overview", first.path)
        assertEquals("Bearer access-old", first.getHeader("Authorization"))
        assertEquals("/api/app/refresh", refresh.path)
        assertEquals("/api/app/writing-room/overview", retried.path)
        assertEquals("Bearer access-new", retried.getHeader("Authorization"))
    }

    @Test
    fun locallyExpiredBearerUsesDeviceRefreshBeforeRequest() = runBlocking(Dispatchers.IO) {
        val base = server.url("/").toString().trimEnd('/')
        val store = JarvisAppSession.MemoryStore().apply {
            put(mapOf(
                JarvisAppSession.KEY_BASE to base,
                JarvisAppSession.KEY_TOKEN to "expired",
                JarvisAppSession.KEY_EXPIRES to "2000-01-01T00:00:00Z",
                JarvisAppSession.KEY_DEVICE to "android_test",
            ))
        }
        val refreshStore = MemoryRefreshCredentialStore().apply {
            save("pair-old", "2099-01-01T00:00:00Z")
        }
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            """{"authenticated":true,"token":"recovered","expires_utc":"2099-01-01T00:00:00Z","refresh_token":"pair-new","refresh_expires_utc":"2099-02-01T00:00:00Z","user":{"id":"owner","username":"owner","role":"owner"}}"""
        ))
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            """{"schema":"jarvis.writing-room.overview.v1","project":{"project_id":"prj_story"}}"""
        ))
        val session = JarvisAppSession(store, refreshStore = refreshStore)
        assertTrue(session.writingRoomOverview("prj_story").isSuccess)
        assertEquals("/api/app/refresh", server.takeRequest().path)
        assertEquals("/api/app/writing-room/overview", server.takeRequest().path)
        assertEquals("recovered", session.token)
        assertTrue(session.hasRefreshCredential)
    }
}
