package com.jarvis.android.ui.writing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The attachment ID is intentionally stable after retry. The Compose decoder
 * keys on both this identifier and loading, so the same file can be decoded
 * again after a read or download failure without generating another image.
 */
class VisualAssetPreviewRecoveryTest {
    @Test fun remoteDrivePermissionErrorIsNotBlamedOnAndroidLogin() {
        val reply = friendlyVisualAssetError(
            "visual_drive_bridge_error:drive_http_403"
        )
        assertTrue(reply.contains("JARVIS no pudo acceder"))
        assertTrue(reply.contains("permisos actuales"))
        assertFalse(reply.contains("Tu sesión"))
    }

    @Test fun OAuthAndRateLimitFailuresPreserveOriginalAndDoNotAutoRegenerate() {
        val oauth = friendlyVisualAssetError(
            "visual_drive_bridge_error:oauth_http_400"
        )
        assertTrue(oauth.contains("autorización del servicio"))
        assertTrue(oauth.contains("sigue intacta"))
        val retry = friendlyVisualAssetError(
            "visual_drive_bridge_error:drive_http_429"
        )
        assertTrue(retry.contains("sin regenerarla"))
    }

    @Test fun sanitizedUnknownBridgeErrorHasActionableFallback() {
        val display = friendlyVisualAssetError(
            "visual_drive_bridge_error:bridge_unavailable"
        )
        assertTrue(display.contains("servicio de Drive"))
        assertFalse(display.contains("secret"))
    }

    @Test fun appSession403StillHasDifferentGuidance() {
        assertTrue(friendlyVisualAssetError("HTTP 403")
            .contains("Tu sesión no tiene acceso"))
    }

    @Test fun cannotDecodeWhileReDownloadingOriginal() {
        assertFalse(shouldDecodeVisualPreview("same-approved-receipt", true))
    }

    @Test fun retriesDecodeForTheSameAttachmentAfterDownloadFinishes() {
        val id = "same-approved-receipt"
        assertTrue(shouldDecodeVisualPreview(id, false))
        assertFalse(shouldDecodeVisualPreview(id, true))
        assertTrue(shouldDecodeVisualPreview(id, false))
    }

    @Test fun missingAttachmentNeverAttemptsToDecode() {
        assertFalse(shouldDecodeVisualPreview(null, false))
        assertFalse(shouldDecodeVisualPreview("", false))
        assertFalse(shouldDecodeVisualPreview("  ", false))
    }
}
