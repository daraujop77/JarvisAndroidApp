package com.jarvis.android.transport.live

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Real client HTTP serialization using a synthetic authenticated server. */
class CopilotConversationHistoryApiTest {
    private lateinit var server: MockWebServer
    private var wrongProject = false
    private val json = Json { ignoreUnknownKeys = true }

    @Before fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.getHeader("Authorization") != "Bearer test-token")
                    return MockResponse().setResponseCode(401)
                if (request.path != "/api/app/writing-room/agent/history")
                    return MockResponse().setResponseCode(404)
                val body = json.parseToJsonElement(
                    request.body.clone().readUtf8(),
                ).jsonObject
                val project = body["project_id"]!!.jsonPrimitive.content
                val conversation = body["conversation_id"]!!.jsonPrimitive.content
                return MockResponse().setBody(
                    """{"schema":"jarvis.writing-room.agent.history.v1",
                    "project_id":"${if (wrongProject) "another-project" else project}",
                    "conversation_id":"$conversation",
                    "turns":[
                    {"request_id":"r1","task_id":"ar_01","user_text":"Hola",
                    "assistant_text":"Hola desde Copilot","state":"SUCCEEDED",
                    "visual_asset":{"asset_id":"va_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                     "sha256":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                     "status":"CANDIDATE","storage_state":"stored",
                     "kind":"PRIMARY_REFERENCE","character_id":"character:alexander",
                     "perspective":"front"}},
                    {"request_id":"r2","task_id":"ar_02","user_text":"Sigue",
                    "assistant_text":"Continuación","state":"SUCCEEDED"}]}""",
                )
            }
        }
        server.start()
    }

    @After fun tearDown() { server.shutdown() }

    private fun session(store: JarvisAppSession.MemoryStore): JarvisAppSession {
        store.put(mapOf(
            JarvisAppSession.KEY_TOKEN to "test-token",
            JarvisAppSession.KEY_BASE to server.url("/").toString().trimEnd('/'),
            JarvisAppSession.KEY_USER_ID to "owner-1",
            JarvisAppSession.KEY_USERNAME to "owner",
            JarvisAppSession.KEY_ROLE to "owner",
            JarvisAppSession.KEY_EXPIRES to "2099-01-01T00:00:00Z",
        ))
        return JarvisAppSession(store)
    }

    @Test fun restartRequestsSamePrincipalProjectConversationOnlyRead() = runBlocking {
        val durablePrefs = JarvisAppSession.MemoryStore()
        val first = session(durablePrefs)
        val originalId = first.writingRoomConversationId("story")
        val history = first.writingRoomCopilotHistory("story").getOrThrow()
        assertEquals(listOf("Hola", "Sigue"), history.turns.map { it.user_text })
        assertEquals("r1", history.turns.first().request_id)
        assertEquals("va_" + "a".repeat(32),
            history.turns.first().visual_asset?.asset_id)
        assertEquals("b".repeat(64), history.turns.first().visual_asset?.sha256)
        assertEquals(null, history.turns.last().visual_asset)
        val reopened = session(durablePrefs)
        assertEquals(originalId, reopened.writingRoomConversationId("story"))
        val afterRestart = reopened.writingRoomCopilotHistory("story").getOrThrow()
        assertEquals(history, afterRestart)
        val requests = (0 until server.requestCount).map { server.takeRequest() }
        assertEquals(2, requests.size)
        assertTrue(requests.all {
            it.method == "POST" && it.path == "/api/app/writing-room/agent/history"
        })
    }

    @Test fun mismatchedProjectResponseNeverEntersLocalTranscript() = runBlocking {
        wrongProject = true
        val result = session(JarvisAppSession.MemoryStore())
            .writingRoomCopilotHistory("story")
        assertTrue(result.isFailure)
    }
}
