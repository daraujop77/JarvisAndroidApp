package com.jarvis.android.ui.writing

import com.jarvis.android.data.repo.ChatCitation
import com.jarvis.android.data.repo.ChatSourcesCodec
import com.jarvis.android.transport.live.JarvisChatContent
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * External web evidence is presentation-only. Reuse Chat Global's exact
 * HTTPS URL + SHA-256 receipt validator; never interpret assistant prose,
 * Wiki IDs or image candidates as clickable public citations.
 */
fun verifiedCopilotPublicSources(content: JarvisChatContent?): List<ChatCitation> {
    if (content?.schema != "jarvis.chat.content.v1") return emptyList()
    if (content.sources.isEmpty()) return emptyList()
    return ChatSourcesCodec.decode(Json.encodeToString(content.sources))
}
