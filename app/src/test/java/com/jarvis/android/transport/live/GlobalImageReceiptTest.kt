package com.jarvis.android.transport.live

import java.security.MessageDigest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test

class GlobalImageReceiptTest {
    private val id = "c".repeat(48)
    private val png = byteArrayOf(
        0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
        0x00, 0x00, 0x00,
    )
    private fun digest(data: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(data)
            .joinToString("") { "%02x".format(it) }

    @Test fun mediaReceiptPreservesHashAndMimeAndRejectsTamperedBytes() {
        val sha = digest(png)
        val body = Json.parseToJsonElement(
            """{"response":{"text":"Image ready"},"media":{"generated_images":[
              {"asset_id":"$id","sha256":"$sha","mime_type":"image/png"}
            ]},"content":{"blocks":[
              {"type":"image","asset_id":"$id","sha256":"$sha","mime_type":"image/png"}
            ]}}"""
        ).jsonObject
        val result = parseGlobalImageReceipts(body)
        assertEquals(1, result.size)
        assertEquals("image/png", result.single().mimeType)
        assertEquals(sha, result.single().sha256)
        assertEquals(id, result.single().assetId)
        assertTrue(verifiedGlobalImageBytes(result.single(), png))
        assertFalse(verifiedGlobalImageBytes(result.single(), png + 0x42.toByte()))
        assertFalse(verifiedGlobalImageBytes(
            result.single().copy(mimeType = "image/jpeg"), png))
    }

    @Test fun conflictingOrUnsignedMetadataCannotSelectAnImage() {
        val sha = digest(png)
        val bad = Json.parseToJsonElement(
            """{"media":{"generated_images":[
              {"asset_id":"$id","sha256":"$sha","mime_type":"image/png"},
              {"asset_id":"$id","sha256":"${"0".repeat(64)}","mime_type":"image/jpeg"}
            ]}}"""
        ).jsonObject
        assertTrue(parseGlobalImageReceipts(bad).isEmpty())
        val unsigned = Json.parseToJsonElement(
            """{"media":{"generated_images":[{"asset_id":"$id"}]}}"""
        ).jsonObject
        assertTrue(parseGlobalImageReceipts(unsigned).isEmpty())
    }

    @Test fun originalWebpAndJpegMimeMustAgreeWithSignature() {
        val id = "d".repeat(48)
        val webp = "RIFF".toByteArray() + byteArrayOf(0, 0, 0, 0) +
            "WEBP".toByteArray()
        val jpeg = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 1)
        assertTrue(verifiedGlobalImageBytes(
            GlobalImageReceipt(id, digest(webp), "image/webp"), webp))
        assertTrue(verifiedGlobalImageBytes(
            GlobalImageReceipt(id, digest(jpeg), "image/jpeg"), jpeg))
        assertFalse(verifiedGlobalImageBytes(
            GlobalImageReceipt(id, digest(jpeg), "image/png"), jpeg))
    }
}
