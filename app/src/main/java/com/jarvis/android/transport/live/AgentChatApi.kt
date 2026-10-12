package com.jarvis.android.transport.live

import com.jarvis.android.transport.TransportException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

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

data class AgentConversationScope(
    val projectId: String,
    val conversationId: String,
) {
    init {
        require(projectId.isNotBlank() && projectId.length <= 160)
        require(conversationId.isNotBlank() && conversationId.length <= 160)
    }
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
        var response = post(baseUrl, path, body, auth)
        if (response.first == 401) {
            val renewed = refresh(staleBearer = auth)
            if (renewed.isFailure) throw renewed.exceptionOrNull()
                ?: TransportException("session expired")
            auth = authHeader() ?: throw TransportException("no live session")
            response = post(baseUrl, path, body, auth)
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
        prompt.isBlank() || prompt.length > 4000
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
