package com.jarvis.android.transport.live

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * What the owner reads in the failed bubble when the chat endpoint rejects a
 * turn. The raw body is a `jarvis.web.error.v1` envelope meant for logs, not
 * for a person.
 */
internal object ChatErrorText {

    fun fromHttp(code: Int, body: String): String {
        val envelope = runCatching { Json.parseToJsonElement(body) as? JsonObject }.getOrNull()
        val error = (envelope?.get("error") as? JsonPrimitive)?.contentOrNull
        return when {
            error == "web_research_unavailable" ->
                "Web search isn't available right now. Try again, or ask something that doesn't need current information."
            code == 429 -> "Too many requests. Wait a moment and try again."
            code == 403 -> "This account isn't allowed to do that."
            code in 500..599 -> "JARVIS's server had a problem ($code). Try again in a moment."
            else -> "The request failed ($code)."
        }
    }
}
