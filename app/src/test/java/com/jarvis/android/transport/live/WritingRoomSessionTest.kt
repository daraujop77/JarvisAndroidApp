package com.jarvis.android.transport.live

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
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
import java.util.concurrent.atomic.AtomicReference

class WritingRoomSessionTest {

    private lateinit var server: MockWebServer
    private val postedBody = AtomicReference<String>("")

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.path != "/api/app/writing-room/council") {
                    return MockResponse().setResponseCode(404)
                }
                if (request.getHeader("Authorization") != "Bearer test-token") {
                    return MockResponse().setResponseCode(401)
                }
                postedBody.set(request.body.readUtf8())
                return MockResponse().setBody(
                    """
                    {
                      "schema":"jarvis.writing-room.turn.v1",
                      "project_id":"prj_story",
                      "project_title":"Alexander History",
                      "participant":{"id":"canon_keeper","label":"Canon Keeper"},
                      "routing":{
                        "requested_mode":"normal",
                        "resolved_profile":"normal",
                        "destination":"cloud",
                        "provider":"freellmapi",
                        "model":"test/model",
                        "worker_id":null
                      },
                      "canon":{
                        "connected":true,
                        "authority":"human_only",
                        "story_id":"STORY-001",
                        "documents":23,
                        "chunks":276,
                        "sources":[{
                          "chunk_id":"c1",
                          "title":"Capítulo 37",
                          "heading":"La contingencia",
                          "canon_status":"OFFICIAL_CANON",
                          "authority":"canon_chapter_source",
                          "drive_url":"https://docs.google.com/document/d/example/edit"
                        }]
                      },
                      "response":{"text":"Grounded answer"},
                      "metrics":{"total_ms":321,"rag_source_count":1}
                    }
                    """.trimIndent(),
                )
            }
        }
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun seededSession(): JarvisAppSession {
        val store = JarvisAppSession.MemoryStore()
        store.put(
            mapOf(
                JarvisAppSession.KEY_TOKEN to "test-token",
                JarvisAppSession.KEY_BASE to server.url("/").toString().trimEnd('/'),
                JarvisAppSession.KEY_USER_ID to "u1",
                JarvisAppSession.KEY_USERNAME to "alex",
                JarvisAppSession.KEY_ROLE to "owner",
                JarvisAppSession.KEY_EXPIRES to "2099-01-01T00:00:00Z",
            ),
        )
        return JarvisAppSession(store)
    }

    @Test
    fun councilTurnUsesAuthenticatedSessionAndParsesGrounding() = runBlocking(Dispatchers.IO) {
        val session = seededSession()
        val result = session.writingRoomTurn(
            projectId = "prj_story",
            projectTitle = "Alexander History",
            participant = "canon_keeper",
            prompt = "What is established about the contingency?",
        )

        assertTrue(result.isSuccess)
        val turn = result.getOrThrow()
        assertEquals("Canon Keeper", turn.participant.label)
        assertEquals("Grounded answer", turn.response.text)
        assertTrue(turn.canon.connected)
        assertEquals(23, turn.canon.documents)
        assertEquals(276, turn.canon.chunks)
        assertEquals("OFFICIAL_CANON", turn.canon.sources.single().canon_status)
        assertEquals("freellmapi", turn.routing.provider)
        assertEquals("test/model", turn.routing.model)

        val body = postedBody.get()
        assertTrue(body.contains("\"project_id\":\"prj_story\""))
        assertTrue(body.contains("\"participant\":\"canon_keeper\""))
        assertTrue(body.contains("contingency"))
    }

    @Test
    fun councilTurnFailsClosedWithoutAuthenticatedSession() = runBlocking(Dispatchers.IO) {
        val session = JarvisAppSession(JarvisAppSession.MemoryStore())
        val result = session.writingRoomTurn(
            projectId = "prj_story",
            projectTitle = "Alexander History",
            participant = "showrunner",
            prompt = "Discuss this scene.",
        )

        assertTrue(result.isFailure)
        assertFalse(session.isAuthenticated)
        assertEquals(0, server.requestCount)
    }
}
