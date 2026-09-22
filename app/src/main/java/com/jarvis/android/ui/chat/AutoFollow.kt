package com.jarvis.android.ui.chat

/**
 * Whether a streaming reply is allowed to pull the list down.
 *
 * Daily v1: "no forced scroll-to-bottom when the user is reading older
 * content; auto-follow resumes when the user returns to the bottom." Snapping
 * a reader back to the newest token is the single most annoying thing a chat
 * can do, so following is a state the reader controls, not a side effect of
 * new text arriving.
 */
object AutoFollow {

    /**
     * How far from the last message still counts as "at the bottom". One row
     * of slack, so a half-scrolled pixel or a growing bubble does not read as
     * an intent to scroll away.
     */
    const val BOTTOM_SLACK = 1

    /** True when the newest message is on screen, within [BOTTOM_SLACK]. */
    fun atBottom(lastVisibleIndex: Int?, lastIndex: Int): Boolean {
        if (lastIndex < 0) return true
        val visible = lastVisibleIndex ?: return true
        return visible >= lastIndex - BOTTOM_SLACK
    }

    /**
     * Next follow state.
     *
     * Leaving the bottom stops the follow. Coming back resumes it. Sending
     * always resumes it, because the reader just produced the newest message
     * and expects to see it.
     */
    fun next(following: Boolean, atBottom: Boolean, justSent: Boolean): Boolean = when {
        justSent -> true
        else -> atBottom
    }

    /** Whether new content should scroll the list right now. */
    fun shouldScroll(following: Boolean, hasMessages: Boolean): Boolean = following && hasMessages
}
