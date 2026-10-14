package com.jarvis.android.transport.live

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Android compatibility with the verified VPS Kimi HTTP/SSE Wiki contract.
 * Synthetic Wiki, fake auth and mocked HTTP; no deployment, live provider or APK.
 */
class AgentKimiWikiSseContractTest {
    private lateinit var server: MockWebServer
    private lateinit var session: JarvisAppSession

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        val store = JarvisAppSession.MemoryStore()
        store.put(mapOf(
            JarvisAppSession.KEY_TOKEN to "test-token",
            JarvisAppSession.KEY_BASE to server.url("/").toString().trimEnd('/'),
            JarvisAppSession.KEY_USER_ID to "owner-synthetic",
            JarvisAppSession.KEY_USERNAME to "tester",
            JarvisAppSession.KEY_ROLE to "owner",
            JarvisAppSession.KEY_EXPIRES to "2099-01-01T00:00:00Z",
        ))
        session = JarvisAppSession(store)
    }

    @After fun tearDown() { server.shutdown() }

    private fun sse(vararg frames: Pair<String, String>): MockResponse =
        MockResponse().setHeader("Content-Type", "text/event-stream")
            .setBody(frames.joinToString(separator = "") { (event, json) ->
                "event: " + event + "\ndata: " +
                    json.trimIndent().replace("\n", "") + "\n\n"
            })

    @Test fun verifiedWikiResponseStreamsOnlyFinalTextAndCreatesDoneCard() =
        runBlocking(Dispatchers.IO) {
            val conversation = session.writingRoomConversationId("prj_story")
            val completion = """
              {"schema":"jarvis.writing-room.chat.v1",
               "classification":{"task_class":"COPILOT_AGENT","source":"adaptive_model_decisions"},
               "selected_participant":"copilot",
               "turn":{"schema":"jarvis.writing-room.turn.v1","project_id":"prj_story",
                 "participant":{"id":"copilot","label":"Writing Room Copilot"},
                 "routing":{"destination":"adaptive_agent"},
                 "response":{"text":"Alexander aparece en el Wiki de prueba."}},
               "copilot_task":{"schema":"jarvis.writing-room.copilot.task.v1",
                 "task_id":"ar_0123456789abcdef01234567",
                 "project_id":"prj_story","conversation_id":"$conversation",
                 "request_mode":"auto","state":"DONE","agent_run_state":"SUCCEEDED",
                 "requires_confirmation":false,
                 "steps":[{"tool":"search_story_wiki","effect":"READ_ONLY",
                           "access":"read","arguments":{}}],
                 "results":[{"tool":"search_story_wiki","effect":"READ_ONLY",
                   "status":"SUCCEEDED","text":"Referencia encontrada",
                   "result":{"results":[{"id":"character:alexander","name":"Alexander"}]},
                   "visual_assets":[]}],"error_code":""},
               "visual_assets":[]}
            """
            server.enqueue(sse(
                "ready" to """{"request_id":"synthetic","conversation_id":"$conversation"}""",
                "progress" to """{"phase":"thinking","round":0,"calls":0,"tool":""}""",
                "progress" to """{"phase":"using_tool","round":1,"calls":0,"tool":"search_story_wiki"}""",
                "progress" to """{"phase":"thinking","round":1,"calls":1,"tool":""}""",
                "heartbeat" to """{"alive":true}""",
                "delta" to """{"delta":"Alexander aparece "}""",
                "delta" to """{"delta":"en el Wiki de prueba."}""",
                "metrics" to """{"first_model_chunk_ms":974,"first_delta_ms":3000,
                  "total_ms":3676,"model_round_ms":[1600,2067],
                  "model_total_ms":3667,"streamed_preview":false}""",
                "complete" to completion,
            ))
            val deltas = mutableListOf<String>()
            val phases = mutableListOf<String>()
            val result = session.writingRoomAutoChatStream(
                "prj_story", "Historia sintética", "¿Quién es Alexander en el Wiki?",
                onProgress = { phases.add(it) }, onDelta = { deltas.add(it) },
            ).getOrThrow()

            assertEquals("Alexander aparece en el Wiki de prueba.",
                         deltas.joinToString(""))
            assertEquals("Alexander aparece en el Wiki de prueba.",
                         result.turn.response.text)
            assertEquals(listOf("thinking", "using_tool", "thinking"), phases)
            assertEquals("DONE", result.copilot_task?.state)
            assertEquals("SUCCEEDED", result.copilot_task?.agent_run_state)
            assertFalse(result.copilot_task?.requires_confirmation ?: true)
            assertEquals("search_story_wiki", result.copilot_task?.results?.single()?.tool)
            assertTrue(result.copilot_task?.results?.single()?.result
                           ?.get("results")?.toString()?.contains("character:alexander") == true)
            assertTrue(result.visual_assets.isEmpty())
            assertEquals(1, server.requestCount)
            val sent = server.takeRequest()
            assertEquals("/api/app/writing-room/agent/stream", sent.path)
            val body = Json.parseToJsonElement(sent.body.readUtf8()).jsonObject
            assertEquals(conversation, body["conversation_id"]?.jsonPrimitive?.content)
            assertTrue(body["request_id"]?.jsonPrimitive?.content?.isNotBlank() == true)
            assertEquals("prj_story", body["project_id"]?.jsonPrimitive?.content)
        }

    @Test fun blockedWikiCitationCannotBecomeDoneOrTriggerAnotherInference() =
        runBlocking(Dispatchers.IO) {
            val conversation = session.writingRoomConversationId("prj_story")
            val completion = """
              {"schema":"jarvis.writing-room.chat.v1",
               "classification":{"task_class":"COPILOT_AGENT"},
               "selected_participant":"copilot",
               "turn":{"project_id":"prj_story",
                 "response":{"text":"JARVIS terminó con estado BLOCKED: unverified_evidence_ref"}},
               "copilot_task":{"schema":"jarvis.writing-room.copilot.task.v1",
                 "task_id":"ar_0123456789abcdef01234567",
                 "project_id":"prj_story","conversation_id":"$conversation",
                 "state":"BLOCKED","agent_run_state":"BLOCKED",
                 "requires_confirmation":false,"error_code":"unverified_evidence_ref",
                 "steps":[{"tool":"search_story_wiki","arguments":{},"effect":"READ_ONLY"}],
                 "results":[{"tool":"search_story_wiki","text":"Información de prueba",
                   "effect":"READ_ONLY","status":"SUCCEEDED",
                   "result":{"results":[{"id":"character:alexander"}]}}]},
               "visual_assets":[]}
            """
            server.enqueue(sse(
                "ready" to """{"request_id":"synthetic","conversation_id":"$conversation"}""",
                "progress" to """{"phase":"using_tool","round":1,"calls":0}""",
                "delta" to """{"delta":"JARVIS terminó con estado BLOCKED: unverified_evidence_ref"}""",
                "metrics" to """{"model_round_ms":[1400,2000],"model_total_ms":3400}""",
                "complete" to completion,
            ))
            val deltas = mutableListOf<String>()
            val result = session.writingRoomAutoChatStream(
                "prj_story", "Historia sintética", "Consulta Wiki",
                onDelta = { deltas.add(it) },
            ).getOrThrow()
            assertEquals("BLOCKED", result.copilot_task?.state)
            assertEquals("BLOCKED", result.copilot_task?.agent_run_state)
            assertEquals("unverified_evidence_ref", result.copilot_task?.error_code)
            assertFalse(result.copilot_task?.requires_confirmation ?: true)
            assertTrue(result.turn.response.text.contains("BLOCKED"))
            assertEquals(result.turn.response.text, deltas.joinToString(""))
            assertEquals(1, server.requestCount)
            assertEquals("/api/app/writing-room/agent/stream", server.takeRequest().path)
        }
}
