package com.jarvis.android.transport.live

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VisualStudioLocationApiTest {

    private lateinit var server: MockWebServer
    private val json = Json { ignoreUnknownKeys = true }

    private fun <T> Result<T>.orThrowStage(stage: String): T =
        getOrElse { error ->
            throw AssertionError(
                "$stage failed: ${error::class.simpleName}: ${error.message}",
                error,
            )
        }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.getHeader("Authorization") != "Bearer test-token") {
                    return MockResponse().setResponseCode(401)
                }
                val body = runCatching {
                    json.parseToJsonElement(
                        request.body.clone().readUtf8(),
                    ).jsonObject
                }.getOrNull()
                return when (request.path) {
                    "/api/app/images/generations" -> {
                        val visual = body
                            ?.get("visual_asset")
                            ?.jsonObject
                        val kind = visual
                            ?.get("kind")
                            ?.jsonPrimitive
                            ?.contentOrNull
                            .orEmpty()
                        val perspective = visual
                            ?.get("perspective")
                            ?.jsonPrimitive
                            ?.contentOrNull
                            .orEmpty()
                        val isMaster = kind == "LOCATION_REFERENCE"
                        val assetId = if (isMaster) {
                            "va_location_master"
                        } else {
                            "va_location_interior"
                        }
                        val digest = if (isMaster) {
                            "C".repeat(64)
                        } else {
                            "D".repeat(64)
                        }
                        val refs = if (isMaster) 0 else 1
                        val response = buildJsonObject {
                            put("schema", "jarvis.image.generation.v2")
                            put("mime_type", "image/png")
                            put("data_base64", "aW1hZ2U=")
                            put("size_bytes", 5)
                            put("provider", "fixture-cloud")
                            put("model", "fixture-image")
                            put(
                                "route",
                                "cloud_image_generation:fixture-cloud",
                            )
                            put("requested_mode", "quality")
                            put("fallback_used", false)
                            put("attempt_count", 1)
                            put("reference_count", refs)
                            put(
                                "vps_persistence",
                                "visual_asset_candidate",
                            )
                            put("storage_retry_required", false)
                            put("visual_asset", buildJsonObject {
                                put("asset_id", assetId)
                                put("project_id", "prj_story")
                                put("kind", kind)
                                put("status", "CANDIDATE")
                                put("source", "CLOUD_GENERATOR")
                                put("sha256", digest)
                                put("mime_type", "image/png")
                                put("size_bytes", 5)
                                put(
                                    "location_ids",
                                    buildJsonArray {
                                        add(
                                            JsonPrimitive(
                                                "location:grayhaven",
                                            ),
                                        )
                                    },
                                )
                                put("perspective", perspective)
                                put("storage", buildJsonObject {
                                    put("backend", "google_drive")
                                    put("state", "stored")
                                    put(
                                        "drive_file_id",
                                        "drive-$assetId",
                                    )
                                    put(
                                        "drive_parent_id",
                                        "locations",
                                    )
                                })
                            })
                        }
                        MockResponse().setBody(response.toString())
                    }

                    "/api/app/writing-room/v2/visual/assets/approve-exact" -> {
                        val assetId = body
                            ?.get("asset_id")
                            ?.jsonPrimitive
                            ?.contentOrNull
                            .orEmpty()
                        val digest = body
                            ?.get("asset_sha256")
                            ?.jsonPrimitive
                            ?.contentOrNull
                            .orEmpty()
                        val isMaster = assetId == "va_location_master"
                        MockResponse().setBody(
                            """
                            {
                              "schema":"jarvis.visual-studio.asset-approval.v1",
                              "asset":{
                                "asset_id":"$assetId",
                                "project_id":"prj_story",
                                "kind":"${if (isMaster) "LOCATION_REFERENCE" else "LOCATION_VARIANT"}",
                                "status":"APPROVED",
                                "sha256":"$digest",
                                "mime_type":"image/png",
                                "size_bytes":5,
                                "location_ids":["location:grayhaven"],
                                "perspective":"${if (isMaster) "establishing" else "interior"}",
                                "visual_revision":1,
                                "storage":{
                                  "backend":"google_drive",
                                  "state":"stored"
                                }
                              }
                            }
                            """.trimIndent(),
                        )
                    }

                    "/api/app/writing-room/v2/visual/location-reference-packs/create" ->
                        locationPackResponse("DRAFT", emptyList())

                    "/api/app/writing-room/v2/visual/location-reference-packs/slot/add" ->
                        locationPackResponse(
                            "DRAFT",
                            listOf(
                                """{"slot_key":"interior","perspective":"interior","asset_id":"va_location_interior","asset_sha256":"${"D".repeat(64)}","required":false}""",
                            ),
                        )

                    "/api/app/writing-room/v2/visual/location-reference-packs/prepare" ->
                        locationPackResponse(
                            "READY_FOR_APPROVAL",
                            listOf(
                                """{"slot_key":"interior","perspective":"interior","asset_id":"va_location_interior","asset_sha256":"${"D".repeat(64)}","required":false}""",
                            ),
                        )

                    "/api/app/writing-room/v2/visual/location-reference-packs/approve" ->
                        locationPackResponse(
                            "APPROVED",
                            listOf(
                                """{"slot_key":"interior","perspective":"interior","asset_id":"va_location_interior","asset_sha256":"${"D".repeat(64)}","required":false}""",
                            ),
                        )

                    "/api/app/writing-room/v2/visual/locations/detail" ->
                        MockResponse().setBody(
                            """
                            {
                              "schema":"jarvis.visual-studio.location-detail.v1",
                              "project_id":"prj_story",
                              "location_id":"location:grayhaven",
                              "gallery":{
                                "schema":"jarvis.story.wiki.visual-gallery.v1",
                                "project_id":"prj_story",
                                "entity_type":"location",
                                "entity_id":"location:grayhaven",
                                "primary":{
                                  "asset_id":"va_location_master",
                                  "kind":"LOCATION_REFERENCE",
                                  "status":"APPROVED",
                                  "sha256":"${"C".repeat(64)}",
                                  "perspective":"establishing"
                                },
                                "variants":[{
                                  "asset_id":"va_location_interior",
                                  "kind":"LOCATION_VARIANT",
                                  "status":"APPROVED",
                                  "sha256":"${"D".repeat(64)}",
                                  "perspective":"interior"
                                }]
                              },
                              "active_reference_pack":{
                                "schema":"jarvis.visual-studio.location-reference-pack.v1",
                                "pack_id":"vlrp_1",
                                "project_id":"prj_story",
                                "location_id":"location:grayhaven",
                                "revision":1,
                                "state":"APPROVED",
                                "master_asset_id":"va_location_master",
                                "master_sha256":"${"C".repeat(64)}",
                                "slots":[{
                                  "slot_key":"interior",
                                  "perspective":"interior",
                                  "asset_id":"va_location_interior",
                                  "asset_sha256":"${"D".repeat(64)}",
                                  "required":false
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

    private fun locationPackResponse(
        state: String,
        slots: List<String>,
    ): MockResponse {
        val slotsJson = slots.joinToString(prefix = "[", postfix = "]")
        return MockResponse().setBody(
            """
            {
              "schema":"jarvis.visual-studio.location-reference-pack.v1",
              "pack":{
                "schema":"jarvis.visual-studio.location-reference-pack.v1",
                "pack_id":"vlrp_1",
                "project_id":"prj_story",
                "location_id":"location:grayhaven",
                "revision":1,
                "state":"$state",
                "master_asset_id":"va_location_master",
                "master_sha256":"${"C".repeat(64)}",
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
                JarvisAppSession.KEY_BASE to server
                    .url("/")
                    .toString()
                    .trimEnd('/'),
                JarvisAppSession.KEY_USER_ID to "owner-1",
                JarvisAppSession.KEY_USERNAME to "owner",
                JarvisAppSession.KEY_ROLE to "owner",
                JarvisAppSession.KEY_EXPIRES to "2099-01-01T00:00:00Z",
            ),
        )
        return JarvisAppSession(store)
    }

    @Test
    fun locationMasterVariantAndPackUseProjectScopedContract() = runBlocking {
        val session = session()

        val master = session.generateLocationVisualAssetImage(
            projectId = "prj_story",
            locationId = "location:grayhaven",
            prompt = "Grayhaven establishing reference",
            kind = "LOCATION_REFERENCE",
            perspective = "establishing",
        ).orThrowStage("location master generation")
        assertEquals("CANDIDATE", master.visual_asset?.status)
        assertEquals(
            listOf("location:grayhaven"),
            master.visual_asset?.location_ids,
        )
        assertEquals(0, master.reference_count)

        session.visualAssetApproveExact(
            "prj_story",
            "va_location_master",
            "C".repeat(64),
            visualRevision = 1,
        ).orThrowStage("location master exact approval")

        val variant = session.generateLocationVisualAssetImage(
            projectId = "prj_story",
            locationId = "location:grayhaven",
            prompt = "Interior view of the same place",
            kind = "LOCATION_VARIANT",
            perspective = "interior",
            referencePerspectives = listOf("establishing"),
        ).orThrowStage("location variant generation")
        assertEquals(1, variant.reference_count)
        assertEquals(
            "va_location_interior",
            variant.visual_asset?.asset_id,
        )

        session.visualAssetApproveExact(
            "prj_story",
            "va_location_interior",
            "D".repeat(64),
            visualRevision = 1,
        ).orThrowStage("location variant exact approval")

        val draft = session.visualLocationReferencePackCreate(
            "prj_story",
            "location:grayhaven",
            "va_location_master",
            "C".repeat(64),
        ).orThrowStage("location pack create")
        assertEquals("DRAFT", draft.pack.state)

        val slotted = session.visualLocationReferencePackAddSlot(
            "prj_story",
            "vlrp_1",
            "interior",
            "va_location_interior",
            "D".repeat(64),
            required = false,
        ).orThrowStage("location pack slot")
        assertEquals("interior", slotted.pack.slots.single().slot_key)

        val ready = session.visualLocationReferencePackPrepare(
            "prj_story",
            "vlrp_1",
        ).orThrowStage("location pack prepare")
        assertEquals("READY_FOR_APPROVAL", ready.pack.state)

        val approved = session.visualLocationReferencePackApprove(
            "prj_story",
            "vlrp_1",
        ).orThrowStage("location pack approve")
        assertEquals("APPROVED", approved.pack.state)

        val detail = session.visualLocationDetail(
            "prj_story",
            "location:grayhaven",
        ).orThrowStage("location detail")
        assertEquals(
            "vlrp_1",
            detail.active_reference_pack?.pack_id,
        )
        assertEquals(
            "va_location_master",
            detail.gallery?.primary?.asset_id,
        )
        assertEquals(
            "va_location_interior",
            detail.gallery?.variants?.single()?.asset_id,
        )

        val requests = generateSequence {
            if (server.requestCount > 0) {
                server.takeRequest()
            } else {
                null
            }
        }.take(8).toList()
        assertTrue(
            requests.all {
                it.getHeader("Authorization") == "Bearer test-token"
            },
        )

        val masterBody = json.parseToJsonElement(
            requests[0].body.readUtf8(),
        ).jsonObject
        val masterVisual = masterBody["visual_asset"]!!.jsonObject
        assertEquals(
            "location_creator",
            masterVisual["surface"]!!.jsonPrimitive.content,
        )
        assertEquals(
            "location:grayhaven",
            masterVisual["location_ids"]!!
                .jsonArray
                .single()
                .jsonPrimitive
                .content,
        )
        assertEquals(
            "landscape",
            masterBody["aspect_ratio"]!!.jsonPrimitive.content,
        )

        val variantBody = json.parseToJsonElement(
            requests[2].body.readUtf8(),
        ).jsonObject
        val variantVisual = variantBody["visual_asset"]!!.jsonObject
        assertEquals(
            "establishing",
            variantVisual["reference_perspectives"]!!
                .jsonArray
                .single()
                .jsonPrimitive
                .content,
        )
    }
}
