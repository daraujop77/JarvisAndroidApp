package com.jarvis.android.transport.live

import java.security.MessageDigest
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The real Kotlin SSE transport must decode the exact visual metadata projected
 * by the backend AdaptiveAgent. No image downloads, APK, or production requests.
 */
class AgentApprovedVisualProjectionContractTest {
    private lateinit var server: MockWebServer
    private lateinit var session: JarvisAppSession
    private val digest = "a".repeat(64)
    private val approvedId = "va_1234567890abcdef12345678"

    private fun agentJson() = """
      {"schema":"jarvis.writing-room.chat.v1",
       "classification":{"task_class":"COPILOT_AGENT","source":"adaptive_model_decisions"},
       "selected_participant":"copilot",
       "turn":{"schema":"jarvis.writing-room.turn.v1","project_id":"prj_story",
               "participant":{"id":"copilot","label":"Writing Room Copilot"},
               "routing":{"destination":"adaptive_agent"},
               "response":{"text":"Referencia aprobada localizada"}},
       "copilot_task":{
         "schema":"jarvis.writing-room.copilot.task.v1",
         "task_id":"ar_0123456789abcdef01234567",
         "project_id":"prj_story","conversation_id":"chat-a",
         "state":"DONE","agent_run_state":"SUCCEEDED","requires_confirmation":false,
         "results":[{"tool":"get_approved_visual_references","effect":"READ_ONLY",
                     "text":"Encontré una imagen aprobada",
                     "result":{"status":"READY","visual_inspection_performed":false},
                     "visual_assets":[
                       {"asset_id":"$approvedId","sha256":"$digest",
                        "status":"APPROVED","storage_state":"stored",
                        "character_ids":["character:alexander"],
                        "kind":"PRIMARY_REFERENCE","visual_revision":1}]}]},
       "visual_assets":[
         {"asset_id":"$approvedId","sha256":"$digest",
          "status":"APPROVED","storage_state":"stored",
          "character_ids":["character:alexander"],
          "kind":"PRIMARY_REFERENCE","visual_revision":1}]
      }
    """.trimIndent().replace("\n", "")

    @Before fun setup() {
        server = MockWebServer()
        server.start()
        val store = JarvisAppSession.MemoryStore()
        store.put(mapOf(
            JarvisAppSession.KEY_TOKEN to "test-token",
            JarvisAppSession.KEY_BASE to server.url("/").toString().trimEnd('/'),
            JarvisAppSession.KEY_USER_ID to "owner-a",
            JarvisAppSession.KEY_USERNAME to "author",
            JarvisAppSession.KEY_ROLE to "owner",
            JarvisAppSession.KEY_EXPIRES to "2099-01-01T00:00:00Z",
        ))
        session = JarvisAppSession(store)
    }

    @After fun cleanup() { server.shutdown() }

    @Test fun adaptiveSseProjectsApprovedMetadataToAndroidChatAndTask() =
        runBlocking(Dispatchers.IO) {
            server.enqueue(MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody("event: ready\ndata: {\"request_id\":\"req\"}\n\n" +
                    "event: complete\ndata: ${agentJson()}\n\n"))
            val chat = session.writingRoomAutoChatStream(
                projectId = "prj_story", projectTitle = "Alexander History",
                prompt = "Enséñame las imágenes aprobadas de Alexander",
                onDelta = {},
            ).getOrThrow()
            val visual = chat.visual_assets.single()
            assertEquals("jarvis.writing-room.chat.v1", chat.schema)
            assertEquals("get_approved_visual_references",
                chat.copilot_task!!.results.single().tool)
            assertEquals(approvedId, visual.asset_id)
            assertEquals(digest, visual.sha256)
            assertEquals("stored", visual.storage_state)
            assertEquals("APPROVED", visual.status)
            assertEquals(listOf("character:alexander"), visual.character_ids)
            assertFalse(chat.copilot_task!!.requires_confirmation)
            assertEquals(visual,
                chat.copilot_task!!.results.single().visual_assets.single())
            val req = server.takeRequest()
            assertEquals("/api/app/writing-room/agent/stream", req.path)
            assertTrue(req.getHeader("Authorization")!!.startsWith("Bearer "))
            assertEquals(1, server.requestCount)
        }


    @Test fun adaptiveApprovedReferenceRoundtripsThroughAuthenticatedFetchAndHashGate() =
        runBlocking(Dispatchers.IO) {
            val pngEncoded =
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/lv8AAAAASUVORK5CYII="
            val bytes = Base64.getDecoder().decode(pngEncoded)
            val sha = MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it) }
            server.enqueue(MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody("event: ready\\ndata: {\\\"request_id\\\":\\\"req\\\"}\\n\\n" +
                    "event: complete\\ndata: ${agentJson().replace(digest, sha)}\\n\\n"))
            server.enqueue(MockResponse().setBody(
                """{"schema":"jarvis.visual.asset.content.v1",
                   "asset_id":"$approvedId","sha256":"$sha",
                   "mime_type":"image/png","image_base64":"$pngEncoded"}""",
            ))

            val chat = session.writingRoomAutoChatStream(
                projectId = "prj_story", projectTitle = "Alexander History",
                prompt = "Muéstrame la imagen aprobada de Alexander",
                onDelta = {},
            ).getOrThrow()
            val approved = chat.visual_assets.single()
            assertEquals("APPROVED", approved.status)
            val response = session.writingRoomVisualAssetFetch(
                "prj_story", approved.asset_id,
            ).getOrThrow()
            assertTrue(isVerifiedApprovedVisualContent(approved, response))
            assertEquals(bytes.toList(), Base64.getDecoder().decode(
                response.image_base64,
            ).toList())
            val stream = server.takeRequest()
            val image = server.takeRequest()
            assertEquals("/api/app/writing-room/agent/stream", stream.path)
            assertEquals("/api/app/writing-room/visual-assets/fetch", image.path)
            assertEquals("Bearer test-token", image.getHeader("Authorization"))
            assertTrue(image.body.readUtf8().contains(approvedId))
            assertEquals(2, server.requestCount)
        }

    @Test fun metadataDoesNotClaimPixelsOrContainImageBytes() {
        val body = agentJson()
        assertFalse(body.contains("image_base64"))
        assertFalse(body.contains("image_url"))
        assertTrue(body.contains("\"visual_inspection_performed\":false"))
    }
}
