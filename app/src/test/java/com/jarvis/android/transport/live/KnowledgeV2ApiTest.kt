package com.jarvis.android.transport.live

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher as MockDispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference

class KnowledgeV2ApiTest {
    private lateinit var server: MockWebServer
    private val lastBody = AtomicReference<String>("")

    @Before
    fun setUp() {
        KnowledgeV2ViewStateStore.clearAll()
        server = MockWebServer()
        server.dispatcher = object : MockDispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.getHeader("Authorization") != "Bearer test-token") {
                    return MockResponse().setResponseCode(401)
                }
                lastBody.set(request.body.readUtf8())
                return when (request.path) {
                    "/knowledge/v2/capabilities" -> MockResponse().setBody(
                        """
                        {
                          "schema":"jarvis.knowledge.response.v2",
                          "project_id":"prj_story",
                          "snapshot_id":"ks_story_1",
                          "visibility":{"view_id":"kv_owner_1","scope":"AUTHORIZED"},
                          "coverage":{"partial":false,"incomplete":false,"reasons":[]},
                          "data":{
                            "project":{
                              "project_id":"prj_story",
                              "title":"Alexander History",
                              "locale":"es-MX",
                              "state":"ACTIVE"
                            },
                            "features":{
                              "timeline":true,
                              "graph":true,
                              "node_get":true,
                              "edge_get":true,
                              "assertion_get":true,
                              "source_resolve":true,
                              "historical_snapshot_pin":true
                            },
                            "counts":{
                              "nodes":5,
                              "assertions":8,
                              "edges":3,
                              "events":2,
                              "timeline_entries":4,
                              "unresolved_references":1
                            },
                            "snapshot":{
                              "state":"READY",
                              "builder_version":"knowledge-v2-c06.1",
                              "source_revision_ids":["rev_story_1"]
                            }
                          }
                        }
                        """.trimIndent(),
                    )
                    "/knowledge/v2/graph/query" -> MockResponse().setBody(
                        """
                        {
                          "schema":"jarvis.knowledge.response.v2",
                          "project_id":"prj_story",
                          "snapshot_id":"ks_story_1",
                          "visibility":{"view_id":"kv_owner_1","scope":"AUTHORIZED"},
                          "coverage":{"partial":false,"incomplete":false,"reasons":[]},
                          "data":{
                            "nodes":[
                              {
                                "node_id":"character:alexander",
                                "type_ids":["character"],
                                "label":"Alexander",
                                "aliases":[]
                              },
                              {
                                "node_id":"character:guardian",
                                "type_ids":["character"],
                                "label":"Guardian",
                                "aliases":[]
                              }
                            ],
                            "edges":[
                              {
                                "edge_id":"edge_guardian",
                                "source_id":"character:alexander",
                                "target_id":"character:guardian",
                                "predicate_id":"related_to",
                                "assertion_ids":["a_guardian"]
                              }
                            ],
                            "page":{"next_cursor":null,"has_more":false}
                          }
                        }
                        """.trimIndent(),
                    )
                    "/knowledge/v2/assertion/get" -> MockResponse().setBody(
                        """
                        {
                          "schema":"jarvis.knowledge.response.v2",
                          "project_id":"prj_story",
                          "snapshot_id":"ks_story_1",
                          "visibility":{"view_id":"kv_owner_1","scope":"AUTHORIZED"},
                          "coverage":{"partial":false,"incomplete":false,"reasons":[]},
                          "data":{
                            "assertion":{
                              "assertion_id":"a_guardian",
                              "assertion_version_id":"av_1",
                              "subject_id":"character:alexander",
                              "predicate_id":"related_to",
                              "object":{
                                "kind":"NODE",
                                "node_id":"character:guardian"
                              },
                              "authority":{"status":"OFFICIAL_CANON"},
                              "temporal":{
                                "occurrence":"OCCURRED",
                                "story_time":{"kind":"UNKNOWN"},
                                "chapter_ids":["chapter:37"]
                              },
                              "provenance":{
                                "adapter_id":"fixture",
                                "adapter_version":"1",
                                "origin_record_id":"rel-1",
                                "method":"EXPLICIT_ANNOTATION"
                              },
                              "evidence_refs":[]
                            }
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
        KnowledgeV2ViewStateStore.clearAll()
        server.shutdown()
    }

    private fun seededSession(userId: String = "owner-1"): JarvisAppSession {
        val store = JarvisAppSession.MemoryStore()
        store.put(
            mapOf(
                JarvisAppSession.KEY_TOKEN to "test-token",
                JarvisAppSession.KEY_BASE to server.url("/").toString().trimEnd('/'),
                JarvisAppSession.KEY_USER_ID to userId,
                JarvisAppSession.KEY_USERNAME to "owner",
                JarvisAppSession.KEY_ROLE to "owner",
                JarvisAppSession.KEY_EXPIRES to "2099-01-01T00:00:00Z",
            ),
        )
        return JarvisAppSession(store)
    }

    @Test
    fun capabilitiesPinsSnapshotAndTracksAuthorizedViewInMemory() = runBlocking(Dispatchers.IO) {
        val session = seededSession()
        val result = session.knowledgeCapabilities("prj_story")

        assertTrue(result.isSuccess)
        val response = result.getOrThrow()
        assertEquals("ks_story_1", response.snapshot_id)
        assertEquals("kv_owner_1", response.visibility.view_id)
        assertTrue(response.data.features.timeline)
        assertTrue(response.data.features.graph)

        val state = KnowledgeV2ViewStateStore.get("owner-1", "prj_story")
        assertEquals("ks_story_1", state?.snapshotId)
        assertEquals("kv_owner_1", state?.viewId)
        assertFalse(lastBody.get().contains("view_id"))
    }

    @Test
    fun graphSendsSnapshotAndPredicateFiltersWithoutInventingProjectScope() = runBlocking(Dispatchers.IO) {
        val session = seededSession()
        val result = session.knowledgeGraph(
            projectId = "prj_story",
            snapshotId = "ks_story_1",
            nodeId = "character:alexander",
            predicateIds = listOf("related_to"),
            limit = 200,
        )

        assertTrue(result.isSuccess)
        val graph = result.getOrThrow()
        assertEquals(2, graph.data.nodes.size)
        assertEquals("edge_guardian", graph.data.edges.single().edge_id)

        val body = lastBody.get()
        assertTrue(body.contains(""project_id":"prj_story""))
        assertTrue(body.contains(""snapshot_id":"ks_story_1""))
        assertTrue(body.contains(""node_id":"character:alexander""))
        assertTrue(body.contains(""predicate_ids":["related_to"]"))
    }

    @Test
    fun assertionObjectFieldMapsFromWireObjectProperty() = runBlocking(Dispatchers.IO) {
        val session = seededSession()
        val result = session.knowledgeAssertion(
            projectId = "prj_story",
            assertionId = "a_guardian",
            snapshotId = "ks_story_1",
        )

        assertTrue(result.isSuccess)
        val assertion = result.getOrThrow().data.assertion
        assertEquals("a_guardian", assertion.assertion_id)
        assertEquals(
            "NODE",
            assertion.object_["kind"]?.toString()?.trim('"'),
        )
        assertEquals(
            "character:guardian",
            assertion.object_["node_id"]?.toString()?.trim('"'),
        )
    }

    @Test
    fun authBoundaryClearsKnowledgeViewState() {
        val session = seededSession()
        KnowledgeV2ViewStateStore.update(
            principalId = "owner-1",
            projectId = "prj_story",
            snapshotId = "ks_story_1",
            viewId = "kv_owner_1",
        )
        assertTrue(KnowledgeV2ViewStateStore.get("owner-1", "prj_story") != null)

        session.clearAccess()
        assertNull(KnowledgeV2ViewStateStore.get("owner-1", "prj_story"))
        assertFalse(session.isAuthenticated)
    }

    @Test
    fun viewStateNeverCrossesPrincipals() {
        KnowledgeV2ViewStateStore.update(
            principalId = "owner-1",
            projectId = "prj_story",
            snapshotId = "ks_owner",
            viewId = "kv_owner",
        )
        KnowledgeV2ViewStateStore.update(
            principalId = "reader-1",
            projectId = "prj_story",
            snapshotId = "ks_reader",
            viewId = "kv_reader",
        )

        KnowledgeV2ViewStateStore.clearPrincipal("owner-1")

        assertNull(KnowledgeV2ViewStateStore.get("owner-1", "prj_story"))
        assertEquals(
            "kv_reader",
            KnowledgeV2ViewStateStore.get("reader-1", "prj_story")?.viewId,
        )
    }
}
