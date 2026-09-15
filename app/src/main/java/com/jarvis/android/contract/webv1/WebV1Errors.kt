package com.jarvis.android.contract.webv1

import com.jarvis.android.contract.ErrorEnvelope
import com.jarvis.android.transport.TransportException

/**
 * Central parsing of PC-A's typed Web V1 HTTP error envelope
 * (`{"schema":"jarvis.web.error.v1","error":...,"message":...}`).
 *
 * Rules:
 *  - A recognized typed error maps `code`/safe `message` into a
 *    [TransportException] with contract-defined retryability; the raw
 *    body is never kept as the message.
 *  - Malformed/untyped bodies degrade to a generic code
 *    (`gateway_unavailable` for 5xx, `invalid_response` otherwise).
 *  - A typed error whose `contract_fingerprint` doesn't match is
 *    protocol drift (mismatch), not a normal failure.
 */
object WebV1Errors {

    fun fromHttp(status: Int, body: String?): TransportException {
        val typed = body?.let { WebV1Codec.parseTypedError(it) }
        if (typed != null) {
            val code = typed.error ?: typed.code ?: "invalid_request"
            if (typed.contractFingerprint != null && typed.contractFingerprint != WebV1.FINGERPRINT) {
                return TransportException(
                    message = "protocol mismatch",
                    code = "protocol_version_mismatch",
                    retryable = false,
                )
            }
            if (code == "protocol_version_mismatch") {
                return TransportException(
                    message = typed.message?.take(200) ?: "protocol mismatch",
                    code = code,
                    retryable = false,
                )
            }
            val retryable = typed.retryable ?: retryableErrorCode(code) ?: (status in 500..599)
            return TransportException(
                message = typed.message?.take(200) ?: "gateway error ($code)",
                code = code,
                retryable = retryable,
            )
        }
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
        )
    }

    fun toInternalError(ex: TransportException): ErrorEnvelope = ErrorEnvelope(
        code = ex.code ?: "transport_error",
        message = ex.message ?: "transport failure",
    )

    fun retryableErrorCode(code: String): Boolean? = when (code) {
        "gateway_unavailable", "executor_unavailable" -> true
        "protocol_version_mismatch", "invalid_request", "invalid_event",
        "duplicate_request", "cancelled", "approval_required",
        "unknown_remote_state", "replay_ignored",
        -> false
        else -> null
    }
}
