package com.jarvis.android.transport.live

import com.jarvis.android.transport.TransportException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID

/**
 * Explicit adaptive-agent API for the authoritative VPS.
 *
 * All requests carry the same project/conversation scope and stable request ID.
 * Legacy v64 Writing Room endpoints remain available until migration completes.
 */
private val agentApiJson = Json { ignoreUnknownKeys = true }

@Serializable
data class AgentApprovedAction(
    val action_hash: String = "",
    val approval_id: String = "",
    val approval_expires_at: String = "",
    val conversation_id: String = "",
)

@Serializable
data class AgentRunStatus(
    val schema: String = "",
    val task_id: String = "",
    val project_id: String = "",
    val conversation_id: String = "",
    val request_mode: String = "auto",
    val state: String = "",
    val agent_run_state: String = "",
    val summary: String = "",
    val action_hash: String = "",
    val approval_id: String = "",
    val approval_expires_at: String = "",
    val requires_confirmation: Boolean = false,
    val error_code: String = "",
    val steps: List<CopilotTaskStep> = emptyList(),
    val results: List<CopilotTaskResult> = emptyList(),
)

@Serializable
data class AgentCancellation(
    val requested: Boolean = false,
    val state: String = "",
)

@Serializable
data class AgentRunEvent(
    val run_id: String = "",
    val seq: Int = 0,
    val state: String = "",
    val calls: Int = 0,
    val rounds: Int = 0,
    val pending_approval_id: String = "",
    val error_code: String = "",
    val created_utc: String = "",
)

@Serializable
data class AgentEventPage(
    val schema: String = "",
    val run_id: String = "",
    val events: List<AgentRunEvent> = emptyList(),
    val next_cursor: Int = -1,
)

data class AgentConversationScope(
    val projectId: String,
    val conversationId: String,
) {
    init {
        require(projectId.isNotBlank() && projectId.length <= 160)
        require(conversationId.isNotBlank() && conversationId.length <= 160)
    }
}

/**
 * JSON agent calls share the SSE-capable OkHttp client (unbounded read timeout),
 * so each non-streaming call must impose its own total call deadline.
 * Model/confirmation operations receive a longer budget than read-only probes.
 * A timed-out request is NOT permission to send another model/effect attempt.
 */
internal fun adaptiveAgentJsonTimeoutMillis(path: String): Long = when (path) {
    "/api/app/chat/agent/project/turn",
    "/api/app/writing-room/agent/turn",
    "/api/app/writing-room/agent/confirm" -> 8L * 60_000L
    else -> 45_000L
}

private suspend inline fun <reified T> JarvisAppSession.agentPost(
    path: String,
    body: String,
): Result<T> = withContext(Dispatchers.IO) {
    if (expired || authHeader() == null) {
        val renewed = refresh(reuseIfAlreadyValid = true)
        if (renewed.isFailure) {
            return@withContext Result.failure(
                renewed.exceptionOrNull() ?: TransportException("session expired"),
            )
        }
    }
    runCatching {
        var auth = authHeader() ?: throw TransportException("no live session")
        val deadlineMs = adaptiveAgentJsonTimeoutMillis(path)
        var response = post(baseUrl, path, body, auth, timeoutMillis = deadlineMs)
        if (response.first == 401) {
            val renewed = refresh(staleBearer = auth)
            if (renewed.isFailure) throw renewed.exceptionOrNull()
                ?: TransportException("session expired")
            auth = authHeader() ?: throw TransportException("no live session")
            response = post(baseUrl, path, body, auth, timeoutMillis = deadlineMs)
            if (response.first == 401) {
                if (authHeader() == auth) clearAccess()
                throw TransportException("session expired")
            }
        }
        if (response.first !in 200..299) {
            throw TransportException("Agent HTTP " + response.first)
        }
        agentApiJson.decodeFromString<T>(response.second)
    }
}

/** Persist requestId before invoking; reuse it for network replays. */
suspend fun JarvisAppSession.agentTurn(
    scope: AgentConversationScope,
    requestId: String,
    prompt: String,
    mode: String = "auto",
): Result<WritingRoomAutoChat> {
    if (requestId.isBlank() || requestId.length > 160 ||
        prompt.isBlank() || prompt.length > 6000
    ) return Result.failure(TransportException("invalid agent request"))
    return agentPost(
        "/api/app/writing-room/agent/turn",
        buildJsonObject {
            put("project_id", scope.projectId)
            put("conversation_id", scope.conversationId)
            put("request_id", requestId)
            put("prompt", prompt.trim())
            put("mode", mode)
        }.toString(),
    )
}

/** Scoped snapshot only; never silently poll latest in a different conversation. */
suspend fun JarvisAppSession.agentStatus(
    scope: AgentConversationScope,
    taskId: String,
): Result<AgentRunStatus> {
    if (!taskId.startsWith("ar_")) {
        return Result.failure(TransportException("invalid agent run"))
    }
    return agentPost(
        "/api/app/writing-room/agent/status",
        buildJsonObject {
            put("project_id", scope.projectId)
            put("conversation_id", scope.conversationId)
            put("task_id", taskId)
        }.toString(),
    )
}

/** The owner approves one frozen action hash, not an open-ended confirmed flag. */
suspend fun JarvisAppSession.agentConfirm(
    scope: AgentConversationScope,
    taskId: String,
    actionHash: String,
    mode: String = "auto",
): Result<AgentRunStatus> {
    if (!taskId.startsWith("ar_") ||
        !Regex("^[a-f0-9]{64}$").matches(actionHash)
    ) return Result.failure(TransportException("invalid agent approval"))
    return agentPost(
        "/api/app/writing-room/agent/confirm",
        buildJsonObject {
            put("project_id", scope.projectId)
            put("conversation_id", scope.conversationId)
            put("task_id", taskId)
            put("action_hash", actionHash)
            put("confirmed", true)
            put("mode", mode)
        }.toString(),
    )
}

/** Event polling returns durable sequence numbers, not another model inference. */
suspend fun JarvisAppSession.agentEvents(
    scope: AgentConversationScope,
    taskId: String,
    afterSequence: Int = -1,
): Result<AgentEventPage> {
    if (!Regex("^ar_[a-f0-9]{24}$").matches(taskId) || afterSequence < -1) {
        return Result.failure(TransportException("invalid agent event cursor"))
    }
    return agentPost(
        "/api/app/writing-room/agent/events",
        buildJsonObject {
            put("project_id", scope.projectId)
            put("conversation_id", scope.conversationId)
            put("task_id", taskId)
            put("after_seq", afterSequence)
        }.toString(),
    )
}

/** Request a safe stop at the next agent boundary, not cancellation of a
 * provider call whose result may already have been committed remotely. */
suspend fun JarvisAppSession.agentCancel(
    scope: AgentConversationScope,
    taskId: String,
): Result<AgentCancellation> {
    if (!Regex("^ar_[a-f0-9]{24}$").matches(taskId)) {
        return Result.failure(TransportException("invalid agent run"))
    }
    return agentPost(
        "/api/app/writing-room/agent/cancel",
        buildJsonObject {
            put("project_id", scope.projectId)
            put("conversation_id", scope.conversationId)
            put("task_id", taskId)
        }.toString(),
    )
}

@Serializable
data class ChatGlobalProjectAgentCapability(
    val schema: String = "",
    val enabled: Boolean = false,
    val project_id: String = "",
    val effect: String = "",
    val supports_attachments: Boolean = false,
    val attachment_policy: String = "",
)

/** Authorization + rollout probe. Never requests a model turn. */
suspend fun JarvisAppSession.chatGlobalProjectAgentCapability(
    projectId: String,
): Result<ChatGlobalProjectAgentCapability> {
    if (projectId.isBlank() || projectId.length > 160) {
        return Result.failure(TransportException("invalid agent project"))
    }
    return agentPost(
        "/api/app/chat/agent/project/capability",
        buildJsonObject { put("project_id", projectId) }.toString(),
    )
}

@Serializable
data class ChatGlobalProjectAgentReplyText(
    val text: String = "",
    val provider: String = "",
    val model: String = "",
)

@Serializable
data class ChatGlobalProjectAgentReplyScope(
    val request_id: String = "",
    val conversation_id: String = "",
    val trace_id: String = "",
    val device_id: String = "",
    val session_id: String = "",
)

@Serializable
data class ChatGlobalProjectAgentReply(
    val schema: String = "",
    val route: String = "",
    val request: ChatGlobalProjectAgentReplyScope? = null,
    val response: ChatGlobalProjectAgentReplyText = ChatGlobalProjectAgentReplyText(),
    val agent_task: AgentRunStatus? = null,
    val content: JsonObject? = null,
)

suspend fun JarvisAppSession.chatGlobalProjectAgentTurn(
    projectId: String,
    conversationId: String,
    requestId: String,
    prompt: String,
    mode: String = "auto",
    images: JsonArray? = null,
    documents: JsonArray? = null,
    frozenTraceId: String? = null,
    frozenDeviceId: String? = null,
    frozenSessionId: String? = null,
): Result<ChatGlobalProjectAgentReply> {
    if (projectId.isBlank() || conversationId.isBlank() ||
        requestId.isBlank() || prompt.isBlank() ||
        projectId.length > 160 || conversationId.length > 160 ||
        requestId.length > 160 || prompt.length > 6000 ||
        mode !in setOf("auto", "local_only", "local_preferred", "cloud_only")
    ) return Result.failure(TransportException("invalid project agent scope"))

    val body = buildJsonObject {
        put("project_id", projectId)
        put("conversation_id", conversationId)
        put("request_id", requestId)
        put("trace_id", frozenTraceId ?: UUID.randomUUID().toString())
        put("device_id", frozenDeviceId ?: deviceId)
        put("session_id", frozenSessionId ?: appSessionId)
        put("mode", mode)
        if (images != null && images.isNotEmpty()) put("images", images)
        if (documents != null && documents.isNotEmpty()) put("documents", documents)
        put("messages", buildJsonArray {
            addJsonObject {
                put("role", "user")
                put("content", prompt)
            }
        })
    }.toString()
    return agentPost("/api/app/chat/agent/project/turn", body)
}
