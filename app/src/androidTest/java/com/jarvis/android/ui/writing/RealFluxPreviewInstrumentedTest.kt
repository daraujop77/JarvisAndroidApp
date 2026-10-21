package com.jarvis.android.ui.writing

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jarvis.android.data.media.AttachmentStore
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.security.MessageDigest
import java.util.Base64

/**
 * A REAL device/emulator bitmap + Compose preview gate, not synthetic-model
 * dispatch. The 32x32 lossless PNG was downscaled from the independently
 * verified 1024x1024 real Nous FLUX 2 Klein 9B image (first canary).
 *
 * Original SHA256: bcbb45314e757aa2ada3364c5b8ece481b428784b94e437565baa4046005186f
 * Source: GitHub Actions run 39557770183, original bytes: 1,186,502.
 * Thumbnail SHA256: 9ea06689be068127447a0c816134a533dd00003d6c4cc544e9ccdc478d692483.
 *
 * This test checks a real Android decode and real app preview component.
 * It does NOT verify actual JARVIS navigation, live user authorization,
 * a production Drive attachment, or real on-device Copilot requests.
 */
@RunWith(AndroidJUnit4::class)
class RealFluxPreviewInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    private val pngBase64 =
        "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"

    private val expectedSha = "9ea06689be068127447a0c816134a533dd00003d6c4cc544e9ccdc478d692483"

    @Test
    fun realModelDerivedPngDecodesRendersAndOpensZoomWithoutProviderCall() {
        val bytes = Base64.getDecoder().decode(pngBase64)
        assertEquals(1514, bytes.size)
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
        assertEquals(expectedSha, digest)
        assertArrayEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a),
            bytes.copyOfRange(0, 8))
        assertEquals(32, java.nio.ByteBuffer.wrap(bytes, 16, 4).int)
        assertEquals(32, java.nio.ByteBuffer.wrap(bytes, 20, 4).int)

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = AttachmentStore(context, "preapk-real-flux-ui-proof")
        val attachment = store.stageVerifiedGeneratedBase64(
            pngBase64, expectedSha, "image/png",
            "preapk-real-flux-ui-recovered-original-sha-bcbb4531",
        )
        assertNotNull("Android could not preserve the genuine-model thumbnail", attachment)
        val saved = requireNotNull(attachment)
        assertEquals("image/png", saved.mimeType)
        assertArrayEquals(bytes, saved.file.readBytes())

        compose.setContent {
            MaterialTheme {
                VisualAssetPreview(
                    attachmentId = saved.attachmentId,
                    store = store,
                    stored = true,
                    loading = false,
                    error = null,
                    description = "Imagen Nous verificada",
                    height = 240.dp,
                    onRetry = { throw AssertionError("No fetch/retry is allowed in this test") },
                )
            }
        }

        val preview = "Imagen Nous verificada. Toca para ampliar."
        compose.waitUntil(15_000) {
            compose.onAllNodes(
                androidx.compose.ui.test.hasContentDescription(preview),
            ).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription(preview).assertExists().performClick()
        compose.onNodeWithContentDescription(
            "Imagen Nous verificada. Pellizca para ampliar o arrastra.",
        ).assertExists()
        compose.onNodeWithText("Cerrar imagen").performClick()
        compose.onNodeWithContentDescription(preview).assertExists()
    }

    @Test
    fun tamperedPngCannotReplaceVerifiedImageOrApproveWrongBytes() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = AttachmentStore(context, "preapk-real-flux-ui-tamper")
        val good = store.stageVerifiedGeneratedBase64(
            pngBase64, expectedSha, "image/png", "preapk-real-flux-same-receipt",
        )
        assertNotNull(good)
        val tampered = Base64.getDecoder().decode(pngBase64).copyOf().apply {
            this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte()
        }
        assertNull(store.stageVerifiedGeneratedBase64(
            Base64.getEncoder().encodeToString(tampered), expectedSha,
            "image/png", "preapk-real-flux-same-receipt",
        ))
        assertNull(store.stageVerifiedGeneratedBase64(
            pngBase64, expectedSha, "image/jpeg", "preapk-real-flux-other-mime",
        ))
        assertEquals(expectedSha, MessageDigest.getInstance("SHA-256")
            .digest(requireNotNull(good).file.readBytes())
            .joinToString("") { "%02x".format(it) })
    }
}
