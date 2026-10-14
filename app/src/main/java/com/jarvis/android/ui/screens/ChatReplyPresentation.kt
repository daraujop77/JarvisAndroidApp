package com.jarvis.android.ui.screens

import java.net.URI

/** Keep provider citations out of the reading flow while retaining every verified HTTPS source. */
internal data class ChatSource(val title: String, val url: String)
internal data class ChatReplyPresentation(val body: String, val sources: List<ChatSource>)

private const val SOURCE_MARKER = "\n\nFuentes consultadas:\n"
private val SOURCE_LINE = Regex("""- \[([^\]\r\n]{1,140})\]\((https://[^\s)]+)\)""")

internal fun splitChatSources(reply: String): ChatReplyPresentation {
    val at = reply.lastIndexOf(SOURCE_MARKER)
    if (at < 0) return ChatReplyPresentation(reply, emptyList())
    val rows = reply.substring(at + SOURCE_MARKER.length).lines().filter(String::isNotBlank)
    if (rows.isEmpty() || rows.size > 12) return ChatReplyPresentation(reply, emptyList())
    val citations = rows.map { row ->
        val found = SOURCE_LINE.matchEntire(row.trim())
            ?: return ChatReplyPresentation(reply, emptyList())
        val url = found.groupValues[2]
        val valid = runCatching {
            val parsed = URI(url)
            parsed.scheme == "https" && !parsed.host.isNullOrBlank() &&
                parsed.userInfo == null
        }.getOrDefault(false)
        if (!valid) return ChatReplyPresentation(reply, emptyList())
        ChatSource(found.groupValues[1], url)
    }
    return ChatReplyPresentation(reply.substring(0, at).trimEnd(), citations)
}

internal fun isGeneratedImageMessage(requestId: String): Boolean =
    requestId.startsWith("img_") || requestId.startsWith("agentimg_")
