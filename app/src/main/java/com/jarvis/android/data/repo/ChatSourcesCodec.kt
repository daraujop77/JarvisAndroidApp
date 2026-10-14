package com.jarvis.android.data.repo

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.URI
import java.security.MessageDigest

data class ChatCitation(val ref: String, val title: String, val url: String)

/** Validated source receipts only, never citations inferred from assistant prose. */
object ChatSourcesCodec {
    private val json = Json { ignoreUnknownKeys = true }
    private val refPattern = Regex("^web:[0-9a-f]{20}$")

    private fun verified(url: String, ref: String): Boolean {
        if (url.length !in 1..2048 || !refPattern.matches(ref)) return false
        val uri = try { URI(url) } catch (_: Exception) { return false }
        val host = uri.host?.lowercase().orEmpty()
        if (uri.scheme != "https" || host.isBlank() ||
            uri.rawUserInfo != null || uri.port !in setOf(-1, 443) ||
            host == "localhost" || host.endsWith(".local") ||
            host.endsWith(".internal") || host.endsWith(".test") ||
            host.endsWith(".invalid") ||
            host.matches(Regex("^127\\.[0-9.]+$")) ||
            host.matches(Regex("^10\\.[0-9.]+$")) ||
            host.matches(Regex("^192\\.168\\.[0-9.]+$")) ||
            host.matches(Regex("^172\\.(1[6-9]|2[0-9]|3[01])\\.[0-9.]+$")) ||
            host == "::1" || host == "[::1]") return false
        val calculated = MessageDigest.getInstance("SHA-256")
            .digest(url.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }.take(20)
        return ref == "web:$calculated"
    }

    fun encodeValidated(content: JsonObject?): String {
        if (content?.get("schema")?.let {
                runCatching { it.jsonPrimitive.content }.getOrNull()
            } != "jarvis.chat.content.v1") return ""
        val supplied = runCatching {
            content["sources"]?.jsonArray ?: JsonArray(emptyList())
        }.getOrDefault(JsonArray(emptyList()))
        val seen = mutableSetOf<String>()
        val trusted = supplied.take(20).mapNotNull { item ->
            val source = runCatching { item.jsonObject }.getOrNull()
                ?: return@mapNotNull null
            val url = runCatching {
                source["url"]?.jsonPrimitive?.content.orEmpty()
            }.getOrDefault("")
            val ref = runCatching {
                source["ref"]?.jsonPrimitive?.content.orEmpty()
            }.getOrDefault("")
            if (!verified(url, ref) || !seen.add(url)) return@mapNotNull null
            ChatCitation(ref, runCatching {
                source["title"]?.jsonPrimitive?.content.orEmpty()
            }.getOrDefault("").take(180), url)
        }.take(12)
        return encode(trusted)
    }

    private fun encode(sources: List<ChatCitation>) = buildJsonArray {
        sources.forEach { source ->
            add(buildJsonObject {
                put("ref", source.ref)
                put("title", source.title)
                put("url", source.url)
            })
        }
    }.toString()

    fun decode(raw: String): List<ChatCitation> {
        if (raw.isBlank() || raw.length > 12000) return emptyList()
        val array = runCatching { json.parseToJsonElement(raw).jsonArray }
            .getOrNull() ?: return emptyList()
        val seen = mutableSetOf<String>()
        return array.take(12).mapNotNull { item ->
            val obj = runCatching { item.jsonObject }.getOrNull()
                ?: return@mapNotNull null
            val url = runCatching {
                obj["url"]?.jsonPrimitive?.content.orEmpty()
            }.getOrDefault("")
            val ref = runCatching {
                obj["ref"]?.jsonPrimitive?.content.orEmpty()
            }.getOrDefault("")
            if (!verified(url, ref) || !seen.add(url)) return@mapNotNull null
            ChatCitation(ref, runCatching {
                obj["title"]?.jsonPrimitive?.content.orEmpty()
            }.getOrDefault("").take(180), url)
        }
    }
}
