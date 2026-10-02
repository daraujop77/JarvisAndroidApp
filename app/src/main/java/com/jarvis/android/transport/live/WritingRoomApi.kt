package com.jarvis.android.transport.live

import com.jarvis.android.transport.TransportException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private val writingRoomJson = Json { ignoreUnknownKeys = true }

@Serializable
data class WritingOverviewProject(
    val project_id: String = "",
    val story_id: String = "",
    val title: String = "",
    val snapshot_date: String? = null,
    val authority: String = "",
)

@Serializable
data class WritingLatestChapter(
    val chapter_number: Int? = null,
    val title: String = "",
    val document_id: String? = null,
    val drive_url: String? = null,
)

@Serializable
data class WritingSourceSummary(
    val total: Int = 0,
    val by_status: Map<String, Int> = emptyMap(),
    val by_category: Map<String, Int> = emptyMap(),
)

@Serializable
data class WritingWorkflowSummary(
    val planning_items: Int = 0,
    val chapter_sessions: Int = 0,
)

@Serializable
data class WritingRoomOverview(
    val schema: String = "",
    val project: WritingOverviewProject = WritingOverviewProject(),
    val latest_official_chapter: WritingLatestChapter? = null,
    val sources: WritingSourceSummary = WritingSourceSummary(),
    val workflow: WritingWorkflowSummary = WritingWorkflowSummary(),
)

@Serializable
data class WritingClassification(
    val task_class: String = "",
    val depth: String = "",
    val source: String = "",
    val reason: String = "",
)

@Serializable
data class WritingRoomAutoChat(
    val schema: String = "",
    val classification: WritingClassification = WritingClassification(),
    val selected_participant: String = "",
    val turn: JarvisAppSession.WritingRoomTurn = JarvisAppSession.WritingRoomTurn(),
)

@Serializable
data class WritingRoomRagSource(
    val chunk_id: String? = null,
    val document_id: String? = null,
    val title: String = "",
    val heading: String? = null,
    val category: String? = null,
    val canon_status: String = "",
    val authority: String = "",
    val drive_url: String = "",
    val excerpt: String = "",
)

@Serializable
data class WritingWikiCategory(
    val id: String = "",
    val label: String = "",
    val subtitle: String = "",
    val query: String = "",
    val source_count: Int = 0,
)

@Serializable
data class WritingWikiLegendItem(
    val status: String = "",
    val label: String = "",
    val meaning: String = "",
)

@Serializable
data class WritingWikiRelationship(
    val target: String = "",
    val kind: String = "",
)

@Serializable
data class WritingWikiHistoryItem(
    val period: String = "",
    val label: String = "",
    val summary: String = "",
    val source_refs: List<String> = emptyList(),
)

@Serializable
data class WritingWikiAppearanceItem(
    val chapters: List<Int> = emptyList(),
    val kind: String = "",
    val summary: String = "",
)

@Serializable
data class WritingWikiFutureNote(
    val authority: String = "",
    val chapter_refs: List<Int> = emptyList(),
    val summary: String = "",
    val source_refs: List<String> = emptyList(),
)

@Serializable
data class WritingWikiFamilyMember(
    val target: String = "",
    val relation: String = "",
    val label: String = "",
)

@Serializable
data class WritingWikiProfile(
    val rank: String = "",
    val affiliations: List<String> = emptyList(),
    val age: String = "",
    val age_note: String = "",
    val height: String = "",
    val first_appearance: Int? = null,
    val latest_appearance: Int? = null,
    val family: List<WritingWikiFamilyMember> = emptyList(),
)

@Serializable
data class WritingWikiChapterActivity(
    val chapters: List<Int> = emptyList(),
    val title: String = "",
    val presence: String = "",
    val evidence_scope: String = "",
    val summary: String = "",
    val actions: List<String> = emptyList(),
    val decisions: List<String> = emptyList(),
    val techniques: List<String> = emptyList(),
    val consequences: List<String> = emptyList(),
    val source_refs: List<String> = emptyList(),
)

@Serializable
data class WritingWikiAnalysisInsight(
    val title: String = "",
    val analysis: String = "",
    val evidence_chapters: List<Int> = emptyList(),
)

@Serializable
data class WritingWikiJarvisAnalysis(
    val status: String = "",
    val summary: String = "",
    val motivations: List<String> = emptyList(),
    val behavior_patterns: List<String> = emptyList(),
    val evolution: String = "",
    val insights: List<WritingWikiAnalysisInsight> = emptyList(),
    val evidence_chapters: List<Int> = emptyList(),
    val source_refs: List<String> = emptyList(),
    val disclaimer: String = "",
)

@Serializable
data class WritingWikiImage(
    val status: String = "",
    val asset_id: String? = null,
    val sha256: String? = null,
    val revision: Int? = null,
    val alt: String? = null,
    val locked_visual: Boolean = false,
    val source: String? = null,
)

@Serializable
data class WritingWikiEntity(
    val id: String = "",
    val type: String = "",
    val name: String = "",
    val canonical_name: String = "",
    val name_locked: Boolean = false,
    val authority: String = "",
    val role: String = "",
    val summary: String = "",
    val appearance: String = "",
    val current_state: String = "",
    val traits: List<String> = emptyList(),
    val abilities: List<String> = emptyList(),
    val techniques: List<String> = emptyList(),
    val limitations: List<String> = emptyList(),
    val relationships: List<WritingWikiRelationship> = emptyList(),
    val history: List<WritingWikiHistoryItem> = emptyList(),
    val appearances: List<WritingWikiAppearanceItem> = emptyList(),
    val chapter_refs: List<Int> = emptyList(),
    val mentions: List<Int> = emptyList(),
    val future_refs: List<Int> = emptyList(),
    val future_notes: List<WritingWikiFutureNote> = emptyList(),
    val central_wound: String = "",
    val desire_vs_need: String = "",
    val internal_contradiction: String = "",
    val profile: WritingWikiProfile = WritingWikiProfile(),
    val chapter_activity: List<WritingWikiChapterActivity> = emptyList(),
    val jarvis_analysis: WritingWikiJarvisAnalysis? = null,
    val image: WritingWikiImage? = null,
)

@Serializable
data class WritingStructuredWikiHome(
    val schema: String = "",
    val story_id: String = "",
    val entry_count: Int = 0,
    val by_type: Map<String, Int> = emptyMap(),
    val by_authority: Map<String, Int> = emptyMap(),
    val occurred_count: Int = 0,
    val future_count: Int = 0,
    val featured: List<WritingWikiEntity> = emptyList(),
)

@Serializable
data class WritingWikiNameLock(
    val entry_id: String = "",
    val canonical_name: String = "",
)

@Serializable
data class WritingWikiBrowse(
    val schema: String = "",
    val project_id: String = "",
    val authority: String = "",
    val entry_type: String? = null,
    val filter_authority: String? = null,
    val entries: List<WritingWikiEntity> = emptyList(),
)

@Serializable
data class WritingWikiEntryNameLock(
    val canonical_name: String = "",
    val locked: Boolean = false,
)

@Serializable
data class WritingWikiEntrySection(
    val id: String = "",
    val label: String = "",
    val temporal_scope: String = "",
    val content: JsonObject = JsonObject(emptyMap()),
)

@Serializable
data class WritingWikiEntryResponse(
    val schema: String = "",
    val project_id: String = "",
    val authority: String = "",
    val entry: WritingWikiEntity = WritingWikiEntity(),
    val name_lock: WritingWikiEntryNameLock? = null,
    val sections: List<WritingWikiEntrySection> = emptyList(),
)

@Serializable
data class WritingWikiHome(
    val schema: String = "",
    val project_id: String = "",
    val authority: String = "",
    val latest_official_chapter: WritingLatestChapter? = null,
    val authority_counts: Map<String, Int> = emptyMap(),
    val categories: List<WritingWikiCategory> = emptyList(),
    val featured: List<WritingRoomRagSource> = emptyList(),
    val structured_wiki: WritingStructuredWikiHome? = null,
    val legend: List<WritingWikiLegendItem> = emptyList(),
)

@Serializable
data class WritingWikiSearch(
    val schema: String = "",
    val project_id: String = "",
    val connected: Boolean = false,
    val authority: String = "",
    val structured_priority: Boolean = false,
    val entities: List<WritingWikiEntity> = emptyList(),
    val name_locks: List<WritingWikiNameLock> = emptyList(),
    val rules: List<String> = emptyList(),
    val results: List<WritingRoomRagSource> = emptyList(),
)

@Serializable
data class WritingPlanItem(
    val item_id: String = "",
    val title: String = "",
    val body: String = "",
    val status: String = "",
    val created_utc: String = "",
    val updated_utc: String = "",
)

@Serializable
data class WritingPlanList(
    val schema: String = "",
    val project_id: String = "",
    val items: List<WritingPlanItem> = emptyList(),
)

@Serializable
data class WritingPlanItemResponse(
    val schema: String = "",
    val project_id: String = "",
    val item: WritingPlanItem = WritingPlanItem(),
)

@Serializable
data class WritingPlanningCouncilSession(
    val session_id: String = "",
    val project_id: String = "",
    val title: String = "",
    val seed_prompt: String = "",
    val status: String = "",
    val created_utc: String = "",
    val updated_utc: String = "",
)

@Serializable
data class WritingPlanningCouncilMessage(
    val message_id: String = "",
    val role: String = "",
    val role_label: String = "",
    val body: String = "",
    val round_index: Int = 0,
    val saved_plan_item_id: String = "",
    val created_utc: String = "",
)

@Serializable
data class WritingPlanningCouncil(
    val schema: String = "",
    val project_id: String = "",
    val session: WritingPlanningCouncilSession = WritingPlanningCouncilSession(),
    val messages: List<WritingPlanningCouncilMessage> = emptyList(),
    val needs_story_architect: Boolean = false,
)

@Serializable
data class WritingPlanningCouncilList(
    val schema: String = "",
    val project_id: String = "",
    val items: List<WritingPlanningCouncilSession> = emptyList(),
)

@Serializable
data class WritingPlanningCouncilTurn(
    val schema: String = "",
    val participant: String = "",
    val participant_label: String = "",
    val phase: String = "",
    val turn: JarvisAppSession.WritingRoomTurn = JarvisAppSession.WritingRoomTurn(),
    val project_id: String = "",
    val session: WritingPlanningCouncilSession = WritingPlanningCouncilSession(),
    val messages: List<WritingPlanningCouncilMessage> = emptyList(),
)


@Serializable
data class WritingPlanningSessionResult(
    val schema: String = "",
    val project_id: String = "",
    val chapter_id: String = "",
    val session_id: String = "",
    val title: String = "",
    val stage: String = "",
    val aggregate_version: Int = 1,
)

@Serializable
data class WritingPlanningSessionResponse(
    val schema: String = "",
    val operation: String = "",
    val project_id: String = "",
    val chapter_id: String = "",
    val session_id: String = "",
    val aggregate_version: Int = 1,
    val stage: String = "",
    val result: WritingPlanningSessionResult = WritingPlanningSessionResult(),
)

@Serializable
data class WritingPlanningTurnItem(
    val turn_id: String = "",
    val turn_number: Int = 0,
    val planning_revision_id: String = "",
    val expected_aggregate_version: Int = 0,
    val aggregate_version: Int = 0,
    val user_message: String = "",
    val assistant_message: String = "",
    val status: String = "",
    val job_status: String = "",
    val progress_sequence: Int = 0,
    val error_code: String = "",
    val created_utc: String = "",
    val completed_utc: String = "",
)

@Serializable
data class WritingPlanningTurnResponse(
    val schema: String = "",
    val operation: String = "",
    val project_id: String = "",
    val chapter_id: String = "",
    val session_id: String = "",
    val aggregate_version: Int = 0,
    val result: WritingPlanningTurnItem = WritingPlanningTurnItem(),
)

@Serializable
data class WritingPlanningHistoryResult(
    val schema: String = "",
    val project_id: String = "",
    val chapter_id: String = "",
    val stage: String = "",
    val aggregate_version: Int = 1,
    val items: List<WritingPlanningTurnItem> = emptyList(),
)

@Serializable
data class WritingPlanningHistoryResponse(
    val schema: String = "",
    val operation: String = "",
    val project_id: String = "",
    val chapter_id: String = "",
    val session_id: String = "",
    val aggregate_version: Int = 1,
    val stage: String = "",
    val result: WritingPlanningHistoryResult = WritingPlanningHistoryResult(),
)

@Serializable
data class WritingDirectionProposalPayload(
    val title_options: List<String> = emptyList(),
    val recommended_title: String = "",
    val direction: String = "",
    val beats: List<String> = emptyList(),
    val ending: String = "",
    val unresolved_choices: List<String> = emptyList(),
    val canon_constraints: List<String> = emptyList(),
    val risks: List<String> = emptyList(),
    val authority: String = "",
)

@Serializable
data class WritingDirectionProposalRecord(
    val proposal_revision_id: String = "",
    val revision_number: Int = 0,
    val proposal_hash: String = "",
    val context_pack_id: String = "",
    val context_hash: String = "",
    val source_revision: String = "",
    val payload: WritingDirectionProposalPayload = WritingDirectionProposalPayload(),
    val created_by: String = "",
    val created_utc: String = "",
)

@Serializable
data class WritingDirectionReviewFinding(
    val message: String = "",
    val severity: String = "",
    val evidence: List<String> = emptyList(),
)

@Serializable
data class WritingDirectionReviewPayload(
    val status: String = "",
    val findings: List<WritingDirectionReviewFinding> = emptyList(),
    val error: String = "",
    val error_type: String = "",
)

@Serializable
data class WritingDirectionReviewRecord(
    val review_id: String = "",
    val proposal_revision_id: String = "",
    val proposal_hash: String = "",
    val context_pack_id: String = "",
    val context_hash: String = "",
    val review_kind: String = "",
    val validation_status: String = "",
    val review_hash: String = "",
    val payload: WritingDirectionReviewPayload = WritingDirectionReviewPayload(),
    val created_by: String = "",
    val created_utc: String = "",
)

@Serializable
data class WritingDirectionBriefPayload(
    val title: String = "",
    val direction: String = "",
    val beats: List<String> = emptyList(),
    val ending: String = "",
    val unresolved_choices: List<String> = emptyList(),
    val canon_constraints: List<String> = emptyList(),
    val risks: List<String> = emptyList(),
    val authority: String = "",
)

@Serializable
data class WritingDirectionBriefRecord(
    val brief_revision_id: String = "",
    val revision_number: Int = 0,
    val proposal_revision_id: String = "",
    val proposal_hash: String = "",
    val context_pack_id: String = "",
    val context_hash: String = "",
    val source_revision: String = "",
    val brief_hash: String = "",
    val payload: WritingDirectionBriefPayload = WritingDirectionBriefPayload(),
    val approved_by: String = "",
    val approved_utc: String = "",
)

@Serializable
data class WritingDraftExecutionState(
    val state: String = "",
    val automatic_execution_enabled: Boolean = false,
    val reason: String = "",
)

@Serializable
data class WritingDirectionStateResult(
    val schema: String = "",
    val project_id: String = "",
    val chapter_id: String = "",
    val chapter_stage: String = "",
    val publication_state: String = "",
    val aggregate_version: Int = 1,
    val proposal: WritingDirectionProposalRecord? = null,
    val reviews: List<WritingDirectionReviewRecord> = emptyList(),
    val bound_review_ids: List<String> = emptyList(),
    val brief: WritingDirectionBriefRecord? = null,
    val approval_ready: Boolean = false,
    val draft_execution: WritingDraftExecutionState? = null,
)

@Serializable
data class WritingDirectionStateResponse(
    val schema: String = "",
    val operation: String = "",
    val project_id: String = "",
    val chapter_id: String = "",
    val session_id: String = "",
    val aggregate_version: Int = 1,
    val result: WritingDirectionStateResult = WritingDirectionStateResult(),
)


@Serializable
data class WritingV2AggregateState(
    val version: Int = 0,
    val chapter_stage: String = "",
    val publication_state: String = "",
    val current_brief_id: String = "",
    val current_draft_revision_id: String = "",
    val current_canon_diff_id: String = "",
)

@Serializable
data class WritingV2JobStep(
    val step_id: String = "",
    val sequence: Int = 0,
    val state: String = "",
    val provider_request_id: String = "",
    val error_code: String = "",
)

@Serializable
data class WritingV2Job(
    val job_id: String = "",
    val chapter_id: String = "",
    val operation: String = "",
    val target_revision_id: String = "",
    val status: String = "",
    val error_code: String = "",
    val progress_sequence: Int = 0,
    val attempt_count: Int = 0,
    val payload: JsonObject = JsonObject(emptyMap()),
    val steps: List<WritingV2JobStep> = emptyList(),
)

@Serializable
data class WritingDraftReviewEvidence(
    val source_ref: String? = null,
    val excerpt: String? = null,
)

@Serializable
data class WritingDraftReviewFinding(
    val message: String = "",
    val category: String = "",
    val severity: String = "",
    val suggestions: List<String> = emptyList(),
    val evidence: List<WritingDraftReviewEvidence> = emptyList(),
)

@Serializable
data class WritingDraftReviewPayload(
    val schema: String = "",
    val review_kind: String = "",
    val validation_status: String = "",
    val status: String = "",
    val summary: String = "",
    val findings: List<WritingDraftReviewFinding> = emptyList(),
)

@Serializable
data class WritingDraftReviewRecord(
    val review_id: String = "",
    val revision_id: String = "",
    val draft_sha256: String = "",
    val brief_revision_id: String = "",
    val context_hash: String = "",
    val review_version: Int = 0,
    val review_kind: String = "",
    val validation_status: String = "",
    val review_hash: String = "",
    val payload: WritingDraftReviewPayload = WritingDraftReviewPayload(),
    val created_by: String = "",
    val created_utc: String = "",
)

@Serializable
data class WritingDraftExecutionResult(
    val schema: String = "",
    val project_id: String = "",
    val chapter_id: String = "",
    val job: WritingV2Job = WritingV2Job(),
    val direction: WritingDirectionStateResult = WritingDirectionStateResult(),
    val aggregate: WritingV2AggregateState = WritingV2AggregateState(),
    val review_job: WritingV2Job? = null,
    val reviews: List<WritingDraftReviewRecord> = emptyList(),
    val chapter: WritingChapter = WritingChapter(),
)

@Serializable
data class WritingDraftExecutionResponse(
    val schema: String = "",
    val operation: String = "",
    val project_id: String = "",
    val chapter_id: String = "",
    val result: WritingDraftExecutionResult = WritingDraftExecutionResult(),
)

@Serializable
data class WritingCanonDiffChange(
    val kind: String = "",
    val subject: String = "",
    val statement: String = "",
    val draft_excerpt: String = "",
    val source_refs: List<String> = emptyList(),
)

@Serializable
data class WritingCanonDiffBlockingIssue(
    val message: String = "",
    val source_ref: String = "",
    val source_excerpt: String = "",
)

@Serializable
data class WritingCanonDiffPayload(
    val schema: String = "",
    val revision_id: String = "",
    val summary: String = "",
    val changes: List<WritingCanonDiffChange> = emptyList(),
    val blocking_issues: List<WritingCanonDiffBlockingIssue> = emptyList(),
    val warnings: List<String> = emptyList(),
)

@Serializable
data class WritingCanonDiffRecord(
    val canon_diff_id: String = "",
    val revision_id: String = "",
    val draft_sha256: String = "",
    val brief_revision_id: String = "",
    val context_hash: String = "",
    val source_revision: String = "",
    val diff_hash: String = "",
    val payload: WritingCanonDiffPayload = WritingCanonDiffPayload(),
    val created_by: String = "",
    val created_utc: String = "",
)

@Serializable
data class WritingApprovalRecord(
    val approval_id: String = "",
    val approval_type: String = "",
    val target_revision_id: String = "",
    val draft_revision_id: String = "",
    val draft_sha256: String = "",
    val brief_revision_id: String = "",
    val canon_diff_id: String = "",
    val canon_diff_hash: String = "",
    val review_ids: List<String> = emptyList(),
    val approved_by: String = "",
    val expected_source_revision: String = "",
    val idempotency_key: String = "",
    val created_utc: String = "",
)

@Serializable
data class WritingApprovalStateResult(
    val schema: String = "",
    val project_id: String = "",
    val chapter_id: String = "",
    val aggregate: WritingV2AggregateState = WritingV2AggregateState(),
    val direction: WritingDirectionStateResult = WritingDirectionStateResult(),
    val chapter: WritingChapter = WritingChapter(),
    val reviews: List<WritingDraftReviewRecord> = emptyList(),
    val review_job: WritingV2Job? = null,
    val canon_diff: WritingCanonDiffRecord? = null,
    val ready_review_ids: List<String> = emptyList(),
    val approvals: List<WritingApprovalRecord> = emptyList(),
    val publish_job: WritingV2Job? = null,
    val approval_ready: Boolean = false,
)

@Serializable
data class WritingApprovalStateResponse(
    val schema: String = "",
    val operation: String = "",
    val project_id: String = "",
    val chapter_id: String = "",
    val result: WritingApprovalStateResult = WritingApprovalStateResult(),
)

@Serializable
data class WritingAuthorIntent(
    val objective: String = "",
    val must_have: String = "",
    val must_avoid: String = "",
    val tone: String = "",
    val desired_end: String = "",
)

@Serializable
data class WritingChapter(
    val chapter_id: String = "",
    val project_id: String = "",
    val title: String = "",
    val objective: String = "",
    val story_point: String = "",
    val status: String = "",
    val context_pack_id: String = "",
    val showrunner_brief: String = "",
    val draft_text: String = "",
    val reviewer_text: String = "",
    val canon_review_text: String = "",
    val characters: List<String> = emptyList(),
    val author_intent: WritingAuthorIntent = WritingAuthorIntent(),
    val revision_count: Int = 0,
    val current_revision_number: Int? = null,
    val source_document_id: String = "",
    val source_chapter_number: Int? = null,
    val source_canon_status: String = "",
    val created_utc: String = "",
    val updated_utc: String = "",
)

@Serializable
data class WritingChapterRevisionSummary(
    val revision_id: String = "",
    val revision_number: Int = 0,
    val source: String = "",
    val restored_from_revision_id: String = "",
    val context_pack_id: String = "",
    val created_utc: String = "",
    val char_count: Int = 0,
    val word_count: Int = 0,
    val preview: String = "",
    val is_current: Boolean = false,
)

@Serializable
data class WritingChapterRevisionList(
    val schema: String = "",
    val project_id: String = "",
    val chapter_id: String = "",
    val current_revision_number: Int? = null,
    val items: List<WritingChapterRevisionSummary> = emptyList(),
)

@Serializable
data class WritingChapterRevision(
    val revision_id: String = "",
    val revision_number: Int = 0,
    val draft_text: String = "",
    val source: String = "",
    val restored_from_revision_id: String = "",
    val context_pack_id: String = "",
    val created_utc: String = "",
    val draft_sha256: String = "",
    val brief_revision_id: String = "",
    val parent_revision_id: String = "",
    val schema_version: String = "",
)

@Serializable
data class WritingChapterRevisionResponse(
    val schema: String = "",
    val project_id: String = "",
    val chapter_id: String = "",
    val revision: WritingChapterRevision = WritingChapterRevision(),
)

@Serializable
data class WritingChapterSummary(
    val chapter_id: String = "",
    val title: String = "",
    val objective: String = "",
    val story_point: String = "",
    val status: String = "",
    val context_pack_id: String = "",
    val revision_count: Int = 0,
    val source_document_id: String = "",
    val source_chapter_number: Int? = null,
    val source_canon_status: String = "",
    val created_utc: String = "",
    val updated_utc: String = "",
)

@Serializable
data class WritingChapterList(
    val schema: String = "",
    val project_id: String = "",
    val items: List<WritingChapterSummary> = emptyList(),
)

@Serializable
data class WritingChapterResponse(
    val schema: String = "",
    val project_id: String = "",
    val chapter: WritingChapter = WritingChapter(),
)

@Serializable
data class WritingReviewEvidence(
    val source_ref: String? = null,
    val excerpt: String? = null,
    val offset: Int? = null,
    val length: Int? = null,
)

@Serializable
data class WritingReviewFinding(
    val schema: String = "",
    val finding_id: String = "",
    val check: String = "",
    val category: String = "",
    val severity: String = "",
    val message: String = "",
    val suggestions: List<String> = emptyList(),
    val evidence: List<WritingReviewEvidence> = emptyList(),
    val evidence_required: Boolean = false,
    val evidence_missing: Boolean = false,
    val evidence_source_binding_enforced: Boolean = false,
    val evidence_bound: Boolean? = null,
    val evidence_unbound: Boolean = false,
    val authority: String = "",
    val canon_mutation: String = "",
    val auto_apply: Boolean = false,
    val resolved: Boolean = false,
)

@Serializable
data class WritingEngineReviewResult(
    val schema: String = "",
    val project_id: String = "",
    val chapter_id: String = "",
    val context_pack_id: String = "",
    val authority: String = "",
    val read_only: Boolean = true,
    val canon_mutation: String = "",
    val auto_rewrite: Boolean = false,
    val auto_approve: Boolean = false,
    val status: String = "",
    val check_order: List<String> = emptyList(),
    val check_status: Map<String, String> = emptyMap(),
    val missing_required_checks: List<String> = emptyList(),
    val failed_required_checks: List<String> = emptyList(),
    val severity_counts: Map<String, Int> = emptyMap(),
    val finding_count: Int = 0,
    val findings: List<WritingReviewFinding> = emptyList(),
    val human_decision_required: Boolean = true,
)

@Serializable
data class WritingEngineReviewEnvelope(
    val schema: String = "",
    val authority: String = "",
    val canon_mutation: String = "",
    val context_pack_id: String = "",
    val frozen_context_chars: Int = 0,
    val result: WritingEngineReviewResult = WritingEngineReviewResult(),
)

@Serializable
data class WritingChapterAction(
    val schema: String = "",
    val chapter: WritingChapter = WritingChapter(),
    val engine_review: WritingEngineReviewEnvelope? = null,
)

@Serializable
data class WritingLibraryChapter(
    val document_id: String = "",
    val chapter_number: Int = 0,
    val title: String = "",
    val excerpt: String = "",
    val word_count: Int = 0,
    val char_count: Int = 0,
    val content_kind: String = "",
    val grouped_range: List<Int> = emptyList(),
    val source_grouped: Boolean = false,
    val source_document_id: String = "",
    val source_title: String = "",
    val drive_url: String = "",
    val canon_status: String = "",
    val authority: String = "",
    val source_ref: String = "",
    val canon_summary: String = "",
    val wiki_entry_id: String = "",
)

@Serializable
data class WritingTimelineCharacterRef(
    val id: String = "",
    val name: String = "",
)

@Serializable
data class WritingTimelineArcRef(
    val id: String = "",
    val name: String = "",
)

@Serializable
data class WritingTimelineOccurred(
    val chapter_number: Int = 0,
    val document_id: String = "",
    val title: String = "",
    val summary: String = "",
    val summary_source: String = "",
    val canon_status: String = "",
    val content_kind: String = "",
    val wiki_entry_id: String = "",
    val source_ref: String = "",
    val related_characters: List<WritingTimelineCharacterRef> = emptyList(),
    val related_arcs: List<WritingTimelineArcRef> = emptyList(),
)

@Serializable
data class WritingTimelineFuture(
    val id: String = "",
    val type: String = "",
    val name: String = "",
    val authority: String = "",
    val summary: String = "",
    val chapter_refs: List<Int> = emptyList(),
    val source_refs: List<String> = emptyList(),
)

@Serializable
data class WritingWikiTimeline(
    val schema: String = "",
    val project_id: String = "",
    val occurred: List<WritingTimelineOccurred> = emptyList(),
    val future: List<WritingTimelineFuture> = emptyList(),
    val rules: List<String> = emptyList(),
)

@Serializable
data class WritingExplorerEntry(
    val id: String = "",
    val type: String = "",
    val name: String = "",
    val authority: String = "",
    val summary: String = "",
    val chapter_refs: List<Int> = emptyList(),
    val future_refs: List<Int> = emptyList(),
    val relationships: List<WritingWikiRelationship> = emptyList(),
    val source_refs: List<String> = emptyList(),
)

@Serializable
data class WritingExplorerSection(
    val id: String = "",
    val label: String = "",
    val items: List<WritingExplorerEntry> = emptyList(),
)

@Serializable
data class WritingCanonExplorer(
    val schema: String = "",
    val project_id: String = "",
    val authority: String = "",
    val authority_counts: Map<String, Int> = emptyMap(),
    val sections: List<WritingExplorerSection> = emptyList(),
    val legend: List<WritingWikiLegendItem> = emptyList(),
)

@Serializable
data class WritingLibraryItem(
    val document_id: String = "",
    val title: String = "",
    val path: String = "",
    val drive_url: String = "",
    val canon_status: String = "",
    val chapter_min: Int? = null,
    val chapter_max: Int? = null,
)

@Serializable
data class WritingLibraryList(
    val schema: String = "",
    val project_id: String = "",
    val items: List<WritingLibraryItem> = emptyList(),
    val chapters: List<WritingLibraryChapter> = emptyList(),
    val chapter_counts: Map<String, Int> = emptyMap(),
    val exports: Map<String, String> = emptyMap(),
)

@Serializable
data class WritingLibraryDocument(
    val document_id: String = "",
    val title: String = "",
    val drive_url: String = "",
    val canon_status: String = "",
    val chapter_number: Int? = null,
    val content_kind: String = "",
    val source_document_id: String = "",
    val source_title: String = "",
    val text: String = "",
)

@Serializable
data class WritingLibraryDocumentResponse(
    val schema: String = "",
    val project_id: String = "",
    val document: WritingLibraryDocument = WritingLibraryDocument(),
)

@Serializable
data class WritingVisualAssetContent(
    val schema: String = "",
    val asset_id: String = "",
    val mime_type: String = "image/jpeg",
    val sha256: String = "",
    val image_base64: String = "",
)

@Serializable
data class WritingLibraryExport(
    val schema: String = "",
    val project_id: String = "",
    val document_id: String = "",
    val authority: String = "",
    val vps_persistence: String = "",
    val format: String = "",
    val filename: String = "",
    val mime_type: String = "application/octet-stream",
    val size_bytes: Int = 0,
    val sha256: String = "",
    val data_base64: String = "",
)

private suspend inline fun <reified T> JarvisAppSession.writingPost(
    path: String,
    body: JsonObject,
): Result<T> = withContext(Dispatchers.IO) {
    if (expired) {
        clear()
        return@withContext Result.failure(TransportException("session expired"))
    }
    val auth = authHeader()
        ?: return@withContext Result.failure(TransportException("no live session"))
    runCatching {
        val response = post(baseUrl, path, body.toString(), auth)
        if (response.first == 401) {
            clear()
            throw TransportException("session expired")
        }
        requireOk(response)
        writingRoomJson.decodeFromString<T>(response.second)
    }
}

suspend fun JarvisAppSession.writingRoomOverview(projectId: String): Result<WritingRoomOverview> =
    writingPost(
        "/api/app/writing-room/overview",
        buildJsonObject { put("project_id", projectId) },
    )

suspend fun JarvisAppSession.writingRoomAutoChat(
    projectId: String,
    projectTitle: String,
    prompt: String,
    room: String = "chat",
): Result<WritingRoomAutoChat> {
    val clean = prompt.trim()
    if (clean.isEmpty()) return Result.failure(TransportException("Writing Room prompt is empty"))
    if (clean.length > 6000) return Result.failure(TransportException("Writing Room prompt is too long"))
    return writingPost(
        "/api/app/writing-room/chat",
        buildJsonObject {
            put("project_id", projectId)
            put("project_title", projectTitle)
            put("room", room)
            put("prompt", clean)
        },
    )
}

suspend fun JarvisAppSession.writingRoomAutoChatStream(
    projectId: String,
    projectTitle: String,
    prompt: String,
    room: String = "chat",
    onDelta: (String) -> Unit,
): Result<WritingRoomAutoChat> = withContext(Dispatchers.IO) {
    val clean = prompt.trim()
    if (clean.isEmpty()) return@withContext Result.failure(TransportException("Writing Room prompt is empty"))
    if (clean.length > 6000) return@withContext Result.failure(TransportException("Writing Room prompt is too long"))
    if (expired) {
        clear()
        return@withContext Result.failure(TransportException("session expired"))
    }
    val auth = authHeader()
        ?: return@withContext Result.failure(TransportException("no live session"))
    val body = buildJsonObject {
        put("project_id", projectId)
        put("project_title", projectTitle)
        put("room", room)
        put("prompt", clean)
        put("request_id", java.util.UUID.randomUUID().toString())
    }.toString()

    runCatching {
        var completed: WritingRoomAutoChat? = null
        var streamError: String? = null
        val status = postSse(
            baseUrl,
            "/api/app/writing-room/chat/stream",
            body,
            auth,
        ) { event, data ->
            when (event) {
                "delta" -> {
                    val obj = writingRoomJson.parseToJsonElement(data).jsonObject
                    obj["delta"]?.jsonPrimitive?.contentOrNull?.let(onDelta)
                }
                "complete" -> {
                    completed = writingRoomJson.decodeFromString<WritingRoomAutoChat>(data)
                }
                "error" -> {
                    val obj = runCatching { writingRoomJson.parseToJsonElement(data).jsonObject }.getOrNull()
                    streamError = obj?.get("message")?.jsonPrimitive?.contentOrNull
                        ?: obj?.get("error")?.jsonPrimitive?.contentOrNull
                        ?: "Writing Room stream failed"
                }
            }
        }
        if (status == 401) {
            clear()
            throw TransportException("session expired")
        }
        if (status !in 200..299) throw TransportException("HTTP $status")
        streamError?.let { throw TransportException(it) }
        completed ?: throw TransportException("Writing Room stream ended without completion")
    }
}

suspend fun JarvisAppSession.writingRoomWikiHome(
    projectId: String,
): Result<WritingWikiHome> =
    writingPost(
        "/api/app/writing-room/wiki/home",
        buildJsonObject { put("project_id", projectId) },
    )

suspend fun JarvisAppSession.writingRoomWikiSearch(
    projectId: String,
    query: String,
    topK: Int = 8,
): Result<WritingWikiSearch> {
    val clean = query.trim()
    if (clean.isEmpty()) return Result.failure(TransportException("Wiki query is empty"))
    return writingPost(
        "/api/app/writing-room/wiki/search",
        buildJsonObject {
            put("project_id", projectId)
            put("query", clean)
            put("top_k", topK.coerceIn(1, 12))
        },
    )
}

suspend fun JarvisAppSession.writingRoomWikiBrowse(
    projectId: String,
    entryType: String? = null,
    authority: String? = null,
    topK: Int = 100,
): Result<WritingWikiBrowse> =
    writingPost(
        "/api/app/writing-room/wiki/browse",
        buildJsonObject {
            put("project_id", projectId)
            if (!entryType.isNullOrBlank()) put("entry_type", entryType.trim())
            if (!authority.isNullOrBlank()) put("authority", authority.trim())
            put("top_k", topK.coerceIn(1, 100))
        },
    )

suspend fun JarvisAppSession.writingRoomVisualAssetFetch(
    projectId: String,
    assetId: String,
): Result<WritingVisualAssetContent> {
    val clean = assetId.trim()
    if (clean.isEmpty()) return Result.failure(TransportException("Visual asset id is empty"))
    return writingPost(
        "/api/app/writing-room/visual-assets/fetch",
        buildJsonObject {
            put("project_id", projectId)
            put("asset_id", clean)
        },
    )
}

suspend fun JarvisAppSession.writingRoomWikiTimeline(
    projectId: String,
): Result<WritingWikiTimeline> =
    writingPost(
        "/api/app/writing-room/wiki/timeline",
        buildJsonObject { put("project_id", projectId) },
    )

suspend fun JarvisAppSession.writingRoomWikiExplorer(
    projectId: String,
): Result<WritingCanonExplorer> =
    writingPost(
        "/api/app/writing-room/wiki/explorer",
        buildJsonObject { put("project_id", projectId) },
    )

suspend fun JarvisAppSession.writingRoomWikiEntry(
    projectId: String,
    entryId: String,
): Result<WritingWikiEntryResponse> {
    val clean = entryId.trim()
    if (clean.isEmpty()) return Result.failure(TransportException("Wiki entry id is empty"))
    return writingPost(
        "/api/app/writing-room/wiki/entry",
        buildJsonObject {
            put("project_id", projectId)
            put("entry_id", clean)
        },
    )
}

suspend fun JarvisAppSession.writingRoomPlanList(projectId: String): Result<WritingPlanList> =
    writingPost(
        "/api/app/writing-room/plan/list",
        buildJsonObject { put("project_id", projectId) },
    )

suspend fun JarvisAppSession.writingRoomPlanningCouncilStart(
    projectId: String,
    prompt: String,
    title: String = "",
): Result<WritingPlanningCouncil> {
    val clean = prompt.trim()
    if (clean.isEmpty()) return Result.failure(TransportException("Planning Council prompt is empty"))
    return writingPost(
        "/api/app/writing-room/plan/council/start",
        buildJsonObject {
            put("project_id", projectId)
            put("prompt", clean)
            put("title", title.trim())
        },
    )
}

suspend fun JarvisAppSession.writingRoomPlanningCouncilGet(
    projectId: String,
    sessionId: String,
): Result<WritingPlanningCouncil> =
    writingPost(
        "/api/app/writing-room/plan/council/get",
        buildJsonObject {
            put("project_id", projectId)
            put("session_id", sessionId)
        },
    )

suspend fun JarvisAppSession.writingRoomPlanningCouncilList(
    projectId: String,
    limit: Int = 20,
): Result<WritingPlanningCouncilList> =
    writingPost(
        "/api/app/writing-room/plan/council/list",
        buildJsonObject {
            put("project_id", projectId)
            put("limit", limit.coerceIn(1, 100))
        },
    )

suspend fun JarvisAppSession.writingRoomPlanningCouncilTurn(
    projectId: String,
    sessionId: String,
    participant: String,
    phase: String = "discussion",
): Result<WritingPlanningCouncilTurn> =
    writingPost(
        "/api/app/writing-room/plan/council/turn",
        buildJsonObject {
            put("project_id", projectId)
            put("session_id", sessionId)
            put("participant", participant)
            put("phase", phase)
        },
    )

suspend fun JarvisAppSession.writingRoomPlanningCouncilMessage(
    projectId: String,
    sessionId: String,
    message: String,
): Result<WritingPlanningCouncil> {
    val clean = message.trim()
    if (clean.isEmpty()) return Result.failure(TransportException("Planning Council message is empty"))
    return writingPost(
        "/api/app/writing-room/plan/council/message",
        buildJsonObject {
            put("project_id", projectId)
            put("session_id", sessionId)
            put("message", clean)
        },
    )
}

suspend fun JarvisAppSession.writingRoomPlanningCouncilSaveIdea(
    projectId: String,
    sessionId: String,
    messageId: String,
    title: String = "",
): Result<WritingPlanItemResponse> =
    writingPost(
        "/api/app/writing-room/plan/council/save",
        buildJsonObject {
            put("project_id", projectId)
            put("session_id", sessionId)
            put("message_id", messageId)
            put("title", title.trim())
        },
    )

suspend fun JarvisAppSession.writingRoomPlanCreate(
    projectId: String,
    title: String,
    body: String,
): Result<WritingPlanItemResponse> {
    val clean = body.trim()
    if (clean.isEmpty()) return Result.failure(TransportException("Planning item is empty"))
    return writingPost(
        "/api/app/writing-room/plan/create",
        buildJsonObject {
            put("project_id", projectId)
            put("title", title.trim())
            put("body", clean)
        },
    )
}

suspend fun JarvisAppSession.writingRoomPlanStatus(
    projectId: String,
    itemId: String,
    status: String,
): Result<WritingPlanItemResponse> =
    writingPost(
        "/api/app/writing-room/plan/status",
        buildJsonObject {
            put("project_id", projectId)
            put("item_id", itemId)
            put("status", status)
        },
    )


suspend fun JarvisAppSession.writingRoomPlanningSessionStart(
    projectId: String,
    idempotencyKey: String,
): Result<WritingPlanningSessionResponse> =
    writingPost(
        "/api/app/writing-room/v2/planning/session/start",
        buildJsonObject {
            put("schema", "jarvis.writing-room.planning-session-start.request.v1")
            put("project_id", projectId)
            put("idempotency_key", idempotencyKey)
        },
    )

suspend fun JarvisAppSession.writingRoomPlanningTurn(
    projectId: String,
    chapterId: String,
    expectedVersion: Int,
    idempotencyKey: String,
    message: String,
): Result<WritingPlanningTurnResponse> {
    val clean = message.trim()
    if (clean.isEmpty()) return Result.failure(TransportException("Planning message is empty"))
    return writingPost(
        "/api/app/writing-room/v2/planning/turn",
        buildJsonObject {
            put("schema", "jarvis.writing-room.planning-turn.request.v1")
            put("project_id", projectId)
            put("chapter_id", chapterId)
            put("expected_version", expectedVersion)
            put("idempotency_key", idempotencyKey)
            put("message", clean)
        },
    )
}

suspend fun JarvisAppSession.writingRoomPlanningHistory(
    projectId: String,
    chapterId: String,
    limit: Int = 100,
): Result<WritingPlanningHistoryResponse> =
    writingPost(
        "/api/app/writing-room/v2/planning/history",
        buildJsonObject {
            put("schema", "jarvis.writing-room.planning-history.request.v1")
            put("project_id", projectId)
            put("chapter_id", chapterId)
            put("limit", limit.coerceIn(1, 200))
        },
    )

suspend fun JarvisAppSession.writingRoomDirectionStatus(
    projectId: String,
    chapterId: String,
): Result<WritingDirectionStateResponse> =
    writingPost(
        "/api/app/writing-room/v2/planning/direction/status",
        buildJsonObject {
            put("schema", "jarvis.writing-room.direction-status.request.v1")
            put("project_id", projectId)
            put("chapter_id", chapterId)
        },
    )

suspend fun JarvisAppSession.writingRoomDirectionPrepare(
    projectId: String,
    chapterId: String,
    expectedVersion: Int,
    idempotencyKey: String,
): Result<WritingDirectionStateResponse> =
    writingPost(
        "/api/app/writing-room/v2/planning/direction/prepare",
        buildJsonObject {
            put("schema", "jarvis.writing-room.direction-prepare.request.v1")
            put("project_id", projectId)
            put("chapter_id", chapterId)
            put("expected_version", expectedVersion)
            put("idempotency_key", idempotencyKey)
        },
    )

suspend fun JarvisAppSession.writingRoomDirectionApprove(
    projectId: String,
    chapterId: String,
    expectedVersion: Int,
    idempotencyKey: String,
    proposalRevisionId: String,
    proposalHash: String,
    reviewIds: List<String>,
    selectedTitle: String,
): Result<WritingDirectionStateResponse> =
    writingPost(
        "/api/app/writing-room/v2/planning/direction/approve",
        buildJsonObject {
            put("schema", "jarvis.writing-room.direction-approve.request.v1")
            put("project_id", projectId)
            put("chapter_id", chapterId)
            put("expected_version", expectedVersion)
            put("idempotency_key", idempotencyKey)
            put("proposal_revision_id", proposalRevisionId)
            put("proposal_hash", proposalHash)
            put("review_ids", buildJsonArray {
                reviewIds.filter(String::isNotBlank).forEach {
                    add(kotlinx.serialization.json.JsonPrimitive(it))
                }
            })
            put("selected_title", selectedTitle.trim())
        },
    )


suspend fun JarvisAppSession.writingRoomDraftStatus(
    projectId: String,
    chapterId: String,
): Result<WritingDraftExecutionResponse> =
    writingPost(
        "/api/app/writing-room/v2/draft/status",
        buildJsonObject {
            put("schema", "jarvis.writing-room.draft-status.request.v1")
            put("project_id", projectId)
            put("chapter_id", chapterId)
        },
    )

suspend fun JarvisAppSession.writingRoomDraftRun(
    projectId: String,
    chapterId: String,
): Result<WritingDraftExecutionResponse> =
    writingPost(
        "/api/app/writing-room/v2/draft/run",
        buildJsonObject {
            put("schema", "jarvis.writing-room.draft-run.request.v1")
            put("project_id", projectId)
            put("chapter_id", chapterId)
        },
    )

suspend fun JarvisAppSession.writingRoomDraftRevise(
    projectId: String,
    chapterId: String,
    expectedVersion: Int,
    idempotencyKey: String,
    draftText: String,
): Result<WritingDraftExecutionResponse> {
    if (draftText.isBlank()) return Result.failure(TransportException("Draft text is empty"))
    return writingPost(
        "/api/app/writing-room/v2/draft/revise",
        buildJsonObject {
            put("schema", "jarvis.writing-room.draft-revise.request.v1")
            put("project_id", projectId)
            put("chapter_id", chapterId)
            put("expected_version", expectedVersion)
            put("idempotency_key", idempotencyKey)
            put("draft_text", draftText)
        },
    )
}

suspend fun JarvisAppSession.writingRoomDraftRestore(
    projectId: String,
    chapterId: String,
    expectedVersion: Int,
    idempotencyKey: String,
    revisionId: String,
): Result<WritingDraftExecutionResponse> =
    writingPost(
        "/api/app/writing-room/v2/draft/restore",
        buildJsonObject {
            put("schema", "jarvis.writing-room.draft-restore.request.v1")
            put("project_id", projectId)
            put("chapter_id", chapterId)
            put("expected_version", expectedVersion)
            put("idempotency_key", idempotencyKey)
            put("revision_id", revisionId)
        },
    )

suspend fun JarvisAppSession.writingRoomDraftReviewRun(
    projectId: String,
    chapterId: String,
): Result<WritingDraftExecutionResponse> =
    writingPost(
        "/api/app/writing-room/v2/draft/review/run",
        buildJsonObject {
            put("schema", "jarvis.writing-room.draft-review-run.request.v1")
            put("project_id", projectId)
            put("chapter_id", chapterId)
        },
    )

suspend fun JarvisAppSession.writingRoomApprovalStatus(
    projectId: String,
    chapterId: String,
): Result<WritingApprovalStateResponse> =
    writingPost(
        "/api/app/writing-room/v2/approval/status",
        buildJsonObject {
            put("schema", "jarvis.writing-room.approval-status.request.v1")
            put("project_id", projectId)
            put("chapter_id", chapterId)
        },
    )

suspend fun JarvisAppSession.writingRoomApprovalPrepare(
    projectId: String,
    chapterId: String,
    expectedVersion: Int,
    idempotencyKey: String,
): Result<WritingApprovalStateResponse> =
    writingPost(
        "/api/app/writing-room/v2/approval/prepare",
        buildJsonObject {
            put("schema", "jarvis.writing-room.approval-prepare.request.v1")
            put("project_id", projectId)
            put("chapter_id", chapterId)
            put("expected_version", expectedVersion)
            put("idempotency_key", idempotencyKey)
        },
    )

suspend fun JarvisAppSession.writingRoomApprovalFinal(
    projectId: String,
    chapterId: String,
    expectedVersion: Int,
    idempotencyKey: String,
    revisionId: String,
    draftSha256: String,
    briefRevisionId: String,
    reviewIds: List<String>,
    canonDiffId: String,
    canonDiffHash: String,
): Result<WritingApprovalStateResponse> =
    writingPost(
        "/api/app/writing-room/v2/approval/final",
        buildJsonObject {
            put("schema", "jarvis.writing-room.approval-final.request.v1")
            put("project_id", projectId)
            put("chapter_id", chapterId)
            put("expected_version", expectedVersion)
            put("idempotency_key", idempotencyKey)
            put("approve", true)
            put("revision_id", revisionId)
            put("draft_sha256", draftSha256)
            put("brief_revision_id", briefRevisionId)
            put("review_ids", buildJsonArray {
                reviewIds.filter(String::isNotBlank).forEach {
                    add(kotlinx.serialization.json.JsonPrimitive(it))
                }
            })
            put("canon_diff_id", canonDiffId)
            put("canon_diff_hash", canonDiffHash)
        },
    )

suspend fun JarvisAppSession.writingRoomChapterList(projectId: String): Result<WritingChapterList> =
    writingPost(
        "/api/app/writing-room/chapter/list",
        buildJsonObject { put("project_id", projectId) },
    )

suspend fun JarvisAppSession.writingRoomChapterGet(
    projectId: String,
    chapterId: String,
): Result<WritingChapterResponse> =
    writingPost(
        "/api/app/writing-room/chapter/get",
        buildJsonObject {
            put("project_id", projectId)
            put("chapter_id", chapterId)
        },
    )

suspend fun JarvisAppSession.writingRoomChapterRevisions(
    projectId: String,
    chapterId: String,
): Result<WritingChapterRevisionList> =
    writingPost(
        "/api/app/writing-room/chapter/revisions",
        buildJsonObject {
            put("project_id", projectId)
            put("chapter_id", chapterId)
        },
    )

suspend fun JarvisAppSession.writingRoomChapterRevisionGet(
    projectId: String,
    chapterId: String,
    revisionId: String,
): Result<WritingChapterRevisionResponse> =
    writingPost(
        "/api/app/writing-room/chapter/revision/get",
        buildJsonObject {
            put("project_id", projectId)
            put("chapter_id", chapterId)
            put("revision_id", revisionId)
        },
    )

suspend fun JarvisAppSession.writingRoomChapterRevisionRestore(
    projectId: String,
    chapterId: String,
    revisionId: String,
): Result<WritingChapterResponse> =
    writingPost(
        "/api/app/writing-room/chapter/revision/restore",
        buildJsonObject {
            put("project_id", projectId)
            put("chapter_id", chapterId)
            put("revision_id", revisionId)
        },
    )

@Serializable
data class WritingChapterDeleteAck(
    val schema: String = "",
    val project_id: String = "",
    val chapter_id: String = "",
    val deleted: Boolean = false,
    val source_untouched: Boolean = true,
    val source_document_id: String = "",
    val source_chapter_number: Int? = null,
    val source_canon_status: String = "",
)

@Serializable
data class WritingChapterTrashItem(
    val chapter_id: String = "",
    val title: String = "",
    val status: String = "",
    val deleted_by: String = "",
    val delete_reason: String = "",
    val deleted_utc: String = "",
)

@Serializable
data class WritingChapterTrashList(
    val schema: String = "",
    val project_id: String = "",
    val items: List<WritingChapterTrashItem> = emptyList(),
)

suspend fun JarvisAppSession.writingRoomChapterDelete(
    projectId: String,
    chapterId: String,
): Result<WritingChapterDeleteAck> =
    writingPost(
        "/api/app/writing-room/chapter/delete",
        buildJsonObject {
            put("project_id", projectId)
            put("chapter_id", chapterId)
        },
    )

suspend fun JarvisAppSession.writingRoomChapterTrash(
    projectId: String,
): Result<WritingChapterTrashList> =
    writingPost(
        "/api/app/writing-room/chapter/trash",
        buildJsonObject { put("project_id", projectId) },
    )

suspend fun JarvisAppSession.writingRoomChapterRestoreDeleted(
    projectId: String,
    chapterId: String,
): Result<WritingChapterResponse> =
    writingPost(
        "/api/app/writing-room/chapter/restore-deleted",
        buildJsonObject {
            put("project_id", projectId)
            put("chapter_id", chapterId)
        },
    )

suspend fun JarvisAppSession.writingRoomChapterStart(
    projectId: String,
    title: String,
    objective: String,
    storyPoint: String = "",
    characters: List<String> = emptyList(),
    mustHave: String = "",
    mustAvoid: String = "",
    tone: String = "",
    desiredEnd: String = "",
): Result<WritingChapterResponse> {
    val clean = objective.trim()
    if (clean.isEmpty()) return Result.failure(TransportException("Author objective is required"))
    return writingPost(
        "/api/app/writing-room/chapter/start",
        buildJsonObject {
            put("project_id", projectId)
            put("title", title.trim())
            put("objective", clean)
            put("story_point", storyPoint.trim())
            put("characters", buildJsonArray {
                characters.map { it.trim() }.filter { it.isNotEmpty() }.take(12).forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
            })
            put("must_have", mustHave.trim())
            put("must_avoid", mustAvoid.trim())
            put("tone", tone.trim())
            put("desired_end", desiredEnd.trim())
        },
    )
}

private suspend fun JarvisAppSession.writingRoomChapterAction(
    projectId: String,
    chapterId: String,
    action: String,
): Result<WritingChapterAction> =
    writingPost(
        "/api/app/writing-room/chapter/$action",
        buildJsonObject {
            put("project_id", projectId)
            put("chapter_id", chapterId)
        },
    )

suspend fun JarvisAppSession.writingRoomChapterShowrunner(
    projectId: String,
    chapterId: String,
): Result<WritingChapterAction> = writingRoomChapterAction(projectId, chapterId, "showrunner")

suspend fun JarvisAppSession.writingRoomChapterPlanApprove(
    projectId: String,
    chapterId: String,
    title: String,
): Result<WritingChapterResponse> {
    val cleanTitle = title.trim()
    if (cleanTitle.isEmpty()) return Result.failure(TransportException("Chapter title is required"))
    return writingPost(
        "/api/app/writing-room/chapter/plan/approve",
        buildJsonObject {
            put("project_id", projectId)
            put("chapter_id", chapterId)
            put("title", cleanTitle)
        },
    )
}

suspend fun JarvisAppSession.writingRoomChapterWrite(
    projectId: String,
    chapterId: String,
): Result<WritingChapterAction> = writingRoomChapterAction(projectId, chapterId, "write")

suspend fun JarvisAppSession.writingRoomChapterReview(
    projectId: String,
    chapterId: String,
): Result<WritingChapterAction> = writingRoomChapterAction(projectId, chapterId, "review")

suspend fun JarvisAppSession.writingRoomSaveDraft(
    projectId: String,
    chapterId: String,
    draftText: String,
): Result<WritingChapterResponse> =
    writingPost(
        "/api/app/writing-room/chapter/save-draft",
        buildJsonObject {
            put("project_id", projectId)
            put("chapter_id", chapterId)
            put("draft_text", draftText)
        },
    )

suspend fun JarvisAppSession.writingRoomLibraryList(projectId: String): Result<WritingLibraryList> =
    writingPost(
        "/api/app/writing-room/library/list",
        buildJsonObject { put("project_id", projectId) },
    )

suspend fun JarvisAppSession.writingRoomLibraryChapterEdit(
    projectId: String,
    documentId: String,
): Result<WritingChapterResponse> =
    writingPost(
        "/api/app/writing-room/library/chapter/edit",
        buildJsonObject {
            put("project_id", projectId)
            put("document_id", documentId)
        },
    )

suspend fun JarvisAppSession.writingRoomLibraryRead(
    projectId: String,
    documentId: String,
): Result<WritingLibraryDocumentResponse> =
    writingPost(
        "/api/app/writing-room/library/read",
        buildJsonObject {
            put("project_id", projectId)
            put("document_id", documentId)
        },
    )


suspend fun JarvisAppSession.writingRoomProjectCreate(title: String): Result<WritingRoomOverview> =
    writingPost(
        "/api/app/writing-room/project/create",
        buildJsonObject {
            put("title", title.trim())
            put("kind", "WRITING_ROOM")
        },
    )

suspend fun JarvisAppSession.writingRoomProjectRename(
    projectId: String,
    title: String,
): Result<WritingRoomOverview> =
    writingPost(
        "/api/app/writing-room/project/update",
        buildJsonObject {
            put("project_id", projectId)
            put("title", title.trim())
        },
    )

suspend fun JarvisAppSession.writingRoomProjectDelete(projectId: String): Result<Unit> =
    writingPost<WritingProjectDeleteAck>(
        "/api/app/writing-room/project/delete",
        buildJsonObject { put("project_id", projectId) },
    ).map { }

@Serializable
private data class WritingProjectDeleteAck(
    val schema: String = "",
    val project_id: String = "",
    val deleted: Boolean = false,
)

suspend fun JarvisAppSession.writingRoomLibraryExport(
    projectId: String,
    documentId: String,
    format: String,
): Result<WritingLibraryExport> =
    writingPost(
        "/api/app/writing-room/library/export",
        buildJsonObject {
            put("project_id", projectId)
            put("document_id", documentId)
            put("format", format)
        },
    )
