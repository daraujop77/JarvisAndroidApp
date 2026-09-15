package com.jarvis.android.contract.webv1

import com.jarvis.android.contract.GatewayEvent
import com.jarvis.android.contract.MobileRequest
import com.jarvis.android.data.state.ConnectionState
import com.jarvis.android.data.state.DiagnosticEntry
import com.jarvis.android.data.state.Reducer
import com.jarvis.android.data.state.ReducerOutcome
import com.jarvis.android.data.state.RequestStatus
import com.jarvis.android.data.state.SessionState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PCB-R0/R1 gate, now against the **official** PC-A Web V1 fixtures copied
 * verbatim from `daraujop77/jarvis@main` `contracts/web-v1/` (baseline SHA in
 * the report). The files are consumed unmodified; [WebV1Adapter] is the only
 * translation and the reducer keeps seeing domain events.
 */
class WebV1FixtureTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun resource(path: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream(path)) { "missing $path" }
            .bufferedReader().readText()

    private fun obj(path: String) = json.parseToJsonElement(path.let(::resource)) as JsonObject

    private fun driveOnce(state: SessionState, raw: String): Pair<SessionState, WebV1Adapter.Inbound> {
        val inbound = WebV1Adapter.inbound(raw)
        return when (inbound) {
            is WebV1Adapter.Inbound.Domain -> {
                val out = Reducer.applyWebV1(state, inbound.envelope, inbound.opaqueCursor, 0L)
                out.state to inbound
            }
            is WebV1Adapter.Inbound.IgnoreOptional -> {
                state.copy(lastCursorToken = inbound.opaqueCursor)
                    .withDiag(DiagnosticEntry.Kind.UNKNOWN_EVENT, inbound.type) to inbound
            }
            is WebV1Adapter.Inbound.ProtocolMismatch -> {
                state.copy(connection = ConnectionState.PROTOCOL_MISMATCH) to inbound
            }
            is WebV1Adapter.Inbound.Malformed -> state to inbound
        }
    }

    private fun SessionState.withDiag(kind: DiagnosticEntry.Kind, detail: String): SessionState =
        copy(diagnostics = diagnostics + DiagnosticEntry(0L, kind, detail))

    @Test
    fun spine0PinsOpaqueCursorAndHttpPaths() {
        val spine = obj("contracts/web-v1/spine-0.json")
        val protocol = spine["protocol"] as JsonObject
        assertEquals("jarvis.web.v1", (protocol["name"] as JsonPrimitive).content)
        assertEquals("1.0", (protocol["version"] as JsonPrimitive).content)
        val api = spine["api"] as JsonObject
        assertEquals("/api/v1/health", (api["health_path"] as JsonPrimitive).content)
        assertEquals("/api/v1/requests", (api["requests_path"] as JsonPrimitive).content)
        assertEquals("/api/v1/events", (api["events_path"] as JsonPrimitive).content)
        val event = spine["event"] as JsonObject
        assertEquals("opaque_replay_position", (event["cursor"] as JsonPrimitive).content)
        val known = event["known_types"] as JsonArray
        assertTrue(known.any { (it as JsonPrimitive).content == "message.delta" })
        assertTrue(known.any { (it as JsonPrimitive).content == "approval.required" })
    }

    @Test
    fun officialEventPositiveDecodesToDomainAccepted() {
        val raw = resource("contracts/web-v1/fixtures/event-positive.json")
        val decoded = WebV1Codec.decodeEvent(raw)
        assertTrue(decoded is WebV1Decode.Ok)
        val wire = (decoded as WebV1Decode.Ok).event
        assertEquals("event-spine-positive-001", wire.eventId)
        assertEquals("cursor-spine-001", wire.cursor)
        assertEquals("jarvis.web.event.v1", wire.schema)
        assertEquals(1L, wire.sequence)
        assertEquals(WebV1.FINGERPRINT, wire.contractFingerprint)

        val (state, inbound) = driveOnce(Reducer.beginSend(SessionState(), "req-spine-positive-001", "conversation-spine-001", "hi", 0L), raw)
        assertTrue(inbound is WebV1Adapter.Inbound.Domain)
        val env = (inbound as WebV1Adapter.Inbound.Domain).envelope
        assertTrue(env.event is GatewayEvent.MessageAccepted)
        assertEquals("cursor-spine-001", state.lastCursorToken)
        // opaque token is not numeric
        assertNull(state.lastCursorToken.toLongOrNull())
    }

    @Test
    fun officialReplayDedupesRepeatsAndIgnoresOptional() {
        val frames = json.parseToJsonElement(resource("contracts/web-v1/fixtures/events-client-replay.json")) as JsonArray
        var state = Reducer.beginSend(SessionState(), "req-replay-001", "conversation-replay-001", "hola", 0L)
        for (el in frames) {
            val (next, inbound) = driveOnce(state, el.toString())
            state = next
            // every official replay frame is compatible Web V1
            assertTrue("unexpected $inbound", inbound !is WebV1Adapter.Inbound.Malformed)
        }
        // event-replay-002 is duplicated in the fixture; completed carries final text
        val req = state.requests["req-replay-001"]!!
        assertEquals(RequestStatus.Completed, req.status)
        assertEquals("Hola", req.text)
        assertEquals("cursor-replay-004", state.lastCursorToken)
        // the ui.future_hint optional event left a diagnostic but no crash/state change
        assertTrue(state.diagnostics.any { it.kind == DiagnosticEntry.Kind.UNKNOWN_EVENT && it.detail.contains("ui.future_hint") })
    }

    @Test
    fun officialIncompatibleVersionFailsClosed() {
        val raw = resource("contracts/web-v1/fixtures/event-incompatible-version.json")
        val (_, inbound) = driveOnce(SessionState(), raw)
        assertTrue(inbound is WebV1Adapter.Inbound.ProtocolMismatch)
    }

    @Test
    fun officialUnknownAdditiveIsIgnoredSafely() {
        val raw = resource("contracts/web-v1/fixtures/event-unknown-additive.json")
        val inbound = WebV1Adapter.inbound(raw)
        assertTrue(inbound is WebV1Adapter.Inbound.IgnoreOptional)
        assertEquals("cursor-spine-002", (inbound as WebV1Adapter.Inbound.IgnoreOptional).opaqueCursor)
    }

    @Test
    fun officialApprovalEnvelopeDecodes() {
        val ap = obj("contracts/web-v1/fixtures/approval-required.json")
        assertEquals("jarvis.web.approval.v1", (ap["schema"] as JsonPrimitive).content)
        assertEquals("approval-spine-001", (ap["approval_id"] as JsonPrimitive).content)
        assertEquals("required", (ap["status"] as JsonPrimitive).content)
    }

    @Test
    fun officialRequestPositiveMapsFromSendMessage() {
        val req = obj("contracts/web-v1/fixtures/request-positive.json")
        val wire = WebV1Adapter.outbound(
            MobileRequest.SendMessage("req-spine-positive-001", "conversation-spine-001", "Hola Jarvis"),
            WebV1Identity(
                userId = "owner-001",
                deviceId = "device-001",
                sessionId = "session-spine-001",
                traceId = "trace-spine-positive-001",
                taskId = "task-spine-001",
                runId = "run-spine-001",
            ),
        )
        assertEquals(req["operation"]!!.asStringOrNull(), wire.operation)
        assertEquals("submit", wire.operation)
        assertEquals(req["request_id"]!!.asStringOrNull(), wire.request_id)
        assertEquals(req["conversation_id"]!!.asStringOrNull(), wire.conversation_id)
        assertEquals(req["user_id"]!!.asStringOrNull(), wire.user_id)
        assertEquals(req["task_id"]!!.asStringOrNull(), wire.task_id)
        assertEquals(req["run_id"]!!.asStringOrNull(), wire.run_id)
        assertEquals(req["schema"]!!.asStringOrNull(), wire.schema)
        assertEquals(req["contract_fingerprint"]!!.asStringOrNull(), wire.contractFingerprint)
    }

    @Test
    fun officialSideEffectRequestCarriesIdempotencyKey() {
        // the frozen contract requires side-effecting operations to carry an
        // idempotency key; our encoder always stamps one for action operations.
        val action = MobileRequest.ResolveApproval(
            "action-spine-001", com.jarvis.android.contract.ApprovalOutcome.APPROVED, "idem-spine-001",
        )
        val wire = WebV1Adapter.outbound(
            action, WebV1Identity(userId = "owner-001", deviceId = "device-001", sessionId = "session-spine-001"),
        )
        assertEquals("action", wire.operation)
        assertTrue(wire.sideEffecting)
        assertEquals("idem-spine-001", wire.idempotencyKey)
        assertEquals("action-spine-001", wire.action_id)
        assertEquals(WebV1.FINGERPRINT, wire.contractFingerprint)
    }

    private fun kotlinx.serialization.json.JsonElement.asStringOrNull(): String? =
        (this as? JsonPrimitive)?.contentOrNull
}
