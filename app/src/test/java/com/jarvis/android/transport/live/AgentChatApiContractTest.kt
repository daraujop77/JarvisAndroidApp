package com.jarvis.android.transport.live

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** New agent wire contract is additive; old APK response types stay intact. */
class AgentChatApiContractTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun stableScopeRejectsMissingPrincipalContext() {
        val scope = AgentConversationScope("prj_story", "conversation-123")
        assertEquals("prj_story", scope.projectId)
        assertEquals("conversation-123", scope.conversationId)
        try {
            AgentConversationScope("prj_story", "")
            throw AssertionError("empty conversation must be rejected")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun preparedActionIsBoundToServerRunAndExpiry() {
        val wire = """
          {
            "schema":"jarvis.writing-room.copilot.task.v1",
            "task_id":"ar_123456",
            "project_id":"prj_story",
            "conversation_id":"conversation-123",
            "state":"READY",
            "agent_run_state":"WAITING_CONFIRMATION",
            "requires_confirmation":true,
            "action_hash":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            "approval_id":"ap_12345",
            "approval_expires_at":"2026-10-12T10:00:00+00:00",
            "steps":[{"tool":"save_story_idea","arguments":{"body":"Idea"},"effect":"PROPOSED_ONLY"}],
            "results":[],
            "extra_future_field":"allowed"
          }
        """.trimIndent()
        val task = json.decodeFromString<AgentRunStatus>(wire)
        assertEquals("ar_123456", task.task_id)
        assertEquals("conversation-123", task.conversation_id)
        assertEquals("WAITING_CONFIRMATION", task.agent_run_state)
        assertTrue(task.requires_confirmation)
        assertEquals(64, task.action_hash.length)
        assertEquals("ap_12345", task.approval_id)
        assertEquals("save_story_idea", task.steps.single().tool)
    }

    @Test fun agentEventsUseSequenceCursorAndNoImagePayload() {
        val payload = """
            {"schema":"jarvis.agent.events.v1","run_id":"ar_0123456789abcdef01234567",
             "events":[{"run_id":"ar_0123456789abcdef01234567",
                        "seq":3,"state":"WAITING_CONFIRMATION",
                        "pending_approval_id":"ap_234"}],
             "next_cursor":3}
        """.trimIndent()
        val page = json.decodeFromString<AgentEventPage>(payload)
        assertEquals(3, page.next_cursor)
        assertEquals(3, page.events.single().seq)
        assertEquals("WAITING_CONFIRMATION", page.events.single().state)
        assertTrue(page.events.single().pending_approval_id.startsWith("ap_"))
    }

    @Test fun completedTurnCanReuseLegacyV64ChatEnvelope() {
        val body = """
          {
            "schema":"jarvis.writing-room.chat.v1",
            "classification":{"task_class":"COPILOT_AGENT","source":"adaptive_model_decisions"},
            "selected_participant":"copilot",
            "turn":{"project_id":"prj_story","participant":{"id":"copilot","label":"Copilot"},
                    "routing":{"destination":"adaptive_agent"},"response":{"text":"Hola"}},
            "copilot_task":null,"visual_assets":[]
          }
        """.trimIndent()
        val parsed = json.decodeFromString<WritingRoomAutoChat>(body)
        assertEquals("Hola", parsed.turn.response.text)
        assertEquals("copilot", parsed.selected_participant)
        assertFalse(parsed.copilot_task != null)
    }
}
