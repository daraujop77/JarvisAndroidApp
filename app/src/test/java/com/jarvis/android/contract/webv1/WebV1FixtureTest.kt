package com.jarvis.android.contract.webv1

import com.jarvis.android.contract.GatewayEvent
import com.jarvis.android.data.state.ConnectionState
import com.jarvis.android.data.state.Reducer
import com.jarvis.android.data.state.RequestStatus
import com.jarvis.android.data.state.SessionState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PCB-R0 gate: consume the checked-in Web V1 fixtures *without rewriting them*.
 * The adapter is the only translation; the reducer still sees domain events.
 */
class WebV1FixtureTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun resource(path: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream(path)) {
            "missing fixture $path"
        }.bufferedReader().readText()

    @Test
    fun spine0DeclaresOpaqueCursorAndHttpPaths() {
        val spine = json.parseToJsonElement(resource("contracts/web-v1/spine-0.json")) as JsonObject
        assertEquals("jarvis.web.v1", spine["protocol"]?.asString())
        assertEquals("1.0", spine["protocol_version"]?.asString())
        val endpoints = spine["endpoints"] as JsonObject
        assertEquals("/api/v1/health", endpoints["health"]?.asString())
        assertEquals("/api/v1/requests", endpoints["requests"]?.asString())
        assertEquals("/api/v1/events", endpoints["events"]?.asString())
        val types = spine["event_types"] as JsonArray
        assertTrue(types.any { it.asString() == "message.delta" })
        assertTrue(types.any { it.asString() == "approval.required" })
    }

    @Test
    fun happyStreamFixtureDrivesReducerWithoutRewritingJson() {
        val frames = json.parseToJsonElement(resource("contracts/web-v1/fixtures/stream-happy.json")) as JsonArray
        var state = Reducer.beginSend(SessionState(), "r1", "c1", "hi", 0L)
        for (el in frames) {
            val inbound = WebV1Adapter.inbound(el.toString())
            assertTrue("failed on $el: $inbound", inbound is WebV1Adapter.Inbound.Domain)
            val domain = inbound as WebV1Adapter.Inbound.Domain
            val outcome = Reducer.applyWebV1(state, domain.envelope, domain.opaqueCursor, 0L)
            state = outcome.state
        }
        val req = state.requests["r1"]!!
        assertEquals(RequestStatus.Completed, req.status)
        assertEquals("Hello world", req.text)
        assertEquals("tok_0005", state.lastCursorToken)
        // opaque token is not a Long
        assertTrue(state.lastCursorToken.toLongOrNull() == null)
    }

    @Test
    fun duplicateEventIdFromFixtureIsDropped() {
        val frames = json.parseToJsonElement(resource("contracts/web-v1/fixtures/stream-happy.json")) as JsonArray
        var state = Reducer.beginSend(SessionState(), "r1", "c1", "hi", 0L)
        for (el in frames.take(3)) {
            val d = WebV1Adapter.inbound(el.toString()) as WebV1Adapter.Inbound.Domain
            state = Reducer.applyWebV1(state, d.envelope, d.opaqueCursor, 0L).state
        }
        val negatives = json.parseToJsonElement(resource("contracts/web-v1/fixtures/negative.json")) as JsonObject
        val dup = WebV1Adapter.inbound(negatives["duplicate"]!!.toString()) as WebV1Adapter.Inbound.Domain
        val before = state.requests["r1"]!!.text
        val outcome = Reducer.applyWebV1(state, dup.envelope, dup.opaqueCursor, 0L)
        assertTrue(outcome is com.jarvis.android.data.state.ReducerOutcome.Duplicate)
        assertEquals(before, outcome.state.requests["r1"]!!.text)
    }

    @Test
    fun optionalUnknownEventIsIgnoredAndDoesNotCrash() {
        val negatives = json.parseToJsonElement(resource("contracts/web-v1/fixtures/negative.json")) as JsonObject
        val inbound = WebV1Adapter.inbound(negatives["optional_unknown"]!!.toString())
        assertTrue(inbound is WebV1Adapter.Inbound.IgnoreOptional)
        assertEquals("tok_opt", (inbound as WebV1Adapter.Inbound.IgnoreOptional).opaqueCursor)
    }

    @Test
    fun requiredUnknownBecomesUnknownDomainEvent() {
        val negatives = json.parseToJsonElement(resource("contracts/web-v1/fixtures/negative.json")) as JsonObject
        val inbound = WebV1Adapter.inbound(negatives["required_unknown"]!!.toString())
        val domain = inbound as WebV1Adapter.Inbound.Domain
        assertTrue(domain.envelope.event is GatewayEvent.Unknown)
        assertEquals("widget.frobnicated", (domain.envelope.event as GatewayEvent.Unknown).unknownType)
    }

    @Test
    fun protocolMismatchFailsClosed() {
        val negatives = json.parseToJsonElement(resource("contracts/web-v1/fixtures/negative.json")) as JsonObject
        val inbound = WebV1Adapter.inbound(negatives["protocol_mismatch"]!!.toString())
        assertTrue(inbound is WebV1Adapter.Inbound.ProtocolMismatch)
    }

    @Test
    fun opaqueCursorThatIsNotANumberRoundTrips() {
        val negatives = json.parseToJsonElement(resource("contracts/web-v1/fixtures/negative.json")) as JsonObject
        val inbound = WebV1Adapter.inbound(negatives["opaque_cursor_not_number"]!!.toString()) as WebV1Adapter.Inbound.Domain
        assertEquals("not-a-number:abc+/=", inbound.opaqueCursor)
        assertEquals(null, inbound.opaqueCursor.toLongOrNull())
        val state = Reducer.applyWebV1(SessionState(), inbound.envelope, inbound.opaqueCursor, 0L).state
        assertEquals("not-a-number:abc+/=", state.lastCursorToken)
        assertEquals(ConnectionState.ONLINE, state.connection)
    }

    @Test
    fun submitMapsToWebV1RequestOperation() {
        val wire = WebV1Adapter.outbound(
            com.jarvis.android.contract.MobileRequest.SendMessage("r1", "c1", "hello"),
        )
        assertEquals("submit", wire.operation)
        assertEquals("r1", wire.request_id)
        assertEquals("c1", wire.conversation_id)
        val replay = WebV1Adapter.outboundReplay("tok_0005")
        assertEquals("resume", replay.operation)
        assertEquals("tok_0005", replay.after)
    }
}

private fun kotlinx.serialization.json.JsonElement.asString(): String? =
    (this as? kotlinx.serialization.json.JsonPrimitive)?.content
