package com.jarvis.android.data.local

/**
 * Copy / share may only emit the visible chat body. Credential-looking
 * substrings and raw request/event envelopes are refused rather than redacted
 * into something that still looks shareable.
 */
object ShareSafeText {

    private val blockedSubstrings = listOf(
        "bearer ",
        "token=",
        "password",
        "authorization:",
        "secret",
        "clientrequestid",
        "lastcursortoken",
        "\"payload\"",
        "\"attachments\"",
        "\"traceid\"",
        "\"envelope\"",
    )

    fun visibleChatText(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return null
        val lower = trimmed.lowercase()
        if (blockedSubstrings.any { lower.contains(it) }) return null
        if (looksLikeInternalPayload(trimmed)) return null
        return trimmed.take(MAX_SHARE_CHARS)
    }

    private fun looksLikeInternalPayload(text: String): Boolean {
        val t = text.trim()
        if (!(t.startsWith("{") && t.endsWith("}"))) return false
        val lower = t.lowercase()
        return lower.contains("\"type\"") ||
            lower.contains("\"client_request") ||
            lower.contains("\"clientrequest") ||
            lower.contains("\"cursor\"") ||
            lower.contains("\"protocol_version\"")
    }

    private const val MAX_SHARE_CHARS = 8_000
}
