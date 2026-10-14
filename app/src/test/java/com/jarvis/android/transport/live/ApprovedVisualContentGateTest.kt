package com.jarvis.android.transport.live

import java.security.MessageDigest
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Authenticated image-fetch wire + pure SHA/format gate, without an APK or emulator. */
class ApprovedVisualContentGateTest {
    private lateinit var server: MockWebServer
    private lateinit var session: JarvisAppSession
    private val assetId = "va_0123456789abcdef01234567"
    // Small fixture image; images from the user's Drive are NEVER used in tests.
    private val png = Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/lv8AAAAASUVORK5CYII=",
    )
    private val digest = MessageDigest.getInstance("SHA-256").digest(png)
        .joinToString("") { "%02x".format(it) }
    private val approved = CopilotApprovedVisualAsset(
        asset_id = assetId,
        sha256 = digest,
        status = "APPROVED",
        storage_state = "stored",
        kind = "PRIMARY_REFERENCE",
        character_ids = listOf("character:alexander"),
    )

    private fun wire(
        bytes: ByteArray = png,
        id: String = assetId,
        sha: String = digest,
        mime: String = "image/png",
        schema: String = "jarvis.visual.asset.content.v1",
    ): String = """
        {"schema":"$schema","asset_id":"$id","sha256":"$sha",
         "mime_type":"$mime","image_base64":"${Base64.getEncoder().encodeToString(bytes)}"}
    """.trimIndent().replace("\n", "")

    private fun parsed(payload: String) =
        kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            .decodeFromString(WritingVisualAssetContent.serializer(), payload)

    @Before fun setup() {
        server = MockWebServer()
        server.start()
        val store = JarvisAppSession.MemoryStore()
        store.put(mapOf(
            JarvisAppSession.KEY_TOKEN to "test-token",
            JarvisAppSession.KEY_BASE to server.url("/").toString().trimEnd('/'),
            JarvisAppSession.KEY_USER_ID to "owner-a",
            JarvisAppSession.KEY_USERNAME to "author",
            JarvisAppSession.KEY_ROLE to "owner",
            JarvisAppSession.KEY_EXPIRES to "2099-01-01T00:00:00Z",
        ))
        session = JarvisAppSession(store)
    }

    @After fun cleanup() { server.shutdown() }

    @Test fun approvedVisualUsesExistingAuthenticatedFetchAndVerifiesBytes() =
        runBlocking(Dispatchers.IO) {
            server.enqueue(MockResponse().setBody(wire()))
            val content = session.writingRoomVisualAssetFetch(
                "prj_story", assetId,
            ).getOrThrow()
            assertTrue(isVerifiedApprovedVisualContent(approved, content))
            val request = server.takeRequest()
            assertEquals("/api/app/writing-room/visual-assets/fetch", request.path)
            assertEquals("Bearer test-token", request.getHeader("Authorization"))
            val requestJson = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
            assertEquals("prj_story", requestJson["project_id"]!!.jsonPrimitive.content)
            assertEquals(assetId, requestJson["asset_id"]!!.jsonPrimitive.content)
            assertEquals(1, server.requestCount)
        }

    @Test fun corruptedBytesWrongDigestAndWrongAssetAreRejected() {
        val corrupted = png.copyOf().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() }
        assertFalse(isVerifiedApprovedVisualContent(approved, parsed(wire(bytes = corrupted))))
        assertFalse(isVerifiedApprovedVisualContent(approved, parsed(wire(sha = "b".repeat(64)))))
        assertFalse(isVerifiedApprovedVisualContent(approved, parsed(wire(id = "va_ffffffffffffffffffffffff"))))
    }

    @Test fun candidateMissingStoreWrongMimeAndWrongSchemaAreRejected() {
        assertFalse(isVerifiedApprovedVisualContent(
            approved.copy(status = "CANDIDATE"), parsed(wire()),
        ))
        assertFalse(isVerifiedApprovedVisualContent(
            approved.copy(storage_state = "pending_upload"), parsed(wire()),
        ))
        assertFalse(isVerifiedApprovedVisualContent(approved, parsed(wire(mime = "image/jpeg"))))
        assertFalse(isVerifiedApprovedVisualContent(approved, parsed(wire(mime = "text/html"))))
        assertFalse(isVerifiedApprovedVisualContent(approved, parsed(wire(schema = "unknown"))))
    }

    @Test fun malformedBase64AndEmptyBytesAreRejected() {
        val malformed = parsed(wire()).copy(image_base64 = "!!notbase64!!")
        assertFalse(isVerifiedApprovedVisualContent(approved, malformed))
        assertFalse(isVerifiedApprovedVisualContent(
            approved, parsed(wire(bytes = byteArrayOf())),
        ))
        assertFalse(isVerifiedApprovedVisualContent(
            approved, parsed(wire(bytes = byteArrayOf(1, 2, 3))),
        ))
    }

    @Test fun unapprovedServerResponseDoesNotFallbackOrRetryOnAnotherEndpoint() =
        runBlocking(Dispatchers.IO) {
            server.enqueue(MockResponse().setResponseCode(403)
                .setBody("""{"error":"owner_required"}"""))
            val result = session.writingRoomVisualAssetFetch("prj_story", assetId)
            assertTrue(result.isFailure)
            assertEquals("/api/app/writing-room/visual-assets/fetch", server.takeRequest().path)
            assertEquals(1, server.requestCount)
        }
}
