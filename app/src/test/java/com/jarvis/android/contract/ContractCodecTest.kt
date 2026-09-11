package com.jarvis.android.contract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract/serialization tests: additive tolerance, unknown events, malformed
 * payloads (plan §6 compatibility checklist, Lane C).
 */
class ContractCodecTest {

    @Test
    fun unknownEnvelopeFieldsAreIgnored() {
        val raw = """
            {"cursor":1,"eventId":"e1","version":"1.0","serverShard":"future",
             "event":{"type":"message.accepted","requestId":"r1","conversationId":"c1","messageId":"m1","extra":{"a":1}}}
        """.trimIndent()
        val decoded = EventCodec.decodeEnvelope(raw)
        assertTrue(decoded is EventCodec.DecodedEnvelope.Ok)
        val env = (decoded as EventCodec.DecodedEnvelope.Ok).envelope
        assertEquals(1L, env.cursor)
        assertTrue(env.event is GatewayEvent.MessageAccepted)
    }

    @Test
    fun unknownEventTypeBecomesUnknownEvent() {
        val raw = """
            {"cursor":2,"eventId":"e2","event":{"type":"hologram.started","foo":"bar"}}
        """.trimIndent()
        val decoded = EventCodec.decodeEnvelope(raw) as EventCodec.DecodedEnvelope.Ok
        assertTrue(decoded.envelope.event is GatewayEvent.Unknown)
        assertEquals("hologram.started", (decoded.envelope.event as GatewayEvent.Unknown).unknownType)
    }

    @Test
    fun malformedEventTypePayloadBecomesUnknownMalformed() {
        val raw = """
            {"cursor":2,"eventId":"e2","event":{"type":"message.delta","requestId":123}}
        """.trimIndent()
        val decoded = EventCodec.decodeEnvelope(raw) as EventCodec.DecodedEnvelope.Ok
        assertTrue(decoded.envelope.event is GatewayEvent.Unknown)
        assertTrue((decoded.envelope.event as GatewayEvent.Unknown).unknownType.endsWith(":malformed"))
    }

    @Test
    fun missingRequiredEnvelopeFieldsAreMalformed() {
        assertTrue(EventCodec.decodeEnvelope("""{"eventId":"x"}""") is EventCodec.DecodedEnvelope.Malformed)
        assertTrue(EventCodec.decodeEnvelope("""{"cursor":1}""") is EventCodec.DecodedEnvelope.Malformed)
        assertTrue(EventCodec.decodeEnvelope("""not json""") is EventCodec.DecodedEnvelope.Malformed)
        assertTrue(EventCodec.decodeEnvelope("""{"cursor":"nan","eventId":"e"}""") is EventCodec.DecodedEnvelope.Malformed)
    }

    @Test
    fun roundTripEnvelopeAndRequests() {
        val env = EventEnvelope(
            cursor = 9,
            eventId = "e9",
            event = GatewayEvent.MessageDelta("r1", "m1", 3, "hey"),
        )
        val back = EventCodec.decodeEnvelope(EventCodec.encodeEnvelope(env)) as EventCodec.DecodedEnvelope.Ok
        assertEquals(env.event, back.envelope.event)
        assertEquals(9L, back.envelope.cursor)

        val req: MobileRequest = MobileRequest.SendMessage("c1", "conv", "text", listOf("a1"))
        val encoded = JarvisJson.default.encodeToString(MobileRequest.serializer(), req)
        assertTrue(encoded.contains("\"type\":\"send_message\""))
        val decoded = JarvisJson.default.decodeFromString(MobileRequest.serializer(), encoded)
        assertEquals(req, decoded)
    }

    @Test
    fun missingOptionalFieldsUseDefaults() {
        val raw = """
            {"cursor":1,"eventId":"e1","event":{"type":"approval.required","payload":{"approvalId":"a1","title":"T"}}}
        """.trimIndent()
        val decoded = EventCodec.decodeEnvelope(raw) as EventCodec.DecodedEnvelope.Ok
        val ev = decoded.envelope.event as GatewayEvent.ApprovalRequired
        assertNotNull(ev.payload)
        assertEquals(ApprovalTier.NORMAL, ev.payload.tier)
        assertTrue(ev.payload.actions.contains("approve"))
    }

    @Test
    fun missingPayloadObjectIsUnknownType() {
        val raw = """
            {"cursor":1,"eventId":"e1","event":{"type":"task.updated"}}
        """.trimIndent()
        val decoded = EventCodec.decodeEnvelope(raw) as EventCodec.DecodedEnvelope.Ok
        assertTrue(decoded.envelope.event is GatewayEvent.Unknown)
    }
}
