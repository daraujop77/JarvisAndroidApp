package com.jarvis.android.contract.webv1

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Frozen PC-A Web V1 wire contract (`jarvis.web.v1 1.0`).
 *
 * This is the **wire** shape. UI/reducer keep using the existing internal
 * domain types; [WebV1Adapter] is the only place that translates.
 *
 * Vendored from the PCB-R0 runbook because `daraujop77/jarvis` is not
 * publicly readable from this workstation. When that repo is reachable,
 * replace the fixtures in `src/test/resources/contracts/web-v1/` with the
 * checked-in PC-A copies and keep this model in lockstep.
 */
object WebV1 {
    const val PROTOCOL = "jarvis.web.v1"
    const val VERSION = "1.0"
    const val FINGERPRINT = "web-v1-1.0"

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
        encodeDefaults = true
        coerceInputValues = true
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
 * never parse it as a number.
 */
@Serializable
data class WebV1Event(
    val cursor: String,
    @SerialName("event_id") val eventId: String,
    val type: String,
    @SerialName("protocol_version") val protocolVersion: String = WebV1.VERSION,
    @SerialName("contract_fingerprint") val contractFingerprint: String? = WebV1.FINGERPRINT,
    @SerialName("timestamp_utc") val timestampUtc: String? = null,
    val optional: Boolean = false,
    val payload: JsonObject = JsonObject(emptyMap()),
    val user_id: String? = null,
    val device_id: String? = null,
    val session_id: String? = null,
    val conversation_id: String? = null,
    val request_id: String? = null,
    val trace_id: String? = null,
    val task_id: String? = null,
    val run_id: String? = null,
    val action_id: String? = null,
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
    val operation: String, // submit | resume | cancel | action
    @SerialName("protocol_version") val protocolVersion: String = WebV1.VERSION,
    @SerialName("contract_fingerprint") val contractFingerprint: String = WebV1.FINGERPRINT,
    val payload: JsonObject = JsonObject(emptyMap()),
    val user_id: String? = null,
    val device_id: String? = null,
    val session_id: String? = null,
    val conversation_id: String? = null,
    val request_id: String? = null,
    val trace_id: String? = null,
    val after: String? = null, // opaque cursor for resume/replay
)

@Serializable
data class WebV1Health(
    val status: String,
    @SerialName("protocol_version") val protocolVersion: String = WebV1.VERSION,
    @SerialName("contract_fingerprint") val contractFingerprint: String? = null,
    val capabilities: List<String> = emptyList(),
)

@Serializable
data class WebV1Capabilities(
    @SerialName("protocol_version") val protocolVersion: String = WebV1.VERSION,
    @SerialName("contract_fingerprint") val contractFingerprint: String = WebV1.FINGERPRINT,
    val operations: List<String> = emptyList(),
    val event_types: List<String> = emptyList(),
)

sealed interface WebV1Decode {
    data class Ok(val event: WebV1Event) : WebV1Decode
    data class Malformed(val reason: String) : WebV1Decode
}

object WebV1Codec {

    fun decodeEvent(raw: String): WebV1Decode = try {
        val obj = WebV1.json.parseToJsonElement(raw) as? JsonObject
            ?: return WebV1Decode.Malformed("not a JSON object")
        val cursor = obj.string("cursor")
            ?: return WebV1Decode.Malformed("missing opaque cursor")
        val eventId = obj.string("event_id")
            ?: return WebV1Decode.Malformed("missing event_id")
        val type = obj.string("type")
            ?: return WebV1Decode.Malformed("missing type")
        WebV1Decode.Ok(WebV1.json.decodeFromJsonElement(WebV1Event.serializer(), obj).copy(
            cursor = cursor,
            eventId = eventId,
            type = type,
        ))
    } catch (t: Throwable) {
        WebV1Decode.Malformed(t.message ?: "decode failure")
    }

    fun encodeEvent(event: WebV1Event): String =
        WebV1.json.encodeToString(WebV1Event.serializer(), event)

    fun encodeRequest(req: WebV1Request): String =
        WebV1.json.encodeToString(WebV1Request.serializer(), req)

    fun protocolCompatible(version: String?, fingerprint: String?): Boolean {
        val v = version ?: return false
        val major = v.substringBefore('.', v)
        if (major != WebV1.VERSION.substringBefore('.')) return false
        if (fingerprint != null && fingerprint != WebV1.FINGERPRINT && !fingerprint.startsWith("web-v1-1.")) {
            return false
        }
        return true
    }
}

internal fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull

internal fun JsonObject.int(key: String): Int? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()

internal fun JsonObject.bool(key: String): Boolean? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull()

internal fun JsonObject.obj(key: String): JsonObject? =
    this[key] as? JsonObject

internal fun JsonElement.asString(): String? = (this as? JsonPrimitive)?.contentOrNull
