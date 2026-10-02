package com.jarvis.android.transport.live

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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

class SceneBuilderApiTest {
    private lateinit var server: MockWebServer
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.getHeader("Authorization") != "Bearer test-token") {
                    return MockResponse().setResponseCode(401)
                }
                return when (request.path) {
                    "/api/app/writing-room/v2/visual/scenes/context/preview" ->
                        MockResponse().setBody(
                            """
                            {
                              "schema":"jarvis.visual.scene-context-preview.v1",
                              "context":{
                                "schema":"jarvis.visual.scene-context.v1",
                                "context_id":"svc_123",
                                "context_hash":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                                "project_id":"prj_story",
                                "chapter_id":"chapter_12",
                                "scene_id":"scene:arrival",
                                "aggregate_version":7,
                                "narrative_boundary":{
                                  "kind":"DRAFT",
                                  "revision_id":"revision_9",
                                  "sha256":"BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB",
                                  "brief_revision_id":"brief_3"
                                },
                                "source":{
                                  "revision":"source-7",
                                  "snapshot_id":"CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC"
                                },
                                "selection":{
                                  "character_ids":["character:alexander"],
                                  "location_id":"location:grayhaven",
                                  "era":"",
                                  "state":"",
                                  "outfit_ids":{},
                                  "time":"night",
                                  "weather":"rain",
                                  "composition":"wide",
                                  "instruction":"tense arrival",
                                  "reference_perspectives":[]
                                },
                                "reference_manifest":{
                                  "max_references":8,
                                  "references":[{
                                    "asset_id":"va_alex",
                                    "asset_sha256":"DDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDD",
                                    "role":"character:alexander",
                                    "authority":"CANON_LOCKED",
                                    "selection":"automatic",
                                    "kind":"PRIMARY_REFERENCE",
                                    "perspective":"front",
                                    "visual_revision":2,
                                    "pack_id":"vrp_1",
                                    "pack_revision":2,
                                    "applicability":{"status":"MATCH","reasons":[]}
                                  }],
                                  "textual_fallback_roles":[],
                                  "unresolved_roles":[],
                                  "selection_explanations":[{
                                    "role":"character:alexander",
                                    "asset_id":"va_alex",
                                    "reason":"active_approved_pack",
                                    "authority":"CANON_LOCKED"
                                  }],
                                  "approval_policy":"APPROVED_ONLY"
                                },
                                "exploratory":false,
                                "generation_ready":true
                              }
                            }
                            """.trimIndent(),
                        )
                    "/api/app/writing-room/v2/visual/scenes/context/get" ->
                        MockResponse().setBody(
                            """
                            {
                              "schema":"jarvis.visual.scene-context.v1",
                              "context":{
                                "schema":"jarvis.visual.scene-context.v1",
                                "context_id":"svc_123",
                                "context_hash":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                                "project_id":"prj_story",
                                "chapter_id":"chapter_12",
                                "scene_id":"scene:arrival",
                                "aggregate_version":7,
                                "narrative_boundary":{
                                  "kind":"DRAFT",
                                  "revision_id":"revision_9",
                                  "sha256":"BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB",
                                  "brief_revision_id":"brief_3"
                                },
                                "source":{"revision":"source-7","snapshot_id":"CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC"},
                                "selection":{"character_ids":["character:alexander"],"location_id":"location:grayhaven"},
                                "reference_manifest":{"max_references":8,"references":[],"textual_fallback_roles":[],"unresolved_roles":[],"selection_explanations":[],"approval_policy":"APPROVED_ONLY"},
                                "exploratory":false,
                                "generation_ready":true
                              }
                            }
                            """.trimIndent(),
                        )
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun session(): JarvisAppSession {
        val store = JarvisAppSession.MemoryStore()
        store.put(
            mapOf(
                JarvisAppSession.KEY_TOKEN to "test-token",
                JarvisAppSession.KEY_BASE to server.url("/").toString().trimEnd('/'),
                JarvisAppSession.KEY_USER_ID to "owner-1",
                JarvisAppSession.KEY_USERNAME to "owner",
                JarvisAppSession.KEY_ROLE to "owner",
                JarvisAppSession.KEY_EXPIRES to "2099-01-01T00:00:00Z",
            ),
        )
        return JarvisAppSession(store)
    }

    @Test
    fun previewSerializesNarrativeBoundarySubjectsAndExactReferenceManifest() = runBlocking {
        val result = session().visualSceneContextPreview(
            projectId = "prj_story",
            chapterId = "chapter_12",
            sceneId = "scene:arrival",
            expectedAggregateVersion = 7,
            draftRevisionId = "revision_9",
            characterIds = listOf("character:alexander"),
            locationId = "location:grayhaven",
            time = "night",
            weather = "rain",
            composition = "wide",
            instruction = "tense arrival",
        ).getOrThrow()

        assertTrue(result.context.generation_ready)
        assertFalse(result.context.exploratory)
        assertEquals("DRAFT", result.context.narrative_boundary.kind)
        assertEquals("CANON_LOCKED", result.context.reference_manifest.references.single().authority)
        assertEquals("D".repeat(64), result.context.reference_manifest.references.single().asset_sha256)

        val request = server.takeRequest()
        val body = json.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals("prj_story", body["project_id"]!!.jsonPrimitive.content)
        assertEquals("revision_9", body["draft_revision_id"]!!.jsonPrimitive.content)
        assertEquals(
            "character:alexander",
            body["character_ids"]!!.jsonArray.single().jsonPrimitive.content,
        )
        assertEquals("location:grayhaven", body["location_id"]!!.jsonPrimitive.content)
        assertEquals(8, body["max_references"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun frozenContextCanBeReopenedByExactId() = runBlocking {
        val result = session().visualSceneContextGet(
            projectId = "prj_story",
            contextId = "svc_123",
        ).getOrThrow()
        assertEquals("svc_123", result.context.context_id)
        assertEquals("source-7", result.context.source.revision)

        val request = server.takeRequest()
        val body = json.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals("svc_123", body["context_id"]!!.jsonPrimitive.content)
    }
}
