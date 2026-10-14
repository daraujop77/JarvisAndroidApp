package com.jarvis.android.transport.live

import java.security.MessageDigest
import java.util.Base64

/**
 * Fail-closed verification of an APPROVED visual reference before Android
 * allocates an app-private attachment. This has no network or storage access.
 *
 * Authentication and project ACL remain the VPS's responsibility; these
 * checks bind one authenticated byte response to the precise approved asset
 * advertised in the Copilot task. Actual bitmap decoding happens on staging.
 */
internal fun isVerifiedApprovedVisualContent(
    approved: CopilotApprovedVisualAsset,
    response: WritingVisualAssetContent,
): Boolean {
    if (approved.status != "APPROVED" || approved.storage_state != "stored") return false
    if (!Regex("^va_[A-Za-z0-9_]{6,80}$").matches(approved.asset_id)) return false
    if (!Regex("^[0-9a-fA-F]{64}$").matches(approved.sha256)) return false
    if (response.schema != "jarvis.visual.asset.content.v1" ||
        response.asset_id != approved.asset_id ||
        !response.sha256.equals(approved.sha256, ignoreCase = true)
    ) return false
    val mime = response.mime_type
    if (mime !in setOf("image/png", "image/jpeg", "image/webp")) return false
    val encoded = response.image_base64
    if (encoded.isEmpty() || encoded.length > 16 * 1024 * 1024) return false
    val bytes = try {
        Base64.getDecoder().decode(encoded)
    } catch (_: IllegalArgumentException) {
        return false
    }
    if (bytes.isEmpty() || bytes.size > 12 * 1024 * 1024) return false
    val signatureMatches = when (mime) {
        "image/png" -> bytes.size >= 8 &&
            bytes.copyOfRange(0, 8).contentEquals(byteArrayOf(
                0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
            ))
        "image/jpeg" -> bytes.size >= 3 &&
            bytes[0] == 0xff.toByte() &&
            bytes[1] == 0xd8.toByte() &&
            bytes[2] == 0xff.toByte()
        "image/webp" -> bytes.size >= 12 &&
            String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" &&
            String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP"
        else -> false
    }
    if (!signatureMatches) return false
    val actual = MessageDigest.getInstance("SHA-256").digest(bytes)
    val digest = actual.joinToString("") { "%02x".format(it) }
    return digest.equals(approved.sha256, ignoreCase = true)
}
