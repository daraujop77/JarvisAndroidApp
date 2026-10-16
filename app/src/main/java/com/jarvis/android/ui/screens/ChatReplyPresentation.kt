package com.jarvis.android.ui.screens

import java.net.URI

/** Presentation-only extraction: never mistake model-written links for verified browsing. */
internal data class ChatSource(val title: String, val url: String)
internal data class ChatReplyPresentation(val body: String, val sources: List<ChatSource>)

private val SOURCES_HEADING = Regex(
    """^\s*(?:#{1,4}\s*)?(?:\*\*)?(?:Fuentes consultadas|Referencias confiables|Fuentes|Referencias)(?:\*\*)?\s*:?\s*$""",
    RegexOption.IGNORE_CASE,
)
private val MARKDOWN_SOURCE = Regex("""^\s*[-*]\s+\[([^\]\r\n]{1,140})\]\((https://[^\s)]+)\)\s*$""")
private val HTTPS_URL = Regex("""https://[^\s<>)]+""")
private val LIST_MARKER = Regex("""^\s*[-*]\s+""")

private fun verifiedCitation(title: String, rawUrl: String): ChatSource? {
    val url = rawUrl.trim().trimEnd('.', ',', ';')
    val safe = runCatching {
        val uri = URI(url)
        uri.scheme == "https" && !uri.host.isNullOrBlank() &&
            uri.userInfo == null && uri.port in -1..65535 &&
            url.length <= 2048 && !url.any(Char::isISOControl)
    }.getOrDefault(false)
    if (!safe) return null
    return ChatSource(title.trim().take(140).ifBlank {
        URI(url).host.removePrefix("www.")
    }, url)
}

/**
 * Accept both the historical machine footer and the natural-language reference
 * footer produced by older models. Keep the whole reply when parsing is uncertain.
 * URLs are untrusted model claims and only become tappable after a user action.
 */
internal fun splitChatSources(reply: String): ChatReplyPresentation {
    val lines = reply.replace("\r\n", "\n").lines()
    val headingIndex = lines.indices.lastOrNull { SOURCES_HEADING.matches(lines[it]) }
        ?: return ChatReplyPresentation(reply, emptyList())
    val trailing = lines.drop(headingIndex + 1).dropWhile(String::isBlank)
        .dropLastWhile(String::isBlank)
    if (trailing.isEmpty() || trailing.size > 48) {
        return ChatReplyPresentation(reply, emptyList())
    }
    val sources = mutableListOf<ChatSource>()
    var index = 0
    while (index < trailing.size) {
        val row = trailing[index].trim()
        if (row.isBlank()) {
            index++
            continue
        }
        val markdown = MARKDOWN_SOURCE.matchEntire(row)
        if (markdown != null) {
            sources += verifiedCitation(markdown.groupValues[1], markdown.groupValues[2])
                ?: return ChatReplyPresentation(reply, emptyList())
            index++
            continue
        }
        if (!LIST_MARKER.containsMatchIn(row)) {
            return ChatReplyPresentation(reply, emptyList())
        }
        val entry = row.replaceFirst(LIST_MARKER, "")
        var title = entry
        var url = HTTPS_URL.find(entry)?.value
        if (url != null) {
            title = entry.substringBefore(url).trim().trimEnd(':', '(', ' ')
        } else {
            val next = trailing.getOrNull(index + 1)?.trim()
            if (next != null && HTTPS_URL.matches(next)) {
                url = next
                index++
            }
        }
        if (url == null) return ChatReplyPresentation(reply, emptyList())
        sources += verifiedCitation(title.trim().trimEnd(':'), url)
            ?: return ChatReplyPresentation(reply, emptyList())
        if (sources.size > 12) return ChatReplyPresentation(reply, emptyList())
        index++
    }
    if (sources.isEmpty()) return ChatReplyPresentation(reply, emptyList())
    return ChatReplyPresentation(lines.take(headingIndex).joinToString("\n").trimEnd(), sources.distinctBy { it.url })
}

internal fun isGeneratedImageMessage(requestId: String): Boolean =
    requestId.startsWith("img_") || requestId.startsWith("agentimg_")
