package com.jarvis.android.ui.home

import com.jarvis.android.contract.ApprovalTier
import com.jarvis.android.contract.ErrorEnvelope
import com.jarvis.android.contract.TaskStatus
import com.jarvis.android.data.local.ConversationListItem
import com.jarvis.android.data.projects.ProjectId
import com.jarvis.android.data.projects.ProjectState
import com.jarvis.android.data.projects.ProjectSummary
import com.jarvis.android.data.projects.ProjectsResult
import com.jarvis.android.data.state.ApprovalUiState
import com.jarvis.android.data.state.ConnectionState
import com.jarvis.android.data.state.DiagnosticEntry
import com.jarvis.android.data.state.RequestState
import com.jarvis.android.data.state.RequestStatus
import com.jarvis.android.data.state.SessionState
import com.jarvis.android.data.state.TaskUiState
import com.jarvis.android.ui.components.OrbActivity
import com.jarvis.android.ui.screens.HomeA11y
import com.jarvis.android.voice.VoicePhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeCommandCenterTest {

    @Test
    fun visualStatePriorityStaysUiOnly() {
        assertEquals(
            JarvisVisualState.BOOT,
            HomeCommandCenter.visualState(false, ConnectionState.ONLINE, VoicePhase.IDLE, false, false, false, false, 0, 0),
        )
        assertEquals(
            JarvisVisualState.LISTENING,
            HomeCommandCenter.visualState(true, ConnectionState.ONLINE, VoicePhase.LISTENING, false, false, false, false, 0, 0),
        )
        assertEquals(
            JarvisVisualState.RESPONDING,
            HomeCommandCenter.visualState(true, ConnectionState.ONLINE, VoicePhase.IDLE, true, false, false, false, 0, 0),
        )
        assertEquals(
            JarvisVisualState.RESPONDING,
            HomeCommandCenter.visualState(true, ConnectionState.ONLINE, VoicePhase.IDLE, false, true, false, false, 0, 0),
        )
        assertEquals(
            JarvisVisualState.THINKING,
            HomeCommandCenter.visualState(true, ConnectionState.ONLINE, VoicePhase.IDLE, false, false, true, false, 0, 0),
        )
        assertEquals(
            JarvisVisualState.OFFLINE,
            HomeCommandCenter.visualState(true, ConnectionState.OFFLINE, VoicePhase.IDLE, false, false, false, false, 0, 0),
        )
        assertEquals(
            JarvisVisualState.ERROR,
            HomeCommandCenter.visualState(true, ConnectionState.DEVICE_REVOKED, VoicePhase.IDLE, false, false, false, false, 0, 0),
        )
        assertEquals(
            JarvisVisualState.ERROR,
            HomeCommandCenter.visualState(true, ConnectionState.ONLINE, VoicePhase.IDLE, false, false, false, true, 0, 0),
        )
        assertEquals(OrbActivity.SPEAKING, JarvisVisualState.RESPONDING.toOrbActivity())
        assertEquals(OrbActivity.OFFLINE, JarvisVisualState.OFFLINE.toOrbActivity())
        assertEquals(OrbActivity.THINKING, JarvisVisualState.BOOT.toOrbActivity())
        assertEquals(OrbActivity.PROCESSING, JarvisVisualState.AWAITING_APPROVAL.toOrbActivity())
    }

    @Test
    fun ownerSeesPendingApprovalsGuestsDoNot() {
        val pending = ApprovalUiState("a", null, "Run", null, ApprovalTier.NORMAL, null, null)
        assertEquals(1, HomeCommandCenter.pendingApprovalCount(true, listOf(pending)))
        assertNull(HomeCommandCenter.pendingApprovalCount(false, listOf(pending)))
        assertEquals(
            JarvisVisualState.AWAITING_APPROVAL,
            HomeCommandCenter.visualState(true, ConnectionState.ONLINE, VoicePhase.IDLE, false, false, false, false, 0, 1),
        )
        assertEquals(
            JarvisVisualState.IDLE,
            HomeCommandCenter.visualState(true, ConnectionState.ONLINE, VoicePhase.IDLE, false, false, false, false, 0, 0),
        )
        assertEquals(
            JarvisVisualState.EXECUTING,
            HomeCommandCenter.visualState(true, ConnectionState.ONLINE, VoicePhase.IDLE, false, false, false, false, 2, 0),
        )
    }

    @Test
    fun profileLabelIsServerCatalogOnly() {
        val catalog = listOf(
            ServerProfileEntry("fast", "Fast", "qwen", "installed"),
            ServerProfileEntry("normal", "Normal", "gemma", "active"),
        )
        assertEquals("Normal", HomeCommandCenter.profileFromServer(catalog, "normal").label)
        assertEquals("gemma", HomeCommandCenter.profileFromServer(catalog, "normal").model)
        assertEquals("Normal", HomeCommandCenter.profileFromServer(catalog, "missing").label)
        assertNull(HomeCommandCenter.profileFromServer(null, "normal").label)
        assertNull(HomeCommandCenter.profileFromServer(emptyList(), "normal").label)
    }

    @Test
    fun fixturesStayLabeledAndHiddenOutsideDebug() {
        val loaded = ProjectsResult.Loaded(
            listOf(
                ProjectSummary(ProjectId("p1"), "Home", ProjectState.ACTIVE, 10),
                ProjectSummary(ProjectId("p2"), "Work", ProjectState.ACTIVE, 30),
                ProjectSummary(ProjectId("p3"), "Old", ProjectState.ARCHIVED, 20),
                ProjectSummary(ProjectId("p4"), "Extra", ProjectState.ACTIVE, 5),
            ),
        )
        val shown = HomeCommandCenter.fixtureProjects(debugBuild = true, loaded) as FixtureProjectsSection.Fixtures
        assertEquals(listOf("Work", "Old", "Home"), shown.projects.map { it.title })
        assertEquals(FixtureProjectsSection.Hidden, HomeCommandCenter.fixtureProjects(debugBuild = false, loaded))
        assertTrue(HomeA11y.PROJECTS_NOT_LIVE.contains("Not live"))
        val chats = (1..6).map { chat("c$it") }
        assertEquals(5, HomeCommandCenter.recentConversations(chats).size)
    }

    @Test
    fun diagnosticsKeepCountsNotSecrets() {
        val session = SessionState(
            connection = ConnectionState.ONLINE,
            negotiatedProtocolVersion = "web-v1",
            lastCursorToken = "secret-cursor",
            diagnostics = listOf(DiagnosticEntry(1L, DiagnosticEntry.Kind.PROTOCOL_MISMATCH, "version")),
            requests = mapOf("r" to RequestState("r", "c", "hi", RequestStatus.Accepted)),
        )
        val diag = HomeCommandCenter.compactDiagnostics("READY", session)
        assertEquals("web-v1", diag.protocolVersion)
        assertEquals(DiagnosticEntry.Kind.PROTOCOL_MISMATCH, diag.lastKind)
        assertEquals(1, diag.activeRequestCount)
        assertFalse(diag.toString().contains("secret-cursor"))
        val running = HomeCommandCenter.runningTaskCount(
            listOf(TaskUiState("t", null, TaskStatus.RUNNING, null, null, 1L)),
        )
        assertEquals(1, running)
        val failed = session.copy(
            requests = mapOf(
                "r" to RequestState("r", "c", "hi", RequestStatus.Failed(ErrorEnvelope("x", "y"), true)),
            ),
        )
        val snap = HomeCommandCenter.snapshot(
            bootShown = true,
            connection = ConnectionState.ONLINE,
            phaseName = "READY",
            session = failed,
            voicePhase = VoicePhase.IDLE,
            speaking = false,
            isOwner = true,
            catalog = null,
            selectedProfileId = "",
            conversations = emptyList(),
            debugBuild = false,
            projects = ProjectsResult.Empty,
        )
        assertEquals(JarvisVisualState.ERROR, snap.visualState)
        assertNull(snap.profile.label)
        assertEquals(FixtureProjectsSection.Hidden, snap.fixtureProjects)
    }

    private fun chat(id: String) = ConversationListItem(
        conversationId = id,
        storedTitle = id,
        displayTitle = id,
        updatedAtMs = 1L,
        lastOpenedAtMs = 0L,
        pinned = false,
        archived = false,
        locallyRenamed = false,
    )
}
