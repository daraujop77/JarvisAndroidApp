package com.jarvis.android.transport.live

import com.jarvis.android.transport.TransportException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.addJsonObject
import java.util.UUID

/**
 * Versioned opt-in project agent request for Chat Global.
 *
 * General messages, images, documents and memory continue using the existing
 * /api/app/chat/stream. This route is deliberately restricted to read-only
 * project tools; the mobile Chat Global approval UI is not available yet.
 */
@Serializable
data class ChatGlobalProjectAgentReplyText(
    val text: String = "",
    val provider: String = "",
    val model: String = "",
)

@Serializable
data class ChatGlobalProjectAgentReply(
    val schema: String = "",
    val route: String = "",
    val response: ChatGlobalProjectAgentReplyText = ChatGlobalProjectAgentReplyText(),
    val agent_task: AgentRunStatus? = null,
)

suspend fun JarvisAppSession.chatGlobalProjectAgentTurn(
    projectId: String,
    conversationId: String,
    requestId: String,
    prompt: String,
    mode: String = "auto",
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
        put("trace_id", UUID.randomUUID().toString())
        put("device_id", deviceId)
        put("session_id", appSessionId)
        put("mode", mode)
        put("messages", buildJsonArray {
            addJsonObject {
                put("role", "user")
                put("content", prompt)
            }
        })
    }.toString()
    return agentPost("/api/app/chat/agent/project/turn", body)
}
