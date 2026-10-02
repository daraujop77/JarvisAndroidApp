package com.jarvis.android.transport.live

import com.jarvis.android.transport.TransportException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private val worldMapJson = Json { ignoreUnknownKeys = true }

class WorldMapConflictException(
    val currentVersion: Int?,
    val currentHash: String?,
    message: String,
) : RuntimeException(message)

@Serializable
data class WorldMapRevision(
    val revision_id: String = "",
    val project_id: String = "",
    val revision: Int = 0,
    val state: String = "",
    val title: String = "",
    val parent_revision_id: String = "",
    val version: Int = 0,
    val content_hash: String = "",
    val created_by: String = "",
    val created_utc: String = "",
    val approved_by: String = "",
    val approved_utc: String = "",
    val deprecated_utc: String = "",
)

@Serializable
data class WorldMapNode(
    val node_id: String = "",
    val node_type: String = "",
    val name: String = "",
    val parent_node_id: String = "",
    val location_id: String = "",
    val x: Double = 0.0,
    val y: Double = 0.0,
    val z: Double = 0.0,
    val placement_source: String = "",
    val visual_asset_id: String = "",
    val visual_asset_sha256: String = "",
    val metadata: JsonObject = JsonObject(emptyMap()),
    val created_utc: String = "",
    val updated_utc: String = "",
)

@Serializable
data class WorldMapEvidence(
    val source_id: String = "",
    val source_type: String = "",
    val chapter_id: String = "",
    val section: String = "",
    val lines: String = "",
    val note: String = "",
)

@Serializable
data class WorldMapCharacterPresence(
    val presence_id: String = "",
    val character_id: String = "",
    val node_id: String = "",
    val temporal_kind: String = "",
    val temporal_ref: String = "",
    val evidence: List<WorldMapEvidence> = emptyList(),
    val metadata: JsonObject = JsonObject(emptyMap()),
    val created_utc: String = "",
    val updated_utc: String = "",
)

@Serializable
data class WorldMapMutation(
    val node_id: String = "",
    val removed_node_id: String = "",
    val presence_id: String = "",
    val removed_presence_id: String = "",
    val version: Int = 0,
    val content_hash: String = "",
)

@Serializable
data class WorldMapResponse(
    val schema: String = "",
    val revision: WorldMapRevision = WorldMapRevision(),
    val nodes: List<WorldMapNode> = emptyList(),
    val character_presence: List<WorldMapCharacterPresence> = emptyList(),
    val mutation: WorldMapMutation? = null,
)

@Serializable
data class WorldMapStatusResponse(
    val schema: String = "",
    val project_id: String = "",
    val active_revision_id: String = "",
    val draft: WorldMapRevision? = null,
    val revisions: List<WorldMapRevision> = emptyList(),
)

private suspend inline fun <reified T> JarvisAppSession.worldMapPost(
    path: String,
    body: JsonObject,
): Result<T> = withContext(Dispatchers.IO) {
    if (expired) {
        clear()
        return@withContext Result.failure(TransportException("session expired"))
    }
    val auth = authHeader()
        ?: return@withContext Result.failure(TransportException("no live session"))
    runCatching {
        val response = post(baseUrl, path, body.toString(), auth)
        if (response.first == 401) {
            clear()
            throw TransportException("session expired")
        }
        if (response.first !in 200..299) {
            val payload = runCatching {
                worldMapJson.parseToJsonElement(response.second).jsonObject
            }.getOrNull()
            val message = payload
                ?.get("message")
                ?.jsonPrimitive
                ?.contentOrNull
                ?: "World Map request failed (${response.first})"
            val details = payload?.get("details") as? JsonObject
            val currentVersion = details
                ?.get("current_version")
                ?.jsonPrimitive
                ?.intOrNull
            val currentHash = details
                ?.get("current_hash")
                ?.jsonPrimitive
                ?.contentOrNull
            if (response.first == 409) {
                throw WorldMapConflictException(
                    currentVersion,
                    currentHash,
                    message,
                )
            }
            requireOk(response)
        }
        worldMapJson.decodeFromString<T>(response.second)
    }
}

suspend fun JarvisAppSession.worldMapStatus(
    projectId: String,
): Result<WorldMapStatusResponse> =
    worldMapPost(
        "/api/app/writing-room/v2/world-map/status",
        buildJsonObject {
            put("project_id", projectId)
        },
    )

suspend fun JarvisAppSession.worldMapGet(
    projectId: String,
    revisionId: String? = null,
): Result<WorldMapResponse> =
    worldMapPost(
        "/api/app/writing-room/v2/world-map/get",
        buildJsonObject {
            put("project_id", projectId)
            if (!revisionId.isNullOrBlank()) put("revision_id", revisionId)
        },
    )

suspend fun JarvisAppSession.worldMapCreateRevision(
    projectId: String,
    title: String = "World Map",
    parentRevisionId: String = "",
): Result<WorldMapResponse> =
    worldMapPost(
        "/api/app/writing-room/v2/world-map/revision/create",
        buildJsonObject {
            put("project_id", projectId)
            put("title", title)
            if (parentRevisionId.isNotBlank()) {
                put("parent_revision_id", parentRevisionId)
            }
        },
    )

suspend fun JarvisAppSession.worldMapUpsertNode(
    projectId: String,
    revisionId: String,
    expectedVersion: Int,
    nodeId: String?,
    nodeType: String,
    name: String,
    parentNodeId: String = "",
    locationId: String = "",
    x: Double,
    y: Double,
    z: Double = 0.0,
    placementSource: String = "MANUAL",
    visualAssetId: String = "",
    visualAssetSha256: String = "",
    metadata: JsonObject = JsonObject(emptyMap()),
): Result<WorldMapResponse> =
    worldMapPost(
        "/api/app/writing-room/v2/world-map/node/upsert",
        buildJsonObject {
            put("project_id", projectId)
            put("revision_id", revisionId)
            put("expected_version", expectedVersion)
            if (!nodeId.isNullOrBlank()) put("node_id", nodeId)
            put("node_type", nodeType)
            put("name", name)
            put("parent_node_id", parentNodeId)
            put("location_id", locationId)
            put("x", x)
            put("y", y)
            put("z", z)
            put("placement_source", placementSource)
            if (visualAssetId.isNotBlank()) {
                put("visual_asset_id", visualAssetId)
                put("visual_asset_sha256", visualAssetSha256)
            }
            put("metadata", metadata)
        },
    )

suspend fun JarvisAppSession.worldMapRemoveNode(
    projectId: String,
    revisionId: String,
    expectedVersion: Int,
    nodeId: String,
): Result<WorldMapResponse> =
    worldMapPost(
        "/api/app/writing-room/v2/world-map/node/remove",
        buildJsonObject {
            put("project_id", projectId)
            put("revision_id", revisionId)
            put("expected_version", expectedVersion)
            put("node_id", nodeId)
        },
    )

suspend fun JarvisAppSession.worldMapUpsertPresence(
    projectId: String,
    revisionId: String,
    expectedVersion: Int,
    presenceId: String? = null,
    characterId: String,
    nodeId: String,
    temporalKind: String,
    temporalRef: String,
    evidence: List<WorldMapEvidence>,
    metadata: JsonObject = JsonObject(emptyMap()),
): Result<WorldMapResponse> =
    worldMapPost(
        "/api/app/writing-room/v2/world-map/presence/upsert",
        buildJsonObject {
            put("project_id", projectId)
            put("revision_id", revisionId)
            put("expected_version", expectedVersion)
            if (!presenceId.isNullOrBlank()) put("presence_id", presenceId)
            put("character_id", characterId)
            put("node_id", nodeId)
            put("temporal_kind", temporalKind)
            put("temporal_ref", temporalRef)
            put(
                "evidence",
                buildJsonArray {
                    evidence.forEach { item ->
                        add(
                            buildJsonObject {
                                put("source_id", item.source_id)
                                put("source_type", item.source_type)
                                put("chapter_id", item.chapter_id)
                                put("section", item.section)
                                put("lines", item.lines)
                                put("note", item.note)
                            },
                        )
                    }
                },
            )
            put("metadata", metadata)
        },
    )

suspend fun JarvisAppSession.worldMapRemovePresence(
    projectId: String,
    revisionId: String,
    expectedVersion: Int,
    presenceId: String,
): Result<WorldMapResponse> =
    worldMapPost(
        "/api/app/writing-room/v2/world-map/presence/remove",
        buildJsonObject {
            put("project_id", projectId)
            put("revision_id", revisionId)
            put("expected_version", expectedVersion)
            put("presence_id", presenceId)
        },
    )

suspend fun JarvisAppSession.worldMapApproveRevision(
    projectId: String,
    revisionId: String,
    expectedVersion: Int,
    expectedHash: String,
): Result<WorldMapResponse> =
    worldMapPost(
        "/api/app/writing-room/v2/world-map/revision/approve",
        buildJsonObject {
            put("project_id", projectId)
            put("revision_id", revisionId)
            put("expected_version", expectedVersion)
            put("expected_hash", expectedHash)
        },
    )
