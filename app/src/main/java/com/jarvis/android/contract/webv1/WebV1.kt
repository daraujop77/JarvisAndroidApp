package com.jarvis.android.contract.webv1

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import java.security.MessageDigest

/**
 * Frozen PC-A Web V1 wire contract (`jarvis.web.v1 1.0`).
 *
 * Wire shape only. UI/reducer keep internal domain types; [WebV1Adapter]
 * translates. Models, required-field validation and errors lockstep with
 * `contracts/web-v1/` (spine-0.json, event/request schemas). Nothing here
 * invents server behavior. Fingerprint is pinned and must not change.
 */
object WebV1 {
    const val PROTOCOL = "jarvis.web.v1"
    const val VERSION = "1.0"
    const val VERSION_MAJOR = 1
    const val MIN_COMPATIBLE_MINOR = 0
    const val MAX_COMPATIBLE_MINOR = 0

    const val FINGERPRINT = "445e4013df96ad986232eaecec6c58c01bab472e2a55c5ec11c54536de552021"

    const val EVENT_SCHEMA = "jarvis.web.event.v1"
    const val REQUEST_SCHEMA = "jarvis.web.request.v1"
    const val APPROVAL_SCHEMA = "jarvis.web.approval.v1"
    const val CAPABILITIES_SCHEMA = "jarvis.web.capabilities.v1"
    const val ERROR_SCHEMA = "jarvis.web.error.v1"

    const val MAX_ID_LENGTH = 128
    const val MAX_CURSOR_LENGTH = 256
    const val MAX_TYPE_LENGTH = 128
    const val MAX_PAYLOAD_BYTES = 65536

    val KNOWN_EVENT_TYPES = setOf(
        "connection.ready",
        "connection.degraded",
        "state.changed",
        "message.accepted",
        "message.delta",
        "message.completed",
        "message.failed",
        "router.decision",
        "task.started",
        "task.progress",
        "task.completed",
        "task.failed",
        "tool.started",
        "tool.completed",
        "action.proposed",
        "action.started",
        "action.completed",
        "approval.required",
        "approval.resolved",
        "error",
    )

    val KNOWN_CAPABILITIES = listOf(
        "conversation",
        "streaming",
        "cancel",
        "replay",
        "approvals",
        "action_proposals",
    )

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
        encodeDefaults = true
        coerceInputValues = false
        explicitNulls = true
    }
}

@Serializable
data class WebV1Identity(
    @SerialName("user_id") val userId: String? = null,
    @SerialName("device_id") val deviceId: String? = null,
    @SerialName("session_id") val sessionId: String? = null,
    @SerialName("conversation_id") val conversationId: String? = null,
    @SerialName("request_id") val requestId: String? = null,
    @SerialName("trace_id") val traceId: String? = null,
    @SerialName("task_id") val taskId: String? = null,
    @SerialName("run_id") val runId: String? = null,
    @SerialName("action_id") val actionId: String? = null,
)

/**
 * Event envelope as frozen by PC-A. [cursor] is an opaque replay token —
 * never parse it as a number. Nullable identity fields are *present-null*
 * on the wire (schema `string | null`); they are never silently defaulted
 * during decode.
 */
@Serializable
data class WebV1Event(
    val schema: String,
    @SerialName("protocol_version") val protocolVersion: String,
    @SerialName("contract_fingerprint") val contractFingerprint: String,
    @SerialName("event_id") val eventId: String,
    val sequence: Long,
    val cursor: String,
    val type: String,
    @SerialName("timestamp_utc") val timestampUtc: String,
    val user_id: String,
    val device_id: String,
    val session_id: String,
    val conversation_id: String,
    val request_id: String?,
    val trace_id: String,
    val task_id: String?,
    val run_id: String?,
    val action_id: String?,
    val payload: JsonObject,
    val optional: Boolean,
) {
    val identity: WebV1Identity
        get() = WebV1Identity(
            userId = user_id,
            deviceId = device_id,
            sessionId = session_id,
            conversationId = conversation_id,
            requestId = request_id,
            traceId = trace_id,
            taskId = task_id,
            runId = run_id,
            actionId = action_id,
        )
}

@Serializable
data class WebV1Request(
    val schema: String = WebV1.REQUEST_SCHEMA,
    @SerialName("protocol_version") val protocolVersion: String = WebV1.VERSION,
    @SerialName("contract_fingerprint") val contractFingerprint: String = WebV1.FINGERPRINT,
    val request_id: String,
    val trace_id: String,
    val user_id: String,
    val device_id: String,
    val session_id: String,
    val conversation_id: String,
    val task_id: String,
    val run_id: String,
    val operation: String,
    val payload: JsonObject = JsonObject(emptyMap()),
    val action_id: String? = null,
    @SerialName("target_request_id") val targetRequestId: String? = null,
    @SerialName("idempotency_key") val idempotencyKey: String? = null,
    @SerialName("side_effecting") val sideEffecting: Boolean = false,
    val after: String? = null,
)

@Serializable
data class WebV1Health(
    val status: String,
    @SerialName("protocol_version") val protocolVersion: String = WebV1.VERSION,
    @SerialName("contract_fingerprint") val contractFingerprint: String? = null,
    val capabilities: List<String> = emptyList(),
)

/**
 * Official capabilities wire (`jarvis.web.capabilities.v1`).
 * Required: schema, protocol_version, server_version, capabilities,
 * contract_fingerprint. `operations` / `event_types` are derived after decode.
 */
@Serializable
data class WebV1Capabilities(
    val schema: String,
    @SerialName("protocol_version") val protocolVersion: String,
    @SerialName("server_version") val serverVersion: String,
    val capabilities: List<String>,
    @SerialName("contract_fingerprint") val contractFingerprint: String,
    @SerialName("disabled_capabilities") val disabledCapabilities: List<String> = emptyList(),
) {
    val operations: List<String>
        get() = buildList {
            if (capabilities.any { it == "conversation" || it == "streaming" }) add("submit")
            if (capabilities.contains("replay")) add("resume")
            if (capabilities.contains("cancel")) add("cancel")
            if (capabilities.any { it == "approvals" || it == "action_proposals" }) add("action")
        }.distinct()

    val eventTypes: List<String> get() = WebV1.KNOWN_EVENT_TYPES.toList()
}

@Serializable
data class WebV1Error(
    val schema: String,
    @SerialName("protocol_version") val protocolVersion: String,
    @SerialName("contract_fingerprint") val contractFingerprint: String,
    @SerialName("status_code") val statusCode: Int,
    val error: String,
    val message: String,
    val retryable: Boolean,
    val request_id: String?,
    val trace_id: String?,
) {
    val code: String get() = error
}

sealed interface WebV1Decode {
    data class Ok(val event: WebV1Event) : WebV1Decode
    data class Malformed(val reason: String) : WebV1Decode
}

sealed interface WebV1CapabilitiesDecode {
    data class Ok(val capabilities: WebV1Capabilities) : WebV1CapabilitiesDecode
    data class Malformed(val reason: String) : WebV1CapabilitiesDecode
    data class ProtocolMismatch(val reason: String) : WebV1CapabilitiesDecode
}

sealed interface WebV1ErrorDecode {
    data class Ok(val error: WebV1Error) : WebV1ErrorDecode
    data class Malformed(val reason: String) : WebV1ErrorDecode
    data class ProtocolMismatch(val reason: String) : WebV1ErrorDecode
}

object WebV1Codec {

    fun decodeEvent(raw: String): WebV1Decode = try {
        val obj = WebV1.json.parseToJsonElement(raw) as? JsonObject
            ?: return WebV1Decode.Malformed("not a JSON object")
        WebV1Decode.Ok(WebV1Validator.requireEvent(obj))
    } catch (t: WebV1ValidationException) {
        WebV1Decode.Malformed(t.message ?: "invalid event")
    } catch (t: Throwable) {
        WebV1Decode.Malformed(t.message ?: "decode failure")
    }

    fun decodeCapabilities(raw: String): WebV1CapabilitiesDecode = try {
        val obj = WebV1.json.parseToJsonElement(raw) as? JsonObject
            ?: return WebV1CapabilitiesDecode.Malformed("not a JSON object")
        val caps = WebV1Validator.requireCapabilities(obj)
        if (!protocolCompatible(caps.protocolVersion, caps.contractFingerprint)) {
            WebV1CapabilitiesDecode.ProtocolMismatch(
                "incompatible protocol ${caps.protocolVersion}/${caps.contractFingerprint}",
            )
        } else {
            WebV1CapabilitiesDecode.Ok(caps)
        }
    } catch (t: WebV1ValidationException) {
        WebV1CapabilitiesDecode.Malformed(t.message ?: "invalid capabilities")
    } catch (t: Throwable) {
        WebV1CapabilitiesDecode.Malformed(t.message ?: "decode failure")
    }

    fun decodeError(raw: String): WebV1ErrorDecode = try {
        val obj = WebV1.json.parseToJsonElement(raw) as? JsonObject
            ?: return WebV1ErrorDecode.Malformed("not a JSON object")
        val parsed = WebV1Validator.requireError(obj)
        if (!protocolCompatible(parsed.protocolVersion, parsed.contractFingerprint)) {
            WebV1ErrorDecode.ProtocolMismatch(
                "incompatible protocol ${parsed.protocolVersion}/${parsed.contractFingerprint}",
            )
        } else {
            WebV1ErrorDecode.Ok(parsed)
        }
    } catch (t: WebV1ValidationException) {
        WebV1ErrorDecode.Malformed(t.message ?: "invalid error")
    } catch (t: Throwable) {
        WebV1ErrorDecode.Malformed(t.message ?: "decode failure")
    }

    /** True when the body is attempting to be a typed Web V1 error (must then be complete). */
    fun looksLikeTypedError(raw: String): Boolean {
        val obj = runCatching { WebV1.json.parseToJsonElement(raw) as? JsonObject }.getOrNull()
            ?: return false
        val schema = obj.string("schema")
        if (schema == WebV1.ERROR_SCHEMA) return true
        return obj.containsKey("error") || obj.containsKey("status_code") || obj.containsKey("retryable")
    }

    fun parseTypedError(raw: String): WebV1Error? = when (val decoded = decodeError(raw)) {
        is WebV1ErrorDecode.Ok -> decoded.error
        else -> null
    }

    fun encodeEvent(event: WebV1Event): String =
        WebV1.json.encodeToString(WebV1Event.serializer(), event)

    /**
     * Request wire encoding omits UNSET optional fields (`explicitNulls=false`)
     * instead of emitting JSON null: PC-A's `validateRequest` treats a
     * present-but-null `action_id`/`target_request_id`/`idempotency_key` as
     * invalid. Set fields still serialize. Events keep explicit nulls.
     */
    private val requestJson = Json(from = WebV1.json) { explicitNulls = false }

    fun encodeRequest(req: WebV1Request): String =
        requestJson.encodeToString(WebV1Request.serializer(), req)

    fun protocolCompatible(version: String?, fingerprint: String?): Boolean {
        if (!WebV1Validator.isProtocolCompatible(version)) return false
        if (fingerprint != null && fingerprint != WebV1.FINGERPRINT) return false
        return true
    }
}

internal class WebV1ValidationException(message: String) : Exception(message)

internal object WebV1Validator {

    private val VERSION_RE = Regex("^[0-9]+\\.[0-9]+$")
    private val FINGERPRINT_RE = Regex("^[0-9a-f]{64}$")

    fun isProtocolCompatible(version: String?): Boolean {
        if (version.isNullOrBlank() || !VERSION_RE.matches(version)) return false
        val parts = version.split('.')
        val major = parts[0].toIntOrNull() ?: return false
        val minor = parts.getOrNull(1)?.toIntOrNull() ?: return false
        if (major != WebV1.VERSION_MAJOR) return false
        return minor in WebV1.MIN_COMPATIBLE_MINOR..WebV1.MAX_COMPATIBLE_MINOR
    }

    fun requireEvent(obj: JsonObject): WebV1Event {
        val schema = requireConstString(obj, "schema", WebV1.EVENT_SCHEMA)
        val protocolVersion = requireVersion(obj, "protocol_version")
        val fingerprint = requireFingerprint(obj, "contract_fingerprint")
        val eventId = requireId(obj, "event_id")
        val sequence = requireSequence(obj, "sequence")
        val cursor = requireBoundedString(obj, "cursor", 1, WebV1.MAX_CURSOR_LENGTH)
        val type = requireBoundedString(obj, "type", 1, WebV1.MAX_TYPE_LENGTH)
        val timestampUtc = requirePresentString(obj, "timestamp_utc")
        val userId = requireId(obj, "user_id")
        val deviceId = requireId(obj, "device_id")
        val sessionId = requireId(obj, "session_id")
        val conversationId = requireId(obj, "conversation_id")
        val requestId = requireNullableId(obj, "request_id")
        val traceId = requireId(obj, "trace_id")
        val taskId = requireNullableId(obj, "task_id")
        val runId = requireNullableId(obj, "run_id")
        val actionId = requireNullableId(obj, "action_id")
        val payload = requireObject(obj, "payload")
        val optional = requireBoolean(obj, "optional")
        return WebV1Event(
            schema = schema,
            protocolVersion = protocolVersion,
            contractFingerprint = fingerprint,
            eventId = eventId,
            sequence = sequence,
            cursor = cursor,
            type = type,
            timestampUtc = timestampUtc,
            user_id = userId,
            device_id = deviceId,
            session_id = sessionId,
            conversation_id = conversationId,
            request_id = requestId,
            trace_id = traceId,
            task_id = taskId,
            run_id = runId,
            action_id = actionId,
            payload = payload,
            optional = optional,
        )
    }

    fun requireCapabilities(obj: JsonObject): WebV1Capabilities {
        val schema = requireConstString(obj, "schema", WebV1.CAPABILITIES_SCHEMA)
        val protocolVersion = requireVersion(obj, "protocol_version")
        val serverVersion = requirePresentString(obj, "server_version")
        if (serverVersion.isBlank()) throw WebV1ValidationException("missing server_version")
        val fingerprint = requireFingerprint(obj, "contract_fingerprint")
        val capsEl = requirePresent(obj, "capabilities")
        if (capsEl is JsonNull) throw WebV1ValidationException("capabilities is null")
        val arr = capsEl as? JsonArray
            ?: throw WebV1ValidationException("capabilities must be an array")
        val caps = arr.mapIndexed { i, el ->
            val p = el as? JsonPrimitive
                ?: throw WebV1ValidationException("capabilities[$i] must be a string")
            p.contentOrNull ?: throw WebV1ValidationException("capabilities[$i] must be a string")
        }
        return WebV1Capabilities(
            schema = schema,
            protocolVersion = protocolVersion,
            serverVersion = serverVersion,
            capabilities = caps,
            contractFingerprint = fingerprint,
            disabledCapabilities = (obj["disabled_capabilities"] as? JsonArray)?.mapNotNull {
                (it as? JsonPrimitive)?.contentOrNull
            } ?: emptyList(),
        )
    }

    fun requireError(obj: JsonObject): WebV1Error {
        val schema = requireConstString(obj, "schema", WebV1.ERROR_SCHEMA)
        val protocolVersion = requireVersion(obj, "protocol_version")
        val fingerprint = requireFingerprint(obj, "contract_fingerprint")
        val statusCode = requireStatusCode(obj, "status_code")
        if (!obj.containsKey("error") && !obj.containsKey("code")) {
            throw WebV1ValidationException("missing error")
        }
        val errorEl = obj["error"] ?: obj["code"]
        val error = when {
            errorEl == null || errorEl is JsonNull ->
                throw WebV1ValidationException("error is null")
            else -> (errorEl as? JsonPrimitive)?.contentOrNull
                ?: throw WebV1ValidationException("error must be a string")
        }
        if (error.isBlank()) throw WebV1ValidationException("missing error")
        if (error.length > WebV1.MAX_ID_LENGTH) throw WebV1ValidationException("invalid error length")
        val message = requirePresentString(obj, "message").take(200)
        val retryable = requireBoolean(obj, "retryable")
        val requestId = requireNullableId(obj, "request_id")
        val traceId = requireNullableId(obj, "trace_id")
        return WebV1Error(
            schema = schema,
            protocolVersion = protocolVersion,
            contractFingerprint = fingerprint,
            statusCode = statusCode,
            error = error,
            message = message,
            retryable = retryable,
            request_id = requestId,
            trace_id = traceId,
        )
    }

    private fun requireStatusCode(obj: JsonObject, key: String): Int {
        val el = requirePresent(obj, key)
        if (el is JsonNull) throw WebV1ValidationException("$key is null")
        val p = el as? JsonPrimitive
            ?: throw WebV1ValidationException("$key must be an integer")
        if (p.isString) throw WebV1ValidationException("$key must be an integer")
        val n = p.intOrNull ?: p.longOrNull?.toInt()
            ?: throw WebV1ValidationException("$key must be an integer")
        if (n !in 100..599) throw WebV1ValidationException("invalid $key")
        return n
    }

    private fun requirePresent(obj: JsonObject, key: String): JsonElement {
        if (!obj.containsKey(key)) throw WebV1ValidationException("missing $key")
        return obj[key] ?: throw WebV1ValidationException("missing $key")
    }

    private fun requireConstString(obj: JsonObject, key: String, expected: String): String {
        val s = requirePresentString(obj, key)
        if (s != expected) throw WebV1ValidationException("invalid $key")
        return s
    }

    private fun requireVersion(obj: JsonObject, key: String): String {
        val s = requirePresentString(obj, key)
        if (!VERSION_RE.matches(s)) throw WebV1ValidationException("invalid $key")
        return s
    }

    private fun requireFingerprint(obj: JsonObject, key: String): String {
        val s = requirePresentString(obj, key)
        if (!FINGERPRINT_RE.matches(s)) throw WebV1ValidationException("invalid $key")
        return s
    }

    private fun requirePresentString(obj: JsonObject, key: String): String {
        val el = requirePresent(obj, key)
        if (el is JsonNull) throw WebV1ValidationException("$key is null")
        val p = el as? JsonPrimitive
            ?: throw WebV1ValidationException("$key must be a string")
        return p.contentOrNull ?: throw WebV1ValidationException("$key must be a string")
    }

    private fun requireBoundedString(obj: JsonObject, key: String, min: Int, max: Int): String {
        val s = requirePresentString(obj, key)
        if (s.length < min || s.length > max) throw WebV1ValidationException("invalid $key length")
        return s
    }

    private fun requireId(obj: JsonObject, key: String): String =
        requireBoundedString(obj, key, 1, WebV1.MAX_ID_LENGTH)

    /** Required key; JSON null is a valid value (schema `string | null`). */
    private fun requireNullableId(obj: JsonObject, key: String): String? {
        val el = requirePresent(obj, key)
        if (el is JsonNull) return null
        val p = el as? JsonPrimitive
            ?: throw WebV1ValidationException("$key must be a string or null")
        val s = p.contentOrNull ?: return null
        if (s.length > WebV1.MAX_ID_LENGTH) throw WebV1ValidationException("invalid $key length")
        return s
    }

    private fun requireSequence(obj: JsonObject, key: String): Long {
        val el = requirePresent(obj, key)
        if (el is JsonNull) throw WebV1ValidationException("$key is null")
        val p = el as? JsonPrimitive
            ?: throw WebV1ValidationException("$key must be an integer")
        val n = p.longOrNull ?: p.intOrNull?.toLong()
            ?: throw WebV1ValidationException("$key must be an integer")
        if (n < 1) throw WebV1ValidationException("invalid $key")
        return n
    }

    private fun requireBoolean(obj: JsonObject, key: String): Boolean {
        val el = requirePresent(obj, key)
        if (el is JsonNull) throw WebV1ValidationException("$key is null")
        val p = el as? JsonPrimitive
            ?: throw WebV1ValidationException("$key must be a boolean")
        return p.booleanOrNull ?: throw WebV1ValidationException("$key must be a boolean")
    }

    private fun requireObject(obj: JsonObject, key: String): JsonObject {
        val el = requirePresent(obj, key)
        if (el is JsonNull) throw WebV1ValidationException("$key is null")
        return el as? JsonObject
            ?: throw WebV1ValidationException("$key must be an object")
    }
}

/**
 * Stable Web V1 identity/scope ids. Retry/replay of the same logical
 * operation must emit the same `task_id`/`run_id`/`request_id` — never a
 * fresh UUID per attempt. Conversation scope is isolated by hashing
 * `conversation_id` into `task_id`.
 */
object WebV1ScopeIds {
    fun clamp(raw: String): String = raw.take(WebV1.MAX_ID_LENGTH).ifBlank { "id" }

    fun taskId(conversationId: String): String =
        clamp("task_" + digest(conversationId.ifBlank { "conversation" }))

    fun runId(conversationId: String, requestId: String): String =
        clamp("run_" + digest(conversationId.ifBlank { "conversation" } + "\u001f" + requestId))

    fun traceId(sessionId: String, requestId: String): String =
        clamp("trace_" + digest(sessionId.ifBlank { "session" } + "\u001f" + requestId))

    fun resumeRequestId(sessionId: String, conversationId: String, cursor: String): String =
        clamp("resume_" + digest(listOf(sessionId, conversationId, "resume", cursor).joinToString("\u001f")))

    fun cancelRequestId(targetRequestId: String): String =
        clamp("cancel_" + digest(targetRequestId))

    private fun digest(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val hex = md.digest(input.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        return hex.take(24)
    }
}

internal fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull

internal fun JsonObject.int(key: String): Int? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()

internal fun JsonObject.bool(key: String): Boolean? =
    (this[key] as? JsonPrimitive)?.booleanOrNull
        ?: (this[key] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull()

internal fun JsonObject.obj(key: String): JsonObject? =
    this[key] as? JsonObject

internal fun JsonElement.asString(): String? = (this as? JsonPrimitive)?.contentOrNull
