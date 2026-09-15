package com.jarvis.android.contract.webv1

import com.jarvis.android.contract.ErrorEnvelope
import com.jarvis.android.transport.TransportException

/**
 * Central parsing of PC-A's typed Web V1 HTTP error envelope.
 *
 * Required wire fields: schema, protocol_version, contract_fingerprint,
 * status_code, error (or code), message, retryable, request_id, trace_id.
 * `request_id` / `trace_id` may be JSON null; they may not be absent.
 * Incomplete typed envelopes fail closed. Untyped/malformed bodies never
 * echo the raw payload (no secrets in messages).
 */
object WebV1Errors {

    fun fromHttp(status: Int, body: String?): TransportException {
        if (body.isNullOrBlank()) return untyped(status)
        if (WebV1Codec.looksLikeTypedError(body)) {
            return when (val decoded = WebV1Codec.decodeError(body)) {
                is WebV1ErrorDecode.Ok -> fromTyped(decoded.error)
                is WebV1ErrorDecode.ProtocolMismatch -> TransportException(
                    message = decoded.reason.take(200),
                    code = "protocol_version_mismatch",
                    retryable = false,
                    statusCode = status,
                )
                is WebV1ErrorDecode.Malformed -> TransportException(
                    message = "invalid typed error envelope",
                    code = "invalid_response",
                    retryable = false,
                    statusCode = status,
                )
            }
        }
        return untyped(status)
    }

    fun toInternalError(ex: TransportException): ErrorEnvelope = ErrorEnvelope(
        code = ex.code ?: "transport_error",
        message = ex.message ?: "transport failure",
        details = listOfNotNull(
            ex.statusCode?.let { "status=$it" },
            ex.requestId?.let { "request_id=$it" },
            ex.traceId?.let { "trace_id=$it" },
        ).joinToString("; ").ifBlank { null },
    )

    fun retryableErrorCode(code: String): Boolean? = when (code) {
        "gateway_unavailable", "executor_unavailable" -> true
        "protocol_version_mismatch", "invalid_request", "invalid_event",
        "duplicate_request", "cancelled", "approval_required",
        "unknown_remote_state", "replay_ignored",
        -> false
        else -> null
    }

    private fun fromTyped(typed: WebV1Error): TransportException {
        val retryable = typed.retryable
        return TransportException(
            message = typed.message.take(200),
            code = typed.error,
            retryable = retryable,
            statusCode = typed.statusCode,
            requestId = typed.request_id,
            traceId = typed.trace_id,
        )
    }

    private fun untyped(status: Int): TransportException {
        val code = when (status) {
            401, 403 -> "invalid_request"
            in 500..599 -> "gateway_unavailable"
            else -> "invalid_response"
        }
        return TransportException(
            message = when (status) {
                401, 403 -> "authentication failed ($status)"
                in 500..599 -> "gateway unavailable ($status)"
                else -> "gateway request failed ($status)"
            },
            code = code,
            retryable = status in 500..599,
            statusCode = status,
        )
    }
}
