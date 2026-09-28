package com.jarvis.android.transport.live

import com.jarvis.android.transport.TransportException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

private val knowledgeJson = Json { ignoreUnknownKeys = true }

@Serializable
data class KnowledgeCoverage(
    val partial: Boolean = false,
    val incomplete: Boolean = false,
    val reasons: List<String> = emptyList(),
)

@Serializable
data class KnowledgeVisibility(
    val view_id: String = "",
    val scope: String = "",
)

@Serializable
data class KnowledgePage(
    val next_cursor: String? = null,
    val has_more: Boolean = false,
)

@Serializable
data class KnowledgeProjectPermissions(
    val read_knowledge: Boolean = false,
    val read_owner_spoilers: Boolean = false,
)

@Serializable
data class KnowledgeProjectDescriptor(
    val project_id: String = "",
    val title: String = "",
    val locale: String = "",
    val state: String = "",
    val knowledge_versions: List<Int> = emptyList(),
    val permissions: KnowledgeProjectPermissions = KnowledgeProjectPermissions(),
)

@Serializable
data class KnowledgeProjectsData(
    val projects: List<KnowledgeProjectDescriptor> = emptyList(),
)

@Serializable
data class KnowledgeProjectsResponse(
    val schema: String = "",
    val registry_view_id: String = "",
    val data: KnowledgeProjectsData = KnowledgeProjectsData(),
    val page: KnowledgePage = KnowledgePage(),
)

@Serializable
data class KnowledgeProjectSummary(
    val project_id: String = "",
    val title: String = "",
    val locale: String = "",
    val state: String = "",
)

@Serializable
data class KnowledgeCapabilitiesFeatures(
    val timeline: Boolean = false,
    val graph: Boolean = false,
    val node_get: Boolean = false,
    val edge_get: Boolean = false,
    val assertion_get: Boolean = false,
    val source_resolve: Boolean = false,
    val historical_snapshot_pin: Boolean = false,
)

@Serializable
data class KnowledgeCapabilitiesCounts(
    val nodes: Int = 0,
    val assertions: Int = 0,
    val edges: Int = 0,
    val events: Int = 0,
    val timeline_entries: Int = 0,
    val unresolved_references: Int = 0,
)

@Serializable
data class KnowledgeSnapshotSummary(
    val state: String = "",
    val builder_version: String = "",
    val source_revision_ids: List<String> = emptyList(),
)

@Serializable
data class KnowledgeCapabilitiesData(
    val project: KnowledgeProjectSummary = KnowledgeProjectSummary(),
    val features: KnowledgeCapabilitiesFeatures = KnowledgeCapabilitiesFeatures(),
    val counts: KnowledgeCapabilitiesCounts = KnowledgeCapabilitiesCounts(),
    val snapshot: KnowledgeSnapshotSummary = KnowledgeSnapshotSummary(),
)

@Serializable
data class KnowledgeCapabilitiesResponse(
    val schema: String = "",
    val project_id: String = "",
    val snapshot_id: String = "",
    val visibility: KnowledgeVisibility = KnowledgeVisibility(),
    val coverage: KnowledgeCoverage = KnowledgeCoverage(),
    val data: KnowledgeCapabilitiesData = KnowledgeCapabilitiesData(),
)

@Serializable
data class KnowledgeNode(
    val node_id: String = "",
    val type_ids: List<String> = emptyList(),
    val label: String = "",
    val aliases: List<String> = emptyList(),
)

@Serializable
data class KnowledgeEvidenceRef(
    val evidence_id: String = "",
    val source_id: String = "",
    val source_revision_id: String = "",
    val source_document_id: String? = null,
    val source_hash: String? = null,
    val content_hash: String? = null,
    val source_line_start: Int? = null,
    val source_line_end: Int? = null,
    val chapter_id: String? = null,
    val chapter_number: Int? = null,
    val section: String? = null,
    val support: String = "",
    val locator_precision: String = "",
    val inherited_from_node_id: String? = null,
    val assertion_id: String? = null,
)

@Serializable
data class KnowledgeAssertion(
    val assertion_id: String = "",
    val assertion_version_id: String = "",
    val subject_id: String = "",
    val predicate_id: String = "",
    @SerialName("object")
    val object_: JsonObject = JsonObject(emptyMap()),
    val authority: JsonObject = JsonObject(emptyMap()),
    val temporal: JsonObject = JsonObject(emptyMap()),
    val provenance: JsonObject = JsonObject(emptyMap()),
    val evidence_refs: List<KnowledgeEvidenceRef> = emptyList(),
)

@Serializable
data class KnowledgeEdge(
    val edge_id: String = "",
    val source_id: String = "",
    val target_id: String = "",
    val predicate_id: String = "",
    val assertion_ids: List<String> = emptyList(),
    val authority_summary: List<String> = emptyList(),
    val evidence_summary: JsonObject = JsonObject(emptyMap()),
)

@Serializable
data class KnowledgeTimelineEntry(
    val entry_id: String = "",
    val kind: String = "",
    val event_id: String? = null,
    val title: String = "",
    val lane: String = "",
    val chapter_ids: List<String> = emptyList(),
    val entity_ids: List<String> = emptyList(),
    val assertion_ids: List<String> = emptyList(),
    val position: JsonObject = JsonObject(emptyMap()),
)

@Serializable
data class KnowledgeTimelineData(
    val entries: List<KnowledgeTimelineEntry> = emptyList(),
    val page: KnowledgePage = KnowledgePage(),
)

@Serializable
data class KnowledgeTimelineResponse(
    val schema: String = "",
    val project_id: String = "",
    val snapshot_id: String = "",
    val visibility: KnowledgeVisibility = KnowledgeVisibility(),
    val coverage: KnowledgeCoverage = KnowledgeCoverage(),
    val data: KnowledgeTimelineData = KnowledgeTimelineData(),
)

@Serializable
data class KnowledgeGraphData(
    val nodes: List<KnowledgeNode> = emptyList(),
    val edges: List<KnowledgeEdge> = emptyList(),
    val page: KnowledgePage = KnowledgePage(),
)

@Serializable
data class KnowledgeGraphResponse(
    val schema: String = "",
    val project_id: String = "",
    val snapshot_id: String = "",
    val visibility: KnowledgeVisibility = KnowledgeVisibility(),
    val coverage: KnowledgeCoverage = KnowledgeCoverage(),
    val data: KnowledgeGraphData = KnowledgeGraphData(),
)

@Serializable
data class KnowledgeNodeData(
    val node: KnowledgeNode = KnowledgeNode(),
    val assertions: List<KnowledgeAssertion> = emptyList(),
)

@Serializable
data class KnowledgeNodeResponse(
    val schema: String = "",
    val project_id: String = "",
    val snapshot_id: String = "",
    val visibility: KnowledgeVisibility = KnowledgeVisibility(),
    val coverage: KnowledgeCoverage = KnowledgeCoverage(),
    val data: KnowledgeNodeData = KnowledgeNodeData(),
)

@Serializable
data class KnowledgeEdgeData(
    val edge: KnowledgeEdge = KnowledgeEdge(),
    val assertions: List<KnowledgeAssertion> = emptyList(),
)

@Serializable
data class KnowledgeEdgeResponse(
    val schema: String = "",
    val project_id: String = "",
    val snapshot_id: String = "",
    val visibility: KnowledgeVisibility = KnowledgeVisibility(),
    val coverage: KnowledgeCoverage = KnowledgeCoverage(),
    val data: KnowledgeEdgeData = KnowledgeEdgeData(),
)

@Serializable
data class KnowledgeAssertionData(
    val assertion: KnowledgeAssertion = KnowledgeAssertion(),
)

@Serializable
data class KnowledgeAssertionResponse(
    val schema: String = "",
    val project_id: String = "",
    val snapshot_id: String = "",
    val visibility: KnowledgeVisibility = KnowledgeVisibility(),
    val coverage: KnowledgeCoverage = KnowledgeCoverage(),
    val data: KnowledgeAssertionData = KnowledgeAssertionData(),
)

@Serializable
data class KnowledgeSource(
    val source_id: String = "",
    val project_id: String = "",
    val source_document_id: String = "",
    val source_revision_id: String = "",
    val role: String = "",
    val authority: String = "",
    val source_hash: String? = null,
    val access_policy: String = "",
    val provider: String = "",
)

@Serializable
data class KnowledgeSourceResolveData(
    val source: KnowledgeSource = KnowledgeSource(),
    val evidence: KnowledgeEvidenceRef = KnowledgeEvidenceRef(),
    val assertion_id: String? = null,
)

@Serializable
data class KnowledgeSourceResolveResponse(
    val schema: String = "",
    val project_id: String = "",
    val snapshot_id: String = "",
    val visibility: KnowledgeVisibility = KnowledgeVisibility(),
    val coverage: KnowledgeCoverage = KnowledgeCoverage(),
    val data: KnowledgeSourceResolveData = KnowledgeSourceResolveData(),
)

data class KnowledgeViewState(
    val principalId: String,
    val projectId: String,
    val snapshotId: String,
    val viewId: String,
)

/**
 * Memory-only coordination state. Knowledge v2 payloads are intentionally not
 * persisted on device in C08. view_id is never treated as an auth credential.
 */
object KnowledgeV2ViewStateStore {
    private val state = ConcurrentHashMap<String, KnowledgeViewState>()

    private fun key(principalId: String, projectId: String): String =
        principalId + "\u001f" + projectId

    fun update(
        principalId: String,
        projectId: String,
        snapshotId: String,
        viewId: String,
    ): Boolean {
        if (principalId.isBlank() || projectId.isBlank()) return false
        val next = KnowledgeViewState(principalId, projectId, snapshotId, viewId)
        val previous = state.put(key(principalId, projectId), next)
        return previous != null && previous != next
    }

    fun get(principalId: String, projectId: String): KnowledgeViewState? =
        state[key(principalId, projectId)]

    fun clearPrincipal(principalId: String) {
        if (principalId.isBlank()) return
        val prefix = principalId + "\u001f"
        state.keys
            .filter { it.startsWith(prefix) }
            .forEach { state.remove(it) }
    }

    fun clearAll() = state.clear()
}

private suspend inline fun <reified T> JarvisAppSession.knowledgePost(
    path: String,
    body: JsonObject,
    projectId: String? = null,
): Result<T> = withContext(Dispatchers.IO) {
    if (expired) {
        KnowledgeV2ViewStateStore.clearPrincipal(userId)
        clearAccess()
        return@withContext Result.failure(TransportException("session expired"))
    }
    val auth = authHeader()
        ?: return@withContext Result.failure(TransportException("no live session"))
    runCatching {
        val response = post(baseUrl, path, body.toString(), auth)
        if (response.first == 401 || response.first == 403) {
            KnowledgeV2ViewStateStore.clearPrincipal(userId)
        }
        if (response.first == 401) {
            clearAccess()
            throw TransportException("session expired")
        }
        requireOk(response)
        val parsed = knowledgeJson.decodeFromString<T>(response.second)
        val root = knowledgeJson.parseToJsonElement(response.second).jsonObject
        val snapshotId = root["snapshot_id"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val viewId = root["visibility"]?.jsonObject
            ?.get("view_id")?.jsonPrimitive?.contentOrNull.orEmpty()
        if (!projectId.isNullOrBlank() && snapshotId.isNotBlank() && viewId.isNotBlank()) {
            KnowledgeV2ViewStateStore.update(userId, projectId, snapshotId, viewId)
        }
        parsed
    }
}

suspend fun JarvisAppSession.knowledgeProjectsList(
    cursor: String? = null,
    limit: Int = 100,
): Result<KnowledgeProjectsResponse> =
    knowledgePost(
        "/projects/list",
        buildJsonObject {
            put("schema", "jarvis.knowledge.request.v2")
            cursor?.let { put("cursor", it) }
            put("limit", limit)
        },
    )

suspend fun JarvisAppSession.knowledgeCapabilities(
    projectId: String,
    snapshotId: String? = null,
): Result<KnowledgeCapabilitiesResponse> =
    knowledgePost(
        "/knowledge/v2/capabilities",
        buildJsonObject {
            put("schema", "jarvis.knowledge.request.v2")
            put("project_id", projectId)
            snapshotId?.let { put("snapshot_id", it) }
        },
        projectId,
    )

suspend fun JarvisAppSession.knowledgeTimeline(
    projectId: String,
    snapshotId: String? = null,
    query: String = "",
    lane: String? = null,
    entityId: String? = null,
    chapterId: String? = null,
    cursor: String? = null,
    limit: Int = 200,
): Result<KnowledgeTimelineResponse> =
    knowledgePost(
        "/knowledge/v2/timeline/query",
        buildJsonObject {
            put("schema", "jarvis.knowledge.request.v2")
            put("project_id", projectId)
            snapshotId?.let { put("snapshot_id", it) }
            if (query.isNotBlank()) put("query", query)
            lane?.takeIf { it.isNotBlank() }?.let { put("lane", it) }
            entityId?.takeIf { it.isNotBlank() }?.let { put("entity_id", it) }
            chapterId?.takeIf { it.isNotBlank() }?.let { put("chapter_id", it) }
            cursor?.let { put("cursor", it) }
            put("limit", limit)
        },
        projectId,
    )

suspend fun JarvisAppSession.knowledgeGraph(
    projectId: String,
    snapshotId: String? = null,
    nodeId: String? = null,
    query: String = "",
    predicateIds: List<String> = emptyList(),
    cursor: String? = null,
    limit: Int = 500,
): Result<KnowledgeGraphResponse> =
    knowledgePost(
        "/knowledge/v2/graph/query",
        buildJsonObject {
            put("schema", "jarvis.knowledge.request.v2")
            put("project_id", projectId)
            snapshotId?.let { put("snapshot_id", it) }
            nodeId?.takeIf { it.isNotBlank() }?.let { put("node_id", it) }
            if (query.isNotBlank()) put("query", query)
            if (predicateIds.isNotEmpty()) {
                putJsonArray("predicate_ids") {
                    predicateIds.filter { it.isNotBlank() }.forEach { add(it) }
                }
            }
            cursor?.let { put("cursor", it) }
            put("limit", limit)
        },
        projectId,
    )

suspend fun JarvisAppSession.knowledgeNode(
    projectId: String,
    nodeId: String,
    snapshotId: String? = null,
): Result<KnowledgeNodeResponse> =
    knowledgePost(
        "/knowledge/v2/node/get",
        buildJsonObject {
            put("schema", "jarvis.knowledge.request.v2")
            put("project_id", projectId)
            put("node_id", nodeId)
            snapshotId?.let { put("snapshot_id", it) }
        },
        projectId,
    )

suspend fun JarvisAppSession.knowledgeEdge(
    projectId: String,
    edgeId: String,
    snapshotId: String? = null,
): Result<KnowledgeEdgeResponse> =
    knowledgePost(
        "/knowledge/v2/edge/get",
        buildJsonObject {
            put("schema", "jarvis.knowledge.request.v2")
            put("project_id", projectId)
            put("edge_id", edgeId)
            snapshotId?.let { put("snapshot_id", it) }
        },
        projectId,
    )

suspend fun JarvisAppSession.knowledgeAssertion(
    projectId: String,
    assertionId: String,
    snapshotId: String? = null,
): Result<KnowledgeAssertionResponse> =
    knowledgePost(
        "/knowledge/v2/assertion/get",
        buildJsonObject {
            put("schema", "jarvis.knowledge.request.v2")
            put("project_id", projectId)
            put("assertion_id", assertionId)
            snapshotId?.let { put("snapshot_id", it) }
        },
        projectId,
    )

suspend fun JarvisAppSession.knowledgeSource(
    projectId: String,
    evidenceId: String,
    snapshotId: String? = null,
): Result<KnowledgeSourceResolveResponse> =
    knowledgePost(
        "/knowledge/v2/source/resolve",
        buildJsonObject {
            put("schema", "jarvis.knowledge.request.v2")
            put("project_id", projectId)
            put("evidence_id", evidenceId)
            snapshotId?.let { put("snapshot_id", it) }
        },
        projectId,
    )
