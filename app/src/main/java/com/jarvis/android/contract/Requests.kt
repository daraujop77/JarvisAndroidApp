package com.jarvis.android.contract

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

/**
 * Mobile-facing request envelope (client -> Gateway).
 * `clientRequestId` provides idempotency: resending the same request after a
 * reconnect must not create a duplicate message (plan AND-W2 gate).
 */
@Serializable
sealed interface MobileRequest {

    @Serializable
    @SerialName("send_message")
    data class SendMessage(
        val clientRequestId: String,
        val conversationId: String,
        val text: String,
        val attachmentIds: List<String> = emptyList(),
        val protocolVersion: String = ContractVersion.SUPPORTED,
    ) : MobileRequest

    @Serializable
    @SerialName("cancel_request")
    data class CancelRequest(
        val targetRequestId: String,
        val reason: String? = null,
    ) : MobileRequest

    @Serializable
    @SerialName("resolve_approval")
    data class ResolveApproval(
        val approvalId: String,
        val outcome: ApprovalOutcome,
        /** Client-side idempotency key for the resolution itself. */
        val resolutionId: String,
    ) : MobileRequest

    @Serializable
    @SerialName("replay")
    data class Replay(
        val sinceCursor: Long,
    ) : MobileRequest

    /**
     * AND-W6 upload intent (plan §11: the binary upload contract itself is NOT
     * frozen by PC-A yet). This declares an attachment by id; the fake transport
     * confirms it locally, the WSS adapter refuses until the contract freezes.
     * Raw local paths are never sent — only the opaque attachmentId referenced
     * by [SendMessage.attachmentIds].
     */
    @Serializable
    @SerialName("attachment.upload")
    data class UploadAttachment(
        val attachmentId: String,
        val conversationId: String,
        val filename: String,
        val mimeType: String,
        val sizeBytes: Long,
    ) : MobileRequest
}

@Serializable
data class HealthResponse(
    val status: String,
    val protocolVersion: String,
    val capabilities: List<String> = emptyList(),
)

object JarvisJson {
    val default = Json {
        ignoreUnknownKeys = true       // additive optional-field tolerance (plan §6)
        encodeDefaults = false
        isLenient = false
        coerceInputValues = false
        classDiscriminator = "type"
    }
}

/**
 * Envelope-level decoder that maps unknown event `type` discriminators to
 * [GatewayEvent.Unknown] instead of throwing (plan §6 unknown event handling).
 */
object EventCodec {

    sealed interface DecodedEnvelope {
        data class Ok(val envelope: EventEnvelope) : DecodedEnvelope
        data class Malformed(val reason: String) : DecodedEnvelope
    }

    fun decodeEnvelope(raw: String): DecodedEnvelope {
        return try {
            val obj = JarvisJson.default.parseToJsonElement(raw) as? JsonObject
                ?: return DecodedEnvelope.Malformed("envelope is not a JSON object")
            val cursor = (obj["cursor"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull()
                ?: return DecodedEnvelope.Malformed("missing cursor")
            val eventId = (obj["eventId"] as? JsonPrimitive)?.contentOrNull
                ?: return DecodedEnvelope.Malformed("missing eventId")
            val version = (obj["version"] as? JsonPrimitive)?.contentOrNull ?: ContractVersion.SUPPORTED
            val timestampMs = (obj["timestampMs"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull()
            val eventObj = obj["event"] as? JsonObject
                ?: return DecodedEnvelope.Malformed("missing event object")
            val type = (eventObj["type"] as? JsonPrimitive)?.contentOrNull
                ?: return DecodedEnvelope.Malformed("missing event.type")

            DecodedEnvelope.Ok(
                EventEnvelope(
                    cursor = cursor,
                    eventId = eventId,
                    version = version,
                    timestampMs = timestampMs,
                    event = decodeEvent(type, eventObj),
                )
            )
        } catch (t: Throwable) {
            DecodedEnvelope.Malformed(t.message ?: "decode failure")
        }
    }

    fun encodeEnvelope(env: EventEnvelope): String =
        JarvisJson.default.encodeToString(EventEnvelope.serializer(), env)

    fun encodeRequest(req: MobileRequest): String =
        JarvisJson.default.encodeToString(MobileRequest.serializer(), req)

    private fun decodeEvent(type: String, obj: JsonObject): GatewayEvent {
        val json = JarvisJson.default
        return try {
            when (type) {
                "message.accepted" -> json.decodeFromJsonElement(GatewayEvent.MessageAccepted.serializer(), obj)
                "message.delta" -> json.decodeFromJsonElement(GatewayEvent.MessageDelta.serializer(), obj)
                "message.completed" -> json.decodeFromJsonElement(GatewayEvent.MessageCompleted.serializer(), obj)
                "message.failed" -> json.decodeFromJsonElement(GatewayEvent.MessageFailed.serializer(), obj)
                "approval.required" -> {
                    val payload = obj["payload"]
                        ?: return GatewayEvent.Unknown(type)
                    GatewayEvent.ApprovalRequired(json.decodeFromJsonElement(ApprovalRequiredPayload.serializer(), payload))
                }
                "approval.resolved" -> {
                    val payload = obj["payload"]
                        ?: return GatewayEvent.Unknown(type)
                    GatewayEvent.ApprovalResolved(json.decodeFromJsonElement(ApprovalResolvedPayload.serializer(), payload))
                }
                "task.updated" -> {
                    val payload = obj["payload"]
                        ?: return GatewayEvent.Unknown(type)
                    GatewayEvent.TaskUpdated(json.decodeFromJsonElement(TaskUpdatedPayload.serializer(), payload))
                }
                "connection.state" -> {
                    val payload = obj["payload"]
                        ?: return GatewayEvent.Unknown(type)
                    GatewayEvent.ConnectionState(json.decodeFromJsonElement(ConnectionPayload.serializer(), payload))
                }
                "attachment.ready" -> {
                    val payload = obj["payload"]
                        ?: return GatewayEvent.Unknown(type)
                    GatewayEvent.AttachmentReady(json.decodeFromJsonElement(AttachmentPayload.serializer(), payload))
                }
                "attachment.failed" -> {
                    val payload = obj["payload"]
                        ?: return GatewayEvent.Unknown(type)
                    GatewayEvent.AttachmentFailed(json.decodeFromJsonElement(AttachmentPayload.serializer(), payload).copy(ready = false))
                }
                else -> GatewayEvent.Unknown(type)
            }
        } catch (t: Throwable) {
            GatewayEvent.Unknown("$type:malformed")
        }
    }
}
