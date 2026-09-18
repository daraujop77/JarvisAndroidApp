package com.jarvis.android.ui.screens

/**
 * TalkBack labels for conversation productivity chrome. Keep these strings in
 * one place so reduced-motion / a11y tests can assert them without driving
 * Compose.
 */
object ConversationA11y {
    const val NEW_CHAT = "New chat"
    const val SEARCH_CHATS = "Search chats"
    const val PIN_ON_DEVICE = "Pin on this device"
    const val UNPIN_ON_DEVICE = "Unpin on this device"
    const val HIDE_ON_DEVICE = "Hide on this device. Server history stays."
    const val UNHIDE_ON_DEVICE = "Show on this device again"
    const val RENAME_ON_DEVICE = "Rename on this device"
    const val SELECT_CHAT = "Select chat"
    const val JUMP_TO_LATEST = "Jump to latest message"
    const val JUMP_TO_TOP = "Jump to first message"
    const val COPY_MESSAGE = "Copy message text"
    const val SHARE_MESSAGE = "Share message text"
    const val LOCAL_ONLY_HINT = "On this device only"
    const val HIDDEN_FILTER = "Hidden on this device"
    const val ACTIVITY_SINCE_OPEN = "New activity since last opened on this device"
    const val EMPTY_CHATS = "No chats yet. Start a conversation to see it here."
    const val EMPTY_SEARCH = "No chats match this search on this device."
    const val EMPTY_HIDDEN = "Nothing is hidden on this device."
    const val EMPTY_THREAD = "No messages in this chat yet."
    const val OFFLINE = "Offline. Showing chats saved on this device."
    const val CONNECTION_ERROR = "Connection error. Showing chats saved on this device."
    const val SHARE_BLOCKED = "This message cannot be copied or shared."
    const val HIDE_CONFIRMATION = "Hidden on this device. Server history is unchanged."
    const val UNHIDE_CONFIRMATION = "Shown on this device again. Server history was never deleted."

    fun pinAction(pinned: Boolean): String = if (pinned) UNPIN_ON_DEVICE else PIN_ON_DEVICE

    fun conversationRow(title: String, pinned: Boolean, activitySinceOpen: Boolean): String = buildString {
        append(title)
        if (pinned) append(". Pinned on this device")
        if (activitySinceOpen) append(". ").append(ACTIVITY_SINCE_OPEN)
    }
}
