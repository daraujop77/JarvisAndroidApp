package com.jarvis.android.transport.live

import com.jarvis.android.transport.TransportException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull
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
data class VisualStudioLocationGallery(
    val schema: String = "",
    val project_id: String = "",
    val entity_type: String = "",
    val entity_id: String = "",
    val primary: VisualStudioGalleryCard? = null,
    val variants: List<VisualStudioGalleryCard> = emptyList(),
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
data class VisualProjectStyleProfile(
    val schema: String = "",
    val source: String = "",
    val project_id: String = "",
    val revision: Int = 0,
    val style_name: String = "",
    val medium: String = "",
    val realism: String = "",
    val palette: String = "",
    val lighting: String = "",
    val rendering: String = "",
    val positive_rules: List<String> = emptyList(),
    val negative_rules: List<String> = emptyList(),
    val updated_by: String = "",
    val updated_utc: String = "",
)

@Serializable
data class VisualProjectStyleResponse(
    val schema: String = "",
    val profile: VisualProjectStyleProfile = VisualProjectStyleProfile(),
    val auto_canon: Boolean = false,
)

@Serializable
data class VisualCharacterDirectionProfile(
    val schema: String = "",
    val source: String = "",
    val project_id: String = "",
    val character_id: String = "",
    val revision: Int = 0,
    val apparent_age: String = "",
    val build: String = "",
    val height: String = "",
    val skin: String = "",
    val face: String = "",
    val eyes: String = "",
    val hair: String = "",
    val base_outfit: String = "",
    val armor: String = "",
    val accessories: String = "",
    val weapons: String = "",
    val dominant_colors: String = "",
    val aura: String = "",
    val notes: String = "",
    val required_traits: List<String> = emptyList(),
    val forbidden_traits: List<String> = emptyList(),
    val updated_by: String = "",
    val updated_utc: String = "",
)

@Serializable
data class VisualCharacterDirectionResponse(
    val schema: String = "",
    val profile: VisualCharacterDirectionProfile = VisualCharacterDirectionProfile(),
    val auto_canon: Boolean = false,
)

@Serializable
data class VisualStudioLocationReferencePack(
    val schema: String = "",
    val pack_id: String = "",
    val project_id: String = "",
    val location_id: String = "",
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
data class VisualStudioLocationDetail(
    val schema: String = "",
    val project_id: String = "",
    val location_id: String = "",
    val gallery: VisualStudioLocationGallery? = null,
    val active_reference_pack: VisualStudioLocationReferencePack? = null,
    val reference_packs: List<VisualStudioLocationReferencePack> = emptyList(),
)

@Serializable
data class VisualStudioLocationPackResponse(
    val schema: String = "",
    val pack: VisualStudioLocationReferencePack = VisualStudioLocationReferencePack(),
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
    val scene_context_id: String = "",
    val scene_context_hash: String = "",
    val visual_asset: VisualStudioAsset? = null,
    val scene_evaluation: VisualSceneEvaluationEvidence = VisualSceneEvaluationEvidence(),
    val scene_attempts: List<VisualSceneGenerationAttempt> = emptyList(),
    val scene_correction_count: Int = 0,
    val scene_human_approval_required: Boolean = false,
    val scene_provider_outcome_unknown: Boolean = false,
    val scene_generation_warning: String = "",
    val auto_canon: Boolean = false,
)


@Serializable
data class VisualCharacterEvaluationEvidence(
    val verdict: String = "",
    val score: Int? = null,
    val identity_score: Int? = null,
    val canon_score: Int? = null,
    val perspective_score: Int? = null,
    val issues: List<String> = emptyList(),
    val correction_instruction: String = "",
    val model: String = "",
)

@Serializable
data class VisualCharacterMasterRosterCounts(
    @SerialName("APPROVED") val approved: Int = 0,
    @SerialName("CANDIDATE") val candidate: Int = 0,
    @SerialName("READY") val ready: Int = 0,
    @SerialName("BLOCKED") val blocked: Int = 0,
)

@Serializable
data class VisualCharacterMasterRosterItem(
    val character_id: String = "",
    val canonical_name: String = "",
    val authority: String = "",
    val state: String = "",
    val asset_id: String = "",
    val sha256: String = "",
    val evidence_source_refs: List<String> = emptyList(),
    val error_code: String = "",
)

@Serializable
data class VisualCharacterMasterRoster(
    val schema: String = "",
    val project_id: String = "",
    val items: List<VisualCharacterMasterRosterItem> = emptyList(),
    val counts: VisualCharacterMasterRosterCounts = VisualCharacterMasterRosterCounts(),
    val generation_required: Int = 0,
    val human_approval_required: Boolean = true,
    val auto_canon: Boolean = false,
)

@Serializable
data class VisualCharacterBatchItem(
    val perspective: String = "",
    val sequence: Int = 0,
    val status: String = "",
    val candidate_asset_id: String = "",
    val candidate_sha256: String = "",
    val correction_count: Int = 0,
    val error_code: String = "",
    val job_id: String = "",
    val job_status: String = "",
    val job_error: String = "",
    val generation_attempts: Int = 0,
    val evaluation_status: String = "",
    val evaluation_score: Int? = null,
    val evaluation_model: String = "",
    val evaluation: VisualCharacterEvaluationEvidence = VisualCharacterEvaluationEvidence(),
    val correction_instruction: String = "",
    val created_utc: String = "",
    val updated_utc: String = "",
)

@Serializable
data class VisualCharacterBatch(
    val project_id: String = "",
    val batch_id: String = "",
    val character_id: String = "",
    val master_asset_id: String = "",
    val master_sha256: String = "",
    val requested_perspectives: List<String> = emptyList(),
    val requested_by: String = "",
    val adjustment: String = "",
    val mode: String = "quality",
    val max_corrections: Int = 2,
    val generation_budget: Int = 0,
    val generation_used: Int = 0,
    val status: String = "",
    val created_utc: String = "",
    val updated_utc: String = "",
    val completed_utc: String = "",
    val pending_count: Int = 0,
    val candidate_count: Int = 0,
    val approved_count: Int = 0,
    val blocked_count: Int = 0,
    val items: List<VisualCharacterBatchItem> = emptyList(),
)

@Serializable
data class VisualCharacterBatchResponse(
    val schema: String = "",
    val batch: VisualCharacterBatch? = null,
    val queued_job_ids: List<String> = emptyList(),
    val generated_asset_ids: List<String> = emptyList(),
    val async_execution: Boolean = false,
    val semantic_evaluation: Boolean = false,
    val max_corrections: Int = 0,
    val generation_budget: Int = 0,
    val human_approval_required: Boolean = true,
    val auto_canon: Boolean = false,
)

@Serializable
data class VisualCharacterBatchApprovedAsset(
    val perspective: String = "",
    val asset_id: String = "",
    val sha256: String = "",
    val visual_revision: Int = 0,
)

@Serializable
data class VisualCharacterBatchApprovalFailure(
    val perspective: String = "",
    val asset_id: String = "",
    val error: String = "",
)

@Serializable
data class VisualCharacterBatchApprovalResponse(
    val schema: String = "",
    val batch: VisualCharacterBatch? = null,
    val approved: List<VisualCharacterBatchApprovedAsset> = emptyList(),
    val failed: List<VisualCharacterBatchApprovalFailure> = emptyList(),
    val partial: Boolean = false,
)


@Serializable
data class VisualSceneApplicability(
    val status: String = "",
    val reasons: List<String> = emptyList(),
)

@Serializable
data class VisualSceneReference(
    val asset_id: String = "",
    val asset_sha256: String = "",
    val role: String = "",
    val authority: String = "",
    val selection: String = "",
    val kind: String = "",
    val perspective: String = "",
    val visual_revision: Int = 0,
    val pack_id: String = "",
    val pack_revision: Int = 0,
    val applicability: VisualSceneApplicability = VisualSceneApplicability(),
)

@Serializable
data class VisualSceneUnresolvedRole(
    val role: String = "",
    val reason: String = "",
    val candidates: List<VisualSceneReference> = emptyList(),
)

@Serializable
data class VisualSceneSelectionExplanation(
    val role: String = "",
    val asset_id: String = "",
    val reason: String = "",
    val authority: String = "",
)

@Serializable
data class VisualSceneReferenceManifest(
    val max_references: Int = 0,
    val references: List<VisualSceneReference> = emptyList(),
    val textual_fallback_roles: List<String> = emptyList(),
    val unresolved_roles: List<VisualSceneUnresolvedRole> = emptyList(),
    val selection_explanations: List<VisualSceneSelectionExplanation> = emptyList(),
    val approval_policy: String = "",
)

@Serializable
data class VisualSceneNarrativeBoundary(
    val kind: String = "",
    val revision_id: String = "",
    val sha256: String = "",
    val brief_revision_id: String = "",
)

@Serializable
data class VisualSceneSourcePin(
    val revision: String = "",
    val snapshot_id: String = "",
)

@Serializable
data class VisualSceneNarrativeEvidence(
    val evidence_id: String = "",
    val kind: String = "",
    val text: String = "",
    val sha256: String = "",
    val canon_status: String = "",
    val authority: String = "",
    val title: String = "",
    val heading: String = "",
    val chapter_number: Int? = null,
    val chunk_id: String = "",
    val document_id: String = "",
    val request_text: String = "",
)

@Serializable
data class VisualSceneDirectorCandidate(
    val evidence_id: String = "",
    val kind: String = "",
    val title: String = "",
    val heading: String = "",
    val chapter_id: String = "",
    val chapter_number: Int? = null,
    val canon_status: String = "",
    val authority: String = "",
    val score: Int = 0,
    val excerpt: String = "",
)

@Serializable
data class VisualSceneEvaluationEvidence(
    val verdict: String = "",
    val score: Int? = null,
    val narrative_score: Int? = null,
    val identity_score: Int? = null,
    val composition_score: Int? = null,
    val issues: List<String> = emptyList(),
    val correction_instruction: String = "",
    val model: String = "",
)

@Serializable
data class VisualSceneGenerationAttempt(
    val attempt_number: Int = 0,
    val correction_number: Int = 0,
    val asset_id: String = "",
    val sha256: String = "",
    val storage_state: String = "",
    val evaluation: VisualSceneEvaluationEvidence = VisualSceneEvaluationEvidence(),
)

@Serializable
data class VisualSceneSelection(
    val character_ids: List<String> = emptyList(),
    val location_id: String = "",
    val era: String = "",
    val state: String = "",
    val outfit_ids: Map<String, String> = emptyMap(),
    val time: String = "",
    val weather: String = "",
    val composition: String = "",
    val instruction: String = "",
    val reference_perspectives: List<String> = emptyList(),
)

@Serializable
data class VisualSceneContext(
    val schema: String = "",
    val context_id: String = "",
    val context_hash: String = "",
    val project_id: String = "",
    val chapter_id: String = "",
    val scene_id: String = "",
    val aggregate_version: Int = 0,
    val narrative_boundary: VisualSceneNarrativeBoundary = VisualSceneNarrativeBoundary(),
    val source: VisualSceneSourcePin = VisualSceneSourcePin(),
    val narrative_evidence: VisualSceneNarrativeEvidence = VisualSceneNarrativeEvidence(),
    val selection: VisualSceneSelection = VisualSceneSelection(),
    val reference_manifest: VisualSceneReferenceManifest = VisualSceneReferenceManifest(),
    val exploratory: Boolean = false,
    val generation_ready: Boolean = false,
)

@Serializable
data class VisualSceneContextResponse(
    val schema: String = "",
    val context: VisualSceneContext = VisualSceneContext(),
)

@Serializable
data class VisualSceneDirectorResponse(
    val schema: String = "",
    val project_id: String = "",
    val request_text: String = "",
    val status: String = "",
    val resolver_model: String = "",
    val candidates: List<VisualSceneDirectorCandidate> = emptyList(),
    val context: VisualSceneContext? = null,
    val recommended_engine: String = "cloud",
    val generation_max_corrections: Int = 0,
    val human_selection_required: Boolean = false,
    val missing_story_fact: Boolean = false,
    val selected_evidence_id: String = "",
)

@Serializable
data class VisualSceneDirectorJobAttempt(
    val attempt_number: Int = 0,
    val status: String = "",
    val asset_id: String = "",
    val sha256: String = "",
    val storage_state: String = "",
    val provider: String = "",
    val model: String = "",
    val evaluation: VisualSceneEvaluationEvidence = VisualSceneEvaluationEvidence(),
    val correction_instruction: String = "",
)

@Serializable
data class VisualSceneDirectorJob(
    val job_id: String = "",
    val project_id: String = "",
    val context_id: String = "",
    val context_hash: String = "",
    val status: String = "",
    val requested_by: String = "",
    val final_asset_id: String = "",
    val final_asset_sha256: String = "",
    val storage_state: String = "",
    val evaluation: VisualSceneEvaluationEvidence = VisualSceneEvaluationEvidence(),
    val correction_count: Int = 0,
    val provider_outcome_unknown: Boolean = false,
    val last_error: String = "",
    val warning: String = "",
    val created_utc: String = "",
    val updated_utc: String = "",
    val attempts: List<VisualSceneDirectorJobAttempt> = emptyList(),
)

@Serializable
data class VisualSceneDirectorJobResponse(
    val schema: String = "",
    val job: VisualSceneDirectorJob? = null,
    val result: VisualStudioGeneratedImage? = null,
    val async_execution: Boolean = false,
    val human_approval_required: Boolean = true,
    val auto_canon: Boolean = false,
    val created: Boolean = false,
)

@Serializable
data class VisualSceneGenerationEngineCapability(
    val state: String = "unavailable",
    val reason: String = "",
    val pc_configured: Boolean = false,
    val reference_policy: String = "",
    val max_reference_count: Int = 0,
    val exact_reference_count: Int = 0,
    val profile: String = "",
    val model: String = "",
    val reference_materialization: String = "",
    val fallback_allowed: Boolean = false,
    val modes: List<String> = emptyList(),
    val models: List<String> = emptyList(),
)

@Serializable
data class VisualSceneGenerationCapabilities(
    val schema: String = "",
    val default_engine: String = "cloud",
    val no_silent_cross_engine_fallback: Boolean = true,
    val engines: Map<String, VisualSceneGenerationEngineCapability> = emptyMap(),
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

private suspend fun JarvisAppSession.visualStudioObjectPost(
    path: String,
    body: JsonObject,
): Result<JsonObject> = withContext(Dispatchers.IO) {
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
        visualStudioJson.parseToJsonElement(response.second).jsonObject
    }
}

private fun JsonObject.stringValue(key: String): String =
    this[key]?.jsonPrimitive?.contentOrNull.orEmpty()

private fun JsonObject.intValue(key: String, fallback: Int = 0): Int =
    stringValue(key).toIntOrNull() ?: fallback

private fun JsonObject.nullableIntValue(key: String): Int? =
    this[key]?.jsonPrimitive?.intOrNull

private fun JsonObject.longValue(key: String, fallback: Long = 0L): Long =
    stringValue(key).toLongOrNull() ?: fallback

private fun JsonObject.booleanValue(key: String, fallback: Boolean = false): Boolean =
    this[key]?.jsonPrimitive?.booleanOrNull ?: fallback

private fun JsonObject.stringList(key: String): List<String> =
    runCatching {
        this[key]?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.contentOrNull }
            .orEmpty()
    }.getOrDefault(emptyList())

private fun parseVisualStudioStorage(value: JsonObject?): VisualStudioStorage =
    if (value == null) {
        VisualStudioStorage()
    } else {
        VisualStudioStorage(
            backend = value.stringValue("backend"),
            state = value.stringValue("state"),
            drive_file_id = value.stringValue("drive_file_id"),
            drive_parent_id = value.stringValue("drive_parent_id"),
        )
    }

private fun parseVisualStudioStorageError(value: JsonObject?): VisualStudioStorageError? =
    value?.let {
        VisualStudioStorageError(
            code = it.stringValue("code"),
            message = it.stringValue("message"),
            retry_required = it.booleanValue("retry_required"),
        )
    }

private fun parseVisualStudioAsset(value: JsonObject): VisualStudioAsset =
    VisualStudioAsset(
        asset_id = value.stringValue("asset_id"),
        project_id = value.stringValue("project_id"),
        kind = value.stringValue("kind"),
        status = value.stringValue("status"),
        source = value.stringValue("source"),
        sha256 = value.stringValue("sha256"),
        mime_type = value.stringValue("mime_type").ifBlank { "image/png" },
        size_bytes = value.longValue("size_bytes"),
        character_ids = value.stringList("character_ids"),
        chapter_ids = value.stringList("chapter_ids"),
        event_ids = value.stringList("event_ids"),
        location_ids = value.stringList("location_ids"),
        scene_ids = value.stringList("scene_ids"),
        perspective = value.stringValue("perspective").ifBlank { "custom" },
        visual_revision = value.intValue("visual_revision"),
        alt = value.stringValue("alt"),
        provenance = value["provenance"] as? JsonObject ?: JsonObject(emptyMap()),
        created_by = value.stringValue("created_by"),
        approved_by = value.stringValue("approved_by"),
        created_utc = value.stringValue("created_utc"),
        approved_utc = value.stringValue("approved_utc"),
        parent_asset_id = value.stringValue("parent_asset_id"),
        parent_sha256 = value.stringValue("parent_sha256"),
        derivation = value.stringValue("derivation"),
        storage = parseVisualStudioStorage(value["storage"] as? JsonObject),
        storage_error = parseVisualStudioStorageError(value["storage_error"] as? JsonObject),
    )

private fun parseVisualSceneEvaluation(value: JsonObject?): VisualSceneEvaluationEvidence =
    if (value == null) {
        VisualSceneEvaluationEvidence()
    } else {
        VisualSceneEvaluationEvidence(
            verdict = value.stringValue("verdict"),
            score = value.nullableIntValue("score"),
            narrative_score = value.nullableIntValue("narrative_score"),
            identity_score = value.nullableIntValue("identity_score"),
            composition_score = value.nullableIntValue("composition_score"),
            issues = value.stringList("issues"),
            correction_instruction = value.stringValue("correction_instruction"),
            model = value.stringValue("model"),
        )
    }

private fun parseVisualSceneAttempts(value: JsonObject): List<VisualSceneGenerationAttempt> =
    runCatching {
        value["scene_attempts"]?.jsonArray?.mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            VisualSceneGenerationAttempt(
                attempt_number = item.intValue("attempt_number"),
                correction_number = item.intValue("correction_number"),
                asset_id = item.stringValue("asset_id"),
                sha256 = item.stringValue("sha256"),
                storage_state = item.stringValue("storage_state"),
                evaluation = parseVisualSceneEvaluation(
                    item["evaluation"] as? JsonObject,
                ),
            )
        }.orEmpty()
    }.getOrDefault(emptyList())

private fun parseVisualStudioGeneratedImage(value: JsonObject): VisualStudioGeneratedImage {
    val assetObject = value["visual_asset"] as? JsonObject
    return VisualStudioGeneratedImage(
        schema = value.stringValue("schema"),
        mime_type = value.stringValue("mime_type").ifBlank { "image/png" },
        data_base64 = value.stringValue("data_base64"),
        size_bytes = value.longValue("size_bytes"),
        provider = value.stringValue("provider"),
        model = value.stringValue("model"),
        route = value.stringValue("route"),
        requested_mode = value.stringValue("requested_mode"),
        requested_model = value.stringValue("requested_model").ifBlank { null },
        fallback_used = value.booleanValue("fallback_used"),
        attempt_count = value.intValue("attempt_count", 1),
        duration_ms = value.longValue("duration_ms"),
        reference_count = value.intValue("reference_count"),
        vps_persistence = value.stringValue("vps_persistence"),
        storage_retry_required = value.booleanValue("storage_retry_required"),
        scene_context_id = value.stringValue("scene_context_id"),
        scene_context_hash = value.stringValue("scene_context_hash"),
        visual_asset = assetObject?.let(::parseVisualStudioAsset),
        scene_evaluation = parseVisualSceneEvaluation(
            value["scene_evaluation"] as? JsonObject,
        ),
        scene_attempts = parseVisualSceneAttempts(value),
        scene_correction_count = value.intValue("scene_correction_count"),
        scene_human_approval_required = value.booleanValue(
            "scene_human_approval_required",
        ),
        scene_provider_outcome_unknown = value.booleanValue(
            "scene_provider_outcome_unknown",
        ),
        scene_generation_warning = value.stringValue(
            "scene_generation_warning",
        ),
        auto_canon = value.booleanValue("auto_canon"),
    )
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


private fun visualStringArray(values: List<String>) =
    buildJsonArray { values.filter { it.isNotBlank() }.forEach { add(JsonPrimitive(it.trim())) } }


suspend fun JarvisAppSession.visualProjectStyle(
    projectId: String,
): Result<VisualProjectStyleResponse> =
    visualStudioPost(
        "/api/app/writing-room/v2/visual/style/profile",
        buildJsonObject { put("project_id", projectId) },
    )


suspend fun JarvisAppSession.visualUpdateProjectStyle(
    projectId: String,
    profile: VisualProjectStyleProfile,
): Result<VisualProjectStyleResponse> =
    visualStudioPost(
        "/api/app/writing-room/v2/visual/style/update",
        buildJsonObject {
            put("project_id", projectId)
            put("expected_revision", profile.revision)
            put(
                "profile",
                buildJsonObject {
                    put("style_name", profile.style_name.trim())
                    put("medium", profile.medium.trim())
                    put("realism", profile.realism.trim())
                    put("palette", profile.palette.trim())
                    put("lighting", profile.lighting.trim())
                    put("rendering", profile.rendering.trim())
                    put("positive_rules", visualStringArray(profile.positive_rules))
                    put("negative_rules", visualStringArray(profile.negative_rules))
                },
            )
        },
    )


suspend fun JarvisAppSession.visualCharacterDirection(
    projectId: String,
    characterId: String,
): Result<VisualCharacterDirectionResponse> =
    visualStudioPost(
        "/api/app/writing-room/v2/visual/characters/design-profile",
        buildJsonObject {
            put("project_id", projectId)
            put("character_id", characterId)
        },
    )


suspend fun JarvisAppSession.visualUpdateCharacterDirection(
    projectId: String,
    characterId: String,
    profile: VisualCharacterDirectionProfile,
): Result<VisualCharacterDirectionResponse> =
    visualStudioPost(
        "/api/app/writing-room/v2/visual/characters/design-profile/update",
        buildJsonObject {
            put("project_id", projectId)
            put("character_id", characterId)
            put("expected_revision", profile.revision)
            put(
                "profile",
                buildJsonObject {
                    put("apparent_age", profile.apparent_age.trim())
                    put("build", profile.build.trim())
                    put("height", profile.height.trim())
                    put("skin", profile.skin.trim())
                    put("face", profile.face.trim())
                    put("eyes", profile.eyes.trim())
                    put("hair", profile.hair.trim())
                    put("base_outfit", profile.base_outfit.trim())
                    put("armor", profile.armor.trim())
                    put("accessories", profile.accessories.trim())
                    put("weapons", profile.weapons.trim())
                    put("dominant_colors", profile.dominant_colors.trim())
                    put("aura", profile.aura.trim())
                    put("notes", profile.notes.trim())
                    put("required_traits", visualStringArray(profile.required_traits))
                    put("forbidden_traits", visualStringArray(profile.forbidden_traits))
                },
            )
        },
    )


suspend fun JarvisAppSession.visualCharacterMasterRosterStatus(
    projectId: String,
): Result<VisualCharacterMasterRoster> =
    visualStudioPost(
        "/api/app/writing-room/v2/visual/characters/masters/status",
        buildJsonObject {
            put("project_id", projectId)
        },
    )


suspend fun JarvisAppSession.visualCharacterBatchStatus(
    projectId: String,
    characterId: String,
    batchId: String = "",
): Result<VisualCharacterBatchResponse> =
    visualStudioPost(
        "/api/app/writing-room/v2/visual/characters/batch/status",
        buildJsonObject {
            put("project_id", projectId)
            put("character_id", characterId)
            if (batchId.isNotBlank()) put("batch_id", batchId)
        },
    )

suspend fun JarvisAppSession.visualCompleteCharacterViews(
    projectId: String,
    characterId: String,
    perspectives: List<String>,
    adjustment: String = "",
    mode: String = "quality",
    batchId: String = "",
): Result<VisualCharacterBatchResponse> =
    visualStudioPost(
        "/api/app/writing-room/v2/visual/characters/complete-views",
        buildJsonObject {
            put("project_id", projectId)
            put("character_id", characterId)
            put(
                "perspectives",
                buildJsonArray {
                    perspectives.forEach { add(JsonPrimitive(it)) }
                },
            )
            put("adjustment", adjustment.trim())
            put("mode", mode)
            if (batchId.isNotBlank()) put("batch_id", batchId)
        },
    )

suspend fun JarvisAppSession.visualApproveCharacterViewBatch(
    projectId: String,
    characterId: String,
    batchId: String,
    assetIds: List<String> = emptyList(),
): Result<VisualCharacterBatchApprovalResponse> =
    visualStudioPost(
        "/api/app/writing-room/v2/visual/characters/batch/approve",
        buildJsonObject {
            put("project_id", projectId)
            put("character_id", characterId)
            put("batch_id", batchId)
            if (assetIds.isNotEmpty()) {
                put(
                    "asset_ids",
                    buildJsonArray {
                        assetIds.forEach { add(JsonPrimitive(it)) }
                    },
                )
            }
        },
    )

suspend fun JarvisAppSession.visualLocationDetail(
    projectId: String,
    locationId: String,
): Result<VisualStudioLocationDetail> =
    visualStudioPost(
        "/api/app/writing-room/v2/visual/locations/detail",
        buildJsonObject {
            put("project_id", projectId)
            put("location_id", locationId)
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

suspend fun JarvisAppSession.visualLocationAssetList(
    projectId: String,
    locationId: String,
    status: String? = null,
): Result<VisualStudioAssetList> =
    visualStudioPost(
        "/api/app/writing-room/visual-assets/list",
        buildJsonObject {
            put("project_id", projectId)
            put("location_id", locationId)
            if (!status.isNullOrBlank()) put("status", status)
        },
    )

suspend fun JarvisAppSession.visualLocationAssetIngest(
    projectId: String,
    imageBase64: String,
    mimeType: String,
    kind: String,
    source: String,
    locationId: String,
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
            put("location_ids", buildJsonArray { add(JsonPrimitive(locationId)) })
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

suspend fun JarvisAppSession.visualLocationReferencePackCreate(
    projectId: String,
    locationId: String,
    masterAssetId: String,
    masterSha256: String,
    parentPackId: String = "",
): Result<VisualStudioLocationPackResponse> =
    visualStudioPost(
        "/api/app/writing-room/v2/visual/location-reference-packs/create",
        buildJsonObject {
            put("project_id", projectId)
            put("location_id", locationId)
            put("master_asset_id", masterAssetId)
            put("master_sha256", masterSha256)
            if (parentPackId.isNotBlank()) put("parent_pack_id", parentPackId)
        },
    )

suspend fun JarvisAppSession.visualLocationReferencePackAddSlot(
    projectId: String,
    packId: String,
    slotKey: String,
    assetId: String,
    assetSha256: String,
    required: Boolean = false,
): Result<VisualStudioLocationPackResponse> =
    visualStudioPost(
        "/api/app/writing-room/v2/visual/location-reference-packs/slot/add",
        buildJsonObject {
            put("project_id", projectId)
            put("pack_id", packId)
            put("slot_key", slotKey)
            put("asset_id", assetId)
            put("asset_sha256", assetSha256)
            put("required", required)
        },
    )

suspend fun JarvisAppSession.visualLocationReferencePackPrepare(
    projectId: String,
    packId: String,
): Result<VisualStudioLocationPackResponse> =
    visualStudioPost(
        "/api/app/writing-room/v2/visual/location-reference-packs/prepare",
        buildJsonObject {
            put("project_id", projectId)
            put("pack_id", packId)
        },
    )

suspend fun JarvisAppSession.visualLocationReferencePackApprove(
    projectId: String,
    packId: String,
): Result<VisualStudioLocationPackResponse> =
    visualStudioPost(
        "/api/app/writing-room/v2/visual/location-reference-packs/approve",
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
    visualStudioObjectPost(
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
    ).mapCatching { raw ->
        val reply = parseVisualStudioGeneratedImage(raw)
        if (reply.data_base64.isBlank() || reply.mime_type.isBlank()) {
            throw TransportException("visual generation returned no image")
        }
        if (reply.visual_asset == null || reply.visual_asset.asset_id.isBlank()) {
            throw TransportException("visual generation returned no durable candidate")
        }
        reply
    }

suspend fun JarvisAppSession.generateLocationVisualAssetImage(
    projectId: String,
    locationId: String,
    prompt: String,
    kind: String,
    perspective: String,
    mode: String = "quality",
    model: String? = null,
    aspectRatio: String = "landscape",
    referencePerspectives: List<String> = emptyList(),
    referencesPerLocation: Int = 2,
    parentAssetId: String = "",
    parentSha256: String = "",
    derivation: String = "",
    assetId: String? = null,
): Result<VisualStudioGeneratedImage> =
    visualStudioObjectPost(
        "/api/app/images/generations",
        buildJsonObject {
            put("project_id", projectId)
            put("prompt", prompt.trim())
            put("mode", mode)
            put("aspect_ratio", aspectRatio)
            if (!model.isNullOrBlank()) put("model", model)
            put("visual_asset", buildJsonObject {
                put("surface", "location_creator")
                put("project_id", projectId)
                put("kind", kind)
                put("location_ids", buildJsonArray { add(JsonPrimitive(locationId)) })
                put("perspective", perspective)
                if (referencePerspectives.isNotEmpty()) {
                    put(
                        "reference_perspectives",
                        buildJsonArray {
                            referencePerspectives.forEach { add(JsonPrimitive(it)) }
                        },
                    )
                }
                put("references_per_location", referencesPerLocation.coerceIn(1, 4))
                if (!assetId.isNullOrBlank()) put("asset_id", assetId)
                if (parentAssetId.isNotBlank()) put("parent_asset_id", parentAssetId)
                if (parentSha256.isNotBlank()) put("parent_sha256", parentSha256)
                if (derivation.isNotBlank()) put("derivation", derivation)
            })
        },
    ).mapCatching { raw ->
        val reply = parseVisualStudioGeneratedImage(raw)
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
    visualStudioObjectPost(
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
    ).mapCatching { raw ->
        val reply = parseVisualStudioGeneratedImage(raw)
        if (reply.data_base64.isBlank() || reply.mime_type.isBlank()) {
            throw TransportException("visual edit returned no image")
        }
        if (reply.visual_asset == null || reply.visual_asset.asset_id.isBlank()) {
            throw TransportException("visual edit returned no durable candidate")
        }
        reply
    }

suspend fun JarvisAppSession.editLocationVisualAssetImage(
    imageBase64: String,
    mimeType: String,
    projectId: String,
    locationId: String,
    instruction: String,
    kind: String,
    perspective: String,
    parentAssetId: String,
    parentSha256: String,
    derivation: String = "EDIT",
    mode: String = "quality",
    model: String? = null,
    aspectRatio: String = "landscape",
): Result<VisualStudioGeneratedImage> =
    visualStudioObjectPost(
        "/api/app/images/edits",
        buildJsonObject {
            put("project_id", projectId)
            put("image_base64", imageBase64)
            put("mime_type", mimeType)
            put("instruction", instruction.trim())
            put("mode", mode)
            put("preserve_identity", "high")
            put("aspect_ratio", aspectRatio)
            if (!model.isNullOrBlank()) put("model", model)
            put("visual_asset", buildJsonObject {
                put("surface", "location_creator")
                put("project_id", projectId)
                put("kind", kind)
                put("location_ids", buildJsonArray { add(JsonPrimitive(locationId)) })
                put("perspective", perspective)
                put("parent_asset_id", parentAssetId)
                put("parent_sha256", parentSha256)
                put("derivation", derivation)
            })
        },
    ).mapCatching { raw ->
        val reply = parseVisualStudioGeneratedImage(raw)
        if (reply.data_base64.isBlank() || reply.mime_type.isBlank()) {
            throw TransportException("visual edit returned no image")
        }
        if (reply.visual_asset == null || reply.visual_asset.asset_id.isBlank()) {
            throw TransportException("visual edit returned no durable candidate")
        }
        reply
    }


suspend fun JarvisAppSession.editSceneVisualAssetImage(
    imageBase64: String,
    mimeType: String,
    projectId: String,
    instruction: String,
    parentAsset: VisualStudioAsset,
    mode: String = "quality",
    model: String? = null,
    preserveIdentity: String = "high",
    aspectRatio: String = "landscape",
): Result<VisualStudioGeneratedImage> {
    if (
        parentAsset.project_id != projectId ||
        parentAsset.kind != "SCENE_ART" ||
        parentAsset.status != "APPROVED" ||
        parentAsset.asset_id.isBlank() ||
        parentAsset.sha256.isBlank()
    ) {
        return Result.failure(
            TransportException("scene edit requires an approved SCENE_ART parent from the same project"),
        )
    }
    return visualStudioObjectPost(
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
                put("surface", "scene_builder")
                put("project_id", projectId)
                put("kind", "SCENE_ART")
                put(
                    "character_ids",
                    buildJsonArray {
                        parentAsset.character_ids.forEach { add(JsonPrimitive(it)) }
                    },
                )
                put(
                    "chapter_ids",
                    buildJsonArray {
                        parentAsset.chapter_ids.forEach { add(JsonPrimitive(it)) }
                    },
                )
                put(
                    "scene_ids",
                    buildJsonArray {
                        parentAsset.scene_ids.forEach { add(JsonPrimitive(it)) }
                    },
                )
                put(
                    "event_ids",
                    buildJsonArray {
                        parentAsset.event_ids.forEach { add(JsonPrimitive(it)) }
                    },
                )
                put(
                    "location_ids",
                    buildJsonArray {
                        parentAsset.location_ids.forEach { add(JsonPrimitive(it)) }
                    },
                )
                put("perspective", parentAsset.perspective.ifBlank { "scene" })
                put("parent_asset_id", parentAsset.asset_id)
                put("parent_sha256", parentAsset.sha256)
                put("derivation", "EDIT")
            })
        },
    ).mapCatching { raw ->
        val reply = parseVisualStudioGeneratedImage(raw)
        val asset = reply.visual_asset
        if (reply.data_base64.isBlank() || reply.mime_type.isBlank()) {
            throw TransportException("scene visual edit returned no image")
        }
        if (asset == null || asset.asset_id.isBlank()) {
            throw TransportException("scene visual edit returned no durable candidate")
        }
        if (
            asset.project_id != projectId ||
            asset.kind != "SCENE_ART" ||
            asset.parent_asset_id != parentAsset.asset_id ||
            asset.parent_sha256.uppercase() != parentAsset.sha256.uppercase()
        ) {
            throw TransportException("scene visual edit lineage mismatch")
        }
        reply
    }
}


suspend fun JarvisAppSession.visualSceneDirectorResolve(
    projectId: String,
    requestText: String,
    selectedEvidenceId: String = "",
): Result<VisualSceneDirectorResponse> =
    visualStudioPost(
        "/api/app/writing-room/v2/visual/scenes/director/resolve",
        buildJsonObject {
            put("project_id", projectId)
            put("request_text", requestText.trim())
            if (selectedEvidenceId.isNotBlank()) {
                put("selected_evidence_id", selectedEvidenceId)
            }
        },
    )

suspend fun JarvisAppSession.visualSceneDirectorGenerate(
    projectId: String,
    contextId: String,
    contextHash: String,
    engine: String = "cloud",
    mode: String = "quality",
    model: String? = null,
    aspectRatio: String = "landscape",
    maxCorrections: Int = 2,
): Result<VisualSceneDirectorJobResponse> =
    visualStudioPost(
        "/api/app/writing-room/v2/visual/scenes/director/generate",
        buildJsonObject {
            put("project_id", projectId)
            put("context_id", contextId)
            put("context_hash", contextHash)
            put("engine", engine)
            put("mode", mode)
            put("aspect_ratio", aspectRatio)
            put("max_corrections", maxCorrections.coerceIn(0, 2))
            if (!model.isNullOrBlank()) put("model", model)
        },
    )

suspend fun JarvisAppSession.visualSceneDirectorJobStatus(
    projectId: String,
    jobId: String = "",
    contextId: String = "",
): Result<VisualSceneDirectorJobResponse> =
    visualStudioPost(
        "/api/app/writing-room/v2/visual/scenes/director/job/status",
        buildJsonObject {
            put("project_id", projectId)
            if (jobId.isNotBlank()) put("job_id", jobId)
            if (contextId.isNotBlank()) put("context_id", contextId)
        },
    )

suspend fun JarvisAppSession.visualSceneContextPreview(
    projectId: String,
    chapterId: String,
    sceneId: String,
    expectedAggregateVersion: Int,
    briefRevisionId: String = "",
    draftRevisionId: String = "",
    characterIds: List<String> = emptyList(),
    locationId: String = "",
    era: String = "",
    state: String = "",
    outfitIds: Map<String, String> = emptyMap(),
    time: String = "",
    weather: String = "",
    composition: String = "",
    instruction: String = "",
    referencePerspectives: List<String> = emptyList(),
    maxReferences: Int = 8,
): Result<VisualSceneContextResponse> =
    visualStudioPost(
        "/api/app/writing-room/v2/visual/scenes/context/preview",
        buildJsonObject {
            put("project_id", projectId)
            put("chapter_id", chapterId)
            put("scene_id", sceneId)
            put("expected_aggregate_version", expectedAggregateVersion)
            if (briefRevisionId.isNotBlank()) put("brief_revision_id", briefRevisionId)
            if (draftRevisionId.isNotBlank()) put("draft_revision_id", draftRevisionId)
            put(
                "character_ids",
                buildJsonArray {
                    characterIds.forEach { add(JsonPrimitive(it)) }
                },
            )
            if (locationId.isNotBlank()) put("location_id", locationId)
            if (era.isNotBlank()) put("era", era)
            if (state.isNotBlank()) put("state", state)
            put(
                "outfit_ids",
                buildJsonObject {
                    outfitIds.forEach { (subjectId, outfitId) ->
                        if (subjectId.isNotBlank() && outfitId.isNotBlank()) {
                            put(subjectId, outfitId)
                        }
                    }
                },
            )
            if (time.isNotBlank()) put("time", time)
            if (weather.isNotBlank()) put("weather", weather)
            if (composition.isNotBlank()) put("composition", composition)
            if (instruction.isNotBlank()) put("instruction", instruction)
            if (referencePerspectives.isNotEmpty()) {
                put(
                    "reference_perspectives",
                    buildJsonArray {
                        referencePerspectives.forEach { add(JsonPrimitive(it)) }
                    },
                )
            }
            put("max_references", maxReferences.coerceIn(1, 16))
        },
    )

suspend fun JarvisAppSession.visualSceneGenerationCapabilities(
    projectId: String,
): Result<VisualSceneGenerationCapabilities> =
    visualStudioPost(
        "/api/app/writing-room/v2/visual/scenes/capabilities",
        buildJsonObject {
            put("project_id", projectId)
        },
    )

suspend fun JarvisAppSession.visualSceneContextGet(
    projectId: String,
    contextId: String,
): Result<VisualSceneContextResponse> =
    visualStudioPost(
        "/api/app/writing-room/v2/visual/scenes/context/get",
        buildJsonObject {
            put("project_id", projectId)
            put("context_id", contextId)
        },
    )


suspend fun JarvisAppSession.generateSceneVisualAssetImage(
    projectId: String,
    contextId: String,
    contextHash: String,
    engine: String = "cloud",
    mode: String = "quality",
    model: String? = null,
    aspectRatio: String = "landscape",
    maxCorrections: Int = 0,
): Result<VisualStudioGeneratedImage> =
    visualStudioObjectPost(
        "/api/app/writing-room/v2/visual/scenes/generate",
        buildJsonObject {
            put("project_id", projectId)
            put("context_id", contextId)
            put("context_hash", contextHash)
            put("engine", engine)
            put("mode", mode)
            put("aspect_ratio", aspectRatio)
            put("max_corrections", maxCorrections.coerceIn(0, 2))
            if (!model.isNullOrBlank()) put("model", model)
        },
    ).mapCatching { raw ->
        val reply = parseVisualStudioGeneratedImage(raw)
        if (reply.data_base64.isBlank() || reply.mime_type.isBlank()) {
            throw TransportException("scene generation returned no image")
        }
        if (reply.visual_asset == null || reply.visual_asset.asset_id.isBlank()) {
            throw TransportException("scene generation returned no durable candidate")
        }
        if (
            reply.scene_context_id.isNotBlank() &&
            reply.scene_context_id != contextId
        ) {
            throw TransportException("scene generation context mismatch")
        }
        reply
    }

suspend fun JarvisAppSession.visualSceneAssetIngest(
    projectId: String,
    imageBase64: String,
    asset: VisualStudioAsset,
): Result<VisualStudioAssetResponse> =
    visualStudioPost(
        "/api/app/writing-room/visual-assets/ingest",
        buildJsonObject {
            put("project_id", projectId)
            put("image_base64", imageBase64)
            put("mime_type", asset.mime_type)
            put("kind", asset.kind)
            put("source", asset.source)
            put(
                "character_ids",
                buildJsonArray {
                    asset.character_ids.forEach { add(JsonPrimitive(it)) }
                },
            )
            put(
                "chapter_ids",
                buildJsonArray {
                    asset.chapter_ids.forEach { add(JsonPrimitive(it)) }
                },
            )
            put(
                "event_ids",
                buildJsonArray {
                    asset.event_ids.forEach { add(JsonPrimitive(it)) }
                },
            )
            put(
                "location_ids",
                buildJsonArray {
                    asset.location_ids.forEach { add(JsonPrimitive(it)) }
                },
            )
            put(
                "scene_ids",
                buildJsonArray {
                    asset.scene_ids.forEach { add(JsonPrimitive(it)) }
                },
            )
            put("perspective", asset.perspective)
            put("asset_id", asset.asset_id)
            put("provenance", asset.provenance)
            if (asset.parent_asset_id.isNotBlank()) {
                put("parent_asset_id", asset.parent_asset_id)
            }
            if (asset.parent_sha256.isNotBlank()) {
                put("parent_sha256", asset.parent_sha256)
            }
            if (asset.derivation.isNotBlank()) {
                put("derivation", asset.derivation)
            }
        },
    )
