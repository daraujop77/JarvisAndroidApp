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
data class WritingWikiSearch(
    val schema: String = "",
    val project_id: String = "",
    val connected: Boolean = false,
    val authority: String = "",
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
data class WritingChapterAction(
    val schema: String = "",
    val chapter: WritingChapter = WritingChapter(),
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
                characters.map(String::trim).filter(String::isNotEmpty).take(12).forEach { add(it) }
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
