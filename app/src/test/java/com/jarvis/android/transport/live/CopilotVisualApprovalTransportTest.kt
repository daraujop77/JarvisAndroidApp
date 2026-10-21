package com.jarvis.android.transport.live

import com.jarvis.android.ui.writing.isMatchingCopilotPortraitApprovalReceipt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Contract: owner approval is one authenticated exact-asset POST, with a
 * project-bound structured receipt and no image-generation fallback.
 * MockWebServer supplies bytes; no production writes or paid model calls.
 */
class CopilotVisualApprovalTransportTest {
    private lateinit var server: MockWebServer
    private lateinit var session: JarvisAppSession

    private val project = "prj_copilot_approval_fixture"
    private val character = "character:alexander"
    private val id = "va_0123456789abcdef"
    private val sha = "a".repeat(64)

    private fun receipt(projectId: String = project, assetSha: String = sha): String = """
        {
          "schema": "jarvis.visual-studio.character-primary-approval.v1",
          "project_id": "$projectId",
          "character_id": "$character",
          "asset": {
            "asset_id": "$id",
            "sha256": "$assetSha",
            "kind": "PRIMARY_REFERENCE",
            "perspective": "front",
            "status": "APPROVED",
            "character_ids": ["$character"],
            "storage": {"backend":"drive","state":"stored"}
          },
          "wiki_link": {"entry_id":"$character","event_id":"evt_approved_fixture"}
        }
    """.trimIndent()

    @Before fun setup() {
        server = MockWebServer()
        server.start()
        val store = JarvisAppSession.MemoryStore()
        store.put(mapOf(
            JarvisAppSession.KEY_TOKEN to "owner-fixture-token",
            JarvisAppSession.KEY_BASE to server.url("/").toString().trimEnd('/'),
            JarvisAppSession.KEY_USER_ID to "owner-fixture",
            JarvisAppSession.KEY_USERNAME to "writer",
            JarvisAppSession.KEY_ROLE to "owner",
            JarvisAppSession.KEY_EXPIRES to "2099-01-01T00:00:00Z",
        ))
        session = JarvisAppSession(store)
    }

    @After fun cleanup() { server.shutdown() }

    @Test fun exactOwnerApprovalPostParsesProjectAndWikiReceipt() =
        runBlocking(Dispatchers.IO) {
            server.enqueue(MockResponse().setBody(receipt()))
            val response = session.writingRoomCopilotApprovePortrait(
                project, character, id, sha,
            ).getOrThrow()
            assertTrue(isMatchingCopilotPortraitApprovalReceipt(
                project, character, id, sha, response,
            ))
            assertEquals(project, response.project_id)
            val posted = server.takeRequest()
            assertEquals("POST", posted.method)
            assertEquals("/api/app/writing-room/v2/visual/characters/approve-primary-exact",
                posted.path)
            assertEquals("Bearer owner-fixture-token",
                posted.getHeader("Authorization"))
            val body = Json.parseToJsonElement(posted.body.readUtf8()).jsonObject
            assertEquals(project, body["project_id"]!!.jsonPrimitive.content)
            assertEquals(character, body["character_id"]!!.jsonPrimitive.content)
            assertEquals(id, body["asset_id"]!!.jsonPrimitive.content)
            assertEquals(sha, body["asset_sha256"]!!.jsonPrimitive.content)
            assertEquals(1, server.requestCount)
        }

    @Test fun crossProjectAndShaConflictsNeverBecomeVerifiedApprovals() =
        runBlocking(Dispatchers.IO) {
            server.enqueue(MockResponse().setBody(receipt(projectId = "prj_other")))
            val crossProject = session.writingRoomCopilotApprovePortrait(
                project, character, id, sha,
            ).getOrThrow()
            assertFalse(isMatchingCopilotPortraitApprovalReceipt(
                project, character, id, sha, crossProject,
            ))
            server.enqueue(MockResponse().setBody(receipt(assetSha = "b".repeat(64))))
            val badSha = session.writingRoomCopilotApprovePortrait(
                project, character, id, sha,
            ).getOrThrow()
            assertFalse(isMatchingCopilotPortraitApprovalReceipt(
                project, character, id, sha, badSha,
            ))
            assertEquals(2, server.requestCount)
        }

    @Test fun invalidCandidatesAreRejectedBeforeAnyPost() =
        runBlocking(Dispatchers.IO) {
            assertTrue(session.writingRoomCopilotApprovePortrait(
                "", character, id, sha,
            ).isFailure)
            assertTrue(session.writingRoomCopilotApprovePortrait(
                project, "another", id, sha,
            ).isFailure)
            assertTrue(session.writingRoomCopilotApprovePortrait(
                project, character, "arbitrary", sha,
            ).isFailure)
            assertTrue(session.writingRoomCopilotApprovePortrait(
                project, character, id, "invalid",
            ).isFailure)
            assertEquals(0, server.requestCount)
        }

    @Test fun ownerDenialDoesNotFallbackToModelOrImageGeneration() =
        runBlocking(Dispatchers.IO) {
            server.enqueue(MockResponse().setResponseCode(403)
                .setBody("""{"error":"owner_required"}"""))
            val response = session.writingRoomCopilotApprovePortrait(
                project, character, id, sha,
            )
            assertTrue(response.isFailure)
            assertEquals("/api/app/writing-room/v2/visual/characters/approve-primary-exact",
                server.takeRequest().path)
            assertEquals(1, server.requestCount)
        }
}
