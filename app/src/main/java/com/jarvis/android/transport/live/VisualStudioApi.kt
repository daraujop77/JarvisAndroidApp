package com.jarvis.android.transport.live

import com.jarvis.android.transport.TransportException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private val visualStudioJson = Json { ignoreUnknownKeys = true }

@Serializable
data class VisualStudioStorage(
    val backend: String = "",
    val state: String = "",
    val drive_file_id: String = "",
    val drive_parent_id: String = "",
)

@Serializable
data class VisualStudioStorageError(
    val code: String = "",
    val message: String = "",
    val retry_required: Boolean = false,
)

@Serializable
data class VisualStudioAsset(
    val asset_id: String = "",
    val project_id: String = "",
    val kind: String = "",
    val status: String = "",
    val source: String = "",
    val sha256: String = "",
    val mime_type: String = "image/png",
    val size_bytes: Long = 0,
    val character_ids: List<String> = emptyList(),
    val chapter_ids: List<String> = emptyList(),
    val event_ids: List<String> = emptyList(),
    val location_ids: List<String> = emptyList(),
    val scene_ids: List<String> = emptyList(),
    val perspective: String = "custom",
    val visual_revision: Int = 0,
    val alt: String = "",
    val provenance: JsonObject = JsonObject(emptyMap()),
    val created_by: String = "",
    val approved_by: String = "",
    val created_utc: String = "",
    val approved_utc: String = "",
    val parent_asset_id: String = "",
    val parent_sha256: String = "",
    val derivation: String = "",
    val storage: VisualStudioStorage = VisualStudioStorage(),
    val storage_error: VisualStudioStorageError? = null,
)

@Serializable
data class VisualStudioAssetList(
    val schema: String = "",
    val project_id: String = "",
    val assets: List<VisualStudioAsset> = emptyList(),
)

@Serializable
data class VisualStudioAssetResponse(
    val schema: String = "",
    val asset: VisualStudioAsset = VisualStudioAsset(),
)

@Serializable
data class VisualStudioGalleryDelivery(
    val mode: String = "",
    val backend: String = "",
    val state: String = "",
    val asset_id: String = "",
)

@Serializable
data class VisualStudioGalleryCard(
    val asset_id: String = "",
    val kind: String = "",
    val status: String = "",
    val sha256: String = "",
    val mime_type: String = "image/png",
    val perspective: String = "custom",
    val visual_revision: Int = 0,
    val alt: String = "",
    val character_ids: List<String> = emptyList(),
    val chapter_ids: List<String> = emptyList(),
    val scene_ids: List<String> = emptyList(),
    val event_ids: List<String> = emptyList(),
    val location_ids: List<String> = emptyList(),
    val delivery: VisualStudioGalleryDelivery = VisualStudioGalleryDelivery(),
)

@Serializable
data class VisualStudioReferencePackSummary(
    val pack_id: String = "",
    val revision: Int = 0,
    val state: String = "",
    val master_asset_id: String = "",
)

@Serializable
data class VisualStudioCharacterGallery(
    val schema: String = "",
    val project_id: String = "",
    val entity_type: String = "",
    val entity_id: String = "",
    val primary: VisualStudioGalleryCard? = null,
    val identity_pack: List<VisualStudioGalleryCard> = emptyList(),
    val outfits: List<VisualStudioGalleryCard> = emptyList(),
    val scenes: List<VisualStudioGalleryCard> = emptyList(),
    val reference_pack: VisualStudioReferencePackSummary? = null,
)

@Serializable
data class VisualStudioReferencePackSlot(
    val slot_key: String = "",
    val perspective: String = "",
    val asset_id: String = "",
    val asset_sha256: String = "",
    val required: Boolean = true,
    val created_utc: String = "",
)

@Serializable
data class VisualStudioReferencePack(
    val schema: String = "",
    val pack_id: String = "",
    val project_id: String = "",
    val character_id: String = "",
    val revision: Int = 0,
    val state: String = "",
    val master_asset_id: String = "",
    val master_sha256: String = "",
    val parent_pack_id: String = "",
    val created_by: String = "",
    val created_utc: String = "",
    val approved_by: String = "",
    val approved_utc: String = "",
    val deprecated_by: String = "",
    val deprecated_utc: String = "",
    val slots: List<VisualStudioReferencePackSlot> = emptyList(),
)

@Serializable
data class VisualStudioCharacterDetail(
    val schema: String = "",
    val project_id: String = "",
    val character_id: String = "",
    val gallery: VisualStudioCharacterGallery? = null,
    val active_reference_pack: VisualStudioReferencePack? = null,
    val reference_packs: List<VisualStudioReferencePack> = emptyList(),
)

@Serializable
data class VisualStudioPackResponse(
    val schema: String = "",
    val pack: VisualStudioReferencePack = VisualStudioReferencePack(),
)

@Serializable
data class VisualStudioGeneratedImage(
    val schema: String = "",
    val mime_type: String = "image/png",
    val data_base64: String = "",
    val size_bytes: Long = 0,
    val provider: String = "",
    val model: String = "",
    val route: String = "",
    val requested_mode: String = "",
    val requested_model: String? = null,
    val fallback_used: Boolean = false,
    val attempt_count: Int = 1,
    val duration_ms: Long = 0,
    val reference_count: Int = 0,
    val vps_persistence: String = "",
    val storage_retry_required: Boolean = false,
    val visual_asset: VisualStudioAsset? = null,
)

private suspend inline fun <reified T> JarvisAppSession.visualStudioPost(
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
        requireOk(response)
        visualStudioJson.decodeFromString<T>(response.second)
    }
}

suspend fun JarvisAppSession.visualCharacterDetail(
    projectId: String,
    characterId: String,
): Result<VisualStudioCharacterDetail> =
    visualStudioPost(
        "/api/app/writing-room/v2/visual/characters/detail",
        buildJsonObject {
            put("project_id", projectId)
            put("character_id", characterId)
        },
    )

suspend fun JarvisAppSession.visualAssetList(
    projectId: String,
    characterId: String,
    status: String? = null,
): Result<VisualStudioAssetList> =
    visualStudioPost(
        "/api/app/writing-room/visual-assets/list",
        buildJsonObject {
            put("project_id", projectId)
            put("character_id", characterId)
            if (!status.isNullOrBlank()) put("status", status)
        },
    )

suspend fun JarvisAppSession.visualAssetIngest(
    projectId: String,
    imageBase64: String,
    mimeType: String,
    kind: String,
    source: String,
    characterId: String,
    perspective: String,
    assetId: String? = null,
    parentAssetId: String = "",
    parentSha256: String = "",
    derivation: String = "",
): Result<VisualStudioAssetResponse> =
    visualStudioPost(
        "/api/app/writing-room/visual-assets/ingest",
        buildJsonObject {
            put("project_id", projectId)
            put("image_base64", imageBase64)
            put("mime_type", mimeType)
            put("kind", kind)
            put("source", source)
            put("character_ids", buildJsonArray { add(JsonPrimitive(characterId)) })
            put("perspective", perspective)
            if (!assetId.isNullOrBlank()) put("asset_id", assetId)
            if (parentAssetId.isNotBlank()) put("parent_asset_id", parentAssetId)
            if (parentSha256.isNotBlank()) put("parent_sha256", parentSha256)
            if (derivation.isNotBlank()) put("derivation", derivation)
        },
    )

suspend fun JarvisAppSession.visualAssetApproveExact(
    projectId: String,
    assetId: String,
    assetSha256: String,
    visualRevision: Int = 0,
): Result<VisualStudioAssetResponse> =
    visualStudioPost(
        "/api/app/writing-room/v2/visual/assets/approve-exact",
        buildJsonObject {
            put("project_id", projectId)
            put("asset_id", assetId)
            put("asset_sha256", assetSha256)
            put("visual_revision", visualRevision)
        },
    )

suspend fun JarvisAppSession.visualReferencePackCreate(
    projectId: String,
    characterId: String,
    masterAssetId: String,
    masterSha256: String,
    parentPackId: String = "",
): Result<VisualStudioPackResponse> =
    visualStudioPost(
        "/api/app/writing-room/v2/visual/reference-packs/create",
        buildJsonObject {
            put("project_id", projectId)
            put("character_id", characterId)
            put("master_asset_id", masterAssetId)
            put("master_sha256", masterSha256)
            if (parentPackId.isNotBlank()) put("parent_pack_id", parentPackId)
        },
    )

suspend fun JarvisAppSession.visualReferencePackAddSlot(
    projectId: String,
    packId: String,
    slotKey: String,
    assetId: String,
    assetSha256: String,
    required: Boolean = true,
): Result<VisualStudioPackResponse> =
    visualStudioPost(
        "/api/app/writing-room/v2/visual/reference-packs/slot/add",
        buildJsonObject {
            put("project_id", projectId)
            put("pack_id", packId)
            put("slot_key", slotKey)
            put("asset_id", assetId)
            put("asset_sha256", assetSha256)
            put("required", required)
        },
    )

suspend fun JarvisAppSession.visualReferencePackPrepare(
    projectId: String,
    packId: String,
): Result<VisualStudioPackResponse> =
    visualStudioPost(
        "/api/app/writing-room/v2/visual/reference-packs/prepare",
        buildJsonObject {
            put("project_id", projectId)
            put("pack_id", packId)
        },
    )

suspend fun JarvisAppSession.visualReferencePackApprove(
    projectId: String,
    packId: String,
): Result<VisualStudioPackResponse> =
    visualStudioPost(
        "/api/app/writing-room/v2/visual/reference-packs/approve",
        buildJsonObject {
            put("project_id", projectId)
            put("pack_id", packId)
        },
    )

suspend fun JarvisAppSession.generateVisualAssetImage(
    projectId: String,
    characterId: String,
    prompt: String,
    kind: String,
    perspective: String,
    mode: String = "quality",
    model: String? = null,
    aspectRatio: String = "portrait",
    referencePerspectives: List<String> = emptyList(),
    referencesPerCharacter: Int = 1,
    parentAssetId: String = "",
    parentSha256: String = "",
    derivation: String = "",
    assetId: String? = null,
): Result<VisualStudioGeneratedImage> =
    visualStudioPost<VisualStudioGeneratedImage>(
        "/api/app/images/generations",
        buildJsonObject {
            put("project_id", projectId)
            put("prompt", prompt.trim())
            put("mode", mode)
            put("aspect_ratio", aspectRatio)
            if (!model.isNullOrBlank()) put("model", model)
            put("visual_asset", buildJsonObject {
                put("surface", "character_creator")
                put("project_id", projectId)
                put("kind", kind)
                put("character_ids", buildJsonArray { add(JsonPrimitive(characterId)) })
                put("perspective", perspective)
                if (referencePerspectives.isNotEmpty()) {
                    put(
                        "reference_perspectives",
                        buildJsonArray { referencePerspectives.forEach { add(JsonPrimitive(it)) } },
                    )
                }
                put("references_per_character", referencesPerCharacter.coerceIn(1, 4))
                if (!assetId.isNullOrBlank()) put("asset_id", assetId)
                if (parentAssetId.isNotBlank()) put("parent_asset_id", parentAssetId)
                if (parentSha256.isNotBlank()) put("parent_sha256", parentSha256)
                if (derivation.isNotBlank()) put("derivation", derivation)
            })
        },
    ).mapCatching { reply ->
        if (reply.data_base64.isBlank() || reply.mime_type.isBlank()) {
            throw TransportException("visual generation returned no image")
        }
        if (reply.visual_asset == null || reply.visual_asset.asset_id.isBlank()) {
            throw TransportException("visual generation returned no durable candidate")
        }
        reply
    }

suspend fun JarvisAppSession.editVisualAssetImage(
    imageBase64: String,
    mimeType: String,
    projectId: String,
    characterId: String,
    instruction: String,
    kind: String,
    perspective: String,
    parentAssetId: String,
    parentSha256: String,
    derivation: String = "EDIT",
    mode: String = "quality",
    model: String? = null,
    preserveIdentity: String = "high",
    aspectRatio: String = "portrait",
): Result<VisualStudioGeneratedImage> =
    visualStudioPost<VisualStudioGeneratedImage>(
        "/api/app/images/edits",
        buildJsonObject {
            put("project_id", projectId)
            put("image_base64", imageBase64)
            put("mime_type", mimeType)
            put("instruction", instruction.trim())
            put("mode", mode)
            put("preserve_identity", preserveIdentity)
            put("aspect_ratio", aspectRatio)
            if (!model.isNullOrBlank()) put("model", model)
            put("visual_asset", buildJsonObject {
                put("surface", "character_creator")
                put("project_id", projectId)
                put("kind", kind)
                put("character_ids", buildJsonArray { add(JsonPrimitive(characterId)) })
                put("perspective", perspective)
                put("parent_asset_id", parentAssetId)
                put("parent_sha256", parentSha256)
                put("derivation", derivation)
            })
        },
    ).mapCatching { reply ->
        if (reply.data_base64.isBlank() || reply.mime_type.isBlank()) {
            throw TransportException("visual edit returned no image")
        }
        if (reply.visual_asset == null || reply.visual_asset.asset_id.isBlank()) {
            throw TransportException("visual edit returned no durable candidate")
        }
        reply
    }
