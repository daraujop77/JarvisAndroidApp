package com.jarvis.android.transport.live

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
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
                    "/api/app/writing-room/wiki/home" -> MockResponse().setBody(
                        """
                        {
                          "schema":"jarvis.writing-room.wiki.home.v1",
                          "project_id":"prj_story",
                          "authority":"human_only",
                          "latest_official_chapter":{"chapter_number":37,"title":"Chapter 37"},
                          "authority_counts":{"OFFICIAL_CANON":6,"REFERENCE":7,"APPROVED_PLAN":5,"PROPOSED":2},
                          "categories":[
                            {"id":"characters","label":"Personajes","subtitle":"Profiles","query":"characters","source_count":1}
                          ],
                          "featured":[
                            {
                              "document_id":"canon1",
                              "title":"CANON & CONTINUITY.md",
                              "category":"01-canon",
                              "canon_status":"OFFICIAL_CANON",
                              "authority":"canon"
                            }
                          ],
                          "legend":[
                            {"status":"OFFICIAL_CANON","label":"Canon oficial","meaning":"Established fact."}
                          ]
                        }
                        """.trimIndent(),
                    )
                    "/api/app/writing-room/chat/stream" -> MockResponse()
                        .setHeader("Content-Type", "text/event-stream")
                        .setBody(
                            """
                            event: ready
                            data: {"request_id":"r1"}

                            event: delta
                            data: {"delta":"Hello "}

                            event: delta
                            data: {"delta":"world"}

                            event: complete
                            data: {"schema":"jarvis.writing-room.chat.v1","classification":{"task_class":"NORMAL","depth":"normal","source":"default_general_chat"},"selected_participant":"moderator","turn":{"schema":"jarvis.writing-room.turn.v1","project_id":"prj_story","project_title":"Alexander History","participant":{"id":"moderator","label":"Moderator"},"routing":{"requested_mode":"normal","resolved_profile":"normal","destination":"hermes_cloud","provider":"nous","model":"deepseek/deepseek-v4-flash"},"canon":{"connected":true,"authority":"human_only","story_id":"STORY-001","documents":23,"chunks":276,"sources":[]},"response":{"text":"Hello world"},"metrics":{"total_ms":2000}}}

                            """.trimIndent(),
                        )
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
                    "/api/app/writing-room/chapter/review" -> MockResponse().setBody(
                        """
                        {
                          "schema":"jarvis.writing-room.chapter.review.v1",
                          "engine_review":{
                            "schema":"jarvis.writing-room.engine-review.v1",
                            "authority":"human_only",
                            "canon_mutation":"forbidden",
                            "context_pack_id":"wcp_test_pack",
                            "frozen_context_chars":18240,
                            "result":{
                              "schema":"jarvis.writing.review.result.v1",
                              "project_id":"prj_story",
                              "chapter_id":"chapter_1",
                              "context_pack_id":"wcp_test_pack",
                              "authority":"human_only",
                              "read_only":true,
                              "canon_mutation":"forbidden",
                              "auto_rewrite":false,
                              "auto_approve":false,
                              "status":"needs_attention",
                              "check_order":["grammar","prose","style","canon","timeline","character_knowledge","power_cost","open_threads","reviewer"],
                              "check_status":{
                                "grammar":"ok",
                                "prose":"ok",
                                "style":"ok",
                                "canon":"ok",
                                "timeline":"ok",
                                "character_knowledge":"ok",
                                "power_cost":"ok",
                                "open_threads":"unavailable",
                                "reviewer":"unavailable"
                              },
                              "missing_required_checks":[],
                              "failed_required_checks":[],
                              "severity_counts":{"blocking":1,"warning":1,"info":0},
                              "finding_count":2,
                              "findings":[
                                {
                                  "schema":"jarvis.writing.review.finding.v1",
                                  "finding_id":"review_1",
                                  "check":"canon",
                                  "category":"continuity",
                                  "severity":"blocking",
                                  "message":"This line conflicts with Chapter 37.",
                                  "suggestions":["Keep Bee's current status unchanged."],
                                  "evidence":[
                                    {
                                      "source_ref":"canon37:bee",
                                      "excerpt":"Bee remains conscious in Suna."
                                    }
                                  ],
                                  "evidence_required":true,
                                  "evidence_missing":false,
                                  "evidence_source_binding_enforced":true,
                                  "evidence_bound":true,
                                  "evidence_unbound":false,
                                  "authority":"ADVISORY",
                                  "canon_mutation":"forbidden",
                                  "auto_apply":false,
                                  "resolved":false
                                },
                                {
                                  "schema":"jarvis.writing.review.finding.v1",
                                  "finding_id":"review_2",
                                  "check":"power_cost",
                                  "category":"power_cost",
                                  "severity":"warning",
                                  "message":"Confirm the cost of the damaged arm before escalating power.",
                                  "suggestions":[],
                                  "evidence":[],
                                  "evidence_required":true,
                                  "evidence_missing":true,
                                  "evidence_source_binding_enforced":true,
                                  "evidence_bound":false,
                                  "evidence_unbound":false,
                                  "authority":"ADVISORY",
                                  "canon_mutation":"forbidden",
                                  "auto_apply":false,
                                  "resolved":false
                                }
                              ],
                              "human_decision_required":true
                            }
                          },
                          "chapter":{
                            "chapter_id":"chapter_1",
                            "project_id":"prj_story",
                            "title":"Chapter 38",
                            "objective":"Move the contingency forward.",
                            "story_point":"After chapter 37",
                            "status":"REVIEW",
                            "context_pack_id":"wcp_test_pack",
                            "draft_text":"Draft text",
                            "reviewer_text":"Reviewer notes",
                            "canon_review_text":"Structured review summary",
                            "characters":["Alexander","Melody"]
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
    fun structuredWikiAcceptsUndefinedImagesWithNullAssetFields() {
        val decoded = Json { ignoreUnknownKeys = true }.decodeFromString<WritingStructuredWikiHome>(
            """
            {
              "schema":"jarvis.structured-wiki.v1",
              "story_id":"STORY-001",
              "entry_count":2,
              "featured":[
                {
                  "id":"character:alexander",
                  "type":"character",
                  "name":"Alexander",
                  "image":{
                    "status":"APPROVED",
                    "asset_id":"va_alexander_2",
                    "sha256":"0123456789abcdef",
                    "revision":2,
                    "alt":"Alexander",
                    "locked_visual":true,
                    "source":"VISUAL_ASSET_REGISTRY"
                  }
                },
                {
                  "id":"character:william",
                  "type":"character",
                  "name":"William",
                  "image":{
                    "status":"UNDEFINED",
                    "asset_id":null,
                    "sha256":null,
                    "revision":null,
                    "alt":null,
                    "locked_visual":false,
                    "source":null
                  }
                }
              ]
            }
            """.trimIndent(),
        )
        assertEquals("va_alexander_2", decoded.featured[0].image?.asset_id)
        assertEquals(null, decoded.featured[1].image?.asset_id)
        assertEquals(null, decoded.featured[1].image?.alt)
        assertEquals(null, decoded.featured[1].image?.revision)
    }

    @Test
    fun wikiHomeSeparatesAuthorityAndProvidesTopics() = runBlocking(Dispatchers.IO) {
        val home = session().writingRoomWikiHome("prj_story").getOrThrow()
        assertEquals(37, home.latest_official_chapter?.chapter_number)
        assertEquals(6, home.authority_counts["OFFICIAL_CANON"])
        assertEquals("characters", home.categories.single().id)
        assertEquals("OFFICIAL_CANON", home.featured.single().canon_status)
    }

    @Test
    fun autoChatStreamEmitsDeltasBeforeCompletion() = runBlocking(Dispatchers.IO) {
        val deltas = mutableListOf<String>()
        val chat = session().writingRoomAutoChatStream(
            projectId = "prj_story",
            projectTitle = "Alexander History",
            prompt = "Talk about the story",
            onDelta = { deltas += it },
        ).getOrThrow()

        assertEquals(listOf("Hello ", "world"), deltas)
        assertEquals("Hello world", chat.turn.response.text)
        assertEquals("moderator", chat.selected_participant)
        assertEquals("nous", chat.turn.routing.provider)
    }

    @Test
    fun structuredChapterReviewDecodesEngineFindingsAndBoundEvidence() = runBlocking(Dispatchers.IO) {
        val review = session().writingRoomChapterReview(
            projectId = "prj_story",
            chapterId = "chapter_1",
        ).getOrThrow()

        assertEquals("REVIEW", review.chapter.status)
        val engine = requireNotNull(review.engine_review)
        assertEquals("wcp_test_pack", engine.context_pack_id)
        assertEquals("needs_attention", engine.result.status)
        assertEquals(1, engine.result.severity_counts["blocking"])
        assertEquals("ok", engine.result.check_status["canon"])
        assertTrue(engine.result.human_decision_required)
        val canonFinding = engine.result.findings.first { it.check == "canon" }
        assertEquals("blocking", canonFinding.severity)
        assertEquals(true, canonFinding.evidence_bound)
        assertEquals("canon37:bee", canonFinding.evidence.single().source_ref)
        assertTrue(!canonFinding.auto_apply)
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
