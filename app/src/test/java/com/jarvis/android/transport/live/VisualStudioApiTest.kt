package com.jarvis.android.transport.live

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VisualStudioApiTest {

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
                val body = runCatching {
                    json.parseToJsonElement(request.body.readUtf8()).jsonObject
                }.getOrNull()
                return when (request.path) {
                    "/api/app/images/generations" -> {
                        val visual = body?.get("visual_asset")?.jsonObject
                        val kind = visual?.get("kind")?.jsonPrimitive?.contentOrNull.orEmpty()
                        val perspective = visual?.get("perspective")?.jsonPrimitive?.contentOrNull.orEmpty()
                        val isMaster = kind == "PRIMARY_REFERENCE"
                        val assetId = if (isMaster) "va_master" else "va_left"
                        val digest = if (isMaster) "A".repeat(64) else "B".repeat(64)
                        val refs = if (isMaster) 0 else 1
                        MockResponse().setBody(
                            """
                            {
                              "schema":"jarvis.image.generation.v2",
                              "mime_type":"image/png",
                              "data_base64":"aW1hZ2U=",
                              "size_bytes":5,
                              "provider":"fixture-cloud",
                              "model":"fixture-image",
                              "route":"cloud_image_generation:fixture-cloud",
                              "requested_mode":"quality",
                              "fallback_used":false,
                              "attempt_count":1,
                              "reference_count":$refs,
                              "vps_persistence":"visual_asset_candidate",
                              "storage_retry_required":false,
                              "visual_asset":{
                                "asset_id":"$assetId",
                                "project_id":"prj_story",
                                "kind":"$kind",
                                "status":"CANDIDATE",
                                "source":"CLOUD_GENERATOR",
                                "sha256":"$digest",
                                "mime_type":"image/png",
                                "size_bytes":5,
                                "character_ids":["character:alexander"],
                                "perspective":"$perspective",
                                "storage":{
                                  "backend":"google_drive",
                                  "state":"stored",
                                  "drive_file_id":"drive-$assetId",
                                  "drive_parent_id":"characters"
                                }
                              }
                            }
                            """.trimIndent(),
                        )
                    }
                    "/api/app/writing-room/v2/visual/assets/approve-exact" -> {
                        val assetId = body?.get("asset_id")?.jsonPrimitive?.contentOrNull.orEmpty()
                        val digest = body?.get("asset_sha256")?.jsonPrimitive?.contentOrNull.orEmpty()
                        if (digest == "0".repeat(64)) {
                            MockResponse().setResponseCode(409).setBody(
                                """{"code":"visual_asset_invalid","message":"visual_asset_hash_mismatch"}""",
                            )
                        } else {
                            val kind = if (assetId == "va_master") "PRIMARY_REFERENCE" else "IDENTITY_PACK"
                            val perspective = if (assetId == "va_master") "front" else "left_profile"
                            MockResponse().setBody(
                                """
                                {
                                  "schema":"jarvis.visual-studio.asset-approval.v1",
                                  "asset":{
                                    "asset_id":"$assetId",
                                    "project_id":"prj_story",
                                    "kind":"$kind",
                                    "status":"APPROVED",
                                    "sha256":"$digest",
                                    "mime_type":"image/png",
                                    "size_bytes":5,
                                    "character_ids":["character:alexander"],
                                    "perspective":"$perspective",
                                    "visual_revision":1,
                                    "storage":{"backend":"google_drive","state":"stored"}
                                  }
                                }
                                """.trimIndent(),
                            )
                        }
                    }
                    "/api/app/writing-room/v2/visual/reference-packs/create" ->
                        packResponse("DRAFT", emptyList())
                    "/api/app/writing-room/v2/visual/reference-packs/slot/add" ->
                        packResponse(
                            "DRAFT",
                            listOf(
                                """{"slot_key":"left_profile","perspective":"left_profile","asset_id":"va_left","asset_sha256":"BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB","required":true}""",
                            ),
                        )
                    "/api/app/writing-room/v2/visual/reference-packs/prepare" ->
                        packResponse(
                            "READY_FOR_APPROVAL",
                            listOf(
                                """{"slot_key":"left_profile","perspective":"left_profile","asset_id":"va_left","asset_sha256":"BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB","required":true}""",
                            ),
                        )
                    "/api/app/writing-room/v2/visual/reference-packs/approve" ->
                        packResponse(
                            "APPROVED",
                            listOf(
                                """{"slot_key":"left_profile","perspective":"left_profile","asset_id":"va_left","asset_sha256":"BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB","required":true}""",
                            ),
                        )
                    "/api/app/writing-room/v2/visual/characters/detail" -> MockResponse().setBody(
                        """
                        {
                          "schema":"jarvis.visual-studio.character-detail.v1",
                          "project_id":"prj_story",
                          "character_id":"character:alexander",
                          "gallery":{
                            "schema":"jarvis.story.wiki.visual-gallery.v1",
                            "project_id":"prj_story",
                            "entity_type":"character",
                            "entity_id":"character:alexander",
                            "primary":{
                              "asset_id":"va_master",
                              "kind":"PRIMARY_REFERENCE",
                              "status":"APPROVED",
                              "sha256":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                              "perspective":"front"
                            },
                            "identity_pack":[{
                              "asset_id":"va_left",
                              "kind":"IDENTITY_PACK",
                              "status":"APPROVED",
                              "sha256":"BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB",
                              "perspective":"left_profile"
                            }]
                          },
                          "active_reference_pack":{
                            "pack_id":"vrp_1",
                            "project_id":"prj_story",
                            "character_id":"character:alexander",
                            "revision":1,
                            "state":"APPROVED",
                            "master_asset_id":"va_master",
                            "master_sha256":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                            "slots":[{
                              "slot_key":"left_profile",
                              "perspective":"left_profile",
                              "asset_id":"va_left",
                              "asset_sha256":"BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB",
                              "required":true
                            }]
                          },
                          "reference_packs":[]
                        }
                        """.trimIndent(),
                    )
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
    }

    private fun packResponse(state: String, slots: List<String>): MockResponse {
        val slotsJson = slots.joinToString(prefix = "[", postfix = "]")
        return MockResponse().setBody(
            """
            {
              "schema":"jarvis.visual-studio.reference-pack.v1",
              "pack":{
                "schema":"jarvis.visual-studio.reference-pack.v1",
                "pack_id":"vrp_1",
                "project_id":"prj_story",
                "character_id":"character:alexander",
                "revision":1,
                "state":"$state",
                "master_asset_id":"va_master",
                "master_sha256":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                "slots":$slotsJson
              }
            }
            """.trimIndent(),
        )
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
    fun masterTurnaroundAndPackUseVersionedDurableContract() = runBlocking {
        val session = session()

        val master = session.generateVisualAssetImage(
            projectId = "prj_story",
            characterId = "character:alexander",
            prompt = "Canonical Alexander portrait",
            kind = "PRIMARY_REFERENCE",
            perspective = "front",
        ).getOrThrow()
        assertEquals("CANDIDATE", master.visual_asset?.status)
        assertEquals("stored", master.visual_asset?.storage?.state)
        assertEquals(0, master.reference_count)

        val masterApproval = session.visualAssetApproveExact(
            "prj_story",
            "va_master",
            "A".repeat(64),
            visualRevision = 1,
        ).getOrThrow()
        assertEquals("APPROVED", masterApproval.asset.status)

        val turnaround = session.generateVisualAssetImage(
            projectId = "prj_story",
            characterId = "character:alexander",
            prompt = "Alexander left profile turnaround",
            kind = "IDENTITY_PACK",
            perspective = "left_profile",
            referencePerspectives = listOf("front"),
        ).getOrThrow()
        assertEquals("va_left", turnaround.visual_asset?.asset_id)
        assertEquals(1, turnaround.reference_count)

        val viewApproval = session.visualAssetApproveExact(
            "prj_story",
            "va_left",
            "B".repeat(64),
            visualRevision = 1,
        ).getOrThrow()
        assertEquals("APPROVED", viewApproval.asset.status)

        val draft = session.visualReferencePackCreate(
            "prj_story",
            "character:alexander",
            "va_master",
            "A".repeat(64),
        ).getOrThrow()
        assertEquals("DRAFT", draft.pack.state)

        val slotted = session.visualReferencePackAddSlot(
            "prj_story",
            "vrp_1",
            "left_profile",
            "va_left",
            "B".repeat(64),
        ).getOrThrow()
        assertEquals(1, slotted.pack.slots.size)
        assertTrue(slotted.pack.slots.single().required)

        val ready = session.visualReferencePackPrepare(
            "prj_story",
            "vrp_1",
        ).getOrThrow()
        assertEquals("READY_FOR_APPROVAL", ready.pack.state)

        val approved = session.visualReferencePackApprove(
            "prj_story",
            "vrp_1",
        ).getOrThrow()
        assertEquals("APPROVED", approved.pack.state)

        val detail = session.visualCharacterDetail(
            "prj_story",
            "character:alexander",
        ).getOrThrow()
        assertEquals("vrp_1", detail.active_reference_pack?.pack_id)
        assertEquals("va_master", detail.gallery?.primary?.asset_id)
        assertEquals("va_left", detail.gallery?.identity_pack?.single()?.asset_id)

        val requests = generateSequence {
            if (server.requestCount > 0) server.takeRequest() else null
        }.take(8).toList()
        assertTrue(requests.all { it.getHeader("Authorization") == "Bearer test-token" })

        val masterBody = json.parseToJsonElement(requests[0].body.readUtf8()).jsonObject
        val masterVisual = masterBody["visual_asset"]!!.jsonObject
        assertEquals("prj_story", masterBody["project_id"]!!.jsonPrimitive.content)
        assertEquals("PRIMARY_REFERENCE", masterVisual["kind"]!!.jsonPrimitive.content)
        assertEquals(
            "character:alexander",
            masterVisual["character_ids"]!!.jsonArray.single().jsonPrimitive.content,
        )

        val turnaroundBody = json.parseToJsonElement(requests[2].body.readUtf8()).jsonObject
        val turnaroundVisual = turnaroundBody["visual_asset"]!!.jsonObject
        assertEquals("IDENTITY_PACK", turnaroundVisual["kind"]!!.jsonPrimitive.content)
        assertEquals("left_profile", turnaroundVisual["perspective"]!!.jsonPrimitive.content)
        assertEquals(
            "front",
            turnaroundVisual["reference_perspectives"]!!.jsonArray.single().jsonPrimitive.content,
        )
    }

    @Test
    fun wrongExactHashApprovalFailsClosed() = runBlocking {
        val result = session().visualAssetApproveExact(
            "prj_story",
            "va_master",
            "0".repeat(64),
            visualRevision = 1,
        )
        assertTrue(result.isFailure)
        assertFalse(result.isSuccess)
        assertNotNull(result.exceptionOrNull())
    }
}
