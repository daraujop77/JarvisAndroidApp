package com.jarvis.android.transport.live

import com.jarvis.android.data.repo.ChatSourcesCodec
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.buildJsonArray

/**
 * Request-status returns durable tool receipts, not the original rich SSE
 * envelope. Reconstruct only public citations from a successful, typed
 * research tool. The same Global Chat URL+SHA-256 validator is applied again.
 * Never turn failed tools, model text, Wiki references or malformed links
 * into clickable external sources.
 */
internal fun recoveredCopilotPublicSources(task: CopilotTaskState): List<JarvisChatContentSource> {
    val rawSources = buildJsonArray {
        task.results.forEach { receipt ->
            if (receipt.tool !in setOf("web_search", "web_extract") ||
                receipt.status != "SUCCEEDED" ||
                receipt.result["authority"]?.jsonPrimitive?.content != "EXTERNAL_UNTRUSTED" ||
                receipt.result["status"]?.jsonPrimitive?.content != "READY"
            ) return@forEach
            val sources = receipt.result["sources"] as? JsonArray ?: return@forEach
            sources.take(5).filterIsInstance<JsonObject>().forEach { add(it) }
        }
    }
    return ChatSourcesCodec.decode(rawSources.toString()).take(5).map {
        JarvisChatContentSource(ref = it.ref, title = it.title, url = it.url)
    }
}

/**
 * Rebuild only a response already saved for the exact authenticated agent
 * request. No retry, fake model answer, privilege upgrade or cross-chat data.
 */
internal fun recoveredCopilotChat(
    projectId: String,
    conversationId: String,
    requestId: String,
    task: CopilotTaskState,
): WritingRoomAutoChat? {
    if (projectId.isBlank() || conversationId.isBlank() || requestId.isBlank() ||
        task.project_id != projectId ||
        task.conversation_id != conversationId ||
        task.request_id != requestId ||
        !task.task_id.startsWith("ar_")
    ) return null
    if (task.state == "NONE") return null

    val pending = task.agent_run_state in setOf("RUNNING", "DISPATCHING") ||
        task.state in setOf("RUNNING", "DISPATCHING")
    val text = when {
        task.reply_text.isNotBlank() -> task.reply_text
        pending -> "Se interrumpió la conexión. Copilot sigue trabajando; consulta el estado de la tarea."
        else -> task.summary.ifBlank {
            "Se recuperó el estado de la tarea; no se recibió una respuesta completa."
        }
    }
    val approved = task.results.asSequence()
        .flatMap { it.visual_assets.asSequence() }
        .filter {
            it.status == "APPROVED" && it.storage_state == "stored" &&
                Regex("^va_[A-Za-z0-9_]{6,80}$").matches(it.asset_id) &&
                Regex("^[0-9a-fA-F]{64}$").matches(it.sha256)
        }
        .distinctBy { it.asset_id }.take(6).toList()

    return WritingRoomAutoChat(
        schema = "jarvis.writing-room.chat.v1",
        classification = WritingClassification(
            task_class = "COPILOT_AGENT",
            source = "exact_request_recovery",
        ),
        selected_participant = "copilot",
        turn = JarvisAppSession.WritingRoomTurn(
            schema = "jarvis.writing-room.turn.v1",
            project_id = projectId,
            participant = JarvisAppSession.WritingRoomParticipant(
                id = "copilot", label = "Writing Room Copilot",
            ),
            routing = JarvisAppSession.WritingRoomRouting(
                destination = "adaptive_agent",
            ),
            response = JarvisAppSession.WritingRoomText(text = text),
        ),
        copilot_task = task,
        visual_assets = approved,
        // Preserve exact public evidence on stream-loss/restart. No web request
        // is replayed, and no external result becomes project canon.
        content = JarvisChatContent(
            schema = "jarvis.chat.content.v1",
            blocks = listOf(JarvisChatContentBlock(type = "text", text = text)),
            sources = recoveredCopilotPublicSources(task),
        ),
        recovered_from_interruption = true,
    )
}
