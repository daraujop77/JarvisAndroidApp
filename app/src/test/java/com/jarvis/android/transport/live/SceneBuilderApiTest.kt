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
import java.util.concurrent.ConcurrentHashMap

class SceneBuilderApiTest {
    private lateinit var server: MockWebServer
    private val json = Json { ignoreUnknownKeys = true }
    @Volatile private var sceneSchema = "jarvis.visual.scene-generation.v2"
    @Volatile private var loseNextSceneGenerationResponse = false
    // A real 1x1 PNG, so the test exercises SHA and magic-byte verification.
    private val fixturePng = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGNgZGT8DwABDQED5NTSmwAAAABJRU5ErkJggg=="
    private val fixtureSha256 = "2f41b02bc94868f7cdd1e503c7f592ee7673dbb155eac2f267a3d1abf64aea18"
    private val paidReceipts = ConcurrentHashMap<String, String>()

    /** Skip the intentional, read-only paid receipt probe before a visual POST. */
    private fun takeActionRequest(): RecordedRequest {
        while (true) {
            val request = server.takeRequest()
            if (request.path?.startsWith("/api/app/images/requests/") != true) {
                return request
            }
        }
    }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.getHeader("Authorization") != "Bearer test-token") {
                    return MockResponse().setResponseCode(401)
                }
                if (request.path?.startsWith("/api/app/images/requests/") == true) {
                    val receipt = paidReceipts[request.path.orEmpty().substringAfterLast('/')]
                    return if (receipt != null) {
                        MockResponse().setBody(receipt)
                    } else {
                        MockResponse().setResponseCode(404)
                            .setBody("""{"error":"image_request_not_found"}""")
                    }
                }
                if (request.path == "/api/app/writing-room/v2/visual/scenes/generate" &&
                    loseNextSceneGenerationResponse
                ) {
                    loseNextSceneGenerationResponse = false
                    return MockResponse().setResponseCode(503)
                        .setBody("""{"error":"image_result_unknown"}""")
                }
                return when (request.path) {
                    "/api/app/writing-room/v2/visual/scenes/capabilities" ->
                        MockResponse().setBody(
                            """
                            {
                              "schema":"jarvis.visual.scene-generation-capabilities.v1",
                              "default_engine":"cloud",
                              "no_silent_cross_engine_fallback":true,
                              "engines":{
                                "cloud":{
                                  "state":"ready",
                                  "reference_policy":"exact_frozen_approved",
                                  "max_reference_count":8,
                                  "modes":["speed","quality","model_select"],
                                  "models":["grok-imagine-image-2.0"]
                                },
                                "local":{
                                  "state":"unavailable",
                                  "reason":"reference_materialization_unavailable",
                                  "pc_configured":true,
                                  "reference_policy":"exact_frozen_approved",
                                  "exact_reference_count":2,
                                  "profile":"visual_canon_flux_klein_4b_v1",
                                  "model":"FLUX.2 Klein Base 4B FP8",
                                  "reference_materialization":"not_configured",
                                  "fallback_allowed":false
                                }
                              }
                            }
                            """.trimIndent(),
                        )
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
                                    "asset_sha256":"DDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDD",
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
                    "/api/app/writing-room/v2/visual/scenes/director/resolve" ->
                        MockResponse().setBody(
                            """
                            {
                              "schema":"jarvis.visual.scene-director.v1",
                              "project_id":"prj_story",
                              "request_text":"Genera la batalla de Doom contra el Guardián y Soren",
                              "status":"READY",
                              "resolver_model":"gpt-6-sol",
                              "recommended_engine":"cloud",
                              "generation_max_corrections":2,
                              "human_selection_required":false,
                              "missing_story_fact":false,
                              "selected_evidence_id":"draft:chapter_38:revision_9:0",
                              "candidates":[{
                                "evidence_id":"draft:chapter_38:revision_9:0",
                                "kind":"DRAFT",
                                "title":"Capítulo 38",
                                "heading":"current draft",
                                "chapter_id":"chapter_38",
                                "chapter_number":38,
                                "canon_status":"DRAFT",
                                "authority":"WRITING_ROOM_DRAFT",
                                "score":47,
                                "excerpt":"Doom enfrenta al Guardián y Soren en Grayhaven."
                              }],
                              "context":{
                                "schema":"jarvis.visual.scene-context.v1",
                                "context_id":"svc_director",
                                "context_hash":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                                "project_id":"prj_story",
                                "chapter_id":"chapter_38",
                                "scene_id":"scene:director:test",
                                "aggregate_version":9,
                                "narrative_boundary":{
                                  "kind":"DRAFT",
                                  "revision_id":"revision_9",
                                  "sha256":"BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB",
                                  "brief_revision_id":"brief_3"
                                },
                                "source":{
                                  "revision":"source-9",
                                  "snapshot_id":"CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC"
                                },
                                "narrative_evidence":{
                                  "evidence_id":"draft:chapter_38:revision_9:0",
                                  "kind":"DRAFT",
                                  "text":"Doom enfrenta al Guardián y Soren en Grayhaven.",
                                  "sha256":"FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF",
                                  "canon_status":"DRAFT",
                                  "authority":"WRITING_ROOM_DRAFT",
                                  "title":"Capítulo 38",
                                  "heading":"current draft",
                                  "chapter_number":38,
                                  "request_text":"Genera la batalla de Doom contra el Guardián y Soren"
                                },
                                "selection":{
                                  "character_ids":["character:doom","character:guardian","character:soren"],
                                  "location_id":"location:grayhaven",
                                  "instruction":"Illustrate the exact frozen written passage."
                                },
                                "reference_manifest":{
                                  "max_references":8,
                                  "references":[],
                                  "textual_fallback_roles":[],
                                  "unresolved_roles":[],
                                  "selection_explanations":[],
                                  "approval_policy":"APPROVED_ONLY"
                                },
                                "exploratory":false,
                                "generation_ready":true
                              }
                            }
                            """.trimIndent(),
                        )
                    "/api/app/writing-room/v2/visual/scenes/director/generate" ->
                        MockResponse().setBody(
                            """
                            {
                              "schema":"jarvis.visual.scene-director-job.v1",
                              "job":{
                                "job_id":"vsj_scene_1",
                                "project_id":"prj_story",
                                "context_id":"svc_123",
                                "context_hash":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                                "status":"QUEUED",
                                "requested_by":"owner-1",
                                "attempts":[]
                              },
                              "result":null,
                              "async_execution":true,
                              "human_approval_required":true,
                              "auto_canon":false,
                              "created":true
                            }
                            """.trimIndent(),
                        )
                    "/api/app/writing-room/v2/visual/scenes/director/job/status" ->
                        MockResponse().setBody(
                            """
                            {
                              "schema":"jarvis.visual.scene-director-job.v1",
                              "job":{
                                "job_id":"vsj_scene_1",
                                "project_id":"prj_story",
                                "context_id":"svc_123",
                                "context_hash":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                                "status":"READY_FOR_REVIEW",
                                "requested_by":"owner-1",
                                "final_asset_id":"va_scene_1",
                                "final_asset_sha256":"EEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEE",
                                "storage_state":"stored",
                                "evaluation":{
                                  "verdict":"PASS",
                                  "score":96,
                                  "narrative_score":98,
                                  "identity_score":95,
                                  "composition_score":90,
                                  "issues":[],
                                  "correction_instruction":"",
                                  "model":"test-scene-vlm"
                                },
                                "correction_count":0,
                                "provider_outcome_unknown":false,
                                "last_error":"",
                                "warning":"",
                                "attempts":[{
                                  "attempt_number":1,
                                  "status":"EVALUATED_PASS",
                                  "asset_id":"va_scene_1",
                                  "sha256":"EEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEE",
                                  "storage_state":"stored",
                                  "provider":"xai-oauth",
                                  "model":"grok-imagine-image-2.0",
                                  "evaluation":{
                                    "verdict":"PASS",
                                    "score":96,
                                    "narrative_score":98,
                                    "identity_score":95,
                                    "composition_score":90,
                                    "issues":[],
                                    "correction_instruction":"",
                                    "model":"test-scene-vlm"
                                  }
                                }]
                              },
                              "result":{
                                "schema":"jarvis.visual.scene-generation.v2",
                                "mime_type":"image/png",
                                "data_base64":"aW1hZ2U=",
                                "size_bytes":5,
                                "provider":"xai-oauth",
                                "model":"grok-imagine-image-2.0",
                                "route":"scene_director_job",
                                "requested_mode":"quality",
                                "fallback_used":false,
                                "attempt_count":1,
                                "reference_count":2,
                                "vps_persistence":"visual_asset_candidate",
                                "storage_retry_required":false,
                                "scene_context_id":"svc_123",
                                "scene_context_hash":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                                "scene_evaluation":{
                                  "verdict":"PASS",
                                  "score":96,
                                  "narrative_score":98,
                                  "identity_score":95,
                                  "composition_score":90,
                                  "issues":[],
                                  "correction_instruction":"",
                                  "model":"test-scene-vlm"
                                },
                                "scene_attempts":[{
                                  "attempt_number":1,
                                  "correction_number":0,
                                  "asset_id":"va_scene_1",
                                  "sha256":"EEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEE",
                                  "storage_state":"stored",
                                  "evaluation":{
                                    "verdict":"PASS",
                                    "score":96,
                                    "narrative_score":98,
                                    "identity_score":95,
                                    "composition_score":90,
                                    "issues":[],
                                    "correction_instruction":"",
                                    "model":"test-scene-vlm"
                                  }
                                }],
                                "scene_correction_count":0,
                                "scene_human_approval_required":true,
                                "auto_canon":false,
                                "visual_asset":{
                                  "asset_id":"va_scene_1",
                                  "project_id":"prj_story",
                                  "kind":"SCENE_ART",
                                  "status":"CANDIDATE",
                                  "source":"CLOUD_GENERATOR",
                                  "sha256":"EEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEE",
                                  "mime_type":"image/png",
                                  "size_bytes":5,
                                  "character_ids":["character:doom","character:guardian","character:soren"],
                                  "chapter_ids":["chapter_38"],
                                  "event_ids":[],
                                  "location_ids":["location:grayhaven"],
                                  "scene_ids":["scene:director:test"],
                                  "perspective":"scene",
                                  "visual_revision":0,
                                  "provenance":{},
                                  "storage":{
                                    "backend":"google_drive",
                                    "state":"stored",
                                    "drive_file_id":"drive-scene-1",
                                    "drive_parent_id":"drive-scenes"
                                  }
                                }
                              },
                              "async_execution":true,
                              "human_approval_required":true,
                              "auto_canon":false,
                              "created":false
                            }
                            """.trimIndent(),
                        )
                    "/api/app/writing-room/v2/visual/scenes/generate" ->
                        MockResponse().setBody(
                            """
                            {
                              "schema":"$sceneSchema",
                              "mime_type":"image/png",
                              "data_base64":"$fixturePng",
                              "size_bytes":70,
                              "provider":"xai-oauth",
                              "model":"grok-imagine-image-2.0",
                              "route":"cloud_image_generation:xai-oauth",
                              "requested_mode":"quality",
                              "fallback_used":false,
                              "attempt_count":1,
                              "duration_ms":1200,
                              "reference_count":2,
                              "vps_persistence":"visual_asset_candidate",
                              "storage_retry_required":false,
                              "scene_context_id":"svc_123",
                              "scene_context_hash":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                              "scene_evaluation":{
                                "verdict":"PASS",
                                "score":96,
                                "narrative_score":98,
                                "identity_score":95,
                                "composition_score":90,
                                "issues":[],
                                "correction_instruction":"",
                                "model":"test-scene-vlm"
                              },
                              "scene_attempts":[{
                                "attempt_number":1,
                                "correction_number":0,
                                "asset_id":"va_scene_1",
                                "sha256":"$fixtureSha256",
                                "storage_state":"stored",
                                "evaluation":{
                                  "verdict":"PASS",
                                  "score":96,
                                  "narrative_score":98,
                                  "identity_score":95,
                                  "composition_score":90,
                                  "issues":[],
                                  "correction_instruction":"",
                                  "model":"test-scene-vlm"
                                }
                              }],
                              "scene_correction_count":0,
                              "scene_human_approval_required":true,
                              "auto_canon":false,
                              "visual_asset":{
                                "asset_id":"va_scene_1",
                                "project_id":"prj_story",
                                "kind":"SCENE_ART",
                                "status":"CANDIDATE",
                                "source":"CLOUD_GENERATOR",
                                "sha256":"$fixtureSha256",
                                "mime_type":"image/png",
                                "size_bytes":70,
                                "character_ids":["character:alexander"],
                                "chapter_ids":["chapter_12"],
                                "event_ids":[],
                                "location_ids":["location:grayhaven"],
                                "scene_ids":["scene:arrival"],
                                "perspective":"scene",
                                "visual_revision":0,
                                "provenance":{},
                                "storage":{
                                  "backend":"google_drive",
                                  "state":"stored",
                                  "drive_file_id":"drive-scene-1",
                                  "drive_parent_id":"drive-scenes"
                                }
                              }
                            }
                            """.trimIndent(),
                        )
                    "/api/app/images/edits" ->
                        MockResponse().setBody(
                            """
                            {
                              "schema":"jarvis.image.edit.v1",
                              "mime_type":"image/png",
                              "data_base64":"iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGNgZGT8DwABDQED5NTSmwAAAABJRU5ErkJggg==",
                              "size_bytes":70,
                              "provider":"openai",
                              "model":"image-edit",
                              "route":"cloud_image_edit:openai",
                              "requested_mode":"quality",
                              "fallback_used":false,
                              "attempt_count":1,
                              "duration_ms":650,
                              "reference_count":1,
                              "vps_persistence":"visual_asset_candidate",
                              "storage_retry_required":false,
                              "visual_asset":{
                                "asset_id":"va_scene_edit_1",
                                "project_id":"prj_story",
                                "kind":"SCENE_ART",
                                "status":"CANDIDATE",
                                "source":"CLOUD_GENERATOR",
                                "sha256":"2f41b02bc94868f7cdd1e503c7f592ee7673dbb155eac2f267a3d1abf64aea18",
                                "mime_type":"image/png",
                                "size_bytes":70,
                                "character_ids":["character:alexander","character:guardian"],
                                "chapter_ids":["chapter_12"],
                                "event_ids":["event:arrival"],
                                "location_ids":["location:grayhaven"],
                                "scene_ids":["scene:arrival"],
                                "perspective":"scene",
                                "visual_revision":0,
                                "parent_asset_id":"va_scene_parent",
                                "parent_sha256":"EEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEE",
                                "derivation":"EDIT",
                                "provenance":{
                                  "parent_asset_id":"va_scene_parent",
                                  "parent_sha256":"EEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEE",
                                  "derivation":"EDIT"
                                },
                                "storage":{
                                  "backend":"google_drive",
                                  "state":"stored",
                                  "drive_file_id":"drive-scene-edit-1",
                                  "drive_parent_id":"drive-scenes"
                                }
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

    private fun session(store: JarvisAppSession.MemoryStore = JarvisAppSession.MemoryStore()): JarvisAppSession {
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

        val request = takeActionRequest()
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
    fun sceneDirectorResolvesFrozenPassageWithoutManualSceneFields() = runBlocking {
        val result = session().visualSceneDirectorResolve(
            projectId = "prj_story",
            requestText = "Genera la batalla de Doom contra el Guardián y Soren",
        ).getOrThrow()

        assertEquals("READY", result.status)
        assertEquals("cloud", result.recommended_engine)
        assertEquals(2, result.generation_max_corrections)
        assertFalse(result.human_selection_required)
        assertFalse(result.missing_story_fact)
        assertEquals("DRAFT", result.context?.narrative_evidence?.kind)
        assertEquals(
            "Doom enfrenta al Guardián y Soren en Grayhaven.",
            result.context?.narrative_evidence?.text,
        )
        assertEquals(
            listOf("character:doom", "character:guardian", "character:soren"),
            result.context?.selection?.character_ids,
        )

        val request = takeActionRequest()
        val body = json.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals("prj_story", body["project_id"]!!.jsonPrimitive.content)
        assertEquals(
            "Genera la batalla de Doom contra el Guardián y Soren",
            body["request_text"]!!.jsonPrimitive.content,
        )
        assertFalse(body.containsKey("chapter_id"))
        assertFalse(body.containsKey("character_ids"))
        assertFalse(body.containsKey("location_id"))
    }

    @Test
    fun frozenContextCanBeReopenedByExactId() = runBlocking {
        val result = session().visualSceneContextGet(
            projectId = "prj_story",
            contextId = "svc_123",
        ).getOrThrow()
        assertEquals("svc_123", result.context.context_id)
        assertEquals("source-7", result.context.source.revision)

        val request = takeActionRequest()
        val body = json.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals("svc_123", body["context_id"]!!.jsonPrimitive.content)
    }
    @Test
    fun sceneGenerationUsesFrozenContextWithoutResendingPromptOrReferences() = runBlocking {
        val result = session().generateSceneVisualAssetImage(
            projectId = "prj_story",
            contextId = "svc_123",
            contextHash = "A".repeat(64),
            engine = "cloud",
            mode = "quality",
            aspectRatio = "landscape",
        ).getOrThrow()

        assertEquals("svc_123", result.scene_context_id)
        assertEquals("SCENE_ART", result.visual_asset?.kind)
        assertEquals("CANDIDATE", result.visual_asset?.status)
        assertEquals(2, result.reference_count)
        assertEquals("PASS", result.scene_evaluation.verdict)
        assertEquals(98, result.scene_evaluation.narrative_score)
        assertEquals(95, result.scene_evaluation.identity_score)
        assertEquals(1, result.scene_attempts.size)
        assertTrue(result.scene_human_approval_required)
        assertFalse(result.auto_canon)

        val request = takeActionRequest()
        val body = json.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals("svc_123", body["context_id"]!!.jsonPrimitive.content)
        assertEquals("A".repeat(64), body["context_hash"]!!.jsonPrimitive.content)
        assertEquals("cloud", body["engine"]!!.jsonPrimitive.content)
        assertEquals("quality", body["mode"]!!.jsonPrimitive.content)
        assertEquals("landscape", body["aspect_ratio"]!!.jsonPrimitive.content)
        assertEquals(0, body["max_corrections"]!!.jsonPrimitive.content.toInt())
        assertFalse(body.containsKey("prompt"))
        assertFalse(body.containsKey("references"))
    }


    @Test
    fun directorGenerationQueuesDurableJobWithOnlyFrozenContext() = runBlocking {
        val queued = session().visualSceneDirectorGenerate(
            projectId = "prj_story",
            contextId = "svc_123",
            contextHash = "A".repeat(64),
            engine = "cloud",
            mode = "quality",
            aspectRatio = "landscape",
            maxCorrections = 2,
        ).getOrThrow()

        assertTrue(queued.async_execution)
        assertTrue(queued.created)
        assertEquals("QUEUED", queued.job?.status)
        assertEquals("vsj_scene_1", queued.job?.job_id)
        assertEquals(null, queued.result)

        val request = takeActionRequest()
        assertEquals(
            "/api/app/writing-room/v2/visual/scenes/director/generate",
            request.path,
        )
        val body = json.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals("svc_123", body["context_id"]!!.jsonPrimitive.content)
        assertEquals("A".repeat(64), body["context_hash"]!!.jsonPrimitive.content)
        assertEquals(2, body["max_corrections"]!!.jsonPrimitive.content.toInt())
        assertFalse(body.containsKey("prompt"))
        assertFalse(body.containsKey("references"))
    }

    @Test
    fun directorStatusRestoresDurableCandidateAndEvaluation() = runBlocking {
        val finished = session().visualSceneDirectorJobStatus(
            projectId = "prj_story",
            jobId = "vsj_scene_1",
        ).getOrThrow()

        assertEquals("READY_FOR_REVIEW", finished.job?.status)
        assertEquals("va_scene_1", finished.job?.final_asset_id)
        assertEquals(1, finished.job?.attempts?.size)
        assertEquals(
            "EVALUATED_PASS",
            finished.job?.attempts?.firstOrNull()?.status,
        )
        assertEquals("PASS", finished.result?.scene_evaluation?.verdict)
        assertEquals("CANDIDATE", finished.result?.visual_asset?.status)
        assertEquals("svc_123", finished.result?.scene_context_id)
        assertFalse(finished.auto_canon)

        val request = takeActionRequest()
        assertEquals(
            "/api/app/writing-room/v2/visual/scenes/director/job/status",
            request.path,
        )
        val body = json.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals("prj_story", body["project_id"]!!.jsonPrimitive.content)
        assertEquals("vsj_scene_1", body["job_id"]!!.jsonPrimitive.content)
        assertFalse(body.containsKey("context_hash"))
    }


    @Test
    fun sceneEditPreservesApprovedParentAndAllSceneAssociations() = runBlocking {
        val parent = VisualStudioAsset(
            asset_id = "va_scene_parent",
            project_id = "prj_story",
            kind = "SCENE_ART",
            status = "APPROVED",
            sha256 = "E".repeat(64),
            mime_type = "image/png",
            character_ids = listOf("character:alexander", "character:guardian"),
            chapter_ids = listOf("chapter_12"),
            event_ids = listOf("event:arrival"),
            location_ids = listOf("location:grayhaven"),
            scene_ids = listOf("scene:arrival"),
            perspective = "scene",
            visual_revision = 4,
        )

        val result = session().editSceneVisualAssetImage(
            imageBase64 = "cGFyZW50",
            mimeType = "image/png",
            projectId = "prj_story",
            instruction = "Mantén identidades y cambia solo la iluminación.",
            parentAsset = parent,
        ).getOrThrow()

        val edited = result.visual_asset!!
        assertEquals("CANDIDATE", edited.status)
        assertEquals(parent.asset_id, edited.parent_asset_id)
        assertEquals(parent.sha256, edited.parent_sha256)
        assertEquals("EDIT", edited.derivation)

        val request = takeActionRequest()
        val body = json.parseToJsonElement(request.body.readUtf8()).jsonObject
        val meta = body["visual_asset"]!!.jsonObject
        assertEquals("scene_builder", meta["surface"]!!.jsonPrimitive.content)
        assertEquals("SCENE_ART", meta["kind"]!!.jsonPrimitive.content)
        assertEquals(parent.asset_id, meta["parent_asset_id"]!!.jsonPrimitive.content)
        assertEquals(parent.sha256, meta["parent_sha256"]!!.jsonPrimitive.content)
        assertEquals(
            parent.character_ids,
            meta["character_ids"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
        assertEquals(
            parent.chapter_ids,
            meta["chapter_ids"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
        assertEquals(
            parent.scene_ids,
            meta["scene_ids"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
        assertEquals(
            parent.event_ids,
            meta["event_ids"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
        assertEquals(
            parent.location_ids,
            meta["location_ids"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
    }

    private fun completedSceneReceipt(requestId: String, fingerprint: String): String =
        """
        {
          "schema":"$sceneSchema",
          "mime_type":"image/png",
          "data_base64":"$fixturePng",
          "size_bytes":70,
          "scene_context_id":"svc_123",
          "request_id":"$requestId",
          "request_fingerprint":"$fingerprint",
          "project_id":"prj_story",
          "route":"/api/app/writing-room/v2/visual/scenes/generate",
          "state":"COMPLETED",
          "dispatch_state":"COMPLETED",
          "retryable":false,
          "visual_asset":{
            "asset_id":"va_scene_1",
            "project_id":"prj_story",
            "kind":"SCENE_ART",
            "status":"CANDIDATE",
            "source":"CLOUD_GENERATOR",
            "sha256":"$fixtureSha256",
            "mime_type":"image/png",
            "size_bytes":70,
            "storage":{"backend":"google_drive","state":"stored","drive_file_id":"drive-scene-1","drive_parent_id":"drive-scenes"}
          }
        }
        """.trimIndent()

    @Test
    fun cloudSceneGenerateUsesPaidJournal() = runBlocking {
        val store = JarvisAppSession.MemoryStore()
        val result = session(store).generateSceneVisualAssetImage(
            projectId = "prj_story",
            contextId = "svc_123",
            contextHash = "A".repeat(64),
            engine = "cloud",
            mode = "quality",
            aspectRatio = "landscape",
            model = "grok-imagine-image-2.0",
        ).getOrThrow()

        assertEquals("CANDIDATE", result.visual_asset?.status)
        assertEquals("stored", result.visual_asset?.storage?.state)
        assertTrue(store.snapshot().keys.none { it.startsWith("jarvis.paid.visual.") })

        val probe = server.takeRequest()
        assertEquals("GET", probe.method)
        val requestId = probe.path.orEmpty().substringAfterLast('/')
        assertTrue(requestId.startsWith("img_"))
        val post = server.takeRequest()
        assertEquals("/api/app/writing-room/v2/visual/scenes/generate", post.path)
        val body = json.parseToJsonElement(post.body.readUtf8()).jsonObject
        assertEquals(requestId, body["request_id"]!!.jsonPrimitive.content)
        assertFalse(body.containsKey("model"))
        assertEquals(2, server.requestCount)
    }

    @Test
    fun cloudSceneGenerateAfterTimeoutOnlyRecoversByGet() = runBlocking {
        val store = JarvisAppSession.MemoryStore()
        val client = session(store)
        loseNextSceneGenerationResponse = true
        assertTrue(
            client.generateSceneVisualAssetImage(
                projectId = "prj_story",
                contextId = "svc_123",
                contextHash = "A".repeat(64),
                engine = "cloud",
            ).isFailure,
        )

        assertTrue(server.takeRequest().path?.startsWith("/api/app/images/requests/") == true)
        val post = server.takeRequest()
        assertEquals("/api/app/writing-room/v2/visual/scenes/generate", post.path)
        val postBody = json.parseToJsonElement(post.body.readUtf8()).jsonObject
        val requestId = postBody["request_id"]!!.jsonPrimitive.content
        paidReceipts[requestId] = completedSceneReceipt(requestId, client.paidVisualFingerprint(postBody))

        val recovered = client.generateSceneVisualAssetImage(
            projectId = "prj_story",
            contextId = "svc_123",
            contextHash = "A".repeat(64),
            engine = "cloud",
        ).getOrThrow()

        assertEquals("stored", recovered.visual_asset?.storage?.state)
        assertEquals(3, server.requestCount) // probe, one POST, then GET recovery only
        val recovery = server.takeRequest()
        assertEquals("GET", recovery.method)
        assertEquals("/api/app/images/requests/$requestId", recovery.path)
        assertTrue(store.snapshot().keys.none { it.startsWith("jarvis.paid.visual.") })
    }

    @Test
    fun localSceneGenerateIsNotPaid() = runBlocking {
        val store = JarvisAppSession.MemoryStore()
        val result = session(store).generateSceneVisualAssetImage(
            projectId = "prj_story",
            contextId = "svc_123",
            contextHash = "A".repeat(64),
            engine = "local",
            model = "flux-local-test",
        ).getOrThrow()

        assertEquals("svc_123", result.scene_context_id)
        assertEquals(1, server.requestCount)
        val body = json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertEquals("local", body["engine"]!!.jsonPrimitive.content)
        assertEquals("flux-local-test", body["model"]!!.jsonPrimitive.content)
        assertFalse(body.containsKey("request_id"))
        assertTrue(store.snapshot().keys.none { it.startsWith("jarvis.paid.visual.") })
    }

    @Test
    fun cloudSceneGenerateAcceptsManualV1Receipt() = runBlocking {
        sceneSchema = "jarvis.visual.scene-generation.v1"
        val result = session().generateSceneVisualAssetImage(
            projectId = "prj_story",
            contextId = "svc_123",
            contextHash = "A".repeat(64),
            engine = "cloud",
        ).getOrThrow()

        assertEquals("stored", result.visual_asset?.storage?.state)
    }

    @Test
    fun cloudSceneGenerateRejectsForeignSchemaAndStaysPinned() = runBlocking {
        sceneSchema = "jarvis.image.edit.v1"
        val store = JarvisAppSession.MemoryStore()
        val client = session(store)
        val makeRequest: suspend () -> Result<VisualStudioGeneratedImage> = {
            client.generateSceneVisualAssetImage(
                projectId = "prj_story",
                contextId = "svc_123",
                contextHash = "A".repeat(64),
                engine = "cloud",
            )
        }

        val first = makeRequest()
        assertEquals("Paid image receipt project or schema mismatch", first.exceptionOrNull()?.message)
        // The server claimed and completed the POST, so its GET returns that receipt rather than 404.
        assertTrue(server.takeRequest().path?.startsWith("/api/app/images/requests/") == true)
        val postBody = json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        val requestId = postBody["request_id"]!!.jsonPrimitive.content
        paidReceipts[requestId] = completedSceneReceipt(requestId, client.paidVisualFingerprint(postBody))
        assertTrue(makeRequest().isFailure)
        assertEquals(3, server.requestCount) // probe, one POST, then GET only
        assertTrue(store.snapshot().keys.any { it.startsWith("jarvis.paid.visual.") })
    }

    @Test
    fun sceneCapabilitiesExposeLocalBlockerAndNoFallback() = runBlocking {
        val result = session().visualSceneGenerationCapabilities(
            projectId = "prj_story",
        ).getOrThrow()

        assertEquals("cloud", result.default_engine)
        assertTrue(result.no_silent_cross_engine_fallback)
        assertEquals("ready", result.engines["cloud"]?.state)
        val local = result.engines["local"]!!
        assertEquals("unavailable", local.state)
        assertEquals(
            "reference_materialization_unavailable",
            local.reason,
        )
        assertEquals(2, local.exact_reference_count)
        assertFalse(local.fallback_allowed)

        val request = takeActionRequest()
        val body = json.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals("prj_story", body["project_id"]!!.jsonPrimitive.content)
    }


}
