package com.jarvis.android.data.state

import com.jarvis.android.contract.ApprovalOutcome
import com.jarvis.android.contract.ApprovalTier
import com.jarvis.android.contract.ErrorEnvelope
import com.jarvis.android.contract.TaskStatus

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    ONLINE,
    RECONNECTING,
    DEGRADED,
    OFFLINE,
    AUTH_EXPIRED,
    DEVICE_REVOKED,
    PROTOCOL_MISMATCH,
    ;

    val isUsable: Boolean get() = this == ONLINE || this == DEGRADED
}

sealed interface RequestStatus {
    data object Pending : RequestStatus
    data object Accepted : RequestStatus
    data object Streaming : RequestStatus
    data object Completed : RequestStatus
    data class Failed(val error: ErrorEnvelope, val retryable: Boolean) : RequestStatus
    data object Cancelling : RequestStatus
    data object Cancelled : RequestStatus

    val isTerminal: Boolean
        get() = this is Completed || this is Failed || this is Cancelled
}

/** Stable wire/DB names (data objects have no enum-style `name`). */
val RequestStatus.dbName: String
    get() = when (this) {
        RequestStatus.Pending -> "Pending"
        RequestStatus.Accepted -> "Accepted"
        RequestStatus.Streaming -> "Streaming"
        RequestStatus.Completed -> "Completed"
        is RequestStatus.Failed -> "Failed"
        RequestStatus.Cancelling -> "Cancelling"
        RequestStatus.Cancelled -> "Cancelled"
    }

fun requestStatusFromName(name: String): RequestStatus = when (name) {
    "Pending" -> RequestStatus.Pending
    "Accepted" -> RequestStatus.Accepted
    "Streaming" -> RequestStatus.Streaming
    "Completed" -> RequestStatus.Completed
    "Failed" -> RequestStatus.Failed(ErrorEnvelope("stored_failure", "previous attempt failed"), retryable = true)
    "Cancelling" -> RequestStatus.Cancelling
    "Cancelled" -> RequestStatus.Cancelled
    else -> RequestStatus.Pending
}

/**
 * One outbound request and the assistant message it produces.
 * Deltas are applied in `seq` order; out-of-order deltas are buffered until
 * contiguous so the UI never renders scrambled text (plan AND-W1 gate).
 */
data class RequestState(
    val clientRequestId: String,
    val conversationId: String,
    val userText: String,
    val status: RequestStatus = RequestStatus.Pending,
    val messageId: String? = null,
    val text: String = "",
    val nextSeq: Int = 0,
    val pendingDeltas: Map<Int, String> = emptyMap(),
    val startedAtMs: Long = 0,
)

data class ApprovalUiState(
    val approvalId: String,
    val requestId: String?,
    val title: String,
    val description: String?,
    val tier: ApprovalTier,
    val risk: String?,
    val expiresAtMs: Long?,
    val outcome: ApprovalOutcome? = null,
    val resolutionInFlight: Boolean = false,
    val resolvedAtMs: Long? = null,
)

data class TaskUiState(
    val taskId: String,
    val requestId: String?,
    val status: TaskStatus,
    val progress: Float?,
    val label: String?,
    val updatedAtMs: Long,
)

data class AttachmentUiState(
    val attachmentId: String,
    val requestId: String?,
    val kind: String,
    val mimeType: String?,
    val sizeBytes: Long?,
    /** Ready = server accepted + stored; Failed carries the error. */
    val ready: Boolean,
    val error: ErrorEnvelope? = null,
    /** Local staging state before/while upload happens. */
    val uploading: Boolean = false,
)

data class DiagnosticEntry(
    val timestampMs: Long,
    val kind: Kind,
    val detail: String,
) {
    enum class Kind { UNKNOWN_EVENT, MALFORMED_EVENT, DUPLICATE_EVENT, PROTOCOL_MISMATCH, TRANSPORT, REDUCER_NOTE }
}

/**
 * Immutable session state produced by [Reducer]. Pure data; the reducer is the
 * only place event semantics live (plan §8 Lane A "event reducer/state machines").
 */
data class SessionState(
    val connection: ConnectionState = ConnectionState.DISCONNECTED,
    /**
     * Why the link is down, in the transport's own words. The banner reads two
     * markers out of it: `pc_worker_offline` and `control_plane_unavailable`.
     * Empty when the link is fine or the cause is unknown.
     */
    val connectionDetail: String = "",
    /** Opaque Web V1 replay token. Empty means "from the beginning". */
    val lastCursorToken: String = "",
    /** Legacy numeric cursor used only by the in-process Fake Gateway path. */
    val lastCursor: Long = 0,
    val seenEventIds: Set<String> = emptySet(),
    val seenOrder: List<String> = emptyList(),
    val requests: Map<String, RequestState> = emptyMap(),
    /** serverRequestId -> clientRequestId index so cancel targets resolve. */
    val serverRequestIndex: Map<String, String> = emptyMap(),
    val approvals: Map<String, ApprovalUiState> = emptyMap(),
    val tasks: Map<String, TaskUiState> = emptyMap(),
    val attachments: Map<String, AttachmentUiState> = emptyMap(),
    val diagnostics: List<DiagnosticEntry> = emptyList(),
    /** Last Web V1 protocol_version observed on the wire ("" until first event). */
    val negotiatedProtocolVersion: String = "",
    /** Last Web V1 contract_fingerprint observed on the wire. */
    val negotiatedFingerprint: String = "",
) {
    val activeRequestCount: Int
        get() = requests.values.count { !it.status.isTerminal }

    fun diagnosticsCapped(): SessionState =
        if (diagnostics.size > MAX_DIAGNOSTICS) copy(diagnostics = diagnostics.takeLast(MAX_DIAGNOSTICS)) else this

    companion object {
        const val MAX_DIAGNOSTICS = 200
        const val SEEN_EVENTS_RETENTION = 512
    }
}
