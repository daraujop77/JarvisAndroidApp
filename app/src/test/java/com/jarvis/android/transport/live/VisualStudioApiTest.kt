package com.jarvis.android.transport.live

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class VisualStudioApiTest {

    private fun <T> Result<T>.orThrowStage(stage: String): T =
        getOrElse { error ->
            throw AssertionError(
                "$stage failed: ${error::class.simpleName}: ${error.message}",
                error,
            )
        }

    private lateinit var server: MockWebServer
    private val json = Json { ignoreUnknownKeys = true }
    private var replyWithWrongProject = false
    @Volatile private var rejectNextGenerationPreDispatch = false
    @Volatile private var preDispatchRejectionStatus = 409
    @Volatile private var preDispatchRejectionError = "visual_project_style_required"
    @Volatile private var nextGenerationStorageState = "stored"
    @Volatile private var returnUnknownAfterNextGeneration = false
    @Volatile private var loseNextGenerationResponseAfterDispatch = false
    @Volatile private var corruptNextGenerationBytes = false
    @Volatile private var corruptNextGenerationHash = false
    @Volatile private var rejectNextGenerationUnclaimed = false
    @Volatile private var nextGenerationCandidateError = false
    @Volatile private var voidFindsOutcomeUnknown = false
    @Volatile private var unknownRecoveryReply: Pair<Int, String>? = null
    // A real 1x1 PNG, so the test exercises SHA and magic-byte verification.
    private val fixturePng = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGNgZGT8DwABDQED5NTSmwAAAABJRU5ErkJggg=="
    private val fixtureSha256 = "2f41b02bc94868f7cdd1e503c7f592ee7673dbb155eac2f267a3d1abf64aea18"
    private val generationPosts = AtomicInteger()
    private val generationPostBodies = java.util.Collections.synchronizedList(mutableListOf<JsonObject>())
    private val recoveryReceipts = ConcurrentHashMap<String, String>()

    private fun canonicalJson(element: JsonElement): String = when (element) {
        is JsonObject -> element.entries.sortedBy { it.key }.joinToString(",", "{", "}") {
            "${JsonPrimitive(it.key)}:${canonicalJson(it.value)}"
        }
        is kotlinx.serialization.json.JsonArray -> element.joinToString(",", "[", "]") {
            canonicalJson(it)
        }
        else -> element.toString()
    }

    private fun paidFingerprint(body: JsonObject): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(canonicalJson(JsonObject(body.filterKeys { it != "request_id" }))
                .toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

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
                val body = runCatching {
                    json.parseToJsonElement(request.body.clone().readUtf8()).jsonObject
                }.getOrNull()
                if (request.path?.startsWith("/api/app/images/requests/") == true) {
                    if (request.method == "POST" && request.path.orEmpty().endsWith("/void")) {
                        return voidReply(
                            request.path.orEmpty().removeSuffix("/void").substringAfterLast('/'),
                            body ?: JsonObject(emptyMap()),
                        )
                    }
                    val requestId = request.path.orEmpty().substringAfterLast('/')
                    val receipt = recoveryReceipts[requestId]
                    return if (receipt != null) {
                        MockResponse().setBody(receipt)
                    } else {
                        unknownRecoveryReply?.let { (code, reply) ->
                            MockResponse().setResponseCode(code).setBody(reply)
                        } ?: MockResponse().setResponseCode(404)
                            .setBody("""{"error":"image_request_not_found"}""")
                    }
                }
                return when (request.path) {
                    "/api/app/images/generations" -> {
                        val paidBody = body ?: JsonObject(emptyMap())
                        val requestId = paidBody["request_id"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        val fingerprint = paidFingerprint(paidBody)
                        generationPosts.incrementAndGet()
                        generationPostBodies.add(paidBody)
                        if (rejectNextGenerationUnclaimed) {
                            rejectNextGenerationUnclaimed = false
                            return MockResponse().setResponseCode(413)
                                .setBody("""{"error":"image_request_too_large"}""")
                        }
                        val requestedProject = paidBody["project_id"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        if (rejectNextGenerationPreDispatch) {
                            rejectNextGenerationPreDispatch = false
                            val rejected = buildJsonObject {
                                put("error", preDispatchRejectionError)
                                put("request_id", requestId)
                                put("request_fingerprint", fingerprint)
                                put("state", "REJECTED_PRE_DISPATCH")
                                put("dispatch_state", "NOT_DISPATCHED")
                                put("retryable", true)
                            }
                            val receipt = buildJsonObject {
                                rejected.forEach { (key, value) -> put(key, value) }
                                put("project_id", requestedProject)
                                put("route", "/api/app/images/generations")
                            }.toString()
                            recoveryReceipts[requestId] = receipt
                            return MockResponse().setResponseCode(preDispatchRejectionStatus).setBody(rejected.toString())
                        }
                        if (returnUnknownAfterNextGeneration) {
                            returnUnknownAfterNextGeneration = false
                            val receipt = buildJsonObject {
                                put("request_id", requestId)
                                put("request_fingerprint", fingerprint)
                                put("project_id", requestedProject)
                                put("route", "/api/app/images/generations")
                                put("state", "OUTCOME_UNKNOWN")
                                put("dispatch_state", "OUTCOME_UNKNOWN")
                                put("retryable", false)
                            }.toString()
                            recoveryReceipts[requestId] = receipt
                            return MockResponse().setResponseCode(503)
                                .setBody("""{"error":"image_result_unknown"}""")
                        }
                        val visual = body?.get("visual_asset")?.jsonObject
                        val kind = visual?.get("kind")?.jsonPrimitive?.contentOrNull.orEmpty()
                        val perspective = visual?.get("perspective")?.jsonPrimitive?.contentOrNull.orEmpty()
                        val isMaster = kind == "PRIMARY_REFERENCE"
                        val assetId = if (isMaster) "va_master" else "va_left"
                        val invalidBytes = corruptNextGenerationBytes
                        val invalidHash = corruptNextGenerationHash
                        corruptNextGenerationBytes = false
                        corruptNextGenerationHash = false
                        val storageState = nextGenerationStorageState
                        nextGenerationStorageState = "stored"
                        val candidateUnpersisted = nextGenerationCandidateError
                        nextGenerationCandidateError = false
                        val imagePayload = if (invalidBytes) "aW1hZ2U=" else fixturePng
                        val digest = if (invalidHash) "A".repeat(64) else fixtureSha256
                        val refs = if (isMaster) 0 else 1
                        val response = buildJsonObject {
                            put("schema", "jarvis.image.generation.v2")
                            put("mime_type", "image/png")
                            put("data_base64", imagePayload)
                            put("size_bytes", if (invalidBytes) 5 else 70)
                            put("provider", "fixture-cloud")
                            put("model", "fixture-image")
                            put("route", "cloud_image_generation:fixture-cloud")
                            put("requested_mode", "quality")
                            put("fallback_used", false)
                            put("attempt_count", 1)
                            put("reference_count", refs)
                            put("vps_persistence", "visual_asset_candidate")
                            put("storage_retry_required", false)
                            put("visual_asset", buildJsonObject {
                                put("asset_id", assetId)
                                put(
                                    "project_id",
                                    if (replyWithWrongProject) "prj_other" else requestedProject.ifBlank { "prj_story" },
                                )
                                put("kind", kind)
                                put("status", "CANDIDATE")
                                put("source", "CLOUD_GENERATOR")
                                put("sha256", digest)
                                put("mime_type", "image/png")
                                put("size_bytes", if (invalidBytes) 5 else 70)
                                put(
                                    "character_ids",
                                    buildJsonArray { add(JsonPrimitive("character:alexander")) },
                                )
                                put("perspective", perspective)
                                put("storage", buildJsonObject {
                                    put("backend", "google_drive")
                                    put("state", storageState)
                                    if (storageState == "stored") {
                                        put("drive_file_id", "drive-$assetId")
                                        put("drive_parent_id", "characters")
                                    }
                                })
                            })
                        }.let { if (candidateUnpersisted) it.withoutPersistedCandidate() else it }
                        if (requestId.isNotBlank()) {
                            val completedReceipt = buildJsonObject {
                                response.forEach { (key, value) -> put(key, value) }
                                put("request_id", requestId)
                                put("request_fingerprint", fingerprint)
                                put("project_id", requestedProject)
                                put("route", "/api/app/images/generations")
                                put("state", "COMPLETED")
                                put("dispatch_state", "COMPLETED")
                                put("retryable", false)
                            }
                            recoveryReceipts[requestId] = completedReceipt.toString()
                        }
                        if (loseNextGenerationResponseAfterDispatch) {
                            loseNextGenerationResponseAfterDispatch = false
                            MockResponse().setResponseCode(503)
                                .setBody("""{"error":"image_result_unknown"}""")
                        } else {
                            MockResponse().setBody(response.toString())
                        }
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
                    "/api/app/writing-room/v2/visual/characters/masters/status" -> MockResponse().setBody(
                        """
                        {
                          "schema":"jarvis.visual-studio.character-master-roster.v1",
                          "project_id":"prj_story",
                          "items":[
                            {"character_id":"character:alexander","canonical_name":"Alexander","authority":"OFFICIAL_CANON","state":"APPROVED","asset_id":"va_master","sha256":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"},
                            {"character_id":"character:guardian","canonical_name":"Guardián","authority":"OFFICIAL_CANON","state":"READY","evidence_source_refs":["canon:guardian"]},
                            {"character_id":"character:unknown","canonical_name":"Unknown","authority":"REFERENCE","state":"BLOCKED","error_code":"appearance_evidence_required"}
                          ],
                          "counts":{"APPROVED":1,"CANDIDATE":0,"READY":1,"BLOCKED":1},
                          "generation_required":1,
                          "human_approval_required":true,
                          "auto_canon":false
                        }
                        """.trimIndent(),
                    )
                    "/api/app/writing-room/v2/visual/characters/complete-views" -> MockResponse().setBody(
                        """
                        {
                          "schema":"jarvis.visual-studio.character-batch.v2",
                          "batch":{
                            "project_id":"prj_story",
                            "batch_id":"vcb_1",
                            "character_id":"character:alexander",
                            "master_asset_id":"va_master",
                            "master_sha256":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                            "requested_perspectives":["left_profile","back"],
                            "requested_by":"owner-1",
                            "adjustment":"",
                            "mode":"quality",
                            "max_corrections":0,
                            "generation_budget":1,
                            "generation_used":0,
                            "status":"RUNNING",
                            "pending_count":1,
                            "candidate_count":0,
                            "approved_count":1,
                            "blocked_count":0,
                            "items":[
                              {
                                "perspective":"left_profile",
                                "sequence":1,
                                "status":"APPROVED_EXISTING",
                                "candidate_asset_id":"va_left",
                                "candidate_sha256":"BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB"
                              },
                              {
                                "perspective":"back",
                                "sequence":2,
                                "status":"PENDING",
                                "job_id":"vcj_back",
                                "job_status":"QUEUED",
                                "generation_attempts":0
                              }
                            ]
                          },
                          "queued_job_ids":["vcj_back"],
                          "generated_asset_ids":[],
                          "async_execution":true,
                          "semantic_evaluation":true,
                          "max_corrections":0,
                          "generation_budget":1,
                          "human_approval_required":true,
                          "auto_canon":false
                        }
                        """.trimIndent(),
                    )
                    "/api/app/writing-room/v2/visual/characters/batch/status" -> MockResponse().setBody(
                        """
                        {
                          "schema":"jarvis.visual-studio.character-batch.v2",
                          "batch":{
                            "project_id":"prj_story",
                            "batch_id":"vcb_1",
                            "character_id":"character:alexander",
                            "master_asset_id":"va_master",
                            "master_sha256":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                            "requested_perspectives":["left_profile","back"],
                            "status":"READY_FOR_REVIEW",
                            "pending_count":0,
                            "candidate_count":1,
                            "approved_count":1,
                            "blocked_count":0,
                            "items":[
                              {"perspective":"left_profile","sequence":1,"status":"APPROVED_EXISTING"},
                              {"perspective":"back","sequence":2,"status":"CANDIDATE","candidate_asset_id":"va_batch_back","candidate_sha256":"CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC","job_id":"vcj_back","job_status":"READY_FOR_REVIEW","generation_attempts":2,"correction_count":1,"evaluation_status":"PASS","evaluation_score":93,"evaluation_model":"test-vlm","evaluation":{"verdict":"PASS","score":93,"identity_score":95,"canon_score":94,"perspective_score":91,"issues":[],"correction_instruction":"","model":"test-vlm"}}
                            ]
                          }
                        }
                        """.trimIndent(),
                    )
                    "/api/app/writing-room/v2/visual/characters/batch/approve" -> MockResponse().setBody(
                        """
                        {
                          "schema":"jarvis.visual-studio.character-batch-approval.v1",
                          "batch":{
                            "project_id":"prj_story",
                            "batch_id":"vcb_1",
                            "character_id":"character:alexander",
                            "master_asset_id":"va_master",
                            "master_sha256":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                            "requested_perspectives":["left_profile","back"],
                            "status":"COMPLETED",
                            "pending_count":0,
                            "candidate_count":0,
                            "approved_count":2,
                            "blocked_count":0,
                            "items":[
                              {"perspective":"left_profile","sequence":1,"status":"APPROVED_EXISTING"},
                              {"perspective":"back","sequence":2,"status":"APPROVED","candidate_asset_id":"va_batch_back","candidate_sha256":"CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC"}
                            ]
                          },
                          "approved":[
                            {
                              "perspective":"back",
                              "asset_id":"va_batch_back",
                              "sha256":"CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC",
                              "visual_revision":1
                            }
                          ],
                          "failed":[],
                          "partial":false
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

    /** Mirrors the server: an unclaimed ID is tombstoned; a claimed ID returns its current state. */
    private fun voidReply(requestId: String, voidBody: JsonObject): MockResponse {
        if (voidFindsOutcomeUnknown) {
            voidFindsOutcomeUnknown = false
            recoveryReceipts[requestId] = paidReceiptJson(
                requestId, voidBody,
                state = "OUTCOME_UNKNOWN", dispatchState = "OUTCOME_UNKNOWN", retryable = false,
            )
        } else if (recoveryReceipts[requestId] == null) {
            recoveryReceipts[requestId] = paidReceiptJson(
                requestId, voidBody,
                state = "REJECTED_PRE_DISPATCH", dispatchState = "NOT_DISPATCHED", retryable = true,
                error = "image_request_voided_unclaimed",
            )
        }
        return MockResponse().setBody(recoveryReceipts.getValue(requestId))
    }

    private fun paidReceiptJson(
        requestId: String,
        requestBody: JsonObject,
        state: String,
        dispatchState: String,
        retryable: Boolean,
        error: String? = null,
    ): String = buildJsonObject {
        put("request_id", requestId)
        put("request_fingerprint", requestBody["request_fingerprint"]?.jsonPrimitive?.contentOrNull.orEmpty())
        put("project_id", requestBody["project_id"]?.jsonPrimitive?.contentOrNull.orEmpty())
        put("route", requestBody["route"]?.jsonPrimitive?.contentOrNull.orEmpty())
        put("state", state)
        put("dispatch_state", dispatchState)
        put("retryable", retryable)
        error?.let { put("error", it) }
    }.toString()

    private suspend fun JarvisAppSession.portraitRequest(prompt: String): Result<VisualStudioGeneratedImage> =
        generateVisualAssetImage(
            projectId = "prj_story",
            characterId = "character:alexander",
            prompt = prompt,
            kind = "PRIMARY_REFERENCE",
            perspective = "front",
        )

    /** Backend shape after the provider result was paid for but the VPS could not persist the candidate. */
    private fun JsonObject.withoutPersistedCandidate(): JsonObject = JsonObject(
        filterKeys { it != "visual_asset" } + mapOf(
            "visual_asset" to JsonNull,
            "candidate_error" to JsonPrimitive("visual_persist_failed"),
            "storage_retry_required" to JsonPrimitive(true),
            "vps_persistence" to JsonPrimitive("provider_result_unpersisted"),
        ),
    )

    private fun pendingPaidIntents(store: JarvisAppSession.MemoryStore): Map<String, String> =
        store.snapshot().filterKeys { it.startsWith("jarvis.paid.visual.") }

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

    private fun projectMasterBody(projectId: String, prompt: String) = buildJsonObject {
        put("project_id", projectId)
        put("prompt", prompt.trim())
        put("mode", "quality")
        put("aspect_ratio", "portrait")
        put("visual_asset", buildJsonObject {
            put("surface", "character_creator")
            put("project_id", projectId)
            put("kind", "PRIMARY_REFERENCE")
            put("character_ids", buildJsonArray { add(JsonPrimitive("character:alexander")) })
            put("perspective", "front")
            put("references_per_character", 1)
        })
    }

    private fun legacyPaidAttemptKey(path: String, userId: String = "owner-1"): String {
        val owner = "${server.url("/").toString().trimEnd('/')}\u001f$userId"
        val hash = java.security.MessageDigest.getInstance("SHA-256")
            .digest(owner.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }.take(32)
        return "jarvis.paid.visual.$hash.${path.substringAfterLast('/')}"
    }

    @Test
    fun candidateErrorFailsClosedWithoutReleasingAndStaysRecoverableByGet() = runBlocking {
        val store = JarvisAppSession.MemoryStore()
        val client = session(store)
        nextGenerationCandidateError = true

        val result = client.portraitRequest("Candidate error portrait")

        assertEquals(
            "La imagen se generó, pero no se pudo guardar en el proyecto. " +
                "Quedó pendiente en este dispositivo; revísala en el aviso de imágenes pendientes.",
            result.exceptionOrNull()?.message,
        )
        assertEquals(1, generationPosts.get())
        assertEquals(1, pendingPaidIntents(store).size)

        val recovered = client.pendingPaidVisuals().single() as JarvisAppSession.PendingPaidVisualState.Completed
        assertEquals(fixturePng, recovered.receipt["data_base64"]?.jsonPrimitive?.contentOrNull)
        assertEquals(1, generationPosts.get())
        assertEquals(1, pendingPaidIntents(store).size)
    }

    @Test
    fun characterMasterRosterDecodesProjectCanonReadiness() = runBlocking {
        val roster = session().visualCharacterMasterRosterStatus("prj_story")
            .orThrowStage("character master roster")

        assertEquals("prj_story", roster.project_id)
        assertEquals(3, roster.items.size)
        assertEquals(1, roster.counts.approved)
        assertEquals(0, roster.counts.candidate)
        assertEquals(1, roster.counts.ready)
        assertEquals(1, roster.counts.blocked)
        assertEquals(1, roster.generation_required)
        assertTrue(roster.human_approval_required)
        assertFalse(roster.auto_canon)
        assertEquals(
            "character:guardian",
            roster.items.single { it.state == "READY" }.character_id,
        )

        val request = takeActionRequest()
        assertEquals(
            "/api/app/writing-room/v2/visual/characters/masters/status",
            request.path,
        )
        assertTrue(request.body.readUtf8().contains("\"project_id\":\"prj_story\""))
    }

    @Test
    fun paidFingerprintMatchesPythonCanonicalJsonGolden() {
        val body = buildJsonObject {
            put("request_id", "img_test_1234")
            put("z", buildJsonObject {
                put("é", buildJsonArray {
                    add(JsonPrimitive(true))
                    add(JsonPrimitive(3))
                })
                put("a", "x")
            })
            put("prompt", "café")
        }
        assertEquals(
            "fb7f168b6884c0862ba5141f686c92763e1cd97fdfe4154848bf4b32464c3033",
            session().paidVisualFingerprint(body),
        )
    }

    @Test
    fun legacyUncertainIntentMigratesOnlyForSameBodyAndKeepsGetOnlyRecovery() = runBlocking {
        val store = JarvisAppSession.MemoryStore()
        val client = session(store)
        val projectId = "prj_story"
        val prompt = "Legacy exact request which may already have been charged"
        val body = projectMasterBody(projectId, prompt)
        val requestId = "img_legacy_req_0001"
        val legacyDigest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(body.toString().toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        val legacyKey = legacyPaidAttemptKey("/api/app/images/generations")
        store.putDurable(mapOf(legacyKey to "$requestId\u001f$legacyDigest"))
        recoveryReceipts[requestId] = buildJsonObject {
            put("request_id", requestId)
            put("request_fingerprint", "F".repeat(64)) // historical server hash was over prepared input
            put("project_id", projectId)
            put("route", "/api/app/images/generations")
            put("state", "OUTCOME_UNKNOWN")
            put("dispatch_state", "OUTCOME_UNKNOWN")
            put("retryable", false)
        }.toString()

        val result = client.generateVisualAssetImage(
            projectId = projectId,
            characterId = "character:alexander",
            prompt = prompt,
            kind = "PRIMARY_REFERENCE",
            perspective = "front",
        )

        assertTrue(result.isFailure)
        assertEquals(0, generationPosts.get())
        assertEquals(2, server.requestCount) // legacy migration lookup and same-ID recovery lookup
        val pending = store.snapshot().filterKeys { it.startsWith("jarvis.paid.visual.") }
        assertEquals(1, pending.size)
        assertFalse(pending.containsKey(legacyKey))
        assertEquals(
            listOf(requestId, client.paidVisualFingerprint(body), "LEGACY_MIGRATED"),
            pending.values.single().split('\u001f'),
        )
    }

    @Test
    fun ambiguousLegacyIntentStaysPinnedAndCannotStartDifferentRequest() = runBlocking {
        val store = JarvisAppSession.MemoryStore()
        val client = session(store)
        val originalBody = projectMasterBody("prj_story", "Original uncertain prompt")
        val requestId = "img_legacy_ambiguous_01"
        val legacyDigest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(originalBody.toString().toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        val legacyKey = legacyPaidAttemptKey("/api/app/images/generations")
        store.putDurable(mapOf(legacyKey to "$requestId\u001f$legacyDigest"))
        recoveryReceipts[requestId] = buildJsonObject {
            put("request_id", requestId)
            put("request_fingerprint", "E".repeat(64))
            put("project_id", "prj_story")
            put("route", "/api/app/images/generations")
            put("state", "OUTCOME_UNKNOWN")
            put("dispatch_state", "OUTCOME_UNKNOWN")
            put("retryable", false)
        }.toString()

        val changed = client.generateVisualAssetImage(
            projectId = "prj_story",
            characterId = "character:alexander",
            prompt = "Changed prompt must stay blocked while old intent is ambiguous",
            kind = "PRIMARY_REFERENCE",
            perspective = "front",
        )

        assertTrue(changed.isFailure)
        assertEquals(0, generationPosts.get())
        assertEquals("$requestId\u001f$legacyDigest", store.get(legacyKey))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun crossProjectPaidReceiptNeverReleasesIdForSecondCharge() = runBlocking {
        replyWithWrongProject = true
        val client = session()
        val makeRequest: suspend () -> Result<VisualStudioGeneratedImage> = {
            client.generateVisualAssetImage(
                projectId = "prj_story",
                characterId = "character:alexander",
                prompt = "Private A portrait",
                kind = "PRIMARY_REFERENCE",
                perspective = "front",
            )
        }
        val first = makeRequest()
        assertTrue(first.isFailure)
        assertTrue(first.exceptionOrNull()?.message.orEmpty().contains("mismatch"))
        // A second tap cannot dispatch a new paid call because the forged
        // response never acknowledged the exact original attempt.
        val second = makeRequest()
        assertTrue(second.isFailure)
        assertEquals(3, server.requestCount) // first GET+POST, second GET only
    }

    @Test
    fun confirmedPreDispatchRejectionAllowsCorrectedProjectRequestWithNewId() = runBlocking {
        val client = session()
        rejectNextGenerationPreDispatch = true

        val rejected = client.generateVisualAssetImage(
            projectId = "prj_story",
            characterId = "character:alexander",
            prompt = "Portrait before project theme is configured",
            kind = "PRIMARY_REFERENCE",
            perspective = "front",
        )
        assertTrue(rejected.isFailure)
        assertTrue(rejected.exceptionOrNull()?.message.orEmpty().contains("visual_project_style_required"))

        val corrected = client.generateVisualAssetImage(
            projectId = "prj_story",
            characterId = "character:alexander",
            prompt = "Portrait using the saved copper project theme",
            kind = "PRIMARY_REFERENCE",
            perspective = "front",
        ).orThrowStage("corrected project image generation")

        assertEquals("stored", corrected.visual_asset?.storage?.state)
        assertEquals(2, generationPosts.get())
        val ids = generationPostBodies.mapNotNull {
            it["request_id"]?.jsonPrimitive?.contentOrNull
        }
        assertEquals(2, ids.distinct().size)
        assertTrue(server.requestCount >= 5) // probe + POST + durable GET, then new probe + POST
    }

    @Test
    fun pendingStorageCandidateReleasesIntentForDistinctPrompt() = runBlocking {
        val store = JarvisAppSession.MemoryStore()
        val client = session(store)
        nextGenerationStorageState = "pending_upload"

        val pending = client.portraitRequest("Portrait staged on the VPS while Drive upload is pending")
            .orThrowStage("pending storage candidate")

        assertEquals("CANDIDATE", pending.visual_asset?.status)
        assertEquals("pending_upload", pending.visual_asset?.storage?.state)
        assertTrue(pendingPaidIntents(store).isEmpty())

        val next = client.portraitRequest("A different portrait requested after the staged candidate")
            .orThrowStage("distinct prompt after pending storage")

        assertEquals("stored", next.visual_asset?.storage?.state)
        assertEquals(2, generationPosts.get())
        val ids = generationPostBodies.mapNotNull { it["request_id"]?.jsonPrimitive?.contentOrNull }
        assertEquals(2, ids.distinct().size)
    }

    private suspend fun assertTypedPreDispatchRejectionReleasesIntent(status: Int, errorCode: String) {
        val store = JarvisAppSession.MemoryStore()
        val client = session(store)
        val prompt = "Portrait refused with typed HTTP $status before dispatch"
        rejectNextGenerationPreDispatch = true
        preDispatchRejectionStatus = status
        preDispatchRejectionError = errorCode

        val failure = client.portraitRequest(prompt).exceptionOrNull()

        assertTrue(failure?.message.orEmpty().contains("HTTP $status"))
        assertTrue(failure?.message.orEmpty().contains(errorCode))
        assertTrue(pendingPaidIntents(store).isEmpty())
        assertEquals(1, generationPosts.get())
        val requestId = generationPostBodies.single()["request_id"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val trace = (0 until server.requestCount).map { server.takeRequest() }
        assertEquals(
            listOf(
                "GET /api/app/images/requests/$requestId",
                "POST /api/app/images/generations",
                "GET /api/app/images/requests/$requestId",
            ),
            trace.map { "${it.method} ${it.path}" },
        )

        val retried = client.portraitRequest(prompt)
            .orThrowStage("request after typed pre-dispatch rejection")
        assertEquals("stored", retried.visual_asset?.storage?.state)
        assertEquals(2, generationPosts.get())
    }

    @Test
    fun typedPreDispatchRejectionWith429ReleasesIntentAfterVerifiedReceipt() = runBlocking {
        assertTypedPreDispatchRejectionReleasesIntent(429, "writing_room_image_budget_exceeded")
    }

    @Test
    fun typedPreDispatchRejectionWith409StillReleasesIntentAsBefore() = runBlocking {
        assertTypedPreDispatchRejectionReleasesIntent(409, "visual_project_style_required")
    }

    @Test
    fun tamperedPaidImageBytesStayPinnedWithoutAnotherPaidPost() = runBlocking {
        val client = session()
        corruptNextGenerationBytes = true
        val request: suspend () -> Result<VisualStudioGeneratedImage> = {
            client.generateVisualAssetImage(
                projectId = "prj_story",
                characterId = "character:alexander",
                prompt = "Keep compromised original out of the gallery",
                kind = "PRIMARY_REFERENCE",
                perspective = "front",
            )
        }
        val first = request()
        val recovered = request()
        assertTrue(first.isFailure)
        assertTrue(recovered.isFailure)
        assertTrue(first.exceptionOrNull()?.message.orEmpty().contains("integrity"))
        assertEquals(1, generationPosts.get())
        assertEquals(3, server.requestCount) // read-only probe, POST, read-only recovery
    }

    @Test
    fun forgedPaidImageShaStaysPinnedWithoutAnotherPaidPost() = runBlocking {
        val client = session()
        corruptNextGenerationHash = true
        val request: suspend () -> Result<VisualStudioGeneratedImage> = {
            client.generateVisualAssetImage(
                projectId = "prj_story",
                characterId = "character:alexander",
                prompt = "A mismatched hash cannot authorize a new charge",
                kind = "PRIMARY_REFERENCE",
                perspective = "front",
            )
        }
        assertTrue(request().isFailure)
        assertTrue(request().isFailure)
        assertEquals(1, generationPosts.get())
        assertEquals(3, server.requestCount)
    }

    @Test
    fun uncertainVisualOutcomeKeepsIdPinnedAndNeverPostsAgain() = runBlocking {
        val client = session()
        returnUnknownAfterNextGeneration = true
        val makeRequest: suspend () -> Result<VisualStudioGeneratedImage> = {
            client.generateVisualAssetImage(
                projectId = "prj_story",
                characterId = "character:alexander",
                prompt = "Keep the exact attempt while outcome is uncertain",
                kind = "PRIMARY_REFERENCE",
                perspective = "front",
            )
        }

        assertTrue(makeRequest().isFailure)
        assertTrue(makeRequest().isFailure)
        assertEquals(1, generationPosts.get())
        val requestId = generationPostBodies.single()["request_id"]?.jsonPrimitive?.contentOrNull
        assertTrue(server.takeRequest().path?.startsWith("/api/app/images/requests/") == true)
        val post = server.takeRequest()
        assertEquals("/api/app/images/generations", post.path)
        val recovery = server.takeRequest()
        assertEquals("/api/app/images/requests/$requestId", recovery.path)
        assertEquals(3, server.requestCount) // same ID only; no second POST
    }

    @Test
    fun pendingIntentSurvivesLogoutAndSameAccountLoginForReceiptRecovery() = runBlocking {
        val store = JarvisAppSession.MemoryStore()
        val firstSession = session(store)
        loseNextGenerationResponseAfterDispatch = true
        val first = firstSession.generateVisualAssetImage(
            projectId = "prj_story",
            characterId = "character:alexander",
            prompt = "Recover the exact accepted project portrait after login",
            kind = "PRIMARY_REFERENCE",
            perspective = "front",
        )
        assertTrue(first.isFailure)
        val originalId = generationPostBodies.single()["request_id"]?.jsonPrimitive?.contentOrNull

        firstSession.clear()
        val loggedInAgain = session(store)
        val recovered = loggedInAgain.generateVisualAssetImage(
            projectId = "prj_story",
            characterId = "character:alexander",
            prompt = "Recover the exact accepted project portrait after login",
            kind = "PRIMARY_REFERENCE",
            perspective = "front",
        ).orThrowStage("recovered project image after login")

        assertEquals("stored", recovered.visual_asset?.storage?.state)
        assertEquals(1, generationPosts.get())
        val requests = (0 until server.requestCount).mapNotNull {
            server.takeRequest().path
        }
        assertTrue(requests.contains("/api/app/images/requests/$originalId"))
        assertEquals(3, requests.size) // initial probe, one POST, then GET recovery
        assertEquals(1, requests.count { it == "/api/app/images/generations" })
    }

    @Test
    fun pendingProjectDoesNotBlockAnotherProjectOrGlobalAndAckIsScoped() = runBlocking {
        val client = session()
        loseNextGenerationResponseAfterDispatch = true
        val projectOnePrompt = "Project one portrait with exact recovery"
        assertTrue(client.generateVisualAssetImage(
            projectId = "prj_one",
            characterId = "character:alexander",
            prompt = projectOnePrompt,
            kind = "PRIMARY_REFERENCE",
            perspective = "front",
        ).isFailure)

        val projectTwo = client.generateVisualAssetImage(
            projectId = "prj_two",
            characterId = "character:alexander",
            prompt = "Project two independent portrait",
            kind = "PRIMARY_REFERENCE",
            perspective = "front",
        ).orThrowStage("second project image generation")
        assertEquals("prj_two", projectTwo.visual_asset?.project_id)

        val recoveredProjectOne = client.generateVisualAssetImage(
            projectId = "prj_one",
            characterId = "character:alexander",
            prompt = projectOnePrompt,
            kind = "PRIMARY_REFERENCE",
            perspective = "front",
        ).orThrowStage("first project receipt recovery")
        assertEquals("prj_one", recoveredProjectOne.visual_asset?.project_id)

        val global = client.generateImage("A Global-only image request", "speed", null)
            .orThrowStage("Global image after Writing Room request")
        assertEquals("image/png", global.mimeType)

        assertEquals(3, generationPosts.get())
        val projects = generationPostBodies.mapNotNull {
            it["project_id"]?.jsonPrimitive?.contentOrNull
        }
        assertEquals(listOf("prj_one", "prj_two"), projects)
        assertTrue(generationPostBodies.last()["project_id"] == null)
    }

    @Test
    fun globalCompletionWithWrongProjectCorrelationStaysPinnedWithoutAck() = runBlocking {
        val client = session()
        loseNextGenerationResponseAfterDispatch = true
        val prompt = "Global completion recovery must match the empty project scope"
        assertTrue(client.generateImage(prompt, "speed", null).isFailure)
        val requestId = generationPostBodies.single()["request_id"]?.jsonPrimitive?.contentOrNull
            ?: error("Global generation omitted request_id")
        val stored = json.parseToJsonElement(recoveryReceipts.getValue(requestId)).jsonObject
        recoveryReceipts[requestId] = buildJsonObject {
            stored.forEach { (key, value) ->
                if (key != "project_id") put(key, value)
            }
            put("project_id", "prj_unrelated")
        }.toString()

        assertTrue(client.generateImage(prompt, "speed", null).isFailure)
        assertTrue(client.generateImage(prompt, "speed", null).isFailure)
        assertEquals(1, generationPosts.get())
        assertEquals(4, server.requestCount) // initial probe+POST, then two GETs for the same ID
    }

    @Test
    fun unclaimedPaidIntentIsVoidedOnGet404ThenOneNewRequestRuns() = runBlocking {
        val store = JarvisAppSession.MemoryStore()
        session(store)
        val writes = java.util.Collections.synchronizedList(mutableListOf<Pair<String, String?>>())
        val recording = object : JarvisAppSession.Store by store {
            override fun putDurable(values: Map<String, String?>): Boolean {
                values.forEach { (key, value) -> writes += key to value }
                return store.putDurable(values)
            }
        }
        val client = JarvisAppSession(recording)
        val prompt = "Portrait whose first POST never reached the server"
        rejectNextGenerationUnclaimed = true
        assertTrue(client.portraitRequest(prompt).isFailure)
        val unclaimedId = generationPostBodies.single()["request_id"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val pinnedKey = pendingPaidIntents(store).keys.single()

        val replaced = client.portraitRequest(prompt)
            .orThrowStage("request after voiding the unclaimed attempt")

        assertEquals("stored", replaced.visual_asset?.storage?.state)
        assertEquals(2, generationPosts.get())
        val newId = generationPostBodies.last()["request_id"]?.jsonPrimitive?.contentOrNull.orEmpty()
        assertTrue(newId.startsWith("img_") && newId != unclaimedId)
        assertEquals(
            listOf(unclaimedId, null, newId, null),
            writes.filter { it.first == pinnedKey }.map { it.second?.substringBefore('\u001f') },
        )
        assertTrue(pendingPaidIntents(store).isEmpty())

        val trace = (0 until server.requestCount).map { server.takeRequest() }
        assertEquals(
            listOf(
                "GET /api/app/images/requests/$unclaimedId",
                "POST /api/app/images/generations",
                "GET /api/app/images/requests/$unclaimedId",
                "POST /api/app/images/requests/$unclaimedId/void",
                "GET /api/app/images/requests/$newId",
                "POST /api/app/images/generations",
            ),
            trace.map { "${it.method} ${it.path}" },
        )
        val voidBody = json.parseToJsonElement(trace[3].body.readUtf8()).jsonObject
        assertEquals("/api/app/images/generations", voidBody["route"]?.jsonPrimitive?.contentOrNull)
        assertEquals("prj_story", voidBody["project_id"]?.jsonPrimitive?.contentOrNull)
        assertEquals(
            client.paidVisualFingerprint(generationPostBodies.first()),
            voidBody["request_fingerprint"]?.jsonPrimitive?.contentOrNull,
        )
    }

    @Test
    fun voidThatDoesNotProveRejectionKeepsIntentPinnedWithoutAnotherPost() = runBlocking {
        val store = JarvisAppSession.MemoryStore()
        val client = session(store)
        val prompt = "Portrait whose void finds the ID already claimed"
        rejectNextGenerationUnclaimed = true
        assertTrue(client.portraitRequest(prompt).isFailure)
        val pinned = pendingPaidIntents(store)
        val postsBefore = generationPosts.get()
        voidFindsOutcomeUnknown = true

        val retry = client.portraitRequest(prompt)

        assertEquals(
            "Paid image attempt could not be voided; automatic retry disabled",
            retry.exceptionOrNull()?.message,
        )
        assertEquals(postsBefore, generationPosts.get())
        assertEquals(pinned, pendingPaidIntents(store))
    }

    private suspend fun assertUnverifiedRecoveryNeverVoidsOrPosts(reply: Pair<Int, String>) {
        val store = JarvisAppSession.MemoryStore()
        val client = session(store)
        val prompt = "Portrait whose recovery GET cannot be trusted"
        rejectNextGenerationUnclaimed = true
        assertTrue(client.portraitRequest(prompt).isFailure)
        val pinned = pendingPaidIntents(store)
        val postsBefore = generationPosts.get()
        unknownRecoveryReply = reply

        val retry = client.portraitRequest(prompt)

        assertEquals(
            "Paid image outcome cannot be verified; automatic retry disabled",
            retry.exceptionOrNull()?.message,
        )
        assertEquals(postsBefore, generationPosts.get())
        assertEquals(pinned, pendingPaidIntents(store))
        val paths = (0 until server.requestCount).map { server.takeRequest().path.orEmpty() }
        assertTrue(paths.none { it.endsWith("/void") })
    }

    @Test
    fun recoveryServerErrorNeverVoidsOrPostsAgain() = runBlocking {
        assertUnverifiedRecoveryNeverVoidsOrPosts(500 to """{"error":"internal_error"}""")
    }

    @Test
    fun recoveryNotFoundWithOtherErrorNeverVoids() = runBlocking {
        assertUnverifiedRecoveryNeverVoidsOrPosts(404 to """{"error":"unknown_request"}""")
    }

    @Test
    fun voidThatCannotBeReleasedDurablyStopsBeforeAnotherPost() = runBlocking {
        val store = JarvisAppSession.MemoryStore()
        val client = session(store)
        val prompt = "Portrait whose void cannot be released durably"
        rejectNextGenerationUnclaimed = true
        assertTrue(client.portraitRequest(prompt).isFailure)
        val pinned = pendingPaidIntents(store)
        val postsBefore = generationPosts.get()
        val unreleasable = object : JarvisAppSession.Store by store {
            override fun putDurable(values: Map<String, String?>): Boolean =
                values.values.none { it == null } && store.putDurable(values)
        }

        val retry = JarvisAppSession(unreleasable).portraitRequest(prompt)

        assertEquals("Paid image attempt cannot be released durably", retry.exceptionOrNull()?.message)
        assertEquals(postsBefore, generationPosts.get())
        assertEquals(pinned, pendingPaidIntents(store))
    }

    private fun pendingReceipt(
        requestId: String,
        fingerprint: String,
        path: String,
        state: String,
        dispatchState: String,
        retryable: Boolean,
        error: String? = null,
    ): String = buildJsonObject {
        put("request_id", requestId)
        put("request_fingerprint", fingerprint)
        put("project_id", "prj_story")
        put("route", path)
        put("state", state)
        put("dispatch_state", dispatchState)
        put("retryable", retryable)
        error?.let { put("error", it) }
    }.toString()

    @Test
    fun pendingIntentNeverClaimedIsVoidedAndReleasedWithoutGenerationPost() = runBlocking {
        val store = JarvisAppSession.MemoryStore()
        val client = session(store)
        val requestId = "img_pending_unclaimed_01"
        val fingerprint = client.paidVisualFingerprint(projectMasterBody("prj_story", "Pending unclaimed portrait"))
        val key = legacyPaidAttemptKey("/api/app/images/generations")
        store.putDurable(mapOf(key to "$requestId\u001f$fingerprint"))

        val pending = client.pendingPaidVisuals()

        assertTrue(pending.isEmpty())
        assertFalse(pendingPaidIntents(store).containsKey(key))
        assertEquals(0, generationPosts.get())
        val trace = (0 until server.requestCount).map { server.takeRequest() }
        assertEquals(
            listOf(
                "GET /api/app/images/requests/$requestId",
                "POST /api/app/images/requests/$requestId/void",
            ),
            trace.map { "${it.method} ${it.path}" },
        )
        val voidBody = json.parseToJsonElement(trace[1].body.readUtf8()).jsonObject
        assertEquals("/api/app/images/generations", voidBody["route"]?.jsonPrimitive?.contentOrNull)
        assertEquals("", voidBody["project_id"]?.jsonPrimitive?.contentOrNull)
        assertEquals(fingerprint, voidBody["request_fingerprint"]?.jsonPrimitive?.contentOrNull)
    }

    @Test
    fun pendingCompletedReceiptIsReturnedAndIntentStaysPinned() = runBlocking {
        val store = JarvisAppSession.MemoryStore()
        val client = session(store)
        val requestId = "img_pending_completed_01"
        val fingerprint = client.paidVisualFingerprint(projectMasterBody("prj_story", "Completed pending portrait"))
        val key = legacyPaidAttemptKey("/api/app/images/generations")
        store.putDurable(mapOf(key to "$requestId\u001f$fingerprint"))
        recoveryReceipts[requestId] = pendingReceipt(
            requestId, fingerprint, "/api/app/images/generations",
            state = "COMPLETED", dispatchState = "COMPLETED", retryable = false,
        )

        val completed = client.pendingPaidVisuals().single() as JarvisAppSession.PendingPaidVisualState.Completed

        assertEquals(key, completed.key)
        assertEquals(requestId, completed.requestId)
        assertEquals("COMPLETED", completed.receipt["state"]?.jsonPrimitive?.contentOrNull)
        assertTrue(pendingPaidIntents(store).containsKey(key))
        assertEquals(0, generationPosts.get())
        assertEquals(1, server.requestCount) // read-only receipt lookup only
    }

    @Test
    fun pendingOutcomeUnknownIsReportedAndIntentStaysPinned() = runBlocking {
        val store = JarvisAppSession.MemoryStore()
        val client = session(store)
        val requestId = "img_pending_unknown_0001"
        val fingerprint = client.paidVisualFingerprint(projectMasterBody("prj_story", "Unknown pending portrait"))
        val key = legacyPaidAttemptKey("/api/app/images/edits")
        store.putDurable(mapOf(key to "$requestId\u001f$fingerprint"))
        recoveryReceipts[requestId] = pendingReceipt(
            requestId, fingerprint, "/api/app/images/edits",
            state = "OUTCOME_UNKNOWN", dispatchState = "OUTCOME_UNKNOWN", retryable = false,
        )

        val unknown = client.pendingPaidVisuals().single() as JarvisAppSession.PendingPaidVisualState.Unknown

        assertEquals(key, unknown.key)
        assertEquals(requestId, unknown.requestId)
        assertTrue(pendingPaidIntents(store).containsKey(key))
        assertEquals(0, generationPosts.get())
        assertEquals(1, server.requestCount)
    }

    @Test
    fun pendingVerifiedPreDispatchRejectionReleasesIntentAndReturnsNothing() = runBlocking {
        val store = JarvisAppSession.MemoryStore()
        val client = session(store)
        val requestId = "img_pending_rejected_01"
        val fingerprint = client.paidVisualFingerprint(projectMasterBody("prj_story", "Rejected pending portrait"))
        val key = legacyPaidAttemptKey("/api/app/images/generations")
        store.putDurable(mapOf(key to "$requestId\u001f$fingerprint"))
        recoveryReceipts[requestId] = pendingReceipt(
            requestId, fingerprint, "/api/app/images/generations",
            state = "REJECTED_PRE_DISPATCH", dispatchState = "NOT_DISPATCHED", retryable = true,
            error = "visual_project_style_required",
        )

        assertTrue(client.pendingPaidVisuals().isEmpty())

        assertFalse(pendingPaidIntents(store).containsKey(key))
        assertEquals(0, generationPosts.get())
        assertEquals(1, server.requestCount) // the receipt already proves no dispatch; no void needed
    }

    @Test
    fun pendingServerErrorOrMismatchedReceiptIsUnverifiableAndDeletesNothing() = runBlocking {
        val store = JarvisAppSession.MemoryStore()
        val client = session(store)
        val fingerprint = "a".repeat(64)
        val failingKey = legacyPaidAttemptKey("/api/app/images/generations")
        store.putDurable(mapOf(failingKey to "img_pending_500_000001\u001f$fingerprint"))
        unknownRecoveryReply = 500 to """{"error":"internal_error"}"""
        val mismatchedKey = legacyPaidAttemptKey("/api/app/images/edits")
        store.putDurable(mapOf(mismatchedKey to "img_pending_mismatch_01\u001f$fingerprint"))
        recoveryReceipts["img_pending_mismatch_01"] = pendingReceipt(
            "img_pending_other_0001", fingerprint, "/api/app/images/edits",
            state = "COMPLETED", dispatchState = "COMPLETED", retryable = false,
        )

        val pending = client.pendingPaidVisuals()

        assertEquals(setOf(failingKey, mismatchedKey), pending.map { it.key }.toSet())
        assertTrue(pending.all { it is JarvisAppSession.PendingPaidVisualState.Unverifiable })
        assertEquals(setOf(failingKey, mismatchedKey), pendingPaidIntents(store).keys)
        assertEquals(0, generationPosts.get())
        assertEquals(2, server.requestCount)
    }

    @Test
    fun pendingMalformedOrUnknownKindIsUnverifiableWithoutAnyRequest() = runBlocking {
        val store = JarvisAppSession.MemoryStore()
        val client = session(store)
        val malformedKey = legacyPaidAttemptKey("/api/app/images/generations")
        store.putDurable(mapOf(malformedKey to "not-a-paid-intent"))
        val unknownKindKey = legacyPaidAttemptKey("/api/app/images/unknown-kind")
        store.putDurable(mapOf(unknownKindKey to "img_unknown_kind_000001\u001f${"d".repeat(64)}"))

        val pending = client.pendingPaidVisuals()

        assertEquals(setOf(malformedKey, unknownKindKey), pending.map { it.key }.toSet())
        assertTrue(pending.all { it is JarvisAppSession.PendingPaidVisualState.Unverifiable })
        assertEquals(0, server.requestCount)
        assertEquals(0, generationPosts.get())
    }

    @Test
    fun pendingVisualsListOnlyTheCurrentAccount() = runBlocking {
        val store = JarvisAppSession.MemoryStore()
        val client = session(store)
        val fingerprint = "e".repeat(64)
        val otherRequestId = "img_other_account_0001"
        val otherKey = legacyPaidAttemptKey("/api/app/images/generations", userId = "owner-2")
        store.putDurable(mapOf(otherKey to "$otherRequestId\u001f$fingerprint"))
        recoveryReceipts[otherRequestId] = pendingReceipt(
            otherRequestId, fingerprint, "/api/app/images/generations",
            state = "OUTCOME_UNKNOWN", dispatchState = "OUTCOME_UNKNOWN", retryable = false,
        )
        val ownRequestId = "img_current_account_0001"
        val ownKey = legacyPaidAttemptKey("/api/app/images/edits")
        store.putDurable(mapOf(ownKey to "$ownRequestId\u001f$fingerprint"))
        recoveryReceipts[ownRequestId] = pendingReceipt(
            ownRequestId, fingerprint, "/api/app/images/edits",
            state = "OUTCOME_UNKNOWN", dispatchState = "OUTCOME_UNKNOWN", retryable = false,
        )

        val unknown = client.pendingPaidVisuals().single() as JarvisAppSession.PendingPaidVisualState.Unknown

        assertEquals(ownKey, unknown.key)
        assertEquals(ownRequestId, unknown.requestId)
        assertEquals(
            listOf("/api/app/images/requests/$ownRequestId"),
            (0 until server.requestCount).map { server.takeRequest().path },
        )
        assertTrue(pendingPaidIntents(store).containsKey(otherKey))
    }

    @Test
    fun releasePaidVisualIntentDeletesOnlyTheRequestedKey() {
        val store = JarvisAppSession.MemoryStore()
        val client = session(store)
        val releasedKey = legacyPaidAttemptKey("/api/app/images/generations")
        val keptKey = legacyPaidAttemptKey("/api/app/images/edits")
        val fingerprint = "f".repeat(64)
        store.putDurable(
            mapOf(
                releasedKey to "img_release_target_0001\u001f$fingerprint",
                keptKey to "img_release_kept_000001\u001f$fingerprint",
            ),
        )

        assertTrue(client.releasePaidVisualIntent(releasedKey, "img_release_target_0001"))

        assertEquals(setOf(keptKey), pendingPaidIntents(store).keys)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun releasePaidVisualIntentFailsClosedWhenStoreCannotPersist() {
        val store = JarvisAppSession.MemoryStore()
        val key = legacyPaidAttemptKey("/api/app/images/generations")
        store.putDurable(mapOf(key to "img_release_refused_01\u001f${"f".repeat(64)}"))
        val unreleasable = object : JarvisAppSession.Store by store {
            override fun putDurable(values: Map<String, String?>): Boolean = false
        }

        val failure = runCatching {
            JarvisAppSession(unreleasable).releasePaidVisualIntent(key, "img_release_refused_01")
        }.exceptionOrNull()

        assertEquals("Paid image attempt cannot be released durably", failure?.message)
        assertTrue(pendingPaidIntents(store).containsKey(key))
    }

    @Test
    fun releasePaidVisualIntentReleasesOnlyTheMatchingRequestId() {
        val store = JarvisAppSession.MemoryStore()
        val client = session(store)
        val key = legacyPaidAttemptKey("/api/app/images/generations")
        val fingerprint = "e".repeat(64)
        val firstId = "img_compare_first_0001"
        val secondId = "img_compare_second_002"
        store.putDurable(mapOf(key to "$firstId\u001f$fingerprint"))

        assertTrue(client.releasePaidVisualIntent(key, firstId))
        assertFalse(pendingPaidIntents(store).containsKey(key))

        store.putDurable(mapOf(key to "$secondId\u001f$fingerprint"))

        assertFalse(client.releasePaidVisualIntent(key, firstId))
        assertEquals("$secondId\u001f$fingerprint", pendingPaidIntents(store)[key])
    }

    @Test
    fun twoDeliberateSuccessfulMasterRequestsUseDistinctPaidAttemptIds() = runBlocking {
        val client = session()
        repeat(2) {
            val reply = client.generateVisualAssetImage(
                projectId = "prj_story",
                characterId = "character:alexander",
                prompt = "The same canonical portrait",
                kind = "PRIMARY_REFERENCE",
                perspective = "front",
            ).orThrowStage("explicit image generation ${it + 1}")
            assertEquals("stored", reply.visual_asset?.storage?.state)
        }
        // Two user gestures, each with exactly one read-only lookup and
        // one intentional paid POST. No cached first image on the second tap.
        val firstProbe = server.takeRequest()
        val firstPost = server.takeRequest()
        val secondProbe = server.takeRequest()
        val secondPost = server.takeRequest()
        assertTrue(firstProbe.path?.startsWith("/api/app/images/requests/") == true)
        assertTrue(secondProbe.path?.startsWith("/api/app/images/requests/") == true)
        assertEquals("/api/app/images/generations", firstPost.path)
        assertEquals("/api/app/images/generations", secondPost.path)
        val firstId = json.parseToJsonElement(firstPost.body.readUtf8()).jsonObject[
            "request_id"]?.jsonPrimitive?.content
        val secondId = json.parseToJsonElement(secondPost.body.readUtf8()).jsonObject[
            "request_id"]?.jsonPrimitive?.content
        assertTrue(!firstId.isNullOrBlank())
        assertTrue(!secondId.isNullOrBlank())
        assertFalse(firstId == secondId)
        assertEquals(4, server.requestCount)
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
        ).orThrowStage("master generation")
        assertEquals("CANDIDATE", master.visual_asset?.status)
        assertEquals("stored", master.visual_asset?.storage?.state)
        assertEquals(0, master.reference_count)

        val masterApproval = session.visualAssetApproveExact(
            "prj_story",
            "va_master",
            "A".repeat(64),
            visualRevision = 1,
        ).orThrowStage("master exact approval")
        assertEquals("APPROVED", masterApproval.asset.status)

        val turnaround = session.generateVisualAssetImage(
            projectId = "prj_story",
            characterId = "character:alexander",
            prompt = "Alexander left profile turnaround",
            kind = "IDENTITY_PACK",
            perspective = "left_profile",
            referencePerspectives = listOf("front"),
        ).orThrowStage("turnaround generation")
        assertEquals("va_left", turnaround.visual_asset?.asset_id)
        assertEquals(1, turnaround.reference_count)

        val viewApproval = session.visualAssetApproveExact(
            "prj_story",
            "va_left",
            "B".repeat(64),
            visualRevision = 1,
        ).orThrowStage("turnaround exact approval")
        assertEquals("APPROVED", viewApproval.asset.status)

        val draft = session.visualReferencePackCreate(
            "prj_story",
            "character:alexander",
            "va_master",
            "A".repeat(64),
        ).orThrowStage("pack create")
        assertEquals("DRAFT", draft.pack.state)

        val slotted = session.visualReferencePackAddSlot(
            "prj_story",
            "vrp_1",
            "left_profile",
            "va_left",
            "B".repeat(64),
        ).orThrowStage("pack slot add")
        assertEquals(1, slotted.pack.slots.size)
        assertTrue(slotted.pack.slots.single().required)

        val ready = session.visualReferencePackPrepare(
            "prj_story",
            "vrp_1",
        ).orThrowStage("pack prepare")
        assertEquals("READY_FOR_APPROVAL", ready.pack.state)

        val approved = session.visualReferencePackApprove(
            "prj_story",
            "vrp_1",
        ).orThrowStage("pack approve")
        assertEquals("APPROVED", approved.pack.state)

        val detail = session.visualCharacterDetail(
            "prj_story",
            "character:alexander",
        ).orThrowStage("character detail restore")
        assertEquals("vrp_1", detail.active_reference_pack?.pack_id)
        assertEquals("va_master", detail.gallery?.primary?.asset_id)
        assertEquals("va_left", detail.gallery?.identity_pack?.single()?.asset_id)

        val requests = generateSequence {
            if (server.requestCount > 0) takeActionRequest() else null
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
    fun characterViewBatchUsesOneDurableRequestAndExactBatchApproval() = runBlocking {
        val session = session()
        val batch = session.visualCompleteCharacterViews(
            projectId = "prj_story",
            characterId = "character:alexander",
            perspectives = listOf("left_profile", "back"),
        ).orThrowStage("complete missing views")

        val createRequest = server.takeRequest()
        val createBody = json.parseToJsonElement(createRequest.body.readUtf8()).jsonObject
        assertEquals(0, createBody["max_corrections"]!!.jsonPrimitive.content.toInt())
        assertEquals("quality", createBody["mode"]!!.jsonPrimitive.content)

        assertEquals("RUNNING", batch.batch?.status)
        assertEquals(listOf("vcj_back"), batch.queued_job_ids)
        assertTrue(batch.async_execution)
        assertTrue(batch.semantic_evaluation)
        assertEquals(1, batch.generation_budget)
        assertTrue(batch.generated_asset_ids.isEmpty())
        assertTrue(batch.human_approval_required)
        assertFalse(batch.auto_canon)
        assertEquals(1, batch.batch?.pending_count)
        assertEquals("APPROVED_EXISTING", batch.batch?.items?.first()?.status)

        val status = session.visualCharacterBatchStatus(
            "prj_story",
            "character:alexander",
            "vcb_1",
        ).orThrowStage("batch status")
        assertEquals("vcb_1", status.batch?.batch_id)
        assertEquals("CANDIDATE", status.batch?.items?.last()?.status)
        assertEquals("PASS", status.batch?.items?.last()?.evaluation_status)
        assertEquals(93, status.batch?.items?.last()?.evaluation_score)
        assertEquals(95, status.batch?.items?.last()?.evaluation?.identity_score)
        assertEquals(94, status.batch?.items?.last()?.evaluation?.canon_score)
        assertEquals(91, status.batch?.items?.last()?.evaluation?.perspective_score)
        assertTrue(status.batch?.items?.last()?.evaluation?.issues.orEmpty().isEmpty())
        assertEquals(2, status.batch?.items?.last()?.generation_attempts)

        val approved = session.visualApproveCharacterViewBatch(
            projectId = "prj_story",
            characterId = "character:alexander",
            batchId = "vcb_1",
            assetIds = listOf("va_batch_back"),
        ).orThrowStage("batch approval")
        assertFalse(approved.partial)
        assertEquals("COMPLETED", approved.batch?.status)
        assertEquals("va_batch_back", approved.approved.single().asset_id)
    }

    @Test
    fun characterViewBatchResumeLeavesPersistedCorrectionPolicyUntouched() = runBlocking {
        val session = session()
        session.visualCompleteCharacterViews(
            projectId = "prj_story",
            characterId = "character:alexander",
            perspectives = listOf("back"),
            mode = "quality",
            batchId = "vcb_1",
        ).orThrowStage("resume existing views")

        val request = server.takeRequest()
        val body = json.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals("vcb_1", body["batch_id"]!!.jsonPrimitive.content)
        assertTrue(body["max_corrections"] == null)
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
