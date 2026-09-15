package com.jarvis.android.contract.webv1

import com.jarvis.android.contract.ApprovalOutcome
import com.jarvis.android.contract.MobileRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cross-system reaudit: the four remaining Web V1 gaps against frozen
 * `contracts/web-v1` (fingerprint unchanged).
 */
class WebV1ContractComplianceTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val identity = WebV1Identity(
        userId = "owner-001",
        deviceId = "device-001",
        sessionId = "session-spine-001",
        traceId = "trace-spine-positive-001",
    )

    private fun validCapabilities(
        protocol: String = "1.0",
        server: String? = "jarvis-pc-a",
        capabilitiesPresent: Boolean = true,
        schema: String? = WebV1.CAPABILITIES_SCHEMA,
        fingerprint: String? = WebV1.FINGERPRINT,
        extra: String = "",
    ): String = buildString {
        append("{")
        val parts = mutableListOf<String>()
        if (schema != null) parts += """"schema":"$schema""""
        parts += """"protocol_version":"$protocol""""
        if (server != null) parts += """"server_version":"$server""""
        if (capabilitiesPresent) parts += """"capabilities":["conversation","streaming"]"""
        if (fingerprint != null) parts += """"contract_fingerprint":"$fingerprint""""
        if (extra.isNotBlank()) parts += extra.trimStart(',')
        append(parts.joinToString(","))
        append("}")
    }

    private fun validTypedError(
        extra: String = "",
        omit: Set<String> = emptySet(),
        overrides: Map<String, String> = emptyMap(),
    ): String {
        val fields = linkedMapOf(
            "schema" to """"${WebV1.ERROR_SCHEMA}"""",
            "protocol_version" to """"1.0"""",
            "contract_fingerprint" to """"${WebV1.FINGERPRINT}"""",
            "status_code" to "503",
            "error" to """"gateway_unavailable"""",
            "message" to """"busy"""",
            "retryable" to "true",
            "request_id" to """"req-err-001"""",
            "trace_id" to """"trace-err-001"""",
        )
        overrides.forEach { (k, v) -> fields[k] = v }
        omit.forEach { fields.remove(it) }
        val body = fields.entries.joinToString(",") { """"${it.key}":${it.value}""" }
        return if (extra.isBlank()) "{$body}" else "{$body,$extra}"
    }

    @Test
    fun capabilitiesValidResponseDecodesOfficialShape() {
        val decoded = WebV1Codec.decodeCapabilities(
            validCapabilities(extra = ""","future_flag":true,"disabled_capabilities":["pc_read"]"""),
        )
        assertTrue(decoded is WebV1CapabilitiesDecode.Ok)
        val caps = (decoded as WebV1CapabilitiesDecode.Ok).capabilities
        assertEquals("1.0", caps.protocolVersion)
        assertEquals("jarvis-pc-a", caps.serverVersion)
        assertEquals(listOf("conversation", "streaming"), caps.capabilities)
        assertEquals(WebV1.CAPABILITIES_SCHEMA, caps.schema)
        assertEquals(WebV1.FINGERPRINT, caps.contractFingerprint)
        assertTrue(caps.operations.contains("submit"))
        assertFalse(caps.operations.contains("action"))
    }

    @Test
    fun capabilitiesMissingSchemaIsMalformed() {
        val decoded = WebV1Codec.decodeCapabilities(validCapabilities(schema = null))
        assertTrue(decoded is WebV1CapabilitiesDecode.Malformed)
        assertTrue((decoded as WebV1CapabilitiesDecode.Malformed).reason.contains("schema"))
    }

    @Test
    fun capabilitiesWrongSchemaIsMalformed() {
        val decoded = WebV1Codec.decodeCapabilities(validCapabilities(schema = "jarvis.web.event.v1"))
        assertTrue(decoded is WebV1CapabilitiesDecode.Malformed)
        assertTrue((decoded as WebV1CapabilitiesDecode.Malformed).reason.contains("schema"))
    }

    @Test
    fun capabilitiesMissingFingerprintIsMalformed() {
        val decoded = WebV1Codec.decodeCapabilities(validCapabilities(fingerprint = null))
        assertTrue(decoded is WebV1CapabilitiesDecode.Malformed)
        assertTrue((decoded as WebV1CapabilitiesDecode.Malformed).reason.contains("contract_fingerprint"))
    }

    @Test
    fun capabilitiesWrongFingerprintFailsClosed() {
        val other = "0".repeat(64)
        val decoded = WebV1Codec.decodeCapabilities(validCapabilities(fingerprint = other))
        assertTrue(decoded is WebV1CapabilitiesDecode.ProtocolMismatch)
    }

    @Test
    fun capabilitiesMissingServerVersionIsMalformed() {
        val decoded = WebV1Codec.decodeCapabilities(validCapabilities(server = null))
        assertTrue(decoded is WebV1CapabilitiesDecode.Malformed)
        assertTrue((decoded as WebV1CapabilitiesDecode.Malformed).reason.contains("server_version"))
    }

    @Test
    fun capabilitiesMissingCapabilitiesArrayIsMalformed() {
        val decoded = WebV1Codec.decodeCapabilities(validCapabilities(capabilitiesPresent = false))
        assertTrue(decoded is WebV1CapabilitiesDecode.Malformed)
        assertTrue((decoded as WebV1CapabilitiesDecode.Malformed).reason.contains("capabilities"))
    }

    @Test
    fun capabilitiesIncompatibleProtocolFailsClosed() {
        val decoded = WebV1Codec.decodeCapabilities(validCapabilities(protocol = "2.0"))
        assertTrue(decoded is WebV1CapabilitiesDecode.ProtocolMismatch)
    }

    @Test
    fun capabilitiesUnknownAdditiveFieldsAreTolerated() {
        val decoded = WebV1Codec.decodeCapabilities(
            validCapabilities(extra = ""","unknown_catalog":{"x":1},"extra_list":[1,2]"""),
        )
        assertTrue(decoded is WebV1CapabilitiesDecode.Ok)
    }

    @Test
    fun everyOutboundOperationCarriesStableTaskAndRunIds() {
        val submit = WebV1Adapter.outbound(
            MobileRequest.SendMessage("req-spine-positive-001", "conversation-spine-001", "Hola Jarvis"),
            identity,
        )
        val resume = WebV1Adapter.outbound(
            MobileRequest.Replay("cursor-spine-001", "conversation-spine-001"),
            identity,
        )
        val cancel = WebV1Adapter.outbound(
            MobileRequest.CancelRequest("req-spine-positive-001", conversationId = "conversation-spine-001"),
            identity,
        )
        val approve = WebV1Adapter.outbound(
            MobileRequest.ResolveApproval(
                "action-spine-001", ApprovalOutcome.APPROVED, "idem-spine-001",
                conversationId = "conversation-spine-001",
            ),
            identity,
        )
        val upload = WebV1Adapter.outbound(
            MobileRequest.UploadAttachment("att-1", "conversation-spine-001", "a.jpg", "image/jpeg", 12),
            identity,
        )
        val replay = WebV1Adapter.outboundReplay("cursor-spine-001", identity, "conversation-spine-001")

        for (wire in listOf(submit, resume, cancel, approve, upload, replay)) {
            val encoded = json.parseToJsonElement(WebV1Codec.encodeRequest(wire)).jsonObject
            assertEquals(WebV1.REQUEST_SCHEMA, encoded.str("schema"))
            assertEquals(WebV1.FINGERPRINT, encoded.str("contract_fingerprint"))
            assertTrue(encoded.str("task_id")!!.isNotBlank())
            assertTrue(encoded.str("run_id")!!.isNotBlank())
            assertTrue(encoded.str("task_id")!!.length <= WebV1.MAX_ID_LENGTH)
            assertTrue(encoded.str("run_id")!!.length <= WebV1.MAX_ID_LENGTH)
            assertEquals(wire.task_id, encoded.str("task_id"))
            assertEquals(wire.run_id, encoded.str("run_id"))
            assertEquals("conversation-spine-001", encoded.str("conversation_id"))
        }

        val submitRetry = WebV1Adapter.outbound(
            MobileRequest.SendMessage("req-spine-positive-001", "conversation-spine-001", "Hola Jarvis"),
            identity,
        )
        assertEquals(submit.task_id, submitRetry.task_id)
        assertEquals(submit.run_id, submitRetry.run_id)
        assertEquals(submit.request_id, submitRetry.request_id)

        val otherConv = WebV1Adapter.outbound(
            MobileRequest.SendMessage("req-spine-positive-001", "conversation-other", "Hola Jarvis"),
            identity,
        )
        assertNotEquals(submit.task_id, otherConv.task_id)
        assertNotEquals(submit.run_id, otherConv.run_id)
        assertEquals(WebV1ScopeIds.taskId("conversation-spine-001"), submit.task_id)
        assertEquals(resume.request_id, replay.request_id)
    }

    @Test
    fun officialPositiveEventKeepsExplicitNullActionId() {
        val raw = resource("contracts/web-v1/fixtures/event-positive.json")
        val decoded = WebV1Codec.decodeEvent(raw)
        assertTrue(decoded is WebV1Decode.Ok)
        val event = (decoded as WebV1Decode.Ok).event
        assertNull(event.action_id)
        assertEquals("task-spine-001", event.task_id)
        assertEquals("run-spine-001", event.run_id)
        assertEquals(1L, event.sequence)
    }

    @Test
    fun missingRequiredEventFieldIsMalformedAndDoesNotEnterReducer() {
        val official = json.parseToJsonElement(
            resource("contracts/web-v1/fixtures/event-positive.json"),
        ).jsonObject
        val required = listOf(
            "schema", "protocol_version", "contract_fingerprint", "event_id", "sequence",
            "cursor", "type", "timestamp_utc", "user_id", "device_id", "session_id",
            "conversation_id", "request_id", "trace_id", "task_id", "run_id",
            "action_id", "payload", "optional",
        )
        for (field in required) {
            val stripped = JsonObject(official.filterKeys { it != field })
            val decoded = WebV1Codec.decodeEvent(stripped.toString())
            assertTrue("$field absent must be malformed", decoded is WebV1Decode.Malformed)
            val inbound = WebV1Adapter.inbound(stripped.toString())
            assertTrue("$field absent must not become Domain", inbound is WebV1Adapter.Inbound.Malformed)
        }
    }

    @Test
    fun presentNullOnNullableIdentityFieldsIsValid() {
        val official = json.parseToJsonElement(
            resource("contracts/web-v1/fixtures/event-positive.json"),
        ).jsonObject
        for (field in listOf("request_id", "task_id", "run_id", "action_id")) {
            val withNull = JsonObject(official + (field to kotlinx.serialization.json.JsonNull))
            val decoded = WebV1Codec.decodeEvent(withNull.toString())
            assertTrue("$field=null must decode", decoded is WebV1Decode.Ok)
        }
    }

    @Test
    fun presentNullOnNonNullableRequiredFieldIsMalformed() {
        val official = json.parseToJsonElement(
            resource("contracts/web-v1/fixtures/event-positive.json"),
        ).jsonObject
        val withNull = JsonObject(official + ("user_id" to kotlinx.serialization.json.JsonNull))
        assertTrue(WebV1Codec.decodeEvent(withNull.toString()) is WebV1Decode.Malformed)
    }

    @Test
    fun additiveUnknownEventFieldsAreTolerated() {
        val official = json.parseToJsonElement(
            resource("contracts/web-v1/fixtures/event-positive.json"),
        ).jsonObject
        val extra = JsonObject(official + ("future_hint" to JsonPrimitive("ok")))
        assertTrue(WebV1Codec.decodeEvent(extra.toString()) is WebV1Decode.Ok)
    }

    @Test
    fun typedErrorEnvelopeMapsCodeRetryabilityAndNeverKeepsRawBody() {
        val typed = validTypedError(extra = """"secret":"do-not-keep"""")
        val ex = WebV1Errors.fromHttp(503, typed)
        assertEquals("gateway_unavailable", ex.code)
        assertEquals(503, ex.statusCode)
        assertEquals("req-err-001", ex.requestId)
        assertEquals("trace-err-001", ex.traceId)
        assertTrue(ex.retryable)
        assertFalse(ex.message!!.contains("secret"))
        assertFalse(ex.message!!.contains("do-not-keep"))
        val env = WebV1Errors.toInternalError(ex)
        assertEquals("gateway_unavailable", env.code)
        assertTrue(env.details!!.contains("request_id=req-err-001"))
        assertTrue(env.details!!.contains("trace_id=trace-err-001"))
        assertTrue(env.details!!.contains("status=503"))
    }

    @Test
    fun typedErrorMissingRequiredFieldFailsClosed() {
        val required = listOf(
            "schema", "protocol_version", "contract_fingerprint", "status_code",
            "error", "message", "retryable", "request_id", "trace_id",
        )
        for (field in required) {
            val decoded = WebV1Codec.decodeError(validTypedError(omit = setOf(field)))
            assertTrue("$field absent must be malformed", decoded is WebV1ErrorDecode.Malformed)
            val ex = WebV1Errors.fromHttp(400, validTypedError(omit = setOf(field)))
            assertEquals("invalid_response", ex.code)
            assertFalse(ex.retryable)
            assertFalse(ex.message!!.contains("secret"))
        }
    }

    @Test
    fun typedErrorPresentNullOnNullableIdsIsValid() {
        val decoded = WebV1Codec.decodeError(
            validTypedError(overrides = mapOf("request_id" to "null", "trace_id" to "null")),
        )
        assertTrue(decoded is WebV1ErrorDecode.Ok)
        val err = (decoded as WebV1ErrorDecode.Ok).error
        assertNull(err.request_id)
        assertNull(err.trace_id)
    }

    @Test
    fun typedErrorWrongTypesAndMismatchesFailClosed() {
        val wrongType = WebV1Codec.decodeError(validTypedError(overrides = mapOf("status_code" to """"503"""")))
        assertTrue(wrongType is WebV1ErrorDecode.Malformed)

        val protocol = WebV1Codec.decodeError(validTypedError(overrides = mapOf("protocol_version" to """"2.0"""")))
        assertTrue(protocol is WebV1ErrorDecode.ProtocolMismatch)

        val fingerprint = WebV1Codec.decodeError(
            validTypedError(overrides = mapOf("contract_fingerprint" to """"${"0".repeat(64)}"""")),
        )
        assertTrue(fingerprint is WebV1ErrorDecode.ProtocolMismatch)

        val wrongSchema = WebV1Codec.decodeError(
            validTypedError(overrides = mapOf("schema" to """"jarvis.web.event.v1"""")),
        )
        assertTrue(wrongSchema is WebV1ErrorDecode.Malformed)
    }

    @Test
    fun malformedAndEmptyErrorBodiesFailSafe() {
        val empty = WebV1Errors.fromHttp(500, "")
        assertEquals("gateway_unavailable", empty.code)
        assertTrue(empty.retryable)
        assertFalse(empty.message!!.contains("{"))

        val junk = WebV1Errors.fromHttp(400, "not-json <html>token=abc</html>")
        assertEquals("invalid_response", junk.code)
        assertFalse(junk.retryable)
        assertFalse(junk.message!!.contains("token=abc"))
        assertFalse(junk.message!!.contains("not-json"))
    }

    @Test
    fun unknownFieldsOnTypedErrorAreIgnored() {
        val body = validTypedError(
            extra = """"extra":{"x":1}""",
            overrides = mapOf(
                "status_code" to "400",
                "error" to """"invalid_request"""",
                "message" to """"nope"""",
                "retryable" to "false",
            ),
        )
        val ex = WebV1Errors.fromHttp(400, body)
        assertEquals("invalid_request", ex.code)
        assertEquals(400, ex.statusCode)
        assertFalse(ex.retryable)
    }

    @Test
    fun authFailureAndProtocolMismatchAreNonRetryable() {
        val auth = WebV1Errors.fromHttp(
            401,
            validTypedError(
                overrides = mapOf(
                    "status_code" to "401",
                    "error" to """"invalid_request"""",
                    "message" to """"denied"""",
                    "retryable" to "false",
                ),
            ),
        )
        assertEquals("invalid_request", auth.code)
        assertEquals(401, auth.statusCode)
        assertFalse(auth.retryable)

        val mismatch = WebV1Errors.fromHttp(
            409,
            validTypedError(
                overrides = mapOf(
                    "status_code" to "409",
                    "error" to """"protocol_version_mismatch"""",
                    "message" to """"need 1.0"""",
                    "retryable" to "false",
                ),
            ),
        )
        assertEquals("protocol_version_mismatch", mismatch.code)
        assertFalse(mismatch.retryable)
    }

    @Test
    fun retryableVersusFinalErrorCodes() {
        val retryable = WebV1Errors.fromHttp(
            503,
            validTypedError(overrides = mapOf("error" to """"executor_unavailable"""", "message" to """"later"""")),
        )
        assertTrue(retryable.retryable)
        val final = WebV1Errors.fromHttp(
            409,
            validTypedError(
                overrides = mapOf(
                    "status_code" to "409",
                    "error" to """"duplicate_request"""",
                    "message" to """"seen"""",
                    "retryable" to "false",
                ),
            ),
        )
        assertFalse(final.retryable)
    }

    private fun resource(path: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream(path)) { "missing $path" }
            .bufferedReader().readText()

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
}
