package com.jarvis.android.contract

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Shared-contract version this Android build supports. Bumped only through an
 * owner-approved contract task (plan §15). Unknown *minor* versions are tolerated
 * additively; unknown *major* versions produce ProtocolMismatch.
 */
object ContractVersion {
    const val MAJOR = 1
    const val MINOR = 0
    const val SUPPORTED = "1.0"
}

/**
 * Event envelope as delivered over the Gateway WebSocket.
 * All unknown fields are ignored (additive tolerance).
 */
@Serializable
data class EventEnvelope(
    /** Monotonic per-connection cursor used for replay (plan §6 replay cursor fields). */
    val cursor: Long,
    /** Unique event id — duplicates must be dropped by the reducer. */
    val eventId: String,
    /** Event schema version, e.g. "1.0". */
    val version: String = ContractVersion.SUPPORTED,
    /** Server timestamp (epoch millis). Optional; never trusted for ordering. */
    val timestampMs: Long? = null,
    val event: GatewayEvent,
)

/**
 * Tagged union of server events. `type` is the discriminator.
 * New types must arrive behind additive versioning; unknown types decode to [Unknown]
 * via [EventCodec] and are handled per plan §6 "unknown event handling".
 */
@Serializable
sealed interface GatewayEvent {
    val requestId: String?

    @Serializable
    @SerialName("message.accepted")
    data class MessageAccepted(
        override val requestId: String,
        val conversationId: String,
        val messageId: String,
    ) : GatewayEvent

    @Serializable
    @SerialName("message.delta")
    data class MessageDelta(
        override val requestId: String,
        val messageId: String,
        val seq: Int,
        val text: String,
    ) : GatewayEvent

    @Serializable
    @SerialName("message.completed")
    data class MessageCompleted(
        override val requestId: String,
        val messageId: String,
        val fullText: String? = null,
        val usage: TokenUsage? = null,
        val provider: String? = null,
        val model: String? = null,
        val route: String? = null,
        val destination: String? = null,
    ) : GatewayEvent

    @Serializable
    @SerialName("message.failed")
    data class MessageFailed(
        override val requestId: String,
        val messageId: String? = null,
        val error: ErrorEnvelope,
        val retryable: Boolean = false,
    ) : GatewayEvent

    @Serializable
    @SerialName("approval.required")
    data class ApprovalRequired(val payload: ApprovalRequiredPayload) : GatewayEvent {
        override val requestId: String? get() = payload.requestId
    }

    @Serializable
    @SerialName("approval.resolved")
    data class ApprovalResolved(val payload: ApprovalResolvedPayload) : GatewayEvent {
        override val requestId: String? get() = null
    }

    @Serializable
    @SerialName("task.updated")
    data class TaskUpdated(val payload: TaskUpdatedPayload) : GatewayEvent {
        override val requestId: String? get() = payload.requestId
    }

    @Serializable
    @SerialName("connection.state")
    data class ConnectionState(val payload: ConnectionPayload) : GatewayEvent {
        override val requestId: String? get() = null
    }

    @Serializable
    @SerialName("attachment.ready")
    data class AttachmentReady(val payload: AttachmentPayload) : GatewayEvent {
        override val requestId: String? get() = null
    }

    @Serializable
    @SerialName("attachment.failed")
    data class AttachmentFailed(val payload: AttachmentPayload) : GatewayEvent {
        override val requestId: String? get() = null
    }

    /** Unknown event types — logged in diagnostics, never crash the reducer. */
    @Serializable
    @SerialName("unknown")
    data class Unknown(val unknownType: String) : GatewayEvent {
        override val requestId: String? get() = null
    }
}

@Serializable
data class TokenUsage(val inputTokens: Int = 0, val outputTokens: Int = 0)

@Serializable
data class ErrorEnvelope(
    val code: String,
    val message: String,
    val details: String? = null,
)

@Serializable
data class ApprovalRequiredPayload(
    val approvalId: String,
    val requestId: String? = null,
    val title: String,
    val description: String? = null,
    val tier: ApprovalTier = ApprovalTier.NORMAL,
    val risk: String? = null,
    val expiresAtMs: Long? = null,
    val actions: List<String> = listOf("approve", "deny"),
)

@Serializable
enum class ApprovalTier { LOW, NORMAL, SENSITIVE, CRITICAL }

@Serializable
data class ApprovalResolvedPayload(
    val approvalId: String,
    val outcome: ApprovalOutcome,
    val resolvedBy: String? = null,
    val resolvedAtMs: Long? = null,
)

@Serializable
enum class ApprovalOutcome { APPROVED, DENIED, EXPIRED, CANCELLED }

@Serializable
data class TaskUpdatedPayload(
    val taskId: String,
    val requestId: String? = null,
    val status: TaskStatus,
    val progress: Float? = null,
    val label: String? = null,
)

@Serializable
enum class TaskStatus { QUEUED, STARTED, RUNNING, COMPLETED, FAILED, CANCELLED }

@Serializable
data class ConnectionPayload(
    val state: ServerConnectionState,
    val reason: String? = null,
)

@Serializable
enum class ServerConnectionState { ONLINE, DEGRADED, OFFLINE, AUTH_EXPIRED, DEVICE_REVOKED }

@Serializable
data class AttachmentPayload(
    val attachmentId: String,
    val requestId: String? = null,
    val kind: String = "image",
    val mimeType: String? = null,
    val sizeBytes: Long? = null,
    val ready: Boolean = true,
    val error: ErrorEnvelope? = null,
)
