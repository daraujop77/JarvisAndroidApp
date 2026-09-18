package com.jarvis.android.ui.theme

import com.jarvis.android.ui.screens.ConversationA11y
import com.jarvis.android.ui.screens.HomeA11y
import com.jarvis.android.ui.screens.ProjectsA11y
import com.jarvis.android.ui.screens.TaskApprovalA11y
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReducedMotionTest {

    @Test
    fun decorativeDurationsCollapseWhenReducedMotionIsOn() {
        assertEquals(0, JarvisMotion.durationMs(reducedMotion = true, durationMs = 280))
        assertEquals(280, JarvisMotion.durationMs(reducedMotion = false, durationMs = 280))
        assertNull(JarvisMotion.loopMs(reducedMotion = true, durationMs = 1500))
        assertEquals(1500, JarvisMotion.loopMs(reducedMotion = false, durationMs = 1500))
    }

    @Test
    fun conversationActionsHaveContentDescriptions() {
        assertEquals("Pin on this device", ConversationA11y.pinAction(false))
        assertEquals("Unpin on this device", ConversationA11y.pinAction(true))
        assertTrue(ConversationA11y.HIDE_ON_DEVICE.contains("Server history stays"))
        assertTrue(ConversationA11y.conversationRow("Kitchen", pinned = true, activitySinceOpen = true)
            .contains("Pinned on this device"))
        assertTrue(ConversationA11y.JUMP_TO_LATEST.isNotBlank())
        assertTrue(ConversationA11y.COPY_MESSAGE.isNotBlank())
        assertTrue(ConversationA11y.SHARE_MESSAGE.isNotBlank())
        assertTrue(ConversationA11y.EMPTY_CHATS.isNotBlank())
        assertTrue(ConversationA11y.OFFLINE.contains("this device"))
        assertTrue(ConversationA11y.CONNECTION_ERROR.contains("this device"))
    }

    @Test
    fun homeCommandCenterHasContentDescriptions() {
        assertEquals("JARVIS home", HomeA11y.SCREEN)
        assertTrue(HomeA11y.PROFILE_UNKNOWN.contains("Not reported by server"))
        assertTrue(HomeA11y.APPROVALS_OWNER_ONLY.contains("owner-only"))
        assertTrue(HomeA11y.PROJECTS_NOT_LIVE.contains("Not live-backed"))
        assertTrue(HomeA11y.visualState("Idle").contains("Idle"))
        assertTrue(HomeA11y.QUICK_CHAT.isNotBlank())
        assertTrue(HomeA11y.QUICK_TASKS.isNotBlank())
        assertTrue(HomeA11y.QUICK_APPROVALS.isNotBlank())
        assertTrue(HomeA11y.QUICK_SETTINGS.isNotBlank())
    }

    @Test
    fun taskAndApprovalActionsHaveContentDescriptions() {
        assertTrue(TaskApprovalA11y.OWNER_ONLY.contains("owner"))
        assertTrue(TaskApprovalA11y.RESOLVING_NOT_AUTHORITATIVE.contains("not the result"))
        assertTrue(TaskApprovalA11y.SERVER_OUTCOME.contains("server"))
        assertTrue(TaskApprovalA11y.EXPIRED_LOCAL.contains("device clock"))
        assertTrue(TaskApprovalA11y.DUPLICATE_TAP_BLOCKED.contains("Already submitted"))
        assertTrue(TaskApprovalA11y.BIOMETRIC_REQUIRED.contains("Not approved"))
        assertTrue(TaskApprovalA11y.PROGRESS_HELD.contains("session state"))
        assertEquals("Task group RUNNING", TaskApprovalA11y.group("RUNNING"))
        assertEquals(0, JarvisMotion.durationMs(reducedMotion = true, durationMs = 280))
    }

    @Test
    fun projectShellHasContentDescriptions() {
        assertTrue(ProjectsA11y.NOT_LIVE.contains("Not live-backed"))
        assertTrue(ProjectsA11y.RELEASE_HIDDEN.contains("no live projects contract"))
        assertTrue(ProjectsA11y.OFFLINE.contains("not a live backend"))
        assertTrue(ProjectsA11y.ACTIVITY_PLACEHOLDER.contains("Not live-backed"))
        assertTrue(ProjectsA11y.card("Home", "ACTIVE", "1 linked chat").contains("Not live-backed"))
        assertEquals(0, JarvisMotion.durationMs(reducedMotion = true, durationMs = 280))
    }
}
