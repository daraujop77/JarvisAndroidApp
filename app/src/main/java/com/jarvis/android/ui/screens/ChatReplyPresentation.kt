package com.jarvis.android.ui.screens

import java.net.URI

/** Presentation-only extraction: never mistake model-written links for verified browsing. */
internal data class ChatSource(val title: String, val url: String)
internal data class ChatReplyPresentation(val body: String, val sources: List<ChatSource>)

private val SOURCES_HEADING = Regex(
    """^\s*(?:#{1,4}\s*)?(?:\*\*)?(?:Fuentes consultadas|Referencias confiables|Fuentes|Referencias)(?:\*\*)?\s*:?\s*$""",
    RegexOption.IGNORE_CASE,
)
private val MARKDOWN_SOURCE = Regex("""^\s*[-*•]\s+\[([^\]\r\n]{1,140})\]\((https://[^\s)]+)\)\s*$""")
private val HTTPS_URL = Regex("""https://[^\s<>)]+""")
private val LIST_MARKER = Regex("""^\s*[-*•]\s+""")

private fun isPublicCitationHost(hostname: String): Boolean {
    val host = hostname.lowercase()
    if (host.length > 253 || host.endsWith(".")) return false
    if (listOf("localhost", "local", "internal", "test", "invalid",
               "lan", "localdomain", "home.arpa")
            .any { host == it || host.endsWith(".$it") }) return false
    // Reject address literals, private host aliases and malformed DNS names.
    // This is link presentation validation, never a replacement for the web
    // subprocess's network/redirect egress restrictions.
    val labels = host.split(".")
    if (labels.size < 2 || labels.any {
            it.length !in 1..63 || it.startsWith("-") || it.endsWith("-") ||
                !it.matches(Regex("[a-z0-9-]+"))
        }) return false
    if (!labels.last().contains(Regex("[a-z]"))) return false
    if (host.matches(Regex("[0-9a-fx.]+"))) return false
    return true
}

private fun verifiedCitation(title: String, rawUrl: String): ChatSource? {
    val url = rawUrl.trim().trimEnd('.', ',', ';')
    val safe = runCatching {
        val uri = URI(url)
        uri.scheme == "https" &&
            uri.host?.let(::isPublicCitationHost) == true &&
            uri.userInfo == null && uri.port in listOf(-1, 443) &&
            url.length <= 1800 && !url.any(Char::isISOControl)
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
