package com.jarvis.android.ui.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Daily v1: a streaming reply must not drag the reader away from older
 * content, and following must come back on its own.
 */
class AutoFollowTest {

    @Test
    fun emptyConversationCountsAsBottom() {
        assertTrue(AutoFollow.atBottom(lastVisibleIndex = null, lastIndex = -1))
    }

    @Test
    fun lastMessageOnScreenIsBottom() {
        assertTrue(AutoFollow.atBottom(lastVisibleIndex = 9, lastIndex = 9))
    }

    @Test
    fun oneRowOfSlackStillCountsAsBottom() {
        assertTrue(AutoFollow.atBottom(lastVisibleIndex = 8, lastIndex = 9))
    }

    @Test
    fun readingOlderContentIsNotBottom() {
        assertFalse(AutoFollow.atBottom(lastVisibleIndex = 3, lastIndex = 40))
    }

    @Test
    fun scrollingUpStopsFollowingAndReturningResumesIt() {
        val afterScrollUp = AutoFollow.next(following = true, atBottom = false, justSent = false)
        assertFalse(afterScrollUp)
        val afterReturn = AutoFollow.next(following = afterScrollUp, atBottom = true, justSent = false)
        assertTrue(afterReturn)
    }

    @Test
    fun sendingResumesFollowingEvenWhileReadingOlderContent() {
        assertTrue(AutoFollow.next(following = false, atBottom = false, justSent = true))
    }

    @Test
    fun streamingDoesNotScrollWhileTheReaderIsAway() {
        assertFalse(AutoFollow.shouldScroll(following = false, hasMessages = true))
        assertTrue(AutoFollow.shouldScroll(following = true, hasMessages = true))
        assertFalse(AutoFollow.shouldScroll(following = true, hasMessages = false))
    }
}
