package com.jarvis.android.transport.live

import com.jarvis.android.transport.TransportException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private val writingRoomJson = Json { ignoreUnknownKeys = true }

@Serializable
data class WritingOverviewProject(
    val project_id: String = "",
    val story_id: String = "",
    val title: String = "",
    val snapshot_date: String? = null,
    val authority: String = "",
)

@Serializable
data class WritingLatestChapter(
    val chapter_number: Int? = null,
    val title: String = "",
    val document_id: String? = null,
    val drive_url: String? = null,
)

@Serializable
data class WritingSourceSummary(
    val total: Int = 0,
    val by_status: Map<String, Int> = emptyMap(),
    val by_category: Map<String, Int> = emptyMap(),
)

@Serializable
data class WritingWorkflowSummary(
    val planning_items: Int = 0,
    val chapter_sessions: Int = 0,
)

@Serializable
data class WritingRoomOverview(
    val schema: String = "",
    val project: WritingOverviewProject = WritingOverviewProject(),
    val latest_official_chapter: WritingLatestChapter? = null,
    val sources: WritingSourceSummary = WritingSourceSummary(),
    val workflow: WritingWorkflowSummary = WritingWorkflowSummary(),
)

@Serializable
data class WritingClassification(
    val task_class: String = "",
    val depth: String = "",
    val source: String = "",
    val reason: String = "",
)

@Serializable
data class WritingRoomAutoChat(
    val schema: String = "",
    val classification: WritingClassification = WritingClassification(),
    val selected_participant: String = "",
    val turn: JarvisAppSession.WritingRoomTurn = JarvisAppSession.WritingRoomTurn(),
)

@Serializable
data class WritingRoomRagSource(
    val chunk_id: String? = null,
    val document_id: String? = null,
    val title: String = "",
    val heading: String? = null,
    val category: String? = null,
    val canon_status: String = "",
    val authority: String = "",
    val drive_url: String = "",
    val excerpt: String = "",
)

@Serializable
data class WritingWikiCategory(
    val id: String = "",
    val label: String = "",
    val subtitle: String = "",
    val query: String = "",
    val source_count: Int = 0,
)

@Serializable
data class WritingWikiLegendItem(
    val status: String = "",
    val label: String = "",
    val meaning: String = "",
)

@Serializable
data class WritingWikiRelationship(
    val target: String = "",
    val kind: String = "",
)

@Serializable
data class WritingWikiHistoryItem(
    val period: String = "",
    val label: String = "",
    val summary: String = "",
    val source_refs: List<String> = emptyList(),
)

@Serializable
data class WritingWikiAppearanceItem(
    val chapters: List<Int> = emptyList(),
    val kind: String = "",
    val summary: String = "",
)

@Serializable
data class WritingWikiFutureNote(
    val authority: String = "",
    val chapter_refs: List<Int> = emptyList(),
    val summary: String = "",
    val source_refs: List<String> = emptyList(),
)

@Serializable
data class WritingWikiFamilyMember(
    val target: String = "",
    val relation: String = "",
    val label: String = "",
)

@Serializable
data class WritingWikiProfile(
    val rank: String = "",
    val affiliations: List<String> = emptyList(),
    val age: String = "",
    val age_note: String = "",
    val height: String = "",
    val first_appearance: Int? = null,
    val latest_appearance: Int? = null,
    val family: List<WritingWikiFamilyMember> = emptyList(),
)

@Serializable
data class WritingWikiChapterActivity(
    val chapters: List<Int> = emptyList(),
    val title: String = "",
    val presence: String = "",
    val evidence_scope: String = "",
    val summary: String = "",
    val actions: List<String> = emptyList(),
    val decisions: List<String> = emptyList(),
    val techniques: List<String> = emptyList(),
    val consequences: List<String> = emptyList(),
    val source_refs: List<String> = emptyList(),
)

@Serializable
data class WritingWikiAnalysisInsight(
    val title: String = "",
    val analysis: String = "",
    val evidence_chapters: List<Int> = emptyList(),
)

@Serializable
data class WritingWikiJarvisAnalysis(
    val status: String = "",
    val summary: String = "",
    val motivations: List<String> = emptyList(),
    val behavior_patterns: List<String> = emptyList(),
    val evolution: String = "",
    val insights: List<WritingWikiAnalysisInsight> = emptyList(),
    val evidence_chapters: List<Int> = emptyList(),
    val source_refs: List<String> = emptyList(),
    val disclaimer: String = "",
)

@Serializable
data class WritingWikiEntity(
    val id: String = "",
    val type: String = "",
    val name: String = "",
    val canonical_name: String = "",
    val name_locked: Boolean = false,
    val authority: String = "",
    val role: String = "",
    val summary: String = "",
    val appearance: String = "",
    val current_state: String = "",
    val traits: List<String> = emptyList(),
    val abilities: List<String> = emptyList(),
    val techniques: List<String> = emptyList(),
    val limitations: List<String> = emptyList(),
    val relationships: List<WritingWikiRelationship> = emptyList(),
    val history: List<WritingWikiHistoryItem> = emptyList(),
    val appearances: List<WritingWikiAppearanceItem> = emptyList(),
    val chapter_refs: List<Int> = emptyList(),
    val mentions: List<Int> = emptyList(),
    val future_refs: List<Int> = emptyList(),
    val future_notes: List<WritingWikiFutureNote> = emptyList(),
    val central_wound: String = "",
    val desire_vs_need: String = "",
    val internal_contradiction: String = "",
    val profile: WritingWikiProfile = WritingWikiProfile(),
    val chapter_activity: List<WritingWikiChapterActivity> = emptyList(),
    val jarvis_analysis: WritingWikiJarvisAnalysis? = null,
)

@Serializable
data class WritingStructuredWikiHome(
    val schema: String = "",
    val story_id: String = "",
    val entry_count: Int = 0,
    val by_type: Map<String, Int> = emptyMap(),
    val by_authority: Map<String, Int> = emptyMap(),
    val occurred_count: Int = 0,
    val future_count: Int = 0,
    val featured: List<WritingWikiEntity> = emptyList(),
)

@Serializable
data class WritingWikiNameLock(
    val entry_id: String = "",
    val canonical_name: String = "",
)

@Serializable
data class WritingWikiBrowse(
    val schema: String = "",
    val project_id: String = "",
    val authority: String = "",
    val entry_type: String? = null,
    val filter_authority: String? = null,
    val entries: List<WritingWikiEntity> = emptyList(),
)

@Serializable
data class WritingWikiEntryNameLock(
    val canonical_name: String = "",
    val locked: Boolean = false,
)

@Serializable
data class WritingWikiEntrySection(
    val id: String = "",
    val label: String = "",
    val temporal_scope: String = "",
    val content: JsonObject = JsonObject(emptyMap()),
)

@Serializable
data class WritingWikiEntryResponse(
    val schema: String = "",
    val project_id: String = "",
    val authority: String = "",
    val entry: WritingWikiEntity = WritingWikiEntity(),
    val name_lock: WritingWikiEntryNameLock? = null,
    val sections: List<WritingWikiEntrySection> = emptyList(),
)

@Serializable
data class WritingWikiHome(
    val schema: String = "",
    val project_id: String = "",
    val authority: String = "",
    val latest_official_chapter: WritingLatestChapter? = null,
    val authority_counts: Map<String, Int> = emptyMap(),
    val categories: List<WritingWikiCategory> = emptyList(),
    val featured: List<WritingRoomRagSource> = emptyList(),
    val structured_wiki: WritingStructuredWikiHome? = null,
    val legend: List<WritingWikiLegendItem> = emptyList(),
)

@Serializable
data class WritingWikiSearch(
    val schema: String = "",
    val project_id: String = "",
    val connected: Boolean = false,
    val authority: String = "",
    val structured_priority: Boolean = false,
    val entities: List<WritingWikiEntity> = emptyList(),
    val name_locks: List<WritingWikiNameLock> = emptyList(),
    val rules: List<String> = emptyList(),
    val results: List<WritingRoomRagSource> = emptyList(),
)

@Serializable
data class WritingPlanItem(
    val item_id: String = "",
    val title: String = "",
    val body: String = "",
    val status: String = "",
    val created_utc: String = "",
    val updated_utc: String = "",
)

@Serializable
data class WritingPlanList(
    val schema: String = "",
    val project_id: String = "",
    val items: List<WritingPlanItem> = emptyList(),
)

@Serializable
data class WritingPlanItemResponse(
    val schema: String = "",
    val project_id: String = "",
    val item: WritingPlanItem = WritingPlanItem(),
)

@Serializable
data class WritingAuthorIntent(
    val objective: String = "",
    val must_have: String = "",
    val must_avoid: String = "",
    val tone: String = "",
    val desired_end: String = "",
)

@Serializable
data class WritingChapter(
    val chapter_id: String = "",
    val project_id: String = "",
    val title: String = "",
    val objective: String = "",
    val story_point: String = "",
    val status: String = "",
    val context_pack_id: String = "",
    val showrunner_brief: String = "",
    val draft_text: String = "",
    val reviewer_text: String = "",
    val canon_review_text: String = "",
    val characters: List<String> = emptyList(),
    val author_intent: WritingAuthorIntent = WritingAuthorIntent(),
    val created_utc: String = "",
    val updated_utc: String = "",
)

@Serializable
data class WritingChapterSummary(
    val chapter_id: String = "",
    val title: String = "",
    val objective: String = "",
    val story_point: String = "",
    val status: String = "",
    val context_pack_id: String = "",
    val created_utc: String = "",
    val updated_utc: String = "",
)

@Serializable
data class WritingChapterList(
    val schema: String = "",
    val project_id: String = "",
    val items: List<WritingChapterSummary> = emptyList(),
)

@Serializable
data class WritingChapterResponse(
    val schema: String = "",
    val project_id: String = "",
    val chapter: WritingChapter = WritingChapter(),
)

@Serializable
data class WritingReviewEvidence(
    val source_ref: String? = null,
    val excerpt: String? = null,
    val offset: Int? = null,
    val length: Int? = null,
)

@Serializable
data class WritingReviewFinding(
    val schema: String = "",
    val finding_id: String = "",
    val check: String = "",
    val category: String = "",
    val severity: String = "",
    val message: String = "",
    val suggestions: List<String> = emptyList(),
    val evidence: List<WritingReviewEvidence> = emptyList(),
    val evidence_required: Boolean = false,
    val evidence_missing: Boolean = false,
    val evidence_source_binding_enforced: Boolean = false,
    val evidence_bound: Boolean? = null,
    val evidence_unbound: Boolean = false,
    val authority: String = "",
    val canon_mutation: String = "",
    val auto_apply: Boolean = false,
    val resolved: Boolean = false,
)

@Serializable
data class WritingEngineReviewResult(
    val schema: String = "",
    val project_id: String = "",
    val chapter_id: String = "",
    val context_pack_id: String = "",
    val authority: String = "",
    val read_only: Boolean = true,
    val canon_mutation: String = "",
    val auto_rewrite: Boolean = false,
    val auto_approve: Boolean = false,
    val status: String = "",
    val check_order: List<String> = emptyList(),
    val check_status: Map<String, String> = emptyMap(),
    val missing_required_checks: List<String> = emptyList(),
    val failed_required_checks: List<String> = emptyList(),
    val severity_counts: Map<String, Int> = emptyMap(),
    val finding_count: Int = 0,
    val findings: List<WritingReviewFinding> = emptyList(),
    val human_decision_required: Boolean = true,
)

@Serializable
data class WritingEngineReviewEnvelope(
    val schema: String = "",
    val authority: String = "",
    val canon_mutation: String = "",
    val context_pack_id: String = "",
    val frozen_context_chars: Int = 0,
    val result: WritingEngineReviewResult = WritingEngineReviewResult(),
)

@Serializable
data class WritingChapterAction(
    val schema: String = "",
    val chapter: WritingChapter = WritingChapter(),
    val engine_review: WritingEngineReviewEnvelope? = null,
)

@Serializable
data class WritingLibraryItem(
    val document_id: String = "",
    val title: String = "",
    val path: String = "",
    val drive_url: String = "",
    val canon_status: String = "",
    val chapter_min: Int? = null,
    val chapter_max: Int? = null,
)

@Serializable
data class WritingLibraryList(
    val schema: String = "",
    val project_id: String = "",
    val items: List<WritingLibraryItem> = emptyList(),
    val exports: Map<String, String> = emptyMap(),
)

@Serializable
data class WritingLibraryDocument(
    val document_id: String = "",
    val title: String = "",
    val drive_url: String = "",
    val canon_status: String = "",
    val text: String = "",
)

@Serializable
data class WritingLibraryDocumentResponse(
    val schema: String = "",
    val project_id: String = "",
    val document: WritingLibraryDocument = WritingLibraryDocument(),
)

@Serializable
data class WritingLibraryExport(
    val schema: String = "",
    val project_id: String = "",
    val document_id: String = "",
    val authority: String = "",
    val vps_persistence: String = "",
    val format: String = "",
    val filename: String = "",
    val mime_type: String = "application/octet-stream",
    val size_bytes: Int = 0,
    val sha256: String = "",
    val data_base64: String = "",
)

private suspend inline fun <reified T> JarvisAppSession.writingPost(
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
        writingRoomJson.decodeFromString<T>(response.second)
    }
}

suspend fun JarvisAppSession.writingRoomOverview(projectId: String): Result<WritingRoomOverview> =
    writingPost(
        "/api/app/writing-room/overview",
        buildJsonObject { put("project_id", projectId) },
    )

suspend fun JarvisAppSession.writingRoomAutoChat(
    projectId: String,
    projectTitle: String,
    prompt: String,
    room: String = "chat",
): Result<WritingRoomAutoChat> {
    val clean = prompt.trim()
    if (clean.isEmpty()) return Result.failure(TransportException("Writing Room prompt is empty"))
    if (clean.length > 6000) return Result.failure(TransportException("Writing Room prompt is too long"))
    return writingPost(
        "/api/app/writing-room/chat",
        buildJsonObject {
            put("project_id", projectId)
            put("project_title", projectTitle)
            put("room", room)
            put("prompt", clean)
        },
    )
}

suspend fun JarvisAppSession.writingRoomAutoChatStream(
    projectId: String,
    projectTitle: String,
    prompt: String,
    room: String = "chat",
    onDelta: (String) -> Unit,
): Result<WritingRoomAutoChat> = withContext(Dispatchers.IO) {
    val clean = prompt.trim()
    if (clean.isEmpty()) return@withContext Result.failure(TransportException("Writing Room prompt is empty"))
    if (clean.length > 6000) return@withContext Result.failure(TransportException("Writing Room prompt is too long"))
    if (expired) {
        clear()
        return@withContext Result.failure(TransportException("session expired"))
    }
    val auth = authHeader()
        ?: return@withContext Result.failure(TransportException("no live session"))
    val body = buildJsonObject {
        put("project_id", projectId)
        put("project_title", projectTitle)
        put("room", room)
        put("prompt", clean)
        put("request_id", java.util.UUID.randomUUID().toString())
    }.toString()

    runCatching {
        var completed: WritingRoomAutoChat? = null
        var streamError: String? = null
        val status = postSse(
            baseUrl,
            "/api/app/writing-room/chat/stream",
            body,
            auth,
        ) { event, data ->
            when (event) {
                "delta" -> {
                    val obj = writingRoomJson.parseToJsonElement(data).jsonObject
                    obj["delta"]?.jsonPrimitive?.contentOrNull?.let(onDelta)
                }
                "complete" -> {
                    completed = writingRoomJson.decodeFromString<WritingRoomAutoChat>(data)
                }
                "error" -> {
                    val obj = runCatching { writingRoomJson.parseToJsonElement(data).jsonObject }.getOrNull()
                    streamError = obj?.get("message")?.jsonPrimitive?.contentOrNull
                        ?: obj?.get("error")?.jsonPrimitive?.contentOrNull
                        ?: "Writing Room stream failed"
                }
            }
        }
        if (status == 401) {
            clear()
            throw TransportException("session expired")
        }
        if (status !in 200..299) throw TransportException("HTTP $status")
        streamError?.let { throw TransportException(it) }
        completed ?: throw TransportException("Writing Room stream ended without completion")
    }
}

suspend fun JarvisAppSession.writingRoomWikiHome(
    projectId: String,
): Result<WritingWikiHome> =
    writingPost(
        "/api/app/writing-room/wiki/home",
        buildJsonObject { put("project_id", projectId) },
    )

suspend fun JarvisAppSession.writingRoomWikiSearch(
    projectId: String,
    query: String,
    topK: Int = 8,
): Result<WritingWikiSearch> {
    val clean = query.trim()
    if (clean.isEmpty()) return Result.failure(TransportException("Wiki query is empty"))
    return writingPost(
        "/api/app/writing-room/wiki/search",
        buildJsonObject {
            put("project_id", projectId)
            put("query", clean)
            put("top_k", topK.coerceIn(1, 12))
        },
    )
}

suspend fun JarvisAppSession.writingRoomWikiBrowse(
    projectId: String,
    entryType: String? = null,
    authority: String? = null,
    topK: Int = 100,
): Result<WritingWikiBrowse> =
    writingPost(
        "/api/app/writing-room/wiki/browse",
        buildJsonObject {
            put("project_id", projectId)
            if (!entryType.isNullOrBlank()) put("entry_type", entryType.trim())
            if (!authority.isNullOrBlank()) put("authority", authority.trim())
            put("top_k", topK.coerceIn(1, 100))
        },
    )

suspend fun JarvisAppSession.writingRoomWikiEntry(
    projectId: String,
    entryId: String,
): Result<WritingWikiEntryResponse> {
    val clean = entryId.trim()
    if (clean.isEmpty()) return Result.failure(TransportException("Wiki entry id is empty"))
    return writingPost(
        "/api/app/writing-room/wiki/entry",
        buildJsonObject {
            put("project_id", projectId)
            put("entry_id", clean)
        },
    )
}

suspend fun JarvisAppSession.writingRoomPlanList(projectId: String): Result<WritingPlanList> =
    writingPost(
        "/api/app/writing-room/plan/list",
        buildJsonObject { put("project_id", projectId) },
    )

suspend fun JarvisAppSession.writingRoomPlanCreate(
    projectId: String,
    title: String,
    body: String,
): Result<WritingPlanItemResponse> {
    val clean = body.trim()
    if (clean.isEmpty()) return Result.failure(TransportException("Planning item is empty"))
    return writingPost(
        "/api/app/writing-room/plan/create",
        buildJsonObject {
            put("project_id", projectId)
            put("title", title.trim())
            put("body", clean)
        },
    )
}

suspend fun JarvisAppSession.writingRoomPlanStatus(
    projectId: String,
    itemId: String,
    status: String,
): Result<WritingPlanItemResponse> =
    writingPost(
        "/api/app/writing-room/plan/status",
        buildJsonObject {
            put("project_id", projectId)
            put("item_id", itemId)
            put("status", status)
        },
    )

suspend fun JarvisAppSession.writingRoomChapterList(projectId: String): Result<WritingChapterList> =
    writingPost(
        "/api/app/writing-room/chapter/list",
        buildJsonObject { put("project_id", projectId) },
    )

suspend fun JarvisAppSession.writingRoomChapterStart(
    projectId: String,
    title: String,
    objective: String,
    storyPoint: String = "",
    characters: List<String> = emptyList(),
    mustHave: String = "",
    mustAvoid: String = "",
    tone: String = "",
    desiredEnd: String = "",
): Result<WritingChapterResponse> {
    val clean = objective.trim()
    if (clean.isEmpty()) return Result.failure(TransportException("Author objective is required"))
    return writingPost(
        "/api/app/writing-room/chapter/start",
        buildJsonObject {
            put("project_id", projectId)
            put("title", title.trim())
            put("objective", clean)
            put("story_point", storyPoint.trim())
            put("characters", buildJsonArray {
                characters.map { it.trim() }.filter { it.isNotEmpty() }.take(12).forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
            })
            put("must_have", mustHave.trim())
            put("must_avoid", mustAvoid.trim())
            put("tone", tone.trim())
            put("desired_end", desiredEnd.trim())
        },
    )
}

private suspend fun JarvisAppSession.writingRoomChapterAction(
    projectId: String,
    chapterId: String,
    action: String,
): Result<WritingChapterAction> =
    writingPost(
        "/api/app/writing-room/chapter/$action",
        buildJsonObject {
            put("project_id", projectId)
            put("chapter_id", chapterId)
        },
    )

suspend fun JarvisAppSession.writingRoomChapterShowrunner(
    projectId: String,
    chapterId: String,
): Result<WritingChapterAction> = writingRoomChapterAction(projectId, chapterId, "showrunner")

suspend fun JarvisAppSession.writingRoomChapterWrite(
    projectId: String,
    chapterId: String,
): Result<WritingChapterAction> = writingRoomChapterAction(projectId, chapterId, "write")

suspend fun JarvisAppSession.writingRoomChapterReview(
    projectId: String,
    chapterId: String,
): Result<WritingChapterAction> = writingRoomChapterAction(projectId, chapterId, "review")

suspend fun JarvisAppSession.writingRoomSaveDraft(
    projectId: String,
    chapterId: String,
    draftText: String,
): Result<WritingChapterResponse> =
    writingPost(
        "/api/app/writing-room/chapter/save-draft",
        buildJsonObject {
            put("project_id", projectId)
            put("chapter_id", chapterId)
            put("draft_text", draftText)
        },
    )

suspend fun JarvisAppSession.writingRoomLibraryList(projectId: String): Result<WritingLibraryList> =
    writingPost(
        "/api/app/writing-room/library/list",
        buildJsonObject { put("project_id", projectId) },
    )

suspend fun JarvisAppSession.writingRoomLibraryRead(
    projectId: String,
    documentId: String,
): Result<WritingLibraryDocumentResponse> =
    writingPost(
        "/api/app/writing-room/library/read",
        buildJsonObject {
            put("project_id", projectId)
            put("document_id", documentId)
        },
    )


suspend fun JarvisAppSession.writingRoomProjectCreate(title: String): Result<WritingRoomOverview> =
    writingPost(
        "/api/app/writing-room/project/create",
        buildJsonObject {
            put("title", title.trim())
            put("kind", "WRITING_ROOM")
        },
    )

suspend fun JarvisAppSession.writingRoomProjectRename(
    projectId: String,
    title: String,
): Result<WritingRoomOverview> =
    writingPost(
        "/api/app/writing-room/project/update",
        buildJsonObject {
            put("project_id", projectId)
            put("title", title.trim())
        },
    )

suspend fun JarvisAppSession.writingRoomProjectDelete(projectId: String): Result<Unit> =
    writingPost<WritingProjectDeleteAck>(
        "/api/app/writing-room/project/delete",
        buildJsonObject { put("project_id", projectId) },
    ).map { }

@Serializable
private data class WritingProjectDeleteAck(
    val schema: String = "",
    val project_id: String = "",
    val deleted: Boolean = false,
)

suspend fun JarvisAppSession.writingRoomLibraryExport(
    projectId: String,
    documentId: String,
    format: String,
): Result<WritingLibraryExport> =
    writingPost(
        "/api/app/writing-room/library/export",
        buildJsonObject {
            put("project_id", projectId)
            put("document_id", documentId)
            put("format", format)
        },
    )
