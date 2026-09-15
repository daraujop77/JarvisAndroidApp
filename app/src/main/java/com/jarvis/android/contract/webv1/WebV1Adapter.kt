package com.jarvis.android.contract.webv1

import com.jarvis.android.contract.ApprovalOutcome
import com.jarvis.android.contract.ApprovalRequiredPayload
import com.jarvis.android.contract.ApprovalResolvedPayload
import com.jarvis.android.contract.ApprovalTier
import com.jarvis.android.contract.ConnectionPayload
import com.jarvis.android.contract.ContractVersion
import com.jarvis.android.contract.ErrorEnvelope
import com.jarvis.android.contract.EventEnvelope
import com.jarvis.android.contract.GatewayEvent
import com.jarvis.android.contract.MobileRequest
import com.jarvis.android.contract.ServerConnectionState
import com.jarvis.android.contract.TaskStatus
import com.jarvis.android.contract.TaskUpdatedPayload
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Thin Web V1 ↔ internal domain adapter (PCB-R0 strategy B).
 *
 * The reducer/UI keep the existing [EventEnvelope] / [GatewayEvent] types.
 * Wire JSON is always the frozen PC-A shape. Cursor stays an opaque string
 * on the wire; internally we store that same string (never parse as Long).
 */
object WebV1Adapter {

    sealed interface Inbound {
        data class Domain(
            val envelope: EventEnvelope,
            val opaqueCursor: String,
            val protocolVersion: String = WebV1.VERSION,
            val fingerprint: String? = WebV1.FINGERPRINT,
        ) : Inbound
        data class IgnoreOptional(
            val type: String,
            val opaqueCursor: String,
            val protocolVersion: String = WebV1.VERSION,
            val fingerprint: String? = WebV1.FINGERPRINT,
        ) : Inbound
        data class ProtocolMismatch(val reason: String) : Inbound
        data class Malformed(val reason: String) : Inbound
    }

    fun inbound(raw: String): Inbound = when (val decoded = WebV1Codec.decodeEvent(raw)) {
        is WebV1Decode.Malformed -> Inbound.Malformed(decoded.reason)
        is WebV1Decode.Ok -> inbound(decoded.event)
    }

    fun inbound(wire: WebV1Event): Inbound {
        if (!WebV1Codec.protocolCompatible(wire.protocolVersion, wire.contractFingerprint)) {
            return Inbound.ProtocolMismatch(
                "incompatible protocol ${wire.protocolVersion}/${wire.contractFingerprint}; " +
                    "client ${WebV1.PROTOCOL} ${WebV1.VERSION} ${WebV1.FINGERPRINT}",
            )
        }
        val event = toDomainEvent(wire) ?: run {
            return if (wire.optional) {
                Inbound.IgnoreOptional(wire.type, wire.cursor, wire.protocolVersion, wire.contractFingerprint)
            } else {
                Inbound.Domain(
                    EventEnvelope(
                        cursor = 0,
                        eventId = wire.eventId,
                        version = ContractVersion.SUPPORTED,
                        timestampMs = parseUtcMillis(wire.timestampUtc),
                        event = GatewayEvent.Unknown(wire.type),
                    ),
                    opaqueCursor = wire.cursor,
                    protocolVersion = wire.protocolVersion,
                    fingerprint = wire.contractFingerprint,
                )
            }
        }
        return Inbound.Domain(
            EventEnvelope(
                cursor = 0, // numeric field retained for Fake-path tests; ignored when opaqueCursor is set
                eventId = wire.eventId,
                version = ContractVersion.SUPPORTED,
                timestampMs = parseUtcMillis(wire.timestampUtc),
                event = event,
            ),
            opaqueCursor = wire.cursor,
            protocolVersion = wire.protocolVersion,
            fingerprint = wire.contractFingerprint,
        )
    }

    fun outbound(req: MobileRequest, identity: WebV1Identity = WebV1Identity()): WebV1Request = when (req) {
        is MobileRequest.SendMessage -> requestEnvelope(
            identity = identity,
            conversationId = req.conversationId,
            requestId = req.clientRequestId,
            operation = "submit",
            payload = buildJsonObject {
                put("text", req.text)
                put("client_request_id", req.clientRequestId)
            },
        )
        is MobileRequest.CancelRequest -> {
            val conversationId = req.conversationId.ifBlank { identity.conversationId.orEmpty() }
            requestEnvelope(
                identity = identity,
                conversationId = conversationId,
                requestId = WebV1ScopeIds.cancelRequestId(req.targetRequestId),
                operation = "cancel",
                targetRequestId = req.targetRequestId,
            )
        }
        is MobileRequest.Replay -> outboundReplay(req.sinceCursor, identity, req.conversationId)
        is MobileRequest.ResolveApproval -> {
            val conversationId = req.conversationId.ifBlank { identity.conversationId.orEmpty() }
            requestEnvelope(
                identity = identity,
                conversationId = conversationId,
                requestId = req.resolutionId,
                operation = "action",
                sideEffecting = true,
                idempotencyKey = req.resolutionId,
                actionId = req.approvalId,
                payload = buildJsonObject {
                    put("approval_id", req.approvalId)
                    put("outcome", req.outcome.name.lowercase())
                    put("resolution_id", req.resolutionId)
                },
            )
        }
        is MobileRequest.UploadAttachment -> requestEnvelope(
            identity = identity,
            conversationId = req.conversationId,
            requestId = req.attachmentId,
            operation = "action",
            sideEffecting = true,
            idempotencyKey = req.attachmentId,
            payload = buildJsonObject {
                put("kind", "attachment.upload")
                put("attachment_id", req.attachmentId)
                put("filename", req.filename)
                put("mime_type", req.mimeType)
                put("size_bytes", req.sizeBytes)
            },
        )
    }

    fun outboundReplay(
        opaqueCursor: String,
        identity: WebV1Identity = WebV1Identity(),
        conversationId: String = "",
    ): WebV1Request {
        val cid = conversationId.ifBlank { identity.conversationId.orEmpty() }
        val sessionId = identity.sessionId.orEmpty()
        return requestEnvelope(
            identity = identity,
            conversationId = cid,
            requestId = WebV1ScopeIds.resumeRequestId(sessionId, cid, opaqueCursor),
            operation = "resume",
            after = opaqueCursor.ifBlank { null },
        )
    }

    private fun requestEnvelope(
        identity: WebV1Identity,
        conversationId: String,
        requestId: String,
        operation: String,
        payload: kotlinx.serialization.json.JsonObject = buildJsonObject { },
        sideEffecting: Boolean = false,
        idempotencyKey: String? = null,
        actionId: String? = null,
        targetRequestId: String? = null,
        after: String? = null,
    ): WebV1Request {
        val cid = conversationId.ifBlank { identity.conversationId.orEmpty() }.ifBlank { "conversation" }
        val rid = requestId.ifBlank { identity.requestId.orEmpty() }.ifBlank { "request" }
        val userId = identity.userId.orEmpty().ifBlank { "user" }
        val deviceId = identity.deviceId.orEmpty().ifBlank { "device" }
        val sessionId = identity.sessionId.orEmpty().ifBlank { "session" }
        val taskId = identity.taskId?.takeIf { it.isNotBlank() } ?: WebV1ScopeIds.taskId(cid)
        val runId = identity.runId?.takeIf { it.isNotBlank() } ?: WebV1ScopeIds.runId(cid, rid)
        val traceId = identity.traceId?.takeIf { it.isNotBlank() } ?: WebV1ScopeIds.traceId(sessionId, rid)
        return WebV1Request(
            request_id = WebV1ScopeIds.clamp(rid),
            trace_id = WebV1ScopeIds.clamp(traceId),
            user_id = WebV1ScopeIds.clamp(userId),
            device_id = WebV1ScopeIds.clamp(deviceId),
            session_id = WebV1ScopeIds.clamp(sessionId),
            conversation_id = WebV1ScopeIds.clamp(cid),
            task_id = WebV1ScopeIds.clamp(taskId),
            run_id = WebV1ScopeIds.clamp(runId),
            operation = operation,
            payload = payload,
            action_id = actionId?.takeIf { it.isNotBlank() }?.let(WebV1ScopeIds::clamp),
            targetRequestId = targetRequestId?.takeIf { it.isNotBlank() }?.let(WebV1ScopeIds::clamp),
            idempotencyKey = idempotencyKey?.takeIf { it.isNotBlank() }?.let(WebV1ScopeIds::clamp),
            sideEffecting = sideEffecting,
            after = after,
        )
    }

    private fun toDomainEvent(wire: WebV1Event): GatewayEvent? {
        val p = wire.payload
        val requestId = wire.request_id ?: p.string("request_id") ?: ""
        return when (wire.type) {
            "connection.ready" -> GatewayEvent.ConnectionState(
                ConnectionPayload(ServerConnectionState.ONLINE, p.string("reason")),
            )
            "connection.degraded" -> GatewayEvent.ConnectionState(
                ConnectionPayload(ServerConnectionState.DEGRADED, p.string("reason")),
            )
            "state.changed" -> {
                val raw = (p.string("state") ?: p.string("connection") ?: "").uppercase()
                val state = when (raw) {
                    "ONLINE", "READY" -> ServerConnectionState.ONLINE
                    "DEGRADED" -> ServerConnectionState.DEGRADED
                    "OFFLINE" -> ServerConnectionState.OFFLINE
                    "AUTH_EXPIRED", "EXPIRED" -> ServerConnectionState.AUTH_EXPIRED
                    "DEVICE_REVOKED", "REVOKED" -> ServerConnectionState.DEVICE_REVOKED
                    else -> return GatewayEvent.Unknown(wire.type)
                }
                GatewayEvent.ConnectionState(ConnectionPayload(state, p.string("reason")))
            }
            "message.accepted" -> GatewayEvent.MessageAccepted(
                requestId = requestId,
                conversationId = wire.conversation_id ?: p.string("conversation_id") ?: "",
                messageId = p.string("message_id") ?: requestId,
            )
            "message.delta" -> GatewayEvent.MessageDelta(
                requestId = requestId,
                messageId = p.string("message_id") ?: requestId,
                seq = p.int("seq") ?: 0,
                text = p.string("delta") ?: p.string("text") ?: "",
            )
            "message.completed" -> GatewayEvent.MessageCompleted(
                requestId = requestId,
                messageId = p.string("message_id") ?: requestId,
                fullText = p.string("full_text") ?: p.string("text"),
            )
            "message.failed" -> GatewayEvent.MessageFailed(
                requestId = requestId,
                messageId = p.string("message_id"),
                error = ErrorEnvelope(
                    p.string("code") ?: "failed",
                    p.string("message") ?: "request failed",
                    p.string("details"),
                ),
                retryable = p.bool("retryable") ?: false,
            )
            "approval.required" -> GatewayEvent.ApprovalRequired(
                ApprovalRequiredPayload(
                    approvalId = p.string("approval_id") ?: wire.action_id ?: "",
                    requestId = requestId.ifBlank { null },
                    title = p.string("title") ?: "Approval required",
                    description = p.string("description"),
                    tier = runCatching {
                        ApprovalTier.valueOf((p.string("tier") ?: "NORMAL").uppercase())
                    }.getOrDefault(ApprovalTier.NORMAL),
                    risk = p.string("risk"),
                    expiresAtMs = p.string("expires_at")?.let { parseUtcMillis(it) },
                ),
            )
            "approval.resolved" -> GatewayEvent.ApprovalResolved(
                ApprovalResolvedPayload(
                    approvalId = p.string("approval_id") ?: wire.action_id ?: "",
                    outcome = runCatching {
                        ApprovalOutcome.valueOf((p.string("outcome") ?: "CANCELLED").uppercase())
                    }.getOrDefault(ApprovalOutcome.CANCELLED),
                    resolvedBy = p.string("resolved_by"),
                    resolvedAtMs = parseUtcMillis(p.string("resolved_at") ?: wire.timestampUtc),
                ),
            )
            "task.started", "task.progress", "task.completed", "task.failed" -> {
                val status = when (wire.type) {
                    "task.started" -> TaskStatus.STARTED
                    "task.progress" -> TaskStatus.RUNNING
                    "task.completed" -> TaskStatus.COMPLETED
                    else -> TaskStatus.FAILED
                }
                GatewayEvent.TaskUpdated(
                    TaskUpdatedPayload(
                        taskId = wire.task_id ?: p.string("task_id") ?: "",
                        requestId = requestId.ifBlank { null },
                        status = status,
                        progress = p.string("progress")?.toFloatOrNull() ?: p.int("percent")?.div(100f),
                        label = p.string("label"),
                    ),
                )
            }
            "error" -> GatewayEvent.MessageFailed(
                requestId = requestId.ifBlank { "error" },
                error = ErrorEnvelope(
                    p.string("code") ?: "error",
                    p.string("message") ?: "gateway error",
                    p.string("details"),
                ),
                retryable = p.bool("retryable") ?: false,
            )
            "attachment.ready" -> GatewayEvent.AttachmentReady(
                com.jarvis.android.contract.AttachmentPayload(
                    attachmentId = p.string("attachment_id") ?: "",
                    requestId = requestId.ifBlank { null },
                    kind = p.string("kind") ?: "image",
                    mimeType = p.string("mime_type"),
                    sizeBytes = p.string("size_bytes")?.toLongOrNull() ?: p.int("size_bytes")?.toLong(),
                    ready = true,
                ),
            )
            "attachment.failed" -> GatewayEvent.AttachmentFailed(
                com.jarvis.android.contract.AttachmentPayload(
                    attachmentId = p.string("attachment_id") ?: "",
                    requestId = requestId.ifBlank { null },
                    kind = p.string("kind") ?: "image",
                    mimeType = p.string("mime_type"),
                    sizeBytes = p.string("size_bytes")?.toLongOrNull() ?: p.int("size_bytes")?.toLong(),
                    ready = false,
                    error = ErrorEnvelope(
                        p.string("code") ?: "upload_failed",
                        p.string("message") ?: "attachment failed",
                    ),
                ),
            )
            "router.decision", "tool.started", "tool.completed",
            "action.proposed", "action.started", "action.completed" ->
                GatewayEvent.Unknown(wire.type)
            else -> null
        }
    }

    /**
     * Domain → frozen Web V1 wire. Fake Gateway and the HTTP adapter both
     * serialize through this so there is only one wire shape.
     */
    fun domainToWire(
        event: GatewayEvent,
        cursor: String,
        eventId: String,
        timestampUtc: String = java.time.Instant.now().toString(),
        protocolVersion: String = WebV1.VERSION,
        fingerprint: String? = WebV1.FINGERPRINT,
        optional: Boolean = false,
        identity: WebV1Identity = WebV1Identity(),
        sequence: Long = 1,
    ): WebV1Event {
        var type = "unknown"
        var requestId: String? = event.requestId
        var conversationId: String? = null
        var taskId: String? = null
        var actionId: String? = null
        val payload = buildJsonObject {
            when (event) {
                is GatewayEvent.ConnectionState -> {
                    type = when (event.payload.state) {
                        ServerConnectionState.ONLINE -> "connection.ready"
                        ServerConnectionState.DEGRADED -> "connection.degraded"
                        else -> "state.changed"
                    }
                    if (type == "state.changed") put("state", event.payload.state.name)
                    event.payload.reason?.let { put("reason", it) }
                }
                is GatewayEvent.MessageAccepted -> {
                    type = "message.accepted"
                    conversationId = event.conversationId
                    put("message_id", event.messageId)
                    put("conversation_id", event.conversationId)
                }
                is GatewayEvent.MessageDelta -> {
                    type = "message.delta"
                    put("message_id", event.messageId)
                    put("seq", event.seq)
                    put("delta", event.text)
                }
                is GatewayEvent.MessageCompleted -> {
                    type = "message.completed"
                    put("message_id", event.messageId)
                    event.fullText?.let { put("text", it) }
                }
                is GatewayEvent.MessageFailed -> {
                    type = "message.failed"
                    event.messageId?.let { put("message_id", it) }
                    put("code", event.error.code)
                    put("message", event.error.message)
                    event.error.details?.let { put("details", it) }
                    put("retryable", event.retryable)
                }
                is GatewayEvent.ApprovalRequired -> {
                    type = "approval.required"
                    actionId = event.payload.approvalId
                    requestId = event.payload.requestId
                    put("approval_id", event.payload.approvalId)
                    put("title", event.payload.title)
                    event.payload.description?.let { put("description", it) }
                    put("tier", event.payload.tier.name)
                    event.payload.risk?.let { put("risk", it) }
                    event.payload.expiresAtMs?.let {
                        put("expires_at", java.time.Instant.ofEpochMilli(it).toString())
                    }
                }
                is GatewayEvent.ApprovalResolved -> {
                    type = "approval.resolved"
                    actionId = event.payload.approvalId
                    put("approval_id", event.payload.approvalId)
                    put("outcome", event.payload.outcome.name.lowercase())
                    event.payload.resolvedBy?.let { put("resolved_by", it) }
                    event.payload.resolvedAtMs?.let {
                        put("resolved_at", java.time.Instant.ofEpochMilli(it).toString())
                    }
                }
                is GatewayEvent.TaskUpdated -> {
                    type = when (event.payload.status) {
                        TaskStatus.QUEUED, TaskStatus.STARTED -> "task.started"
                        TaskStatus.RUNNING -> "task.progress"
                        TaskStatus.COMPLETED -> "task.completed"
                        TaskStatus.FAILED, TaskStatus.CANCELLED -> "task.failed"
                    }
                    taskId = event.payload.taskId
                    requestId = event.payload.requestId
                    put("task_id", event.payload.taskId)
                    event.payload.progress?.let { put("progress", it.toString()) }
                    event.payload.label?.let { put("label", it) }
                }
                is GatewayEvent.AttachmentReady -> {
                    type = "attachment.ready"
                    requestId = event.payload.requestId
                    put("attachment_id", event.payload.attachmentId)
                    put("kind", event.payload.kind)
                    event.payload.mimeType?.let { put("mime_type", it) }
                    event.payload.sizeBytes?.let { put("size_bytes", it) }
                }
                is GatewayEvent.AttachmentFailed -> {
                    type = "attachment.failed"
                    requestId = event.payload.requestId
                    put("attachment_id", event.payload.attachmentId)
                    put("kind", event.payload.kind)
                    event.payload.mimeType?.let { put("mime_type", it) }
                    event.payload.sizeBytes?.let { put("size_bytes", it) }
                    event.payload.error?.let {
                        put("code", it.code)
                        put("message", it.message)
                    }
                }
                is GatewayEvent.Unknown -> {
                    type = event.unknownType
                }
            }
        }
        val cid = (conversationId ?: identity.conversationId).orEmpty().ifBlank { "conversation" }
        val rid = requestId ?: identity.requestId
        val sessionId = identity.sessionId.orEmpty().ifBlank { "session" }
        val userId = identity.userId.orEmpty().ifBlank { "user" }
        val deviceId = identity.deviceId.orEmpty().ifBlank { "device" }
        val resolvedTask = taskId ?: identity.taskId ?: WebV1ScopeIds.taskId(cid)
        val resolvedRun = identity.runId ?: WebV1ScopeIds.runId(cid, rid ?: eventId)
        val resolvedTrace = identity.traceId ?: WebV1ScopeIds.traceId(sessionId, rid ?: eventId)
        return WebV1Event(
            schema = WebV1.EVENT_SCHEMA,
            protocolVersion = protocolVersion,
            contractFingerprint = fingerprint ?: WebV1.FINGERPRINT,
            eventId = eventId,
            sequence = sequence.coerceAtLeast(1),
            cursor = cursor,
            type = type,
            timestampUtc = timestampUtc,
            user_id = userId,
            device_id = deviceId,
            session_id = sessionId,
            conversation_id = cid,
            request_id = rid,
            trace_id = resolvedTrace,
            task_id = resolvedTask,
            run_id = resolvedRun,
            action_id = actionId ?: identity.actionId,
            payload = payload,
            optional = optional,
        )
    }

    private fun parseUtcMillis(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        raw.toLongOrNull()?.let { return it }
        return runCatching {
            java.time.Instant.parse(raw).toEpochMilli()
        }.getOrNull()
    }
}
