package com.jarvis.android.ui.theme

import com.jarvis.android.ui.screens.ConversationA11y
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
}
