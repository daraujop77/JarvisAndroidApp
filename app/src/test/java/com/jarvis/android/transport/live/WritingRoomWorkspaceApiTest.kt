package com.jarvis.android.transport.live

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
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
                    "/api/app/images/generations" -> MockResponse().setBody(
                        """{"schema":"jarvis.image.generation.v2","model":"gpt-image-2-medium","provider":"openai-codex","fallback_used":false,"mime_type":"image/jpeg","data_base64":"aW1hZ2U=","visual_asset":{"asset_id":"va_portrait_1","sha256":"BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB","kind":"PRIMARY_REFERENCE","status":"CANDIDATE","storage":{"state":"stored"}}}""",
                    )
                    "/api/app/writing-room/v2/visual/characters/approve-primary-exact" -> MockResponse().setBody(
                        """{"schema":"jarvis.visual-studio.character-primary-approval.v1","character_id":"character:naruto","wiki_link":{"entry_id":"character:naruto"},"asset":{"asset_id":"va_portrait_1","sha256":"BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB","kind":"PRIMARY_REFERENCE","status":"APPROVED","storage":{"state":"stored"}}}""",
                    )
                    "/api/app/writing-room/visual-assets/list" -> MockResponse().setBody(
                        """{"schema":"jarvis.visual.assets.v1","project_id":"prj_story","assets":[{"asset_id":"va_1","sha256":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA","status":"CANDIDATE","kind":"PRIMARY_REFERENCE","perspective":"front","character_ids":["character:naruto"],"alt":"Naruto canonical portrait","storage":{"state":"stored"},"provenance":{"requested_from":"copilot","operation":"generation","model":"gpt-image-2-medium","fallback_used":false}}]}""",
                    )
                    "/api/app/writing-room/visual-assets/ingest" -> MockResponse().setBody(
                        """{"schema":"jarvis.visual.asset.v1","asset":{"asset_id":"va_copilot_1","sha256":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA","kind":"REFERENCE","status":"CANDIDATE"}}""",
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
                    "/api/app/writing-room/plan/council/start" -> MockResponse().setBody(
                        """
                        {
                          "schema":"jarvis.writing-room.plan.council.v1",
                          "project_id":"prj_story",
                          "needs_story_architect":true,
                          "session":{
                            "session_id":"council_1",
                            "project_id":"prj_story",
                            "title":"Future council",
                            "seed_prompt":"Explore a future reveal",
                            "status":"ACTIVE",
                            "created_utc":"2026-09-26T00:00:00Z",
                            "updated_utc":"2026-09-26T00:00:00Z"
                          },
                          "messages":[{
                            "message_id":"msg_1",
                            "role":"user",
                            "role_label":"Tú",
                            "body":"Explore a future reveal",
                            "round_index":0,
                            "saved_plan_item_id":"",
                            "created_utc":"2026-09-26T00:00:00Z"
                          }]
                        }
                        """.trimIndent(),
                    )
                    "/api/app/writing-room/plan/council/turn" -> MockResponse().setBody(
                        """
                        {
                          "schema":"jarvis.writing-room.plan.council.turn.v1",
                          "project_id":"prj_story",
                          "participant":"challenger",
                          "participant_label":"Challenger",
                          "phase":"discussion",
                          "session":{
                            "session_id":"council_1",
                            "project_id":"prj_story",
                            "title":"Future council",
                            "seed_prompt":"Explore a future reveal",
                            "status":"ACTIVE",
                            "created_utc":"2026-09-26T00:00:00Z",
                            "updated_utc":"2026-09-26T00:00:01Z"
                          },
                          "messages":[
                            {
                              "message_id":"msg_1",
                              "role":"user",
                              "role_label":"Tú",
                              "body":"Explore a future reveal",
                              "round_index":0,
                              "saved_plan_item_id":"",
                              "created_utc":"2026-09-26T00:00:00Z"
                            },
                            {
                              "message_id":"msg_2",
                              "role":"challenger",
                              "role_label":"Challenger",
                              "body":"Raise the cost and preserve the reveal constraint.",
                              "round_index":1,
                              "saved_plan_item_id":"",
                              "created_utc":"2026-09-26T00:00:01Z"
                            }
                          ],
                          "turn":{
                            "schema":"jarvis.writing-room.turn.v1",
                            "project_id":"prj_story",
                            "project_title":"Alexander History",
                            "participant":{"id":"challenger","label":"Challenger"},
                            "routing":{
                              "requested_mode":"deep",
                              "resolved_profile":"deep",
                              "destination":"hermes_cloud",
                              "provider":"xai-oauth",
                              "model":"grok-4.7",
                              "worker_id":null
                            },
                            "canon":{"connected":true,"authority":"human_only","story_id":"STORY-001","documents":23,"chunks":276,"sources":[]},
                            "response":{"text":"Raise the cost and preserve the reveal constraint."},
                            "metrics":{"total_ms":1200}
                          }
                        }
                        """.trimIndent(),
                    )

                    "/api/app/writing-room/v2/planning/session/start" -> MockResponse().setBody(
                        """
                        {
                          "schema":"jarvis.writing-room.planning-session-start.response.v1",
                          "operation":"session_start",
                          "project_id":"prj_story",
                          "chapter_id":"chapter_w1",
                          "session_id":"chapter_w1",
                          "aggregate_version":1,
                          "stage":"PLANNING",
                          "result":{
                            "schema":"jarvis.writing-room.planning-session.v1",
                            "project_id":"prj_story",
                            "chapter_id":"chapter_w1",
                            "session_id":"chapter_w1",
                            "title":"Nuevo capítulo",
                            "stage":"PLANNING",
                            "aggregate_version":1
                          }
                        }
                        """.trimIndent(),
                    )
                    "/api/app/writing-room/v2/planning/turn" -> MockResponse().setBody(
                        """
                        {
                          "schema":"jarvis.writing-room.planning-turn.response.v1",
                          "operation":"turn",
                          "project_id":"prj_story",
                          "chapter_id":"chapter_w1",
                          "session_id":"chapter_w1",
                          "aggregate_version":2,
                          "result":{
                            "turn_id":"turn_1",
                            "turn_number":1,
                            "planning_revision_id":"planrev_1",
                            "expected_aggregate_version":1,
                            "aggregate_version":2,
                            "user_message":"Quiero una señal falsa.",
                            "assistant_message":"Podemos mantener el origen incierto.",
                            "status":"SUCCEEDED",
                            "job_status":"SUCCEEDED",
                            "progress_sequence":3,
                            "error_code":"",
                            "created_utc":"2026-10-01T12:00:00Z",
                            "completed_utc":"2026-10-01T12:00:01Z"
                          }
                        }
                        """.trimIndent(),
                    )
                    "/api/app/writing-room/v2/planning/history" -> MockResponse().setBody(
                        """
                        {
                          "schema":"jarvis.writing-room.planning-history.response.v1",
                          "operation":"history",
                          "project_id":"prj_story",
                          "chapter_id":"chapter_w1",
                          "session_id":"chapter_w1",
                          "aggregate_version":2,
                          "stage":"PLANNING",
                          "result":{
                            "schema":"jarvis.writing-room.planning-history.v1",
                            "project_id":"prj_story",
                            "chapter_id":"chapter_w1",
                            "stage":"PLANNING",
                            "aggregate_version":2,
                            "items":[{
                              "turn_id":"turn_1",
                              "turn_number":1,
                              "planning_revision_id":"planrev_1",
                              "expected_aggregate_version":1,
                              "aggregate_version":2,
                              "user_message":"Quiero una señal falsa.",
                              "assistant_message":"Podemos mantener el origen incierto.",
                              "status":"SUCCEEDED",
                              "job_status":"SUCCEEDED",
                              "progress_sequence":3,
                              "error_code":""
                            }]
                          }
                        }
                        """.trimIndent(),
                    )
                    "/api/app/writing-room/v2/planning/direction/status" -> MockResponse().setBody(
                        """
                        {
                          "schema":"jarvis.writing-room.direction-status.response.v1",
                          "operation":"direction_status",
                          "project_id":"prj_story",
                          "chapter_id":"chapter_w1",
                          "session_id":"chapter_w1",
                          "aggregate_version":2,
                          "result":{
                            "schema":"jarvis.writing-room.direction-state.v1",
                            "project_id":"prj_story",
                            "chapter_id":"chapter_w1",
                            "chapter_stage":"PLANNING",
                            "aggregate_version":2,
                            "reviews":[],
                            "bound_review_ids":[],
                            "approval_ready":false
                          }
                        }
                        """.trimIndent(),
                    )
                    "/api/app/writing-room/v2/planning/direction/prepare" -> MockResponse().setBody(
                        """
                        {
                          "schema":"jarvis.writing-room.direction-prepare.response.v1",
                          "operation":"direction_prepare",
                          "project_id":"prj_story",
                          "chapter_id":"chapter_w1",
                          "session_id":"chapter_w1",
                          "aggregate_version":5,
                          "result":{
                            "schema":"jarvis.writing-room.direction-state.v1",
                            "project_id":"prj_story",
                            "chapter_id":"chapter_w1",
                            "chapter_stage":"DIRECTION_READY",
                            "aggregate_version":5,
                            "proposal":{
                              "proposal_revision_id":"proposal_1",
                              "revision_number":1,
                              "proposal_hash":"abc123",
                              "context_pack_id":"wcp_1",
                              "context_hash":"ctx123",
                              "source_revision":"context:hash",
                              "payload":{
                                "title_options":["La señal falsa","El eco incierto"],
                                "recommended_title":"La señal falsa",
                                "direction":"Investigar sin resolver el origen.",
                                "beats":["Detectar","Debatir","Retirarse"],
                                "ending":"La amenaza queda abierta.",
                                "unresolved_choices":["Origen de la señal"],
                                "canon_constraints":["No resolver todavía"],
                                "risks":["No convertir sospechas en hechos"],
                                "authority":"PROPOSED"
                              }
                            },
                            "reviews":[
                              {
                                "review_id":"review_keeper",
                                "review_kind":"canon_keeper",
                                "validation_status":"PASS",
                                "payload":{"status":"PASS","findings":[]}
                              },
                              {
                                "review_id":"review_challenger",
                                "review_kind":"challenger",
                                "validation_status":"PASS",
                                "payload":{"status":"PASS","findings":[]}
                              }
                            ],
                            "bound_review_ids":["review_challenger","review_keeper"],
                            "approval_ready":true
                          }
                        }
                        """.trimIndent(),
                    )
                    "/api/app/writing-room/v2/planning/direction/approve" -> MockResponse().setBody(
                        """
                        {
                          "schema":"jarvis.writing-room.direction-approve.response.v1",
                          "operation":"direction_approve",
                          "project_id":"prj_story",
                          "chapter_id":"chapter_w1",
                          "session_id":"chapter_w1",
                          "aggregate_version":6,
                          "result":{
                            "schema":"jarvis.writing-room.direction-state.v1",
                            "project_id":"prj_story",
                            "chapter_id":"chapter_w1",
                            "chapter_stage":"BRIEF_APPROVED",
                            "aggregate_version":6,
                            "proposal":{
                              "proposal_revision_id":"proposal_1",
                              "proposal_hash":"abc123",
                              "payload":{
                                "title_options":["La señal falsa","El eco incierto"],
                                "recommended_title":"La señal falsa",
                                "direction":"Investigar sin resolver el origen.",
                                "beats":["Detectar","Debatir","Retirarse"],
                                "ending":"La amenaza queda abierta.",
                                "authority":"PROPOSED"
                              }
                            },
                            "reviews":[
                              {"review_id":"review_keeper","review_kind":"canon_keeper","validation_status":"PASS","payload":{"status":"PASS","findings":[]}},
                              {"review_id":"review_challenger","review_kind":"challenger","validation_status":"PASS","payload":{"status":"PASS","findings":[]}}
                            ],
                            "bound_review_ids":[],
                            "brief":{
                              "brief_revision_id":"brief_1",
                              "revision_number":1,
                              "proposal_revision_id":"proposal_1",
                              "proposal_hash":"abc123",
                              "brief_hash":"briefhash",
                              "payload":{
                                "title":"La señal falsa",
                                "direction":"Investigar sin resolver el origen.",
                                "beats":["Detectar","Debatir","Retirarse"],
                                "ending":"La amenaza queda abierta.",
                                "authority":"HUMAN_APPROVED_DIRECTION"
                              }
                            },
                            "approval_ready":false,
                            "draft_execution":{
                              "state":"QUEUED_INTENT_ONLY",
                              "automatic_execution_enabled":false,
                              "reason":"W2_NOT_ENABLED"
                            }
                          }
                        }
                        """.trimIndent(),
                    )
                    "/api/app/writing-room/v2/draft/status",
                    "/api/app/writing-room/v2/draft/run" -> MockResponse().setBody(
                        """
                        {
                          "schema":"jarvis.writing-room.draft-run.response.v1",
                          "operation":"run",
                          "project_id":"prj_story",
                          "chapter_id":"chapter_w1",
                          "result":{
                            "schema":"jarvis.writing-room.draft-execution.v1",
                            "project_id":"prj_story",
                            "chapter_id":"chapter_w1",
                            "job":{"job_id":"job_draft","operation":"draft_chapter","status":"SUCCEEDED","steps":[{"step_id":"writer_full_chapter","sequence":3,"state":"SUCCEEDED"}]},
                            "direction":{"project_id":"prj_story","chapter_id":"chapter_w1","chapter_stage":"DRAFT_READY","aggregate_version":9,"brief":{"brief_revision_id":"brief_1","payload":{"title":"La señal falsa"}}},
                            "aggregate":{"version":9,"chapter_stage":"DRAFT_READY","publication_state":"NOT_REQUESTED","current_brief_id":"brief_1","current_draft_revision_id":"draft_1"},
                            "reviews":[
                              {"review_id":"review_writer","revision_id":"draft_1","review_kind":"reviewer","review_version":1,"validation_status":"PASS","payload":{"review_kind":"reviewer","validation_status":"PASS","summary":"Ritmo correcto.","findings":[]}},
                              {"review_id":"review_canon","revision_id":"draft_1","review_kind":"canon_keeper","review_version":1,"validation_status":"PASS","payload":{"review_kind":"canon_keeper","validation_status":"PASS","summary":"Continuidad válida.","findings":[]}}
                            ],
                            "chapter":{"chapter_id":"chapter_w1","project_id":"prj_story","title":"La señal falsa","status":"REVIEW","draft_text":"La señal puede funcionar si conserva la incertidumbre.","revision_count":1}
                          }
                        }
                        """.trimIndent(),
                    )
                    "/api/app/writing-room/v2/draft/revise",
                    "/api/app/writing-room/v2/draft/restore" -> MockResponse().setBody(
                        """
                        {
                          "schema":"jarvis.writing-room.draft-revise.response.v1",
                          "operation":"revise",
                          "project_id":"prj_story",
                          "chapter_id":"chapter_w1",
                          "result":{
                            "schema":"jarvis.writing-room.draft-execution.v1",
                            "project_id":"prj_story",
                            "chapter_id":"chapter_w1",
                            "job":{"job_id":"job_draft","operation":"draft_chapter","status":"SUCCEEDED"},
                            "direction":{"project_id":"prj_story","chapter_id":"chapter_w1","chapter_stage":"DRAFT_READY","aggregate_version":10,"brief":{"brief_revision_id":"brief_1","payload":{"title":"La señal falsa"}}},
                            "aggregate":{"version":10,"chapter_stage":"DRAFT_READY","publication_state":"NOT_REQUESTED","current_brief_id":"brief_1","current_draft_revision_id":"draft_2"},
                            "review_job":{"job_id":"job_review","operation":"review_draft","status":"QUEUED","target_revision_id":"draft_2"},
                            "reviews":[],
                            "chapter":{"chapter_id":"chapter_w1","project_id":"prj_story","title":"La señal falsa","status":"DRAFT","draft_text":"Texto editado.","revision_count":2}
                          }
                        }
                        """.trimIndent(),
                    )
                    "/api/app/writing-room/v2/draft/review/run" -> MockResponse().setBody(
                        """
                        {
                          "schema":"jarvis.writing-room.draft-review-run.response.v1",
                          "operation":"review_run",
                          "project_id":"prj_story",
                          "chapter_id":"chapter_w1",
                          "result":{
                            "schema":"jarvis.writing-room.draft-execution.v1",
                            "project_id":"prj_story",
                            "chapter_id":"chapter_w1",
                            "job":{"job_id":"job_draft","operation":"draft_chapter","status":"SUCCEEDED"},
                            "direction":{"project_id":"prj_story","chapter_id":"chapter_w1","chapter_stage":"DRAFT_READY","aggregate_version":12,"brief":{"brief_revision_id":"brief_1","payload":{"title":"La señal falsa"}}},
                            "aggregate":{"version":12,"chapter_stage":"DRAFT_READY","publication_state":"NOT_REQUESTED","current_brief_id":"brief_1","current_draft_revision_id":"draft_2"},
                            "review_job":{"job_id":"job_review","operation":"review_draft","status":"SUCCEEDED","steps":[{"step_id":"reviewer_review","sequence":1,"state":"SUCCEEDED"},{"step_id":"canon_keeper_review","sequence":2,"state":"SUCCEEDED"}]},
                            "reviews":[
                              {"review_id":"review_2a","revision_id":"draft_2","review_kind":"reviewer","review_version":1,"validation_status":"PASS","payload":{"review_kind":"reviewer","validation_status":"PASS","summary":"Revisión editorial completa.","findings":[]}},
                              {"review_id":"review_2b","revision_id":"draft_2","review_kind":"canon_keeper","review_version":1,"validation_status":"PASS","payload":{"review_kind":"canon_keeper","validation_status":"PASS","summary":"Canon revisado.","findings":[]}}
                            ],
                            "chapter":{"chapter_id":"chapter_w1","project_id":"prj_story","title":"La señal falsa","status":"REVIEW","draft_text":"Texto editado.","revision_count":2}
                          }
                        }
                        """.trimIndent(),
                    )
                    "/api/app/writing-room/v2/chapter/auto-review/status",
                    "/api/app/writing-room/v2/chapter/auto-review/run" -> MockResponse().setBody(
                        """
                        {
                          "schema":"jarvis.writing-room.chapter-auto-review-run.response.v1",
                          "operation":"run",
                          "project_id":"prj_story",
                          "chapter_id":"chapter_w1",
                          "async_execution":true,
                          "polling_required":false,
                          "result":{
                            "schema":"jarvis.writing-room.chapter-auto-review.v1",
                            "project_id":"prj_story",
                            "chapter_id":"chapter_w1",
                            "status":"READY_FOR_HUMAN_APPROVAL",
                            "job":{"job_id":"job_auto","operation":"auto_review_chapter","status":"SUCCEEDED","steps":[{"step_id":"canon_diff_persist","sequence":31,"state":"SUCCEEDED"}]},
                            "draft":{
                              "schema":"jarvis.writing-room.draft-execution.v1",
                              "project_id":"prj_story",
                              "chapter_id":"chapter_w1",
                              "job":{"job_id":"job_draft","operation":"draft_chapter","status":"SUCCEEDED"},
                              "aggregate":{"version":14,"chapter_stage":"READY_FOR_APPROVAL","current_brief_id":"brief_1","current_draft_revision_id":"draft_2","current_canon_diff_id":"diff_1"},
                              "reviews":[
                                {"review_id":"review_2a","revision_id":"draft_2","review_kind":"reviewer","review_version":1,"validation_status":"PASS","payload":{"review_kind":"reviewer","validation_status":"PASS","findings":[]}},
                                {"review_id":"review_2b","revision_id":"draft_2","review_kind":"canon_keeper","review_version":1,"validation_status":"PASS","payload":{"review_kind":"canon_keeper","validation_status":"PASS","findings":[]}}
                              ],
                              "chapter":{"chapter_id":"chapter_w1","project_id":"prj_story","title":"La señal falsa","status":"READY_FOR_HUMAN_APPROVAL","draft_text":"Texto corregido."}
                            },
                            "approval":{
                              "schema":"jarvis.writing-room.approval-state.v1",
                              "project_id":"prj_story",
                              "chapter_id":"chapter_w1",
                              "aggregate":{"version":14,"chapter_stage":"READY_FOR_APPROVAL","current_brief_id":"brief_1","current_draft_revision_id":"draft_2","current_canon_diff_id":"diff_1"},
                              "direction":{"project_id":"prj_story","chapter_id":"chapter_w1","chapter_stage":"READY_FOR_APPROVAL","brief":{"brief_revision_id":"brief_1","payload":{"title":"La señal falsa"}}},
                              "chapter":{"chapter_id":"chapter_w1","project_id":"prj_story","title":"La señal falsa","status":"READY_FOR_HUMAN_APPROVAL","draft_text":"Texto corregido."},
                              "reviews":[
                                {"review_id":"review_2a","revision_id":"draft_2","review_kind":"reviewer","review_version":1,"validation_status":"PASS","payload":{"review_kind":"reviewer","validation_status":"PASS","findings":[]}},
                                {"review_id":"review_2b","revision_id":"draft_2","review_kind":"canon_keeper","review_version":1,"validation_status":"PASS","payload":{"review_kind":"canon_keeper","validation_status":"PASS","findings":[]}}
                              ],
                              "canon_diff":{"canon_diff_id":"diff_1","revision_id":"draft_2","draft_sha256":"sha2","brief_revision_id":"brief_1","diff_hash":"diffhash","payload":{"revision_id":"draft_2","summary":"Sin conflictos.","changes":[],"blocking_issues":[],"warnings":[]}},
                              "ready_review_ids":["review_2a","review_2b"],
                              "approval_ready":true
                            },
                            "corrections_used":1,
                            "max_corrections":2,
                            "assessment":{
                              "schema":"jarvis.writing-room.auto-review-assessment.v1",
                              "both_required_reviews_pass":true,
                              "requires_correction":false,
                              "correction_allowed":false,
                              "corrections_used":1,
                              "max_corrections":2,
                              "pending_findings":[{"review_kind":"reviewer","severity":"warning","category":"pacing","message":"Pulir una transición."}],
                              "review_ids":["review_2a","review_2b"]
                            },
                            "canon_diff_blocking_issues":[],
                            "human_action_required":true,
                            "automatic_final_approval":false
                          }
                        }
                        """.trimIndent(),
                    )
                    "/api/app/writing-room/v2/approval/status",
                    "/api/app/writing-room/v2/approval/prepare" -> MockResponse().setBody(
                        """
                        {
                          "schema":"jarvis.writing-room.approval-prepare.response.v1",
                          "operation":"prepare",
                          "project_id":"prj_story",
                          "chapter_id":"chapter_w1",
                          "result":{
                            "schema":"jarvis.writing-room.approval-state.v1",
                            "project_id":"prj_story",
                            "chapter_id":"chapter_w1",
                            "aggregate":{"version":14,"chapter_stage":"READY_FOR_APPROVAL","publication_state":"NOT_REQUESTED","current_brief_id":"brief_1","current_draft_revision_id":"draft_2","current_canon_diff_id":"diff_1"},
                            "direction":{"project_id":"prj_story","chapter_id":"chapter_w1","chapter_stage":"READY_FOR_APPROVAL","aggregate_version":14,"brief":{"brief_revision_id":"brief_1","payload":{"title":"La señal falsa"}}},
                            "chapter":{"chapter_id":"chapter_w1","project_id":"prj_story","title":"La señal falsa","status":"READY_FOR_HUMAN_APPROVAL","draft_text":"Texto editado.","revision_count":2},
                            "reviews":[
                              {"review_id":"review_2a","revision_id":"draft_2","review_kind":"reviewer","review_version":1,"validation_status":"PASS","payload":{"review_kind":"reviewer","validation_status":"PASS","summary":"Revisión editorial completa.","findings":[]}},
                              {"review_id":"review_2b","revision_id":"draft_2","review_kind":"canon_keeper","review_version":1,"validation_status":"PASS","payload":{"review_kind":"canon_keeper","validation_status":"PASS","summary":"Canon revisado.","findings":[]}}
                            ],
                            "canon_diff":{
                              "canon_diff_id":"diff_1",
                              "revision_id":"draft_2",
                              "draft_sha256":"sha-draft-2",
                              "brief_revision_id":"brief_1",
                              "context_hash":"ctx123",
                              "source_revision":"context:hash",
                              "diff_hash":"diffhash",
                              "payload":{
                                "schema":"jarvis.writing-room.canon-diff.payload.v1",
                                "revision_id":"draft_2",
                                "summary":"Una señal nueva queda abierta.",
                                "changes":[{"kind":"event","subject":"La señal","statement":"El grupo decide investigarla.","draft_excerpt":"Texto editado.","source_refs":[]}],
                                "blocking_issues":[],
                                "warnings":[]
                              }
                            },
                            "ready_review_ids":["review_2a","review_2b"],
                            "approval_ready":true
                          }
                        }
                        """.trimIndent(),
                    )
                    "/api/app/writing-room/v2/approval/final" -> MockResponse().setBody(
                        """
                        {
                          "schema":"jarvis.writing-room.approval-final.response.v1",
                          "operation":"final",
                          "project_id":"prj_story",
                          "chapter_id":"chapter_w1",
                          "result":{
                            "schema":"jarvis.writing-room.approval-state.v1",
                            "project_id":"prj_story",
                            "chapter_id":"chapter_w1",
                            "aggregate":{"version":15,"chapter_stage":"APPROVED","publication_state":"SOURCE_PENDING","current_brief_id":"brief_1","current_draft_revision_id":"draft_2","current_canon_diff_id":"diff_1"},
                            "direction":{"project_id":"prj_story","chapter_id":"chapter_w1","chapter_stage":"APPROVED","aggregate_version":15,"brief":{"brief_revision_id":"brief_1","payload":{"title":"La señal falsa"}}},
                            "chapter":{"chapter_id":"chapter_w1","project_id":"prj_story","title":"La señal falsa","status":"HUMAN_APPROVED_PENDING_SOURCE","draft_text":"Texto editado.","revision_count":2},
                            "canon_diff":{"canon_diff_id":"diff_1","revision_id":"draft_2","draft_sha256":"sha-draft-2","brief_revision_id":"brief_1","diff_hash":"diffhash","payload":{"summary":"Una señal nueva queda abierta.","changes":[],"blocking_issues":[],"warnings":[]}},
                            "ready_review_ids":["review_2a","review_2b"],
                            "publish_job":{"job_id":"job_publish","operation":"publish_chapter","status":"QUEUED","payload":{"automatic_execution_enabled":false}},
                            "approval_ready":false
                          }
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
                    "/api/app/writing-room/chapter/plan/approve" -> MockResponse().setBody(
                        """
                        {
                          "schema":"jarvis.writing-room.chapter.plan-approve.v1",
                          "project_id":"prj_story",
                          "chapter":{
                            "chapter_id":"chapter_1",
                            "project_id":"prj_story",
                            "title":"La asamblea de generales",
                            "objective":"Move the contingency forward.",
                            "story_point":"After chapter 37",
                            "status":"PLANNING",
                            "context_pack_id":"",
                            "showrunner_brief":"## Dirección propuesta\nTensión política contenida.",
                            "draft_text":"",
                            "reviewer_text":"",
                            "canon_review_text":"",
                            "characters":["Alexander","Melody"]
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
    fun planningCouncilStartAndTurnExposeVisibleParticipants() = runBlocking(Dispatchers.IO) {
        val session = session()
        val started = session.writingRoomPlanningCouncilStart(
            projectId = "prj_story",
            prompt = "Explore a future reveal",
            title = "Future council",
        ).getOrThrow()

        assertEquals("council_1", started.session.session_id)
        assertTrue(started.needs_story_architect)
        assertEquals("user", started.messages.single().role)

        val turn = session.writingRoomPlanningCouncilTurn(
            projectId = "prj_story",
            sessionId = "council_1",
            participant = "challenger",
        ).getOrThrow()

        assertEquals("challenger", turn.participant)
        assertEquals("Challenger", turn.participant_label)
        assertEquals("challenger", turn.messages.last().role)
        assertEquals("grok-4.7", turn.turn.routing.model)
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
    fun persistentPlanningV2DecodesReviewedBriefContract() = runBlocking(Dispatchers.IO) {
        val session = session()

        val started = session.writingRoomPlanningSessionStart(
            projectId = "prj_story",
            idempotencyKey = "start-1",
        ).getOrThrow()
        assertEquals("chapter_w1", started.result.chapter_id)
        assertEquals(1, started.result.aggregate_version)

        val turn = session.writingRoomPlanningTurn(
            projectId = "prj_story",
            chapterId = "chapter_w1",
            expectedVersion = 1,
            idempotencyKey = "turn-1",
            message = "Quiero una señal falsa.",
        ).getOrThrow()
        assertTrue(turn.result.assistant_message.isNotBlank())
        assertEquals(2, turn.result.aggregate_version)

        val history = session.writingRoomPlanningHistory(
            projectId = "prj_story",
            chapterId = "chapter_w1",
        ).getOrThrow()
        assertEquals(1, history.result.items.size)
        assertEquals("Podemos mantener el origen incierto.", history.result.items.single().assistant_message)

        val prepared = session.writingRoomDirectionPrepare(
            projectId = "prj_story",
            chapterId = "chapter_w1",
            expectedVersion = 2,
            idempotencyKey = "direction-1",
        ).getOrThrow()
        assertTrue(prepared.result.approval_ready)
        assertEquals("DIRECTION_READY", prepared.result.chapter_stage)
        assertEquals(
            setOf("canon_keeper", "challenger"),
            prepared.result.reviews.map { it.review_kind }.toSet(),
        )

        val proposal = requireNotNull(prepared.result.proposal)
        val approved = session.writingRoomDirectionApprove(
            projectId = "prj_story",
            chapterId = "chapter_w1",
            expectedVersion = prepared.result.aggregate_version,
            idempotencyKey = "approve-1",
            proposalRevisionId = proposal.proposal_revision_id,
            proposalHash = proposal.proposal_hash,
            reviewIds = prepared.result.bound_review_ids,
            selectedTitle = "La señal falsa",
        ).getOrThrow()

        assertEquals("BRIEF_APPROVED", approved.result.chapter_stage)
        assertEquals("La señal falsa", approved.result.brief?.payload?.title)
        assertEquals(false, approved.result.draft_execution?.automatic_execution_enabled)
        assertEquals("QUEUED_INTENT_ONLY", approved.result.draft_execution?.state)
    }


    @Test
    fun a4AutoReviewContractReturnsOneHumanGateAndNoAutoApproval() = runBlocking(Dispatchers.IO) {
        val session = session()
        val response = session.writingRoomChapterAutoReviewRun(
            projectId = "prj_story",
            chapterId = "chapter_w1",
        ).getOrThrow()

        assertTrue(response.async_execution)
        assertEquals(false, response.polling_required)
        assertEquals("READY_FOR_HUMAN_APPROVAL", response.result.status)
        assertEquals("SUCCEEDED", response.result.job?.status)
        assertEquals(1, response.result.corrections_used)
        assertEquals(2, response.result.max_corrections)
        assertTrue(response.result.assessment.both_required_reviews_pass)
        assertEquals("Pulir una transición.", response.result.assessment.pending_findings.single().message)
        assertTrue(response.result.approval.approval_ready)
        assertEquals("diff_1", response.result.approval.canon_diff?.canon_diff_id)
        assertEquals(false, response.result.automatic_final_approval)

        val recorded = server.takeRequest()
        assertEquals("/api/app/writing-room/v2/chapter/auto-review/run", recorded.path)
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("jarvis.writing-room.chapter-auto-review-run.request.v1"))
        assertTrue(body.contains("\"chapter_id\":\"chapter_w1\""))
    }

    @Test
    fun w2DraftReviewRevisionAndExactApprovalContractsDecode() = runBlocking(Dispatchers.IO) {
        val session = session()

        val drafted = session.writingRoomDraftRun(
            projectId = "prj_story",
            chapterId = "chapter_w1",
        ).getOrThrow()
        assertEquals("DRAFT_READY", drafted.result.aggregate.chapter_stage)
        assertEquals("SUCCEEDED", drafted.result.job.status)
        assertEquals(2, drafted.result.reviews.size)
        assertEquals("La señal falsa", drafted.result.chapter.title)

        val revised = session.writingRoomDraftRevise(
            projectId = "prj_story",
            chapterId = "chapter_w1",
            expectedVersion = drafted.result.aggregate.version,
            idempotencyKey = "revise-1",
            draftText = "Texto editado.",
        ).getOrThrow()
        assertEquals("QUEUED", revised.result.review_job?.status)
        assertTrue(revised.result.reviews.isEmpty())

        val reviewed = session.writingRoomDraftReviewRun(
            projectId = "prj_story",
            chapterId = "chapter_w1",
        ).getOrThrow()
        assertEquals("SUCCEEDED", reviewed.result.review_job?.status)
        assertEquals(
            setOf("reviewer", "canon_keeper"),
            reviewed.result.reviews.map { it.review_kind }.toSet(),
        )

        val prepared = session.writingRoomApprovalPrepare(
            projectId = "prj_story",
            chapterId = "chapter_w1",
            expectedVersion = reviewed.result.aggregate.version,
            idempotencyKey = "canon-diff-1",
        ).getOrThrow()
        assertTrue(prepared.result.approval_ready)
        assertEquals("READY_FOR_APPROVAL", prepared.result.aggregate.chapter_stage)
        assertEquals("diff_1", prepared.result.canon_diff?.canon_diff_id)
        assertEquals(2, prepared.result.ready_review_ids.size)

        val diff = requireNotNull(prepared.result.canon_diff)
        val approved = session.writingRoomApprovalFinal(
            projectId = "prj_story",
            chapterId = "chapter_w1",
            expectedVersion = prepared.result.aggregate.version,
            idempotencyKey = "final-1",
            revisionId = prepared.result.aggregate.current_draft_revision_id,
            draftSha256 = diff.draft_sha256,
            briefRevisionId = requireNotNull(prepared.result.direction.brief).brief_revision_id,
            reviewIds = prepared.result.ready_review_ids,
            canonDiffId = diff.canon_diff_id,
            canonDiffHash = diff.diff_hash,
        ).getOrThrow()
        assertEquals("APPROVED", approved.result.aggregate.chapter_stage)
        assertEquals("SOURCE_PENDING", approved.result.aggregate.publication_state)
        assertEquals("QUEUED", approved.result.publish_job?.status)
        assertEquals(
            "false",
            approved.result.publish_job?.payload?.get("automatic_execution_enabled").toString(),
        )
    }

    @Test
    fun copilotPortraitRecoveryListsSavedCandidatesWithoutGenerating() = runBlocking(Dispatchers.IO) {
        val response = session().writingRoomCopilotListPortraits("prj_story").getOrThrow()
        assertEquals("prj_story", response.project_id)
        assertEquals(1, response.assets.size)
        assertEquals("character:naruto", response.assets.single().character_ids.single())
        assertEquals("copilot", response.assets.single().provenance["requested_from"]?.jsonPrimitive?.content)
        assertEquals(false, response.assets.single().provenance["fallback_used"]?.jsonPrimitive?.content?.toBoolean())
        assertEquals(1, server.requestCount)
        val request = server.takeRequest()
        assertEquals("/api/app/writing-room/visual-assets/list", request.path)
        assertEquals("Bearer test-token", request.getHeader("Authorization"))
        val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals("prj_story", body["project_id"]?.jsonPrimitive?.content)
    }

    @Test
    fun copilotPortraitPinsImage2MediumAndCharacterCreatorEvidence() = runBlocking(Dispatchers.IO) {
        val image = session().writingRoomCopilotGeneratePortrait(
            "prj_story", "character:naruto", "Naruto", "con ropa de batalla",
        ).getOrThrow()
        assertEquals("gpt-image-2-medium", image.model)
        assertEquals(false, image.fallback_used)
        assertEquals("CANDIDATE", image.visual_asset?.status)
        assertEquals("stored", image.visual_asset?.storage?.state)
        assertEquals(1, server.requestCount)
        val request = server.takeRequest()
        assertEquals("/api/app/images/generations", request.path)
        assertEquals("Bearer test-token", request.getHeader("Authorization"))
        val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals("gpt-image-2-medium", body["model"]?.jsonPrimitive?.content)
        assertEquals("model_select", body["mode"]?.jsonPrimitive?.content)
        assertEquals("square", body["aspect_ratio"]?.jsonPrimitive?.content)
        val meta = body["visual_asset"]!!.jsonObject
        assertEquals("character_creator", meta["surface"]?.jsonPrimitive?.content)
        assertEquals("PRIMARY_REFERENCE", meta["kind"]?.jsonPrimitive?.content)
        assertEquals("front", meta["perspective"]?.jsonPrimitive?.content)
        assertEquals("character:naruto", meta["character_ids"]!!.jsonArray.single().jsonPrimitive.content)
        assertEquals("CANDIDATE", meta["provenance"]!!.jsonObject["authority"]?.jsonPrimitive?.content)
    }

    @Test
    fun copilotPortraitApprovalUsesExactHashAndNeverGeneratesAgain() = runBlocking(Dispatchers.IO) {
        val sha = "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB"
        val approved = session().writingRoomCopilotApprovePortrait(
            "prj_story", "character:naruto", "va_portrait_1", sha,
        ).getOrThrow()
        assertEquals("APPROVED", approved.asset.status)
        assertEquals("character:naruto", approved.wiki_link?.entry_id)
        assertEquals(1, server.requestCount)
        val request = server.takeRequest()
        assertEquals("/api/app/writing-room/v2/visual/characters/approve-primary-exact", request.path)
        assertEquals("Bearer test-token", request.getHeader("Authorization"))
        val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals("va_portrait_1", body["asset_id"]?.jsonPrimitive?.content)
        assertEquals("character:naruto", body["character_id"]?.jsonPrimitive?.content)
        assertEquals(sha, body["asset_sha256"]?.jsonPrimitive?.content)
        assertEquals("prj_story", body["project_id"]?.jsonPrimitive?.content)
    }

    @Test
    fun copilotPortraitRejectsUnverifiedApprovalBeforeNetwork() = runBlocking(Dispatchers.IO) {
        val denied = session().writingRoomCopilotApprovePortrait(
            "prj_story", "character:naruto", "va_portrait_1", "bad-hash",
        )
        assertTrue(denied.isFailure)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun copilotPhotoUploadsAsCandidateWithoutInvokingGenerationOrCanon() = runBlocking(Dispatchers.IO) {
        val encoded = "aGVsbG8="
        val imported = session().writingRoomCopilotImportReference(
            projectId = "prj_story",
            imageBase64 = encoded,
            note = "Inspiración de estilo para Doom",
        ).getOrThrow()
        assertEquals("CANDIDATE", imported.asset.status)
        assertEquals("REFERENCE", imported.asset.kind)
        assertEquals("va_copilot_1", imported.asset.asset_id)
        assertEquals(1, server.requestCount)
        val request = server.takeRequest()
        assertEquals("/api/app/writing-room/visual-assets/ingest", request.path)
        assertEquals("Bearer test-token", request.getHeader("Authorization"))
        val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals("prj_story", body["project_id"]?.jsonPrimitive?.content)
        assertEquals(encoded, body["image_base64"]?.jsonPrimitive?.content)
        assertEquals("REFERENCE", body["kind"]?.jsonPrimitive?.content)
        assertEquals("MANUAL_UPLOAD", body["source"]?.jsonPrimitive?.content)
        assertEquals("copilot", body["provenance"]?.jsonObject?.get("surface")?.jsonPrimitive?.content)
        assertEquals("CANDIDATE", body["provenance"]?.jsonObject?.get("authority")?.jsonPrimitive?.content)
        assertTrue(body["character_ids"] == null)
        assertTrue(body["status"] == null)
    }

    @Test
    fun copilotPhotoRejectsOversizedInputBeforeNetwork() = runBlocking(Dispatchers.IO) {
        val result = session().writingRoomCopilotImportReference(
            projectId = "prj_story",
            imageBase64 = "A".repeat(8 * 1024 * 1024 + 1),
            note = "",
        )
        assertTrue(result.isFailure)
        assertEquals(0, server.requestCount)
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

        val approved = session.writingRoomChapterPlanApprove(
            projectId = "prj_story",
            chapterId = "chapter_1",
            title = "La asamblea de generales",
        ).getOrThrow()
        assertEquals("La asamblea de generales", approved.chapter.title)
        assertEquals("PLANNING", approved.chapter.status)

        assertEquals(6, server.requestCount)
        repeat(6) {
            val recorded = server.takeRequest()
            assertEquals("Bearer test-token", recorded.getHeader("Authorization"))
        }
        assertTrue(session.isAuthenticated)
    }
}
