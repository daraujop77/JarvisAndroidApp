package com.jarvis.android.transport.live

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
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

class WorldMapApiTest {

    private lateinit var server: MockWebServer
    private val json = Json { ignoreUnknownKeys = true }
    private var version = 1
    private var hash = "A".repeat(64)
    private var approved = false

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
                    "/api/app/writing-room/v2/world-map/status" ->
                        MockResponse().setBody(
                            """
                            {
                              "schema":"jarvis.world-map.status.v1",
                              "project_id":"prj_story",
                              "active_revision_id":"${if (approved) "wmr_1" else ""}",
                              "draft":${if (approved) "null" else revisionJson("DRAFT")},
                              "revisions":[${revisionJson(if (approved) "APPROVED" else "DRAFT")}]
                            }
                            """.trimIndent(),
                        )

                    "/api/app/writing-room/v2/world-map/revision/create" -> {
                        version = 1
                        hash = "A".repeat(64)
                        approved = false
                        mapResponse("DRAFT", emptyList())
                    }

                    "/api/app/writing-room/v2/world-map/node/upsert" -> {
                        val expected = body
                            ?.get("expected_version")
                            ?.jsonPrimitive
                            ?.intOrNull
                        if (expected != version) {
                            conflictResponse()
                        } else {
                            version += 1
                            hash = "B".repeat(64)
                            val nodeId = body
                                ?.get("node_id")
                                ?.jsonPrimitive
                                ?.contentOrNull
                                ?: "world:main"
                            val nodeType = body
                                ?.get("node_type")
                                ?.jsonPrimitive
                                ?.contentOrNull
                                ?: "WORLD"
                            val name = body
                                ?.get("name")
                                ?.jsonPrimitive
                                ?.contentOrNull
                                ?: "World"
                            mapResponse(
                                "DRAFT",
                                listOf(
                                    """{"node_id":"$nodeId","node_type":"$nodeType","name":"$name","parent_node_id":"","location_id":"","x":0.0,"y":0.0,"z":0.0,"placement_source":"MANUAL","visual_asset_id":"","visual_asset_sha256":""}""",
                                ),
                            )
                        }
                    }

                    "/api/app/writing-room/v2/world-map/get" ->
                        mapResponse(
                            if (approved) "APPROVED" else "DRAFT",
                            listOf(
                                """{"node_id":"world:main","node_type":"WORLD","name":"World","parent_node_id":"","location_id":"","x":0.0,"y":0.0,"z":0.0,"placement_source":"MANUAL","visual_asset_id":"","visual_asset_sha256":""}""",
                            ),
                        )

                    "/api/app/writing-room/v2/world-map/presence/upsert" -> {
                        val expected = body
                            ?.get("expected_version")
                            ?.jsonPrimitive
                            ?.intOrNull
                        if (expected != version) {
                            conflictResponse()
                        } else {
                            version += 1
                            hash = "C".repeat(64)
                            MockResponse().setBody(
                                """
                                {
                                  "schema":"jarvis.world-map.v1",
                                  "revision":${revisionJson("DRAFT")},
                                  "nodes":[{
                                    "node_id":"world:main",
                                    "node_type":"WORLD",
                                    "name":"World",
                                    "parent_node_id":"",
                                    "location_id":"",
                                    "x":0.0,
                                    "y":0.0,
                                    "z":0.0,
                                    "placement_source":"MANUAL",
                                    "visual_asset_id":"",
                                    "visual_asset_sha256":""
                                  }],
                                  "character_presence":[{
                                    "presence_id":"wmp_1",
                                    "character_id":"character:alexander",
                                    "node_id":"world:main",
                                    "temporal_kind":"OCCURRED",
                                    "temporal_ref":"chapter:12",
                                    "evidence":[{
                                      "source_id":"chapter:12",
                                      "source_type":"chapter",
                                      "chapter_id":"chapter:12",
                                      "lines":"120-144"
                                    }]
                                  }]
                                }
                                """.trimIndent(),
                            )
                        }
                    }

                    "/api/app/writing-room/v2/world-map/revision/approve" -> {
                        val expected = body
                            ?.get("expected_version")
                            ?.jsonPrimitive
                            ?.intOrNull
                        val wantedHash = body
                            ?.get("expected_hash")
                            ?.jsonPrimitive
                            ?.contentOrNull
                        if (expected != version || wantedHash != hash) {
                            conflictResponse()
                        } else {
                            approved = true
                            mapResponse(
                                "APPROVED",
                                listOf(
                                    """{"node_id":"world:main","node_type":"WORLD","name":"World","parent_node_id":"","location_id":"","x":0.0,"y":0.0,"z":0.0,"placement_source":"MANUAL","visual_asset_id":"","visual_asset_sha256":""}""",
                                ),
                            )
                        }
                    }

                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
    }

    private fun revisionJson(state: String): String =
        """
        {
          "revision_id":"wmr_1",
          "project_id":"prj_story",
          "revision":1,
          "state":"$state",
          "title":"Known World",
          "parent_revision_id":"",
          "version":$version,
          "content_hash":"$hash",
          "created_by":"owner-1",
          "created_utc":"2026-10-02T00:00:00Z"
        }
        """.trimIndent()

    private fun mapResponse(
        state: String,
        nodes: List<String>,
    ): MockResponse {
        val nodesJson = nodes.joinToString(prefix = "[", postfix = "]")
        return MockResponse().setBody(
            """
            {
              "schema":"jarvis.world-map.v1",
              "revision":${revisionJson(state)},
              "nodes":$nodesJson,
              "character_presence":[]
            }
            """.trimIndent(),
        )
    }

    private fun conflictResponse(): MockResponse =
        MockResponse()
            .setResponseCode(409)
            .setBody(
                """
                {
                  "schema":"jarvis.web.error.v1",
                  "error":"world_map_version_conflict",
                  "message":"world_map_version_conflict",
                  "details":{
                    "current_version":$version,
                    "current_hash":"$hash"
                  }
                }
                """.trimIndent(),
            )

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
    fun revisionNodeRestoreAndApprovalUseExactCasContract() = runBlocking {
        val session = session()
        val draft = session.worldMapCreateRevision(
            "prj_story",
            title = "Known World",
        ).getOrThrow()
        assertEquals(1, draft.revision.version)
        assertEquals("DRAFT", draft.revision.state)

        val placed = session.worldMapUpsertNode(
            projectId = "prj_story",
            revisionId = draft.revision.revision_id,
            expectedVersion = draft.revision.version,
            nodeId = "world:main",
            nodeType = "WORLD",
            name = "World",
            x = 0.0,
            y = 0.0,
        ).getOrThrow()
        assertEquals(2, placed.revision.version)
        assertEquals("world:main", placed.nodes.single().node_id)

        val reopened = session.worldMapGet(
            "prj_story",
            draft.revision.revision_id,
        ).getOrThrow()
        assertEquals(placed.revision.content_hash, reopened.revision.content_hash)

        val approvedMap = session.worldMapApproveRevision(
            projectId = "prj_story",
            revisionId = reopened.revision.revision_id,
            expectedVersion = reopened.revision.version,
            expectedHash = reopened.revision.content_hash,
        ).getOrThrow()
        assertEquals("APPROVED", approvedMap.revision.state)

        val status = session.worldMapStatus("prj_story").getOrThrow()
        assertEquals("wmr_1", status.active_revision_id)
    }

    @Test
    fun presenceSerializesNarrativeEvidenceExplicitly() = runBlocking {
        val session = session()
        val draft = session.worldMapCreateRevision("prj_story").getOrThrow()
        val placed = session.worldMapUpsertNode(
            projectId = "prj_story",
            revisionId = draft.revision.revision_id,
            expectedVersion = draft.revision.version,
            nodeId = "world:main",
            nodeType = "WORLD",
            name = "World",
            x = 0.0,
            y = 0.0,
        ).getOrThrow()

        val presence = session.worldMapUpsertPresence(
            projectId = "prj_story",
            revisionId = placed.revision.revision_id,
            expectedVersion = placed.revision.version,
            characterId = "character:alexander",
            nodeId = "world:main",
            temporalKind = "OCCURRED",
            temporalRef = "chapter:12",
            evidence = listOf(
                WorldMapEvidence(
                    source_id = "chapter:12",
                    source_type = "chapter",
                    chapter_id = "chapter:12",
                    lines = "120-144",
                ),
            ),
        ).getOrThrow()
        assertEquals(
            "chapter:12",
            presence.character_presence.single().evidence.single().source_id,
        )

        repeat(2) { server.takeRequest() }
        val request = server.takeRequest()
        val body = json.parseToJsonElement(
            request.body.readUtf8(),
        ).jsonObject
        val evidence = body["evidence"]!!
            .jsonArray
            .single()
            .jsonObject
        assertEquals(
            "chapter",
            evidence["source_type"]!!.jsonPrimitive.content,
        )
        assertEquals(
            "chapter:12",
            evidence["source_id"]!!.jsonPrimitive.content,
        )
    }

    @Test
    fun staleVersionReturnsCurrentVersionAndHash() = runBlocking {
        val session = session()
        val draft = session.worldMapCreateRevision("prj_story").getOrThrow()
        val placed = session.worldMapUpsertNode(
            projectId = "prj_story",
            revisionId = draft.revision.revision_id,
            expectedVersion = draft.revision.version,
            nodeId = "world:main",
            nodeType = "WORLD",
            name = "World",
            x = 0.0,
            y = 0.0,
        ).getOrThrow()

        val result = session.worldMapUpsertNode(
            projectId = "prj_story",
            revisionId = draft.revision.revision_id,
            expectedVersion = draft.revision.version,
            nodeId = "world:main",
            nodeType = "WORLD",
            name = "Stale",
            x = 1.0,
            y = 1.0,
        )
        assertTrue(result.isFailure)
        val conflict = result.exceptionOrNull() as WorldMapConflictException
        assertEquals(placed.revision.version, conflict.currentVersion)
        assertEquals(placed.revision.content_hash, conflict.currentHash)
    }
}
