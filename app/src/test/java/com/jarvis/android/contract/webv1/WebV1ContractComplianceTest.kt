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
        extra: String = "",
    ): String = buildString {
        append("""{"protocol_version":"$protocol"""")
        if (server != null) append(""","server_version":"$server"""")
        if (capabilitiesPresent) append(""","capabilities":["conversation","streaming"]""")
        append(extra)
        append("}")
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
        assertTrue(caps.operations.contains("submit"))
        assertFalse(caps.operations.contains("action"))
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
        val typed = """{"schema":"jarvis.web.error.v1","error":"gateway_unavailable","message":"busy","retryable":true,"secret":"do-not-keep"}"""
        val ex = WebV1Errors.fromHttp(503, typed)
        assertEquals("gateway_unavailable", ex.code)
        assertTrue(ex.retryable)
        assertFalse(ex.message!!.contains("secret"))
        assertFalse(ex.message!!.contains("do-not-keep"))
        val env = WebV1Errors.toInternalError(ex)
        assertEquals("gateway_unavailable", env.code)
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
        val body = """{"schema":"jarvis.web.error.v1","error":"invalid_request","message":"nope","extra":{"x":1}}"""
        val ex = WebV1Errors.fromHttp(400, body)
        assertEquals("invalid_request", ex.code)
        assertFalse(ex.retryable)
    }

    @Test
    fun authFailureAndProtocolMismatchAreNonRetryable() {
        val auth = WebV1Errors.fromHttp(401, """{"schema":"jarvis.web.error.v1","error":"invalid_request","message":"denied"}""")
        assertEquals("invalid_request", auth.code)
        assertFalse(auth.retryable)

        val mismatch = WebV1Errors.fromHttp(
            409,
            """{"schema":"jarvis.web.error.v1","error":"protocol_version_mismatch","message":"need 1.0","contract_fingerprint":"${WebV1.FINGERPRINT}"}""",
        )
        assertEquals("protocol_version_mismatch", mismatch.code)
        assertFalse(mismatch.retryable)
    }

    @Test
    fun retryableVersusFinalErrorCodes() {
        val retryable = WebV1Errors.fromHttp(503, """{"schema":"jarvis.web.error.v1","error":"executor_unavailable","message":"later"}""")
        assertTrue(retryable.retryable)
        val final = WebV1Errors.fromHttp(409, """{"schema":"jarvis.web.error.v1","error":"duplicate_request","message":"seen"}""")
        assertFalse(final.retryable)
    }

    private fun resource(path: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream(path)) { "missing $path" }
            .bufferedReader().readText()

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
}
