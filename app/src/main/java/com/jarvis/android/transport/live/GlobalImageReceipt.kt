package com.jarvis.android.transport.live

import java.security.MessageDigest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Exact owner-scoped, generated-image receipt; no image bytes in the JSON. */
internal data class GlobalImageReceipt(
    val assetId: String,
    val sha256: String,
    val mimeType: String,
)

private val globalAssetId = Regex("^[A-Za-z0-9_-]{30,64}$")
private val globalSha256 = Regex("^[a-fA-F0-9]{64}$")
private val globalMime = setOf("image/png", "image/jpeg", "image/webp")

internal fun parseGlobalImageReceipts(reply: JsonObject): List<GlobalImageReceipt> {
    fun read(entry: JsonObject): GlobalImageReceipt? {
        val id = (entry["asset_id"] as? JsonPrimitive)?.content ?: return null
        val hash = (entry["sha256"] as? JsonPrimitive)?.content ?: return null
        val mime = (entry["mime_type"] as? JsonPrimitive)?.content ?: return null
        if (!globalAssetId.matches(id) || !globalSha256.matches(hash) || mime !in globalMime)
            return null
        return GlobalImageReceipt(id, hash.lowercase(), mime)
    }
    val media = (reply["media"] as? JsonObject)?.get("generated_images") as? JsonArray
    val blocks = (reply["content"] as? JsonObject)?.get("blocks") as? JsonArray
    val all = buildList {
        media?.forEach { (it as? JsonObject)?.let { item -> read(item)?.let(::add) } }
        blocks?.forEach {
            (it as? JsonObject)?.takeIf {
                entry -> (entry["type"] as? JsonPrimitive)?.content == "image"
            }?.let { item -> read(item)?.let(::add) }
        }
    }
    // Two same-ID receipts with disagreeing immutable metadata are not an
    // invitation to choose a convenient one; reject the whole conflicted ID.
    val conflicts = all.groupBy { it.assetId }
        .filterValues { versions -> versions.distinctBy { it.sha256 to it.mimeType }.size > 1 }
        .keys
    return all.filterNot { it.assetId in conflicts }.distinctBy { it.assetId }.take(3)
}

internal fun verifiedGlobalImageBytes(
    receipt: GlobalImageReceipt,
    bytes: ByteArray,
): Boolean {
    if (!globalAssetId.matches(receipt.assetId) ||
        !globalSha256.matches(receipt.sha256) ||
        receipt.mimeType !in globalMime ||
        bytes.isEmpty() || bytes.size > 12 * 1024 * 1024) return false
    val signature = when (receipt.mimeType) {
        "image/jpeg" -> bytes.size >= 3 &&
            bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() &&
            bytes[2] == 0xff.toByte()
        "image/png" -> bytes.size >= 8 && bytes.sliceArray(0..7).contentEquals(
            byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a))
        "image/webp" -> bytes.size >= 12 &&
            bytes.copyOfRange(0, 4).toString(Charsets.US_ASCII) == "RIFF" &&
            bytes.copyOfRange(8, 12).toString(Charsets.US_ASCII) == "WEBP"
        else -> false
    }
    if (!signature) return false
    val hash = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }
    return hash.equals(receipt.sha256, ignoreCase = true)
}
