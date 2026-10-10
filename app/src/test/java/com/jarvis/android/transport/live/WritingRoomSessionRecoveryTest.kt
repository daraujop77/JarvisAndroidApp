package com.jarvis.android.transport.live

import com.jarvis.android.security.MemoryRefreshCredentialStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.Dispatcher as MockDispatcher
import okhttp3.mockwebserver.RecordedRequest
import java.util.concurrent.atomic.AtomicInteger
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
    @Test
    fun streaming401RefreshesOnceAndKeepsRequestIdAndPairing() = runBlocking(Dispatchers.IO) {
        val base = server.url("/").toString().trimEnd('/')
        val refreshStore = MemoryRefreshCredentialStore()
        val session = JarvisAppSession(JarvisAppSession.MemoryStore(), refreshStore = refreshStore)
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            """{"authenticated":true,"token":"access-old","expires_utc":"2099-01-01T00:00:00Z","refresh_token":"pair-old","refresh_expires_utc":"2099-02-01T00:00:00Z","user":{"id":"owner","username":"owner","role":"owner"}}"""
        ))
        server.enqueue(MockResponse().setResponseCode(401).setBody("{}"))
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            """{"authenticated":true,"token":"access-new","expires_utc":"2099-01-02T00:00:00Z","refresh_token":"pair-new","refresh_expires_utc":"2099-02-02T00:00:00Z","user":{"id":"owner","username":"owner","role":"owner"}}"""
        ))
        server.enqueue(MockResponse().setResponseCode(200)
            .setHeader("Content-Type", "text/event-stream")
            .setBody(
                """
                event: delta
                data: {"delta":"hola"}

                event: complete
                data: {"schema":"jarvis.writing-room.autochat.v1"}

                """.trimIndent() + "\n\n"
            ))
        assertTrue(session.login(base, "owner", "secret").isSuccess)
        val streamed = StringBuilder()
        val response = session.writingRoomAutoChatStream(
            "prj_story", "Story", "Hola", onDelta = { streamed.append(it) },
        )
        assertTrue(response.toString(), response.isSuccess)
        assertEquals("hola", streamed.toString())
        assertEquals("access-new", session.token)
        assertEquals("pair-new", refreshStore.load()?.token)
        val login = server.takeRequest()
        val first = server.takeRequest()
        val renew = server.takeRequest()
        val second = server.takeRequest()
        assertEquals("/api/app/login", login.path)
        assertEquals("/api/app/writing-room/chat/stream", first.path)
        assertEquals("/api/app/refresh", renew.path)
        assertEquals("/api/app/writing-room/chat/stream", second.path)
        assertEquals("Bearer access-old", first.getHeader("Authorization"))
        assertEquals("Bearer access-new", second.getHeader("Authorization"))
        assertEquals(first.body.readUtf8(), second.body.readUtf8())
        assertTrue(session.hasRefreshCredential)
    }

    @Test
    fun concurrentExpiredWorkspaceRequestsRotateDeviceCredentialOnlyOnce() = runBlocking {
        val base = server.url("/").toString().trimEnd('/')
        val counter = AtomicInteger(0)
        server.dispatcher = object : MockDispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/api/app/refresh" -> {
                    counter.incrementAndGet()
                    MockResponse().setResponseCode(200).setBody(
                        """{"authenticated":true,"token":"recovered","expires_utc":"2099-01-01T00:00:00Z","refresh_token":"pair-new","refresh_expires_utc":"2099-02-01T00:00:00Z","user":{"id":"owner","username":"owner","role":"owner"}}"""
                    )
                }
                "/api/app/writing-room/overview" -> {
                    if (request.getHeader("Authorization") == "Bearer recovered") {
                        MockResponse().setResponseCode(200).setBody(
                            """{"schema":"jarvis.writing-room.overview.v1","project":{"project_id":"prj_story"}}"""
                        )
                    } else MockResponse().setResponseCode(401)
                }
                else -> MockResponse().setResponseCode(404)
            }
        }
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
        val session = JarvisAppSession(store, refreshStore = refreshStore)
        val responses = coroutineScope {
            (1..8).map {
                async(Dispatchers.IO) { session.writingRoomOverview("prj_story") }
            }.awaitAll()
        }
        assertTrue(responses.toString(), responses.all { it.isSuccess })
        assertEquals(1, counter.get())
        assertEquals("recovered", session.token)
        assertEquals("pair-new", refreshStore.load()?.token)
    }


    @Test
    fun concurrentMissingBearerRequestsShareOneRefresh() = runBlocking {
        val base = server.url("/").toString().trimEnd('/')
        val count = AtomicInteger(0)
        server.dispatcher = object : MockDispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/api/app/refresh" -> {
                    count.incrementAndGet()
                    MockResponse().setResponseCode(200).setBody(
                        """{"authenticated":true,"token":"fresh","expires_utc":"2099-01-01T00:00:00Z","refresh_token":"pair-next","refresh_expires_utc":"2099-02-01T00:00:00Z","user":{"id":"u1","username":"owner","role":"owner"}}"""
                    )
                }
                "/api/app/writing-room/overview" ->
                    MockResponse().setResponseCode(
                        if (request.getHeader("Authorization") == "Bearer fresh") 200 else 401
                    ).setBody(
                        """{"schema":"jarvis.writing-room.overview.v1","project":{"project_id":"prj_story"}}"""
                    )
                else -> MockResponse().setResponseCode(404)
            }
        }
        val store = JarvisAppSession.MemoryStore().apply {
            put(mapOf(JarvisAppSession.KEY_BASE to base))
        }
        val refreshStore = MemoryRefreshCredentialStore().apply {
            save("pair-old", "2099-02-01T00:00:00Z")
        }
        val session = JarvisAppSession(store, refreshStore = refreshStore)
        val results = coroutineScope {
            (1..8).map {
                async(Dispatchers.IO) { session.writingRoomOverview("prj_story") }
            }.awaitAll()
        }
        assertTrue(results.toString(), results.all { it.isSuccess })
        assertEquals(1, count.get())
        assertEquals("fresh", session.token)
        assertEquals("pair-next", refreshStore.load()?.token)
    }
}
