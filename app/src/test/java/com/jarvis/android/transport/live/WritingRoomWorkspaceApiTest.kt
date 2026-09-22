package com.jarvis.android.transport.live

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WritingRoomWorkspaceApiTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.getHeader("Authorization") != "Bearer test-token") {
                    return MockResponse().setResponseCode(401)
                }
                return when (request.path) {
                    "/api/app/writing-room/overview" -> MockResponse().setBody(
                        """
                        {
                          "schema":"jarvis.writing-room.overview.v1",
                          "project":{
                            "project_id":"prj_story",
                            "story_id":"STORY-001",
                            "title":"Alexander History",
                            "authority":"human_only"
                          },
                          "latest_official_chapter":{
                            "chapter_number":37,
                            "title":"Chapter 37"
                          },
                          "sources":{
                            "total":23,
                            "by_status":{"OFFICIAL_CANON":20},
                            "by_category":{"05-chapters":20}
                          },
                          "workflow":{
                            "planning_items":2,
                            "chapter_sessions":1
                          },
                          "sections":[]
                        }
                        """.trimIndent(),
                    )
                    "/api/app/writing-room/plan/list" -> MockResponse().setBody(
                        """
                        {
                          "schema":"jarvis.writing-room.plan.list.v1",
                          "project_id":"prj_story",
                          "items":[
                            {
                              "item_id":"plan_1",
                              "title":"Future reveal",
                              "body":"A future reveal",
                              "status":"PROPOSED",
                              "created_utc":"2026-09-22T00:00:00Z",
                              "updated_utc":"2026-09-22T00:00:00Z"
                            }
                          ]
                        }
                        """.trimIndent(),
                    )
                    "/api/app/writing-room/library/list" -> MockResponse().setBody(
                        """
                        {
                          "schema":"jarvis.writing-room.library.v1",
                          "project_id":"prj_story",
                          "items":[
                            {
                              "document_id":"doc37",
                              "title":"Chapter 37",
                              "path":"story/ch37.md",
                              "canon_status":"OFFICIAL_CANON",
                              "chapter_min":37,
                              "chapter_max":37
                            }
                          ],
                          "exports":{
                            "pdf":"planned",
                            "docx":"planned",
                            "epub":"planned",
                            "markdown":"source_available"
                          }
                        }
                        """.trimIndent(),
                    )
                    "/api/app/writing-room/library/export" -> MockResponse().setBody(
                        """
                        {
                          "schema":"jarvis.writing-room.library.export.v1",
                          "project_id":"prj_story",
                          "document_id":"doc37",
                          "authority":"derived_from_official_canon",
                          "vps_persistence":"none",
                          "format":"pdf",
                          "filename":"Chapter 37.pdf",
                          "mime_type":"application/pdf",
                          "size_bytes":9,
                          "sha256":"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                          "data_base64":"JVBERi0xLjQK"
                        }
                        """.trimIndent(),
                    )
                    "/api/app/writing-room/chapter/start" -> MockResponse().setBody(
                        """
                        {
                          "schema":"jarvis.writing-room.chapter.v1",
                          "project_id":"prj_story",
                          "chapter":{
                            "chapter_id":"chapter_1",
                            "project_id":"prj_story",
                            "title":"Chapter 38",
                            "objective":"Move the contingency forward.",
                            "story_point":"After chapter 37",
                            "status":"BRIEF_READY",
                            "context_pack_id":"",
                            "showrunner_brief":"",
                            "draft_text":"",
                            "reviewer_text":"",
                            "canon_review_text":"",
                            "characters":["Alexander","Melody"],
                            "author_intent":{
                              "objective":"Move the contingency forward.",
                              "must_have":"Disagreement",
                              "must_avoid":"Future spoilers",
                              "tone":"tense",
                              "desired_end":"A decision"
                            },
                            "created_utc":"2026-09-22T00:00:00Z",
                            "updated_utc":"2026-09-22T00:00:00Z"
                          }
                        }
                        """.trimIndent(),
                    )
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun session(): JarvisAppSession {
        val store = JarvisAppSession.MemoryStore()
        store.put(
            mapOf(
                JarvisAppSession.KEY_TOKEN to "test-token",
                JarvisAppSession.KEY_BASE to server.url("/").toString().trimEnd('/'),
                JarvisAppSession.KEY_USER_ID to "u1",
                JarvisAppSession.KEY_USERNAME to "owner",
                JarvisAppSession.KEY_ROLE to "owner",
                JarvisAppSession.KEY_EXPIRES to "2099-01-01T00:00:00Z",
            ),
        )
        return JarvisAppSession(store)
    }

    @Test
    fun overviewPlanLibraryAndChapterUseAuthenticatedWorkspaceRoutes() = runBlocking(Dispatchers.IO) {
        val session = session()

        val overview = session.writingRoomOverview("prj_story").getOrThrow()
        assertEquals("STORY-001", overview.project.story_id)
        assertEquals(37, overview.latest_official_chapter?.chapter_number)
        assertEquals(23, overview.sources.total)

        val plan = session.writingRoomPlanList("prj_story").getOrThrow()
        assertEquals("PROPOSED", plan.items.single().status)

        val library = session.writingRoomLibraryList("prj_story").getOrThrow()
        assertEquals("planned", library.exports["pdf"])
        assertEquals("OFFICIAL_CANON", library.items.single().canon_status)

        val export = session.writingRoomLibraryExport(
            projectId = "prj_story",
            documentId = "doc37",
            format = "pdf",
        ).getOrThrow()
        assertEquals("pdf", export.format)
        assertEquals("application/pdf", export.mime_type)
        assertEquals("none", export.vps_persistence)

        val chapter = session.writingRoomChapterStart(
            projectId = "prj_story",
            title = "Chapter 38",
            objective = "Move the contingency forward.",
            storyPoint = "After chapter 37",
            characters = listOf("Alexander", "Melody"),
            mustHave = "Disagreement",
            mustAvoid = "Future spoilers",
            tone = "tense",
            desiredEnd = "A decision",
        ).getOrThrow()
        assertEquals("BRIEF_READY", chapter.chapter.status)
        assertEquals(listOf("Alexander", "Melody"), chapter.chapter.characters)

        assertEquals(5, server.requestCount)
        repeat(5) {
            val recorded = server.takeRequest()
            assertEquals("Bearer test-token", recorded.getHeader("Authorization"))
        }
        assertTrue(session.isAuthenticated)
    }
}
