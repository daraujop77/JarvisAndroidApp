package com.jarvis.android.ui.home

import com.jarvis.android.contract.TaskStatus
import com.jarvis.android.data.local.ConversationListItem
import com.jarvis.android.data.projects.ProjectSummary
import com.jarvis.android.data.projects.ProjectsResult
import com.jarvis.android.data.state.ApprovalUiState
import com.jarvis.android.data.state.ConnectionState
import com.jarvis.android.data.state.DiagnosticEntry
import com.jarvis.android.data.state.RequestStatus
import com.jarvis.android.data.state.SessionState
import com.jarvis.android.data.state.TaskUiState
import com.jarvis.android.voice.VoicePhase

/** Server-catalog row. Built from `/api/app/status` when that fetch succeeded. */
data class ServerProfileEntry(
    val profile: String,
    val label: String,
    val model: String,
    val state: String,
)

/**
 * Active model/profile as the server catalog described it. [label] is null when
 * the catalog was not fetched — never a hardcoded product name.
 */
data class HomeProfile(
    val label: String?,
    val model: String?,
)

data class CompactDiagnostics(
    val sessionPhase: String,
    val protocolVersion: String?,
    val lastKind: DiagnosticEntry.Kind?,
    val activeRequestCount: Int,
)

sealed interface FixtureProjectsSection {
    data object Hidden : FixtureProjectsSection
    data object Loading : FixtureProjectsSection
    data object Empty : FixtureProjectsSection
    data object Unavailable : FixtureProjectsSection
    data class Fixtures(val projects: List<ProjectSummary>) : FixtureProjectsSection
}

data class HomeSnapshot(
    val visualState: JarvisVisualState,
    val connection: ConnectionState,
    val profile: HomeProfile,
    val runningTaskCount: Int,
    val pendingApprovalCount: Int?,
    val recentConversations: List<ConversationListItem>,
    val fixtureProjects: FixtureProjectsSection,
    val diagnostics: CompactDiagnostics,
)

/**
 * Pure Home / visual-state assembly from already-available local and session
 * fields. Missing server data stays unknown rather than invented.
 */
object HomeCommandCenter {

    const val RECENT_CONVERSATION_LIMIT = 5
    const val FIXTURE_PROJECT_LIMIT = 3

    fun visualState(
        bootShown: Boolean,
        connection: ConnectionState,
        voicePhase: VoicePhase,
        speaking: Boolean,
        streamingResponse: Boolean,
        acceptedPending: Boolean,
        failedRequest: Boolean,
        runningTaskCount: Int,
        ownerPendingApprovals: Int,
    ): JarvisVisualState {
        if (!bootShown) return JarvisVisualState.BOOT
        when (connection) {
            ConnectionState.AUTH_EXPIRED,
            ConnectionState.DEVICE_REVOKED,
            ConnectionState.PROTOCOL_MISMATCH,
            -> return JarvisVisualState.ERROR
            ConnectionState.OFFLINE,
            ConnectionState.DISCONNECTED,
            -> return JarvisVisualState.OFFLINE
            else -> Unit
        }
        if (voicePhase == VoicePhase.ERROR || failedRequest) return JarvisVisualState.ERROR
        if (voicePhase == VoicePhase.LISTENING || voicePhase == VoicePhase.REQUESTING_PERMISSION) {
            return JarvisVisualState.LISTENING
        }
        if (speaking || streamingResponse) return JarvisVisualState.RESPONDING
        if (voicePhase == VoicePhase.PROCESSING || acceptedPending) return JarvisVisualState.THINKING
        if (ownerPendingApprovals > 0) return JarvisVisualState.AWAITING_APPROVAL
        if (runningTaskCount > 0 ||
            connection == ConnectionState.CONNECTING ||
            connection == ConnectionState.RECONNECTING
        ) {
            return JarvisVisualState.EXECUTING
        }
        return JarvisVisualState.IDLE
    }

    fun runningTaskCount(tasks: Collection<TaskUiState>): Int =
        tasks.count {
            it.status == TaskStatus.RUNNING ||
                it.status == TaskStatus.STARTED ||
                it.status == TaskStatus.QUEUED
        }

    /**
     * Pending approvals are owner-only. Non-owners get null (unknown / hidden),
     * never a guest-visible count.
     */
    fun pendingApprovalCount(isOwner: Boolean, approvals: Collection<ApprovalUiState>): Int? {
        if (!isOwner) return null
        return approvals.count { it.outcome == null }
    }

    fun profileFromServer(
        catalog: List<ServerProfileEntry>?,
        selectedProfileId: String,
    ): HomeProfile {
        if (catalog.isNullOrEmpty()) return HomeProfile(label = null, model = null)
        val entry = catalog.firstOrNull { it.profile == selectedProfileId }
            ?: catalog.firstOrNull { it.state.equals("active", ignoreCase = true) }
            ?: return HomeProfile(label = null, model = null)
        val label = entry.label.trim().ifBlank { entry.profile.trim() }.ifBlank { null }
        val model = entry.model.trim().takeIf { it.isNotEmpty() && it != "—" }
        return HomeProfile(label = label, model = model)
    }

    fun compactDiagnostics(phaseName: String, session: SessionState): CompactDiagnostics =
        CompactDiagnostics(
            sessionPhase = phaseName,
            protocolVersion = session.negotiatedProtocolVersion.trim().takeIf { it.isNotEmpty() },
            lastKind = session.diagnostics.lastOrNull()?.kind,
            activeRequestCount = session.activeRequestCount,
        )

    fun recentConversations(
        items: List<ConversationListItem>,
        limit: Int = RECENT_CONVERSATION_LIMIT,
    ): List<ConversationListItem> = items.take(limit)

    fun fixtureProjects(debugBuild: Boolean, result: ProjectsResult): FixtureProjectsSection {
        if (!debugBuild) return FixtureProjectsSection.Hidden
        return when (result) {
            ProjectsResult.Loading -> FixtureProjectsSection.Loading
            ProjectsResult.Empty -> FixtureProjectsSection.Empty
            is ProjectsResult.Error -> FixtureProjectsSection.Unavailable
            is ProjectsResult.Loaded -> FixtureProjectsSection.Fixtures(
                result.projects
                    .sortedByDescending { it.updatedAtMs }
                    .take(FIXTURE_PROJECT_LIMIT),
            )
        }
    }

    fun snapshot(
        bootShown: Boolean,
        connection: ConnectionState,
        phaseName: String,
        session: SessionState,
        voicePhase: VoicePhase,
        speaking: Boolean,
        isOwner: Boolean,
        catalog: List<ServerProfileEntry>?,
        selectedProfileId: String,
        conversations: List<ConversationListItem>,
        debugBuild: Boolean,
        projects: ProjectsResult,
    ): HomeSnapshot {
        val running = runningTaskCount(session.tasks.values)
        val pending = pendingApprovalCount(isOwner, session.approvals.values)
        val streaming = session.requests.values.any { it.status is RequestStatus.Streaming }
        val acceptedPending = session.requests.values.any {
            it.status is RequestStatus.Pending ||
                it.status is RequestStatus.Accepted ||
                it.status is RequestStatus.Cancelling
        }
        val failed = session.requests.values.any { it.status is RequestStatus.Failed }
        return HomeSnapshot(
            visualState = visualState(
                bootShown = bootShown,
                connection = connection,
                voicePhase = voicePhase,
                speaking = speaking,
                streamingResponse = streaming,
                acceptedPending = acceptedPending,
                failedRequest = failed,
                runningTaskCount = running,
                ownerPendingApprovals = pending ?: 0,
            ),
            connection = connection,
            profile = profileFromServer(catalog, selectedProfileId),
            runningTaskCount = running,
            pendingApprovalCount = pending,
            recentConversations = recentConversations(conversations),
            fixtureProjects = fixtureProjects(debugBuild, projects),
            diagnostics = compactDiagnostics(phaseName, session),
        )
    }
}
