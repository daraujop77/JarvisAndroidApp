package com.jarvis.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jarvis.android.JarvisApp
import com.jarvis.android.contract.ApprovalOutcome
import com.jarvis.android.data.local.ConversationEntity
import com.jarvis.android.data.media.StagedAttachment
import com.jarvis.android.data.repo.ChatMessage
import com.jarvis.android.data.repo.ConversationSummary
import com.jarvis.android.data.repo.SessionSnapshot
import com.jarvis.android.di.AppContainer
import com.jarvis.android.transport.fake.FakeScenario
import com.jarvis.android.update.AppUpdateManager
import com.jarvis.android.update.AppUpdateState
import com.jarvis.android.transport.live.*
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Single app-level ViewModel (Lane B). Composables never touch transport logic —
 * they read flows and post commands (plan §8 Lane B: "no direct transport logic
 * in composables").
 */
@OptIn(ExperimentalCoroutinesApi::class)
class JarvisViewModel(private val app: JarvisApp) : ViewModel() {

    private val container = app.container

    private val session = container.session
    private val conversations = container.conversations

    private val _conversationId = MutableStateFlow<String?>(null)
    val conversationId: StateFlow<String?> = _conversationId

    val snapshot: StateFlow<SessionSnapshot> = session.snapshot

    val conversationList: StateFlow<List<ConversationSummary>> =
        conversations.observeConversations()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val conversationListState: StateFlow<com.jarvis.android.data.repo.ConversationListState> =
        conversations.observeConversationList()
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                com.jarvis.android.data.repo.ConversationListState.Loading,
            )

    val messages: StateFlow<List<ChatMessage>> = _conversationId
        .flatMapLatest { id ->
            if (id == null) kotlinx.coroutines.flow.flowOf(emptyList())
            else conversations.observeMessages(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val settings = container.settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), com.jarvis.android.data.prefs.SettingsStore.Settings())

    private val appUpdateManager = AppUpdateManager(app)
    val appUpdateState: StateFlow<AppUpdateState> = appUpdateManager.state

    fun checkForAppUpdate() = viewModelScope.launch {
        appUpdateManager.checkForUpdate()
    }

    fun downloadAppUpdate() = viewModelScope.launch {
        val current = appUpdateState.value
        if (current is AppUpdateState.Available) {
            appUpdateManager.downloadUpdate(current.manifest)
        }
    }

    fun requestAppUpdateInstallPermission() {
        appUpdateManager.requestInstallPermission()
    }

    fun installDownloadedAppUpdate() {
        when (val current = appUpdateState.value) {
            is AppUpdateState.ReadyToInstall ->
                appUpdateManager.installDownloaded(current.manifest, current.apk)
            is AppUpdateState.PermissionRequired ->
                appUpdateManager.installDownloaded(current.manifest, current.apk)
            else -> Unit
        }
    }

    fun openConversation(id: String) {
        _conversationId.value = id
    }

    // ---- AND-W9 Projects shell (Lane F: backend NOT_CONNECTED) ----------------

    private val _projectsEpoch = MutableStateFlow(0)
    val projects = _projectsEpoch
        .flatMapLatest { container.projectsRepository.observeProjects() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), com.jarvis.android.data.projects.ProjectsResult.Loading)

    fun refreshProjects() { _projectsEpoch.value += 1 }

    private val _projectsMessage = MutableStateFlow<String?>(null)
    val projectsMessage: StateFlow<String?> = _projectsMessage

    fun clearProjectsMessage() {
        _projectsMessage.value = null
    }

    fun createProject(title: String) = mutateProject { container.projectsRepository.createProject(title) }

    fun renameProject(projectId: com.jarvis.android.data.projects.ProjectId, title: String) =
        mutateProject { container.projectsRepository.renameProject(projectId, title) }

    fun deleteProject(projectId: com.jarvis.android.data.projects.ProjectId) =
        mutateProject { container.projectsRepository.deleteProject(projectId) }

    private fun mutateProject(block: suspend () -> Result<*>) {
        viewModelScope.launch {
            block().fold(
                onSuccess = {
                    _projectsMessage.value = null
                    refreshProjects()
                },
                onFailure = { error ->
                    _projectsMessage.value = error.message ?: "Project request failed"
                },
            )
        }
    }

    fun conversationsFor(projectId: com.jarvis.android.data.projects.ProjectId) =
        container.projectsRepository.conversationsFor(projectId)

    sealed interface WritingRoomState {
        data object Idle : WritingRoomState
        data class Running(val participant: String) : WritingRoomState
        data class Success(val turn: com.jarvis.android.transport.live.JarvisAppSession.WritingRoomTurn) : WritingRoomState
        data class Error(val message: String) : WritingRoomState
    }

    private val _writingRoomState = MutableStateFlow<WritingRoomState>(WritingRoomState.Idle)
    val writingRoomState: StateFlow<WritingRoomState> = _writingRoomState

    fun runWritingRoomTurn(
        projectId: String,
        projectTitle: String,
        participant: String,
        prompt: String,
    ) {
        val cleanPrompt = prompt.trim()
        if (cleanPrompt.isEmpty()) return
        _writingRoomState.value = WritingRoomState.Running(participant)
        viewModelScope.launch {
            val result = container.liveSession.writingRoomTurn(
                projectId = projectId,
                projectTitle = projectTitle,
                participant = participant,
                prompt = cleanPrompt,
            )
            _writingRoomState.value = result.fold(
                onSuccess = { WritingRoomState.Success(it) },
                onFailure = { WritingRoomState.Error(it.message ?: "Writing Room request failed") },
            )
        }
    }

    fun clearWritingRoomTurn() {
        _writingRoomState.value = WritingRoomState.Idle
    }

    data class WritingWorkspaceChatTurn(
        val prompt: String,
        val response: WritingRoomAutoChat? = null,
    )

    data class WritingWorkspaceState(
        val busy: Boolean = false,
        val busyLabel: String = "",
        val chatProjectId: String? = null,
        val overview: WritingRoomOverview? = null,
        val chat: WritingRoomAutoChat? = null,
        val chatHistory: List<WritingWorkspaceChatTurn> = emptyList(),
        val streamingText: String = "",
        val wikiHome: WritingWikiHome? = null,
        val wiki: WritingWikiSearch? = null,
        val wikiTimeline: WritingWikiTimeline? = null,
        val canonExplorer: WritingCanonExplorer? = null,
        val knowledgeCapabilities: KnowledgeCapabilitiesResponse? = null,
        val knowledgeTimelineV2: KnowledgeTimelineResponse? = null,
        val knowledgeGraphV2: KnowledgeGraphResponse? = null,
        val knowledgeSelectedNode: KnowledgeNodeResponse? = null,
        val knowledgeSelectedEdge: KnowledgeEdgeResponse? = null,
        val knowledgeResolvedSource: KnowledgeSourceResolveResponse? = null,
        val knowledgeAtlasLoading: Boolean = false,
        val knowledgeAtlasError: String? = null,
        val knowledgeSnapshotChanged: Boolean = false,
        val wikiCharacters: List<WritingWikiEntity> = emptyList(),
        val wikiLocations: List<WritingWikiEntity> = emptyList(),
        val plans: List<WritingPlanItem> = emptyList(),
        val planningCouncil: WritingPlanningCouncil? = null,
        val planningCouncilSessions: List<WritingPlanningCouncilSession> = emptyList(),
        val planningV2ChapterId: String? = null,
        val planningV2AggregateVersion: Int = 0,
        val planningV2Turns: List<WritingPlanningTurnItem> = emptyList(),
        val planningV2Direction: WritingDirectionStateResult? = null,
        val draftV2: WritingDraftExecutionResult? = null,
        val approvalV2: WritingApprovalStateResult? = null,
        val autoReviewV2: WritingChapterAutoReviewResult? = null,
        val chapters: List<WritingChapterSummary> = emptyList(),
        val activeChapter: WritingChapter? = null,
        val chapterRevisions: List<WritingChapterRevisionSummary> = emptyList(),
        val chapterRevisionChapterId: String? = null,
        val chapterTrash: List<WritingChapterTrashItem> = emptyList(),
        val engineReview: WritingEngineReviewEnvelope? = null,
        val library: WritingLibraryList? = null,
        val document: WritingLibraryDocument? = null,
        val pendingExport: WritingLibraryExport? = null,
        val exportMessage: String? = null,
        val error: String? = null,
    )

    private val _writingWorkspace = MutableStateFlow(WritingWorkspaceState())
    val writingWorkspace: StateFlow<WritingWorkspaceState> = _writingWorkspace

    private fun writingWorkspaceBusy(label: String) {
        _writingWorkspace.value = _writingWorkspace.value.copy(
            busy = true,
            busyLabel = label,
            error = null,
        )
    }

    private fun writingWorkspaceError(error: Throwable?) {
        _writingWorkspace.value = _writingWorkspace.value.copy(
            busy = false,
            busyLabel = "",
            error = error?.message ?: "Writing Room request failed",
        )
    }

    fun clearWritingWorkspaceError() {
        _writingWorkspace.value = _writingWorkspace.value.copy(error = null)
    }

    fun refreshWritingWorkspace(projectId: String) {
        if (_writingWorkspace.value.chatProjectId != projectId) {
            // Project changes are an authorization boundary for visual media.
            // Drop local thumbnails/candidates so protected bytes cannot bleed
            // into another project's UI even if asset ids happen to collide.
            resetVisualStudioProtectedMedia()
            _writingWorkspace.value = _writingWorkspace.value.copy(
                chatProjectId = projectId,
                chat = null,
                chatHistory = emptyList(),
                streamingText = "",
                knowledgeCapabilities = null,
                knowledgeTimelineV2 = null,
                knowledgeGraphV2 = null,
                knowledgeSelectedNode = null,
                knowledgeSelectedEdge = null,
                knowledgeResolvedSource = null,
                knowledgeAtlasLoading = false,
                knowledgeAtlasError = null,
                knowledgeSnapshotChanged = false,
                planningV2ChapterId = null,
                planningV2AggregateVersion = 0,
                planningV2Turns = emptyList(),
                planningV2Direction = null,
                draftV2 = null,
                approvalV2 = null,
            )
        }
        writingWorkspaceBusy("Loading workspace")
        viewModelScope.launch {
            val overview = container.liveSession.writingRoomOverview(projectId)
            val wikiHome = container.liveSession.writingRoomWikiHome(projectId)
            val wikiCharacters = container.liveSession.writingRoomWikiBrowse(
                projectId = projectId,
                entryType = "character",
                topK = 100,
            )
            val wikiLocations = container.liveSession.writingRoomWikiBrowse(
                projectId = projectId,
                entryType = "location",
                topK = 100,
            )
            val wikiTimeline = container.liveSession.writingRoomWikiTimeline(projectId)
            val canonExplorer = container.liveSession.writingRoomWikiExplorer(projectId)
            val plans = container.liveSession.writingRoomPlanList(projectId)
            val councilSessions = container.liveSession.writingRoomPlanningCouncilList(projectId)
            val chapters = container.liveSession.writingRoomChapterList(projectId)
            val library = container.liveSession.writingRoomLibraryList(projectId)
            val failure = listOf(
                overview, wikiHome, wikiCharacters, wikiLocations, wikiTimeline,
                canonExplorer, plans, councilSessions, chapters, library,
            ).firstOrNull { it.isFailure }
            if (failure != null) {
                writingWorkspaceError(failure.exceptionOrNull())
                return@launch
            }
            _writingWorkspace.value = _writingWorkspace.value.copy(
                busy = false,
                busyLabel = "",
                overview = overview.getOrNull(),
                wikiHome = wikiHome.getOrNull(),
                wikiTimeline = wikiTimeline.getOrNull(),
                canonExplorer = canonExplorer.getOrNull(),
                wikiCharacters = wikiCharacters.getOrNull()?.entries.orEmpty(),
                wikiLocations = wikiLocations.getOrNull()?.entries.orEmpty(),
                plans = plans.getOrNull()?.items.orEmpty(),
                planningCouncilSessions = councilSessions.getOrNull()?.items.orEmpty(),
                chapters = chapters.getOrNull()?.items.orEmpty(),
                library = library.getOrNull(),
                error = null,
            )
            refreshKnowledgeAtlas(projectId = projectId, refreshSnapshot = true)
        }
    }

    fun refreshKnowledgeAtlas(
        projectId: String,
        query: String = "",
        lane: String? = null,
        refreshSnapshot: Boolean = false,
    ) {
        val initial = _writingWorkspace.value
        _writingWorkspace.value = initial.copy(
            knowledgeAtlasLoading = true,
            knowledgeAtlasError = null,
        )
        viewModelScope.launch {
            val beforeSnapshot = _writingWorkspace.value.knowledgeCapabilities?.snapshot_id
            val capabilities = if (!refreshSnapshot && _writingWorkspace.value.knowledgeCapabilities != null) {
                _writingWorkspace.value.knowledgeCapabilities!!
            } else {
                container.liveSession.knowledgeCapabilities(projectId).getOrElse { error ->
                    if (_writingWorkspace.value.chatProjectId == projectId) {
                        _writingWorkspace.value = _writingWorkspace.value.copy(
                            knowledgeAtlasLoading = false,
                            knowledgeAtlasError = error.message ?: "Knowledge v2 no disponible; se mantiene Canon v46.",
                        )
                    }
                    return@launch
                }
            }
            val snapshotId = capabilities.snapshot_id
            if (snapshotId.isBlank()) {
                if (_writingWorkspace.value.chatProjectId == projectId) {
                    _writingWorkspace.value = _writingWorkspace.value.copy(
                        knowledgeAtlasLoading = false,
                        knowledgeAtlasError = "Knowledge v2 no tiene una snapshot activa.",
                    )
                }
                return@launch
            }
            val timeline = container.liveSession.knowledgeTimeline(
                projectId = projectId,
                snapshotId = snapshotId,
                query = query,
                lane = lane,
                limit = 200,
            )
            val graph = container.liveSession.knowledgeGraph(
                projectId = projectId,
                snapshotId = snapshotId,
                query = query,
                limit = 500,
            )
            if (_writingWorkspace.value.chatProjectId != projectId) return@launch
            val current = _writingWorkspace.value
            val failures = listOfNotNull(
                timeline.exceptionOrNull()?.message,
                graph.exceptionOrNull()?.message,
            )
            _writingWorkspace.value = current.copy(
                knowledgeCapabilities = capabilities,
                knowledgeTimelineV2 = timeline.getOrNull() ?: current.knowledgeTimelineV2,
                knowledgeGraphV2 = graph.getOrNull() ?: current.knowledgeGraphV2,
                knowledgeSelectedNode = if (beforeSnapshot != null && beforeSnapshot != snapshotId) null else current.knowledgeSelectedNode,
                knowledgeSelectedEdge = if (beforeSnapshot != null && beforeSnapshot != snapshotId) null else current.knowledgeSelectedEdge,
                knowledgeResolvedSource = if (beforeSnapshot != null && beforeSnapshot != snapshotId) null else current.knowledgeResolvedSource,
                knowledgeAtlasLoading = false,
                knowledgeAtlasError = failures.takeIf { it.isNotEmpty() }
                    ?.joinToString(prefix = "Atlas parcialmente disponible: ", separator = " | "),
                knowledgeSnapshotChanged = beforeSnapshot != null && beforeSnapshot != snapshotId,
            )
        }
    }

    fun focusKnowledgeNode(projectId: String, nodeId: String) {
        val clean = nodeId.trim()
        val snapshotId = _writingWorkspace.value.knowledgeCapabilities?.snapshot_id.orEmpty()
        if (clean.isEmpty() || snapshotId.isEmpty()) return
        _writingWorkspace.value = _writingWorkspace.value.copy(
            knowledgeAtlasLoading = true,
            knowledgeAtlasError = null,
            knowledgeSelectedEdge = null,
            knowledgeResolvedSource = null,
        )
        viewModelScope.launch {
            val node = container.liveSession.knowledgeNode(
                projectId = projectId,
                nodeId = clean,
                snapshotId = snapshotId,
            )
            val graph = container.liveSession.knowledgeGraph(
                projectId = projectId,
                snapshotId = snapshotId,
                nodeId = clean,
                limit = 500,
            )
            if (_writingWorkspace.value.chatProjectId != projectId) return@launch
            val current = _writingWorkspace.value
            _writingWorkspace.value = current.copy(
                knowledgeSelectedNode = node.getOrNull() ?: current.knowledgeSelectedNode,
                knowledgeSelectedEdge = null,
                knowledgeGraphV2 = graph.getOrNull() ?: current.knowledgeGraphV2,
                knowledgeAtlasLoading = false,
                knowledgeAtlasError = node.exceptionOrNull()?.message ?: graph.exceptionOrNull()?.message,
            )
        }
    }

    fun focusKnowledgeEdge(projectId: String, edgeId: String) {
        val clean = edgeId.trim()
        val snapshotId = _writingWorkspace.value.knowledgeCapabilities?.snapshot_id.orEmpty()
        if (clean.isEmpty() || snapshotId.isEmpty()) return
        _writingWorkspace.value = _writingWorkspace.value.copy(
            knowledgeAtlasLoading = true,
            knowledgeAtlasError = null,
            knowledgeResolvedSource = null,
        )
        viewModelScope.launch {
            val edge = container.liveSession.knowledgeEdge(
                projectId = projectId,
                edgeId = clean,
                snapshotId = snapshotId,
            )
            if (_writingWorkspace.value.chatProjectId != projectId) return@launch
            _writingWorkspace.value = _writingWorkspace.value.copy(
                knowledgeSelectedEdge = edge.getOrNull(),
                knowledgeAtlasLoading = false,
                knowledgeAtlasError = edge.exceptionOrNull()?.message,
            )
        }
    }

    fun resolveKnowledgeEvidence(projectId: String, evidenceId: String) {
        val clean = evidenceId.trim()
        val snapshotId = _writingWorkspace.value.knowledgeCapabilities?.snapshot_id.orEmpty()
        if (clean.isEmpty() || snapshotId.isEmpty()) return
        _writingWorkspace.value = _writingWorkspace.value.copy(
            knowledgeAtlasLoading = true,
            knowledgeAtlasError = null,
        )
        viewModelScope.launch {
            val source = container.liveSession.knowledgeSource(
                projectId = projectId,
                evidenceId = clean,
                snapshotId = snapshotId,
            )
            if (_writingWorkspace.value.chatProjectId != projectId) return@launch
            _writingWorkspace.value = _writingWorkspace.value.copy(
                knowledgeResolvedSource = source.getOrNull(),
                knowledgeAtlasLoading = false,
                knowledgeAtlasError = source.exceptionOrNull()?.message,
            )
        }
    }

    fun clearKnowledgeSelection() {
        _writingWorkspace.value = _writingWorkspace.value.copy(
            knowledgeSelectedNode = null,
            knowledgeSelectedEdge = null,
            knowledgeResolvedSource = null,
        )
    }

    fun runWritingRoomAutoChat(
        projectId: String,
        projectTitle: String,
        prompt: String,
        room: String = "chat",
    ) {
        val clean = prompt.trim()
        if (clean.isEmpty()) return

        val current = _writingWorkspace.value
        val existingHistory = if (current.chatProjectId == projectId) current.chatHistory else emptyList()
        _writingWorkspace.value = current.copy(
            busy = true,
            busyLabel = "JARVIS is routing the Writing Room task",
            chatProjectId = projectId,
            chat = null,
            chatHistory = existingHistory + WritingWorkspaceChatTurn(prompt = clean),
            streamingText = "",
            error = null,
        )

        viewModelScope.launch {
            val result = container.liveSession.writingRoomAutoChatStream(
                projectId = projectId,
                projectTitle = projectTitle,
                prompt = clean,
                room = room,
                onDelta = { delta ->
                    val state = _writingWorkspace.value
                    if (state.chatProjectId == projectId) {
                        _writingWorkspace.value = state.copy(
                            streamingText = state.streamingText + delta,
                            busyLabel = "Streaming response",
                        )
                    }
                },
            )
            result.fold(
                onSuccess = { response ->
                    val state = _writingWorkspace.value
                    if (state.chatProjectId == projectId) {
                        val updatedHistory = state.chatHistory.toMutableList()
                        val pendingIndex = updatedHistory.indexOfLast { it.response == null }
                        if (pendingIndex >= 0) {
                            updatedHistory[pendingIndex] = updatedHistory[pendingIndex].copy(response = response)
                        } else {
                            updatedHistory += WritingWorkspaceChatTurn(prompt = clean, response = response)
                        }
                        _writingWorkspace.value = state.copy(
                            busy = false,
                            busyLabel = "",
                            chat = response,
                            chatHistory = updatedHistory,
                            streamingText = "",
                            error = null,
                        )
                    }
                },
                onFailure = { error ->
                    val state = _writingWorkspace.value
                    if (state.chatProjectId == projectId) {
                        val partial = state.streamingText
                        _writingWorkspace.value = state.copy(
                            busy = false,
                            busyLabel = "",
                            streamingText = partial,
                            error = error.message ?: "Writing Room request failed",
                        )
                    }
                },
            )
        }
    }

    fun searchWritingWiki(projectId: String, query: String) {
        val clean = query.trim()
        if (clean.isEmpty()) return
        writingWorkspaceBusy("Searching canon")
        viewModelScope.launch {
            val result = container.liveSession.writingRoomWikiSearch(projectId, clean)
            result.fold(
                onSuccess = {
                    _writingWorkspace.value = _writingWorkspace.value.copy(
                        busy = false,
                        busyLabel = "",
                        wiki = it,
                        error = null,
                    )
                },
                onFailure = ::writingWorkspaceError,
            )
        }
    }

    fun clearWritingWikiSearch() {
        _writingWorkspace.value = _writingWorkspace.value.copy(wiki = null)
    }

    private fun planningCouncilNeedsArchitect(text: String): Boolean {
        val lowered = text.lowercase()
        return listOf(
            "arco", "varios capítulos", "varios capitulos", "más adelante", "mas adelante",
            "revelación", "revelacion", "foreshadow", "presagio", "setup", "payoff",
            "temporada", "saga", "largo plazo", "consecuencia futura", "consecuencias futuras",
        ).any(lowered::contains)
    }

    private suspend fun runPlanningCouncilRole(
        projectId: String,
        sessionId: String,
        participant: String,
        label: String,
        phase: String = "discussion",
    ): Result<WritingPlanningCouncil> {
        _writingWorkspace.value = _writingWorkspace.value.copy(
            busy = true,
            busyLabel = label,
            error = null,
        )
        val result = container.liveSession.writingRoomPlanningCouncilTurn(
            projectId = projectId,
            sessionId = sessionId,
            participant = participant,
            phase = phase,
        )
        if (result.isFailure) return Result.failure(result.exceptionOrNull()!!)
        val turn = result.getOrThrow()
        val council = WritingPlanningCouncil(
            schema = turn.schema,
            project_id = turn.project_id,
            session = turn.session,
            messages = turn.messages,
            needs_story_architect = _writingWorkspace.value.planningCouncil?.needs_story_architect ?: false,
        )
        _writingWorkspace.value = _writingWorkspace.value.copy(
            planningCouncil = council,
            busy = true,
            busyLabel = label,
            error = null,
        )
        return Result.success(council)
    }

    private suspend fun runPlanningCouncilReview(
        projectId: String,
        sessionId: String,
        seedText: String,
        includeArchitect: Boolean,
    ): Result<WritingPlanningCouncil> {
        var latest = _writingWorkspace.value.planningCouncil
        for ((participant, label) in listOf(
            "lore_keeper" to "LORE KEEPER · VERIFICANDO CANON",
            "challenger" to "CHALLENGER · PONIENDO A PRUEBA LA DIRECCIÓN",
        )) {
            val result = runPlanningCouncilRole(projectId, sessionId, participant, label)
            if (result.isFailure) return result
            latest = result.getOrNull()
        }

        if (includeArchitect || planningCouncilNeedsArchitect(seedText)) {
            val architect = runPlanningCouncilRole(
                projectId,
                sessionId,
                "story_architect",
                "STORY ARCHITECT · REVISANDO IMPACTO A LARGO PLAZO",
            )
            if (architect.isFailure) return architect
            latest = architect.getOrNull()
        }

        val synthesis = runPlanningCouncilRole(
            projectId,
            sessionId,
            "showrunner",
            "SHOWRUNNER · CONSOLIDANDO EL CONSEJO",
            "synthesis",
        )
        if (synthesis.isFailure) return synthesis
        latest = synthesis.getOrNull()

        return Result.success(requireNotNull(latest))
    }

    fun startPlanningCouncil(projectId: String, title: String, prompt: String) {
        val clean = prompt.trim()
        if (clean.isEmpty()) return
        writingWorkspaceBusy("ABRIENDO SALA DE PLANIFICACIÓN")
        viewModelScope.launch {
            val started = container.liveSession.writingRoomPlanningCouncilStart(
                projectId = projectId,
                prompt = clean,
                title = title,
            )
            if (started.isFailure) {
                writingWorkspaceError(started.exceptionOrNull())
                return@launch
            }
            val initial = started.getOrThrow()
            _writingWorkspace.value = _writingWorkspace.value.copy(
                planningCouncil = initial,
                busy = true,
                busyLabel = "SHOWRUNNER · EXPLORANDO DIRECCIÓN",
                error = null,
            )

            val showrunner = runPlanningCouncilRole(
                projectId = projectId,
                sessionId = initial.session.session_id,
                participant = "showrunner",
                label = "SHOWRUNNER · EXPLORANDO DIRECCIÓN",
            )
            if (showrunner.isFailure) {
                writingWorkspaceError(showrunner.exceptionOrNull())
                return@launch
            }

            val sessions = container.liveSession.writingRoomPlanningCouncilList(projectId)
            _writingWorkspace.value = _writingWorkspace.value.copy(
                planningCouncil = showrunner.getOrThrow(),
                planningCouncilSessions = sessions.getOrNull()?.items ?: _writingWorkspace.value.planningCouncilSessions,
                busy = false,
                busyLabel = "",
                error = sessions.exceptionOrNull()?.message,
            )
        }
    }

    fun continuePlanningCouncil(projectId: String, message: String) {
        val council = _writingWorkspace.value.planningCouncil ?: return
        val clean = message.trim()
        if (clean.isEmpty()) return
        val sessionId = council.session.session_id
        writingWorkspaceBusy("AÑADIENDO TU DECISIÓN AL CONSEJO")
        viewModelScope.launch {
            val appended = container.liveSession.writingRoomPlanningCouncilMessage(
                projectId = projectId,
                sessionId = sessionId,
                message = clean,
            )
            if (appended.isFailure) {
                writingWorkspaceError(appended.exceptionOrNull())
                return@launch
            }
            _writingWorkspace.value = _writingWorkspace.value.copy(
                planningCouncil = appended.getOrThrow(),
                busy = true,
                busyLabel = "SHOWRUNNER · RESPONDIENDO A TU DECISIÓN",
                error = null,
            )
            val showrunner = runPlanningCouncilRole(
                projectId = projectId,
                sessionId = sessionId,
                participant = "showrunner",
                label = "SHOWRUNNER · RESPONDIENDO A TU DECISIÓN",
            )
            if (showrunner.isFailure) {
                writingWorkspaceError(showrunner.exceptionOrNull())
                return@launch
            }
            _writingWorkspace.value = _writingWorkspace.value.copy(
                planningCouncil = showrunner.getOrThrow(),
                busy = false,
                busyLabel = "",
                error = null,
            )
        }
    }

    fun deepenPlanningCouncil(projectId: String) {
        val council = _writingWorkspace.value.planningCouncil ?: return
        val sessionId = council.session.session_id
        val seedText = buildString {
            append(council.session.seed_prompt)
            council.messages
                .filter { it.role == "user" }
                .takeLast(2)
                .forEach {
                    append("\n")
                    append(it.body)
                }
        }
        writingWorkspaceBusy("BAJANDO AL CONSEJO")
        viewModelScope.launch {
            val review = runPlanningCouncilReview(
                projectId = projectId,
                sessionId = sessionId,
                seedText = seedText,
                includeArchitect = council.needs_story_architect,
            )
            if (review.isFailure) {
                writingWorkspaceError(review.exceptionOrNull())
                return@launch
            }
            _writingWorkspace.value = _writingWorkspace.value.copy(
                planningCouncil = review.getOrThrow(),
                busy = false,
                busyLabel = "",
                error = null,
            )
        }
    }

    fun clearPlanningCouncil() {
        _writingWorkspace.value = _writingWorkspace.value.copy(
            planningCouncil = null,
            error = null,
        )
    }

    fun openPlanningCouncil(projectId: String, sessionId: String) {
        writingWorkspaceBusy("CARGANDO SALA DE PLANIFICACIÓN")
        viewModelScope.launch {
            val result = container.liveSession.writingRoomPlanningCouncilGet(projectId, sessionId)
            result.fold(
                onSuccess = {
                    _writingWorkspace.value = _writingWorkspace.value.copy(
                        planningCouncil = it,
                        busy = false,
                        busyLabel = "",
                        error = null,
                    )
                },
                onFailure = ::writingWorkspaceError,
            )
        }
    }

    fun savePlanningCouncilIdea(projectId: String, messageId: String) {
        val council = _writingWorkspace.value.planningCouncil ?: return
        writingWorkspaceBusy("GUARDANDO IDEA COMO PROPUESTA")
        viewModelScope.launch {
            val saved = container.liveSession.writingRoomPlanningCouncilSaveIdea(
                projectId = projectId,
                sessionId = council.session.session_id,
                messageId = messageId,
            )
            if (saved.isFailure) {
                writingWorkspaceError(saved.exceptionOrNull())
                return@launch
            }
            val plans = container.liveSession.writingRoomPlanList(projectId)
            val refreshed = container.liveSession.writingRoomPlanningCouncilGet(projectId, council.session.session_id)
            _writingWorkspace.value = _writingWorkspace.value.copy(
                plans = plans.getOrNull()?.items ?: _writingWorkspace.value.plans,
                planningCouncil = refreshed.getOrNull() ?: _writingWorkspace.value.planningCouncil,
                busy = false,
                busyLabel = "",
                error = plans.exceptionOrNull()?.message ?: refreshed.exceptionOrNull()?.message,
            )
        }
    }

    fun createWritingPlan(projectId: String, title: String, body: String) {
        val clean = body.trim()
        if (clean.isEmpty()) return
        writingWorkspaceBusy("Saving plan")
        viewModelScope.launch {
            val created = container.liveSession.writingRoomPlanCreate(projectId, title, clean)
            if (created.isFailure) {
                writingWorkspaceError(created.exceptionOrNull())
                return@launch
            }
            val list = container.liveSession.writingRoomPlanList(projectId)
            list.fold(
                onSuccess = {
                    _writingWorkspace.value = _writingWorkspace.value.copy(
                        busy = false,
                        busyLabel = "",
                        plans = it.items,
                        error = null,
                    )
                },
                onFailure = ::writingWorkspaceError,
            )
        }
    }

    fun setWritingPlanStatus(projectId: String, itemId: String, status: String) {
        writingWorkspaceBusy("Updating plan authority")
        viewModelScope.launch {
            val result = container.liveSession.writingRoomPlanStatus(projectId, itemId, status)
            result.fold(
                onSuccess = { response ->
                    _writingWorkspace.value = _writingWorkspace.value.copy(
                        busy = false,
                        busyLabel = "",
                        plans = _writingWorkspace.value.plans.map {
                            if (it.item_id == response.item.item_id) response.item else it
                        },
                        error = null,
                    )
                },
                onFailure = ::writingWorkspaceError,
            )
        }
    }


    fun startPersistentWritingPlanning(projectId: String, firstMessage: String) {
        val clean = firstMessage.trim()
        if (clean.isEmpty()) return
        writingWorkspaceBusy("SHOWRUNNER · ABRIENDO PLANEACIÓN")
        viewModelScope.launch {
            val startKey = "android-w1-start-" + java.util.UUID.randomUUID().toString()
            val started = container.liveSession.writingRoomPlanningSessionStart(
                projectId = projectId,
                idempotencyKey = startKey,
            )
            if (started.isFailure) {
                writingWorkspaceError(started.exceptionOrNull())
                return@launch
            }

            val session = started.getOrThrow().result
            val chapterResult = container.liveSession.writingRoomChapterGet(
                projectId,
                session.chapter_id,
            )
            val provisionalChapter = chapterResult.getOrNull()?.chapter
            _writingWorkspace.value = _writingWorkspace.value.copy(
                planningV2ChapterId = session.chapter_id,
                planningV2AggregateVersion = session.aggregate_version,
                planningV2Turns = emptyList(),
                planningV2Direction = null,
                activeChapter = provisionalChapter ?: _writingWorkspace.value.activeChapter,
                engineReview = null,
                busy = true,
                busyLabel = "SHOWRUNNER · EXPLORANDO DIRECCIÓN",
                error = chapterResult.exceptionOrNull()?.message,
            )

            val turn = container.liveSession.writingRoomPlanningTurn(
                projectId = projectId,
                chapterId = session.chapter_id,
                expectedVersion = session.aggregate_version,
                idempotencyKey = "android-w1-turn-" + java.util.UUID.randomUUID().toString(),
                message = clean,
            )
            if (turn.isFailure) {
                _writingWorkspace.value = _writingWorkspace.value.copy(
                    busy = false,
                    busyLabel = "",
                    error = turn.exceptionOrNull()?.message ?: "No se pudo iniciar la conversación con Showrunner",
                )
                return@launch
            }

            val history = container.liveSession.writingRoomPlanningHistory(
                projectId,
                session.chapter_id,
            )
            val direction = container.liveSession.writingRoomDirectionStatus(
                projectId,
                session.chapter_id,
            )
            val list = container.liveSession.writingRoomChapterList(projectId)
            val historyResult = history.getOrNull()?.result
            _writingWorkspace.value = _writingWorkspace.value.copy(
                busy = false,
                busyLabel = "",
                planningV2ChapterId = session.chapter_id,
                planningV2AggregateVersion = historyResult?.aggregate_version
                    ?: turn.getOrThrow().result.aggregate_version,
                planningV2Turns = historyResult?.items ?: listOf(turn.getOrThrow().result),
                planningV2Direction = direction.getOrNull()?.result,
                chapters = list.getOrNull()?.items ?: _writingWorkspace.value.chapters,
                error = history.exceptionOrNull()?.message
                    ?: direction.exceptionOrNull()?.message
                    ?: list.exceptionOrNull()?.message,
            )
        }
    }

    fun continuePersistentWritingPlanning(projectId: String, message: String) {
        val clean = message.trim()
        val current = _writingWorkspace.value
        val chapterId = current.planningV2ChapterId ?: current.activeChapter?.chapter_id ?: return
        val expectedVersion = current.planningV2AggregateVersion
        if (clean.isEmpty() || expectedVersion < 1) return

        writingWorkspaceBusy("SHOWRUNNER · CONTINUANDO PLANEACIÓN")
        viewModelScope.launch {
            val turn = container.liveSession.writingRoomPlanningTurn(
                projectId = projectId,
                chapterId = chapterId,
                expectedVersion = expectedVersion,
                idempotencyKey = "android-w1-turn-" + java.util.UUID.randomUUID().toString(),
                message = clean,
            )
            if (turn.isFailure) {
                writingWorkspaceError(turn.exceptionOrNull())
                return@launch
            }
            val history = container.liveSession.writingRoomPlanningHistory(projectId, chapterId)
            val direction = container.liveSession.writingRoomDirectionStatus(projectId, chapterId)
            val historyResult = history.getOrNull()?.result
            _writingWorkspace.value = _writingWorkspace.value.copy(
                busy = false,
                busyLabel = "",
                planningV2ChapterId = chapterId,
                planningV2AggregateVersion = historyResult?.aggregate_version
                    ?: turn.getOrThrow().result.aggregate_version,
                planningV2Turns = historyResult?.items
                    ?: (_writingWorkspace.value.planningV2Turns + turn.getOrThrow().result),
                planningV2Direction = direction.getOrNull()?.result
                    ?: _writingWorkspace.value.planningV2Direction,
                error = history.exceptionOrNull()?.message ?: direction.exceptionOrNull()?.message,
            )
        }
    }

    fun preparePersistentWritingDirection(projectId: String) {
        val current = _writingWorkspace.value
        val chapterId = current.planningV2ChapterId ?: return
        val expectedVersion = current.planningV2AggregateVersion
        if (expectedVersion < 1 || current.planningV2Turns.none { it.assistant_message.isNotBlank() }) return

        writingWorkspaceBusy("REVISANDO DIRECCIÓN · CANON KEEPER + CHALLENGER")
        viewModelScope.launch {
            val prepared = container.liveSession.writingRoomDirectionPrepare(
                projectId = projectId,
                chapterId = chapterId,
                expectedVersion = expectedVersion,
                idempotencyKey = "android-w1-direction-" + java.util.UUID.randomUUID().toString(),
            )
            prepared.fold(
                onSuccess = { response ->
                    _writingWorkspace.value = _writingWorkspace.value.copy(
                        busy = false,
                        busyLabel = "",
                        planningV2AggregateVersion = response.result.aggregate_version,
                        planningV2Direction = response.result,
                        error = null,
                    )
                },
                onFailure = ::writingWorkspaceError,
            )
        }
    }

    fun approvePersistentWritingDirection(
        projectId: String,
        selectedTitle: String,
    ) {
        val current = _writingWorkspace.value
        val chapterId = current.planningV2ChapterId ?: return
        val direction = current.planningV2Direction ?: return
        val proposal = direction.proposal ?: return
        val title = selectedTitle.trim()
        if (
            !direction.approval_ready ||
            title.isEmpty() ||
            title !in proposal.payload.title_options
        ) return

        writingWorkspaceBusy("APROBANDO BRIEF INMUTABLE")
        viewModelScope.launch {
            val approved = container.liveSession.writingRoomDirectionApprove(
                projectId = projectId,
                chapterId = chapterId,
                expectedVersion = current.planningV2AggregateVersion,
                idempotencyKey = "android-w1-approve-" + java.util.UUID.randomUUID().toString(),
                proposalRevisionId = proposal.proposal_revision_id,
                proposalHash = proposal.proposal_hash,
                reviewIds = direction.bound_review_ids,
                selectedTitle = title,
            )
            if (approved.isFailure) {
                writingWorkspaceError(approved.exceptionOrNull())
                return@launch
            }
            val result = approved.getOrThrow().result
            _writingWorkspace.value = _writingWorkspace.value.copy(
                busy = true,
                busyLabel = "WRITER · REDACTANDO CAPÍTULO",
                planningV2AggregateVersion = result.aggregate_version,
                planningV2Direction = result,
                draftV2 = null,
                approvalV2 = null,
                error = null,
            )
            val drafted = container.liveSession.writingRoomDraftRun(
                projectId = projectId,
                chapterId = chapterId,
            )
            if (drafted.isFailure) {
                writingWorkspaceError(drafted.exceptionOrNull())
                return@launch
            }
            val draftResult = drafted.getOrThrow().result
            val approval = container.liveSession.writingRoomApprovalStatus(projectId, chapterId)
            val revisions = container.liveSession.writingRoomChapterRevisions(projectId, chapterId)
            val list = container.liveSession.writingRoomChapterList(projectId)
            _writingWorkspace.value = _writingWorkspace.value.copy(
                busy = false,
                busyLabel = "",
                planningV2AggregateVersion = draftResult.aggregate.version,
                planningV2Direction = draftResult.direction,
                draftV2 = draftResult,
                approvalV2 = approval.getOrNull()?.result,
                activeChapter = draftResult.chapter,
                chapterRevisions = revisions.getOrNull()?.items.orEmpty(),
                chapterRevisionChapterId = chapterId,
                chapters = list.getOrNull()?.items ?: _writingWorkspace.value.chapters,
                error = approval.exceptionOrNull()?.message
                    ?: revisions.exceptionOrNull()?.message
                    ?: list.exceptionOrNull()?.message,
            )
        }
    }

    private suspend fun restorePersistentPlanningIfPresent(
        projectId: String,
        chapterId: String,
    ) {
        val history = container.liveSession.writingRoomPlanningHistory(
            projectId = projectId,
            chapterId = chapterId,
            limit = 100,
        )
        val historyResult = history.getOrNull()?.result
        if (historyResult == null || historyResult.items.isEmpty()) {
            if (_writingWorkspace.value.planningV2ChapterId == chapterId) {
                _writingWorkspace.value = _writingWorkspace.value.copy(
                    planningV2ChapterId = null,
                    planningV2AggregateVersion = 0,
                    planningV2Turns = emptyList(),
                    planningV2Direction = null,
                )
            }
            return
        }
        val direction = container.liveSession.writingRoomDirectionStatus(projectId, chapterId)
        _writingWorkspace.value = _writingWorkspace.value.copy(
            planningV2ChapterId = chapterId,
            planningV2AggregateVersion = historyResult.aggregate_version,
            planningV2Turns = historyResult.items,
            planningV2Direction = direction.getOrNull()?.result,
            error = direction.exceptionOrNull()?.message,
        )
    }


    private suspend fun restorePersistentDraftIfPresent(
        projectId: String,
        chapterId: String,
    ) {
        val draft = container.liveSession.writingRoomDraftStatus(projectId, chapterId)
        if (draft.isFailure) {
            if (_writingWorkspace.value.draftV2?.chapter_id == chapterId) {
                _writingWorkspace.value = _writingWorkspace.value.copy(
                    draftV2 = null,
                    approvalV2 = null,
                )
            }
            return
        }
        val draftResult = draft.getOrThrow().result
        val approval = container.liveSession.writingRoomApprovalStatus(projectId, chapterId)
        _writingWorkspace.value = _writingWorkspace.value.copy(
            planningV2AggregateVersion = maxOf(
                _writingWorkspace.value.planningV2AggregateVersion,
                draftResult.aggregate.version,
            ),
            planningV2Direction = draftResult.direction,
            draftV2 = draftResult,
            approvalV2 = approval.getOrNull()?.result,
            activeChapter = draftResult.chapter,
            error = approval.exceptionOrNull()?.message,
        )
    }

    fun runPersistentWritingDraft(projectId: String) {
        val current = _writingWorkspace.value
        val chapterId = current.planningV2ChapterId ?: current.activeChapter?.chapter_id ?: return
        writingWorkspaceBusy("WRITER · REDACTANDO CAPÍTULO")
        viewModelScope.launch {
            val drafted = container.liveSession.writingRoomDraftRun(projectId, chapterId)
            if (drafted.isFailure) {
                writingWorkspaceError(drafted.exceptionOrNull())
                return@launch
            }
            val result = drafted.getOrThrow().result
            val approval = container.liveSession.writingRoomApprovalStatus(projectId, chapterId)
            val revisions = container.liveSession.writingRoomChapterRevisions(projectId, chapterId)
            _writingWorkspace.value = _writingWorkspace.value.copy(
                busy = false,
                busyLabel = "",
                planningV2AggregateVersion = result.aggregate.version,
                planningV2Direction = result.direction,
                draftV2 = result,
                approvalV2 = approval.getOrNull()?.result,
                activeChapter = result.chapter,
                chapterRevisions = revisions.getOrNull()?.items.orEmpty(),
                chapterRevisionChapterId = chapterId,
                error = approval.exceptionOrNull()?.message ?: revisions.exceptionOrNull()?.message,
            )
        }
    }

    fun reviewPersistentWritingDraft(projectId: String) {
        val current = _writingWorkspace.value
        val chapterId = current.draftV2?.chapter_id ?: current.activeChapter?.chapter_id ?: return
        writingWorkspaceBusy("REVIEWER + CANON KEEPER · REVISANDO")
        viewModelScope.launch {
            val reviewed = container.liveSession.writingRoomDraftReviewRun(projectId, chapterId)
            if (reviewed.isFailure) {
                writingWorkspaceError(reviewed.exceptionOrNull())
                return@launch
            }
            val result = reviewed.getOrThrow().result
            val approval = container.liveSession.writingRoomApprovalStatus(projectId, chapterId)
            _writingWorkspace.value = _writingWorkspace.value.copy(
                busy = false,
                busyLabel = "",
                planningV2AggregateVersion = result.aggregate.version,
                planningV2Direction = result.direction,
                draftV2 = result,
                approvalV2 = approval.getOrNull()?.result,
                activeChapter = result.chapter,
                error = approval.exceptionOrNull()?.message,
            )
        }
    }

    fun savePersistentWritingRevision(projectId: String, draftText: String) {
        val current = _writingWorkspace.value
        val draftState = current.draftV2 ?: return
        val chapterId = draftState.chapter_id
        if (draftText.isBlank() || draftText == draftState.chapter.draft_text) return
        writingWorkspaceBusy("GUARDANDO REVISIÓN INMUTABLE")
        viewModelScope.launch {
            val revised = container.liveSession.writingRoomDraftRevise(
                projectId = projectId,
                chapterId = chapterId,
                expectedVersion = draftState.aggregate.version,
                idempotencyKey = "android-w2-revise-" + java.util.UUID.randomUUID().toString(),
                draftText = draftText,
            )
            if (revised.isFailure) {
                writingWorkspaceError(revised.exceptionOrNull())
                return@launch
            }
            val result = revised.getOrThrow().result
            val revisions = container.liveSession.writingRoomChapterRevisions(projectId, chapterId)
            _writingWorkspace.value = _writingWorkspace.value.copy(
                busy = false,
                busyLabel = "",
                planningV2AggregateVersion = result.aggregate.version,
                planningV2Direction = result.direction,
                draftV2 = result,
                approvalV2 = null,
                activeChapter = result.chapter,
                chapterRevisions = revisions.getOrNull()?.items.orEmpty(),
                chapterRevisionChapterId = chapterId,
                error = revisions.exceptionOrNull()?.message,
            )
        }
    }

    fun restorePersistentWritingRevision(projectId: String, revisionId: String) {
        val current = _writingWorkspace.value
        val draftState = current.draftV2 ?: return
        val chapterId = draftState.chapter_id
        writingWorkspaceBusy("RESTAURANDO COMO NUEVA REVISIÓN")
        viewModelScope.launch {
            val restored = container.liveSession.writingRoomDraftRestore(
                projectId = projectId,
                chapterId = chapterId,
                expectedVersion = draftState.aggregate.version,
                idempotencyKey = "android-w2-restore-" + java.util.UUID.randomUUID().toString(),
                revisionId = revisionId,
            )
            if (restored.isFailure) {
                writingWorkspaceError(restored.exceptionOrNull())
                return@launch
            }
            val result = restored.getOrThrow().result
            val revisions = container.liveSession.writingRoomChapterRevisions(projectId, chapterId)
            _writingWorkspace.value = _writingWorkspace.value.copy(
                busy = false,
                busyLabel = "",
                planningV2AggregateVersion = result.aggregate.version,
                planningV2Direction = result.direction,
                draftV2 = result,
                approvalV2 = null,
                activeChapter = result.chapter,
                chapterRevisions = revisions.getOrNull()?.items.orEmpty(),
                chapterRevisionChapterId = chapterId,
                error = revisions.exceptionOrNull()?.message,
            )
        }
    }

    fun preparePersistentWritingApproval(projectId: String) {
        val current = _writingWorkspace.value
        val draftState = current.draftV2 ?: return
        val chapterId = draftState.chapter_id
        writingWorkspaceBusy("CANON KEEPER · PREPARANDO CANONDIFF")
        viewModelScope.launch {
            val prepared = container.liveSession.writingRoomApprovalPrepare(
                projectId = projectId,
                chapterId = chapterId,
                expectedVersion = draftState.aggregate.version,
                idempotencyKey = "android-w2-canon-diff-" + java.util.UUID.randomUUID().toString(),
            )
            prepared.fold(
                onSuccess = { response ->
                    _writingWorkspace.value = _writingWorkspace.value.copy(
                        busy = false,
                        busyLabel = "",
                        planningV2AggregateVersion = response.result.aggregate.version,
                        planningV2Direction = response.result.direction,
                        approvalV2 = response.result,
                        activeChapter = response.result.chapter,
                        error = null,
                    )
                },
                onFailure = ::writingWorkspaceError,
            )
        }
    }

    fun approvePersistentWritingChapter(projectId: String) {
        val current = _writingWorkspace.value
        val approval = current.approvalV2 ?: return
        val diff = approval.canon_diff ?: return
        val brief = approval.direction.brief ?: return
        val revisionId = approval.aggregate.current_draft_revision_id
        if (
            !approval.approval_ready ||
            revisionId.isBlank() ||
            diff.draft_sha256.isBlank() ||
            approval.ready_review_ids.isEmpty()
        ) return

        writingWorkspaceBusy("APROBANDO TEXTO + CANONDIFF")
        viewModelScope.launch {
            val approved = container.liveSession.writingRoomApprovalFinal(
                projectId = projectId,
                chapterId = approval.chapter_id,
                expectedVersion = approval.aggregate.version,
                idempotencyKey = "android-w2-final-approve-" + java.util.UUID.randomUUID().toString(),
                revisionId = revisionId,
                draftSha256 = diff.draft_sha256,
                briefRevisionId = brief.brief_revision_id,
                reviewIds = approval.ready_review_ids,
                canonDiffId = diff.canon_diff_id,
                canonDiffHash = diff.diff_hash,
            )
            approved.fold(
                onSuccess = { response ->
                    val list = container.liveSession.writingRoomChapterList(projectId)
                    _writingWorkspace.value = _writingWorkspace.value.copy(
                        busy = false,
                        busyLabel = "",
                        planningV2AggregateVersion = response.result.aggregate.version,
                        planningV2Direction = response.result.direction,
                        approvalV2 = response.result,
                        activeChapter = response.result.chapter,
                        chapters = list.getOrNull()?.items ?: _writingWorkspace.value.chapters,
                        error = list.exceptionOrNull()?.message,
                    )
                },
                onFailure = ::writingWorkspaceError,
            )
        }
    }

    fun startWritingChapter(
        projectId: String,
        title: String,
        objective: String,
        storyPoint: String,
        characters: List<String>,
        mustHave: String,
        mustAvoid: String,
        tone: String,
        desiredEnd: String,
    ) {
        val clean = objective.trim()
        if (clean.isEmpty()) return
        writingWorkspaceBusy("SHOWRUNNER ANALIZANDO BRIEF")
        viewModelScope.launch {
            val started = container.liveSession.writingRoomChapterStart(
                projectId = projectId,
                title = title,
                objective = clean,
                storyPoint = storyPoint,
                characters = characters,
                mustHave = mustHave,
                mustAvoid = mustAvoid,
                tone = tone,
                desiredEnd = desiredEnd,
            )
            if (started.isFailure) {
                writingWorkspaceError(started.exceptionOrNull())
                return@launch
            }

            val provisional = started.getOrThrow().chapter
            _writingWorkspace.value = _writingWorkspace.value.copy(
                activeChapter = provisional,
                engineReview = null,
                busy = true,
                busyLabel = "SHOWRUNNER ANALIZANDO BRIEF",
                error = null,
            )

            val showrunner = container.liveSession.writingRoomChapterShowrunner(projectId, provisional.chapter_id)
            if (showrunner.isFailure) {
                val state = _writingWorkspace.value
                _writingWorkspace.value = state.copy(
                    busy = false,
                    busyLabel = "",
                    activeChapter = provisional,
                    error = showrunner.exceptionOrNull()?.message ?: "Showrunner request failed",
                )
                return@launch
            }

            val chapter = showrunner.getOrThrow().chapter
            val list = container.liveSession.writingRoomChapterList(projectId)
            _writingWorkspace.value = _writingWorkspace.value.copy(
                busy = false,
                busyLabel = "",
                activeChapter = chapter,
                engineReview = null,
                chapters = list.getOrNull()?.items ?: _writingWorkspace.value.chapters,
                error = list.exceptionOrNull()?.message,
            )
        }
    }

    fun approveWritingChapterPlan(projectId: String, chapterId: String, title: String) {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) return
        writingWorkspaceBusy("APROBANDO DIRECCIÓN DEL CAPÍTULO")
        viewModelScope.launch {
            val approved = container.liveSession.writingRoomChapterPlanApprove(
                projectId = projectId,
                chapterId = chapterId,
                title = cleanTitle,
            )
            if (approved.isFailure) {
                writingWorkspaceError(approved.exceptionOrNull())
                return@launch
            }

            val approvedChapter = approved.getOrThrow().chapter
            _writingWorkspace.value = _writingWorkspace.value.copy(
                busy = true,
                busyLabel = "WRITER REDACTANDO BORRADOR",
                activeChapter = approvedChapter,
                engineReview = null,
                error = null,
            )

            val written = container.liveSession.writingRoomChapterWrite(
                projectId = projectId,
                chapterId = chapterId,
            )
            if (written.isFailure) {
                val state = _writingWorkspace.value
                _writingWorkspace.value = state.copy(
                    busy = false,
                    busyLabel = "",
                    activeChapter = approvedChapter,
                    error = written.exceptionOrNull()?.message ?: "Writer request failed",
                )
                return@launch
            }

            val chapter = written.getOrThrow().chapter
            val list = container.liveSession.writingRoomChapterList(projectId)
            _writingWorkspace.value = _writingWorkspace.value.copy(
                busy = false,
                busyLabel = "",
                activeChapter = chapter,
                engineReview = null,
                chapters = list.getOrNull()?.items ?: _writingWorkspace.value.chapters,
                error = list.exceptionOrNull()?.message,
            )
        }
    }

    fun runWritingChapterStep(projectId: String, chapterId: String, step: String) {
        writingWorkspaceBusy(
            when (step) {
                "showrunner" -> "Building chapter brief"
                "write" -> "Writing grounded draft"
                "review" -> "Running WR-5 structured review"
                else -> "Running chapter step"
            },
        )
        viewModelScope.launch {
            val result = when (step) {
                "showrunner" -> container.liveSession.writingRoomChapterShowrunner(projectId, chapterId)
                "write" -> container.liveSession.writingRoomChapterWrite(projectId, chapterId)
                "review" -> container.liveSession.writingRoomChapterReview(projectId, chapterId)
                else -> {
                    writingWorkspaceError(IllegalArgumentException("Unknown chapter step"))
                    return@launch
                }
            }
            if (result.isFailure) {
                writingWorkspaceError(result.exceptionOrNull())
                return@launch
            }
            val action = result.getOrThrow()
            val chapter = action.chapter
            val list = container.liveSession.writingRoomChapterList(projectId)
            val revisions = container.liveSession.writingRoomChapterRevisions(projectId, chapterId)
            _writingWorkspace.value = _writingWorkspace.value.copy(
                busy = false,
                busyLabel = "",
                activeChapter = chapter,
                chapterRevisions = revisions.getOrNull()?.items ?: _writingWorkspace.value.chapterRevisions,
                chapterRevisionChapterId = if (revisions.isSuccess) chapterId else _writingWorkspace.value.chapterRevisionChapterId,
                engineReview = if (step == "review") action.engine_review else null,
                chapters = list.getOrNull()?.items ?: _writingWorkspace.value.chapters,
                error = list.exceptionOrNull()?.message ?: revisions.exceptionOrNull()?.message,
            )
        }
    }

    fun openWritingChapter(projectId: String, chapterId: String) {
        writingWorkspaceBusy("ABRIENDO CAPÍTULO")
        viewModelScope.launch {
            val chapterResult = container.liveSession.writingRoomChapterGet(projectId, chapterId)
            if (chapterResult.isFailure) {
                writingWorkspaceError(chapterResult.exceptionOrNull())
                return@launch
            }
            val revisions = container.liveSession.writingRoomChapterRevisions(projectId, chapterId)
            _writingWorkspace.value = _writingWorkspace.value.copy(
                busy = false,
                busyLabel = "",
                activeChapter = chapterResult.getOrThrow().chapter,
                chapterRevisions = revisions.getOrNull()?.items.orEmpty(),
                chapterRevisionChapterId = chapterId,
                engineReview = null,
                error = revisions.exceptionOrNull()?.message,
            )
            restorePersistentPlanningIfPresent(projectId, chapterId)
            restorePersistentDraftIfPresent(projectId, chapterId)
        }
    }

    fun loadWritingChapterRevisions(projectId: String, chapterId: String) {
        if (_writingWorkspace.value.chapterRevisionChapterId == chapterId &&
            _writingWorkspace.value.chapterRevisions.isNotEmpty()
        ) return
        viewModelScope.launch {
            container.liveSession.writingRoomChapterRevisions(projectId, chapterId).fold(
                onSuccess = { response ->
                    _writingWorkspace.value = _writingWorkspace.value.copy(
                        chapterRevisions = response.items,
                        chapterRevisionChapterId = chapterId,
                        error = null,
                    )
                },
                onFailure = { error ->
                    _writingWorkspace.value = _writingWorkspace.value.copy(
                        error = error.message ?: "No se pudo cargar el historial del capítulo",
                    )
                },
            )
        }
    }

    fun restoreWritingChapterRevision(projectId: String, chapterId: String, revisionId: String) {
        if (_writingWorkspace.value.draftV2?.chapter_id == chapterId) {
            restorePersistentWritingRevision(projectId, revisionId)
            return
        }
        writingWorkspaceBusy("RESTAURANDO VERSIÓN")
        viewModelScope.launch {
            val restored = container.liveSession.writingRoomChapterRevisionRestore(
                projectId,
                chapterId,
                revisionId,
            )
            if (restored.isFailure) {
                writingWorkspaceError(restored.exceptionOrNull())
                return@launch
            }
            val revisions = container.liveSession.writingRoomChapterRevisions(projectId, chapterId)
            val list = container.liveSession.writingRoomChapterList(projectId)
            _writingWorkspace.value = _writingWorkspace.value.copy(
                busy = false,
                busyLabel = "",
                activeChapter = restored.getOrThrow().chapter,
                chapterRevisions = revisions.getOrNull()?.items.orEmpty(),
                chapterRevisionChapterId = chapterId,
                engineReview = null,
                chapters = list.getOrNull()?.items ?: _writingWorkspace.value.chapters,
                error = revisions.exceptionOrNull()?.message ?: list.exceptionOrNull()?.message,
            )
        }
    }

    fun saveWritingChapterDraft(projectId: String, chapterId: String, draftText: String) {
        if (_writingWorkspace.value.draftV2?.chapter_id == chapterId) {
            savePersistentWritingRevision(projectId, draftText)
            return
        }
        writingWorkspaceBusy("GUARDANDO NUEVA VERSIÓN")
        viewModelScope.launch {
            val result = container.liveSession.writingRoomSaveDraft(projectId, chapterId, draftText)
            result.fold(
                onSuccess = {
                    val revisions = container.liveSession.writingRoomChapterRevisions(projectId, chapterId)
                    val list = container.liveSession.writingRoomChapterList(projectId)
                    _writingWorkspace.value = _writingWorkspace.value.copy(
                        busy = false,
                        busyLabel = "",
                        activeChapter = it.chapter,
                        chapterRevisions = revisions.getOrNull()?.items ?: _writingWorkspace.value.chapterRevisions,
                        chapterRevisionChapterId = if (revisions.isSuccess) chapterId else _writingWorkspace.value.chapterRevisionChapterId,
                        chapters = list.getOrNull()?.items ?: _writingWorkspace.value.chapters,
                        engineReview = null,
                        error = revisions.exceptionOrNull()?.message ?: list.exceptionOrNull()?.message,
                    )
                },
                onFailure = ::writingWorkspaceError,
            )
        }
    }

    fun editWritingLibraryChapter(
        projectId: String,
        documentId: String,
        onReady: () -> Unit = {},
    ) {
        writingWorkspaceBusy("ABRIENDO CAPÍTULO PARA EDICIÓN")
        viewModelScope.launch {
            val result = container.liveSession.writingRoomLibraryChapterEdit(projectId, documentId)
            if (result.isFailure) {
                writingWorkspaceError(result.exceptionOrNull())
                return@launch
            }
            val chapter = result.getOrThrow().chapter
            val revisions = container.liveSession.writingRoomChapterRevisions(projectId, chapter.chapter_id)
            val list = container.liveSession.writingRoomChapterList(projectId)
            _writingWorkspace.value = _writingWorkspace.value.copy(
                busy = false,
                busyLabel = "",
                activeChapter = chapter,
                chapterRevisions = revisions.getOrNull()?.items.orEmpty(),
                chapterRevisionChapterId = chapter.chapter_id,
                chapters = list.getOrNull()?.items ?: _writingWorkspace.value.chapters,
                engineReview = null,
                error = revisions.exceptionOrNull()?.message ?: list.exceptionOrNull()?.message,
            )
            onReady()
        }
    }

    fun deleteWritingChapter(projectId: String, chapterId: String) {
        writingWorkspaceBusy("MOVIENDO SESIÓN A PAPELERA")
        viewModelScope.launch {
            val result = container.liveSession.writingRoomChapterDelete(projectId, chapterId)
            if (result.isFailure) {
                writingWorkspaceError(result.exceptionOrNull())
                return@launch
            }
            val list = container.liveSession.writingRoomChapterList(projectId)
            val trash = container.liveSession.writingRoomChapterTrash(projectId)
            val current = _writingWorkspace.value
            val deletingActive = current.activeChapter?.chapter_id == chapterId
            _writingWorkspace.value = current.copy(
                busy = false,
                busyLabel = "",
                chapters = list.getOrNull()?.items ?: current.chapters.filterNot { it.chapter_id == chapterId },
                chapterTrash = trash.getOrNull()?.items ?: current.chapterTrash,
                activeChapter = if (deletingActive) null else current.activeChapter,
                chapterRevisions = if (deletingActive) emptyList() else current.chapterRevisions,
                chapterRevisionChapterId = if (deletingActive) null else current.chapterRevisionChapterId,
                engineReview = if (deletingActive) null else current.engineReview,
                error = list.exceptionOrNull()?.message ?: trash.exceptionOrNull()?.message,
            )
        }
    }

    fun loadWritingChapterTrash(projectId: String) {
        viewModelScope.launch {
            val trash = container.liveSession.writingRoomChapterTrash(projectId)
            trash.fold(
                onSuccess = {
                    _writingWorkspace.value = _writingWorkspace.value.copy(
                        chapterTrash = it.items,
                        error = null,
                    )
                },
                onFailure = { error ->
                    _writingWorkspace.value = _writingWorkspace.value.copy(
                        error = error.message ?: "No se pudo cargar la papelera",
                    )
                },
            )
        }
    }

    fun restoreDeletedWritingChapter(projectId: String, chapterId: String) {
        writingWorkspaceBusy("RESTAURANDO SESIÓN")
        viewModelScope.launch {
            val restored = container.liveSession.writingRoomChapterRestoreDeleted(projectId, chapterId)
            if (restored.isFailure) {
                writingWorkspaceError(restored.exceptionOrNull())
                return@launch
            }
            val list = container.liveSession.writingRoomChapterList(projectId)
            val trash = container.liveSession.writingRoomChapterTrash(projectId)
            _writingWorkspace.value = _writingWorkspace.value.copy(
                busy = false,
                busyLabel = "",
                activeChapter = restored.getOrThrow().chapter,
                chapters = list.getOrNull()?.items ?: _writingWorkspace.value.chapters,
                chapterTrash = trash.getOrNull()?.items ?: _writingWorkspace.value.chapterTrash.filterNot { it.chapter_id == chapterId },
                chapterRevisions = emptyList(),
                chapterRevisionChapterId = null,
                engineReview = null,
                error = list.exceptionOrNull()?.message ?: trash.exceptionOrNull()?.message,
            )
        }
    }

    fun readWritingLibraryDocument(projectId: String, documentId: String) {
        writingWorkspaceBusy("Opening official chapter")
        viewModelScope.launch {
            val result = container.liveSession.writingRoomLibraryRead(projectId, documentId)
            result.fold(
                onSuccess = {
                    _writingWorkspace.value = _writingWorkspace.value.copy(
                        busy = false,
                        busyLabel = "",
                        document = it.document,
                        error = null,
                    )
                },
                onFailure = ::writingWorkspaceError,
            )
        }
    }

    fun requestWritingLibraryExport(projectId: String, documentId: String, format: String) {
        writingWorkspaceBusy("Preparing ${format.uppercase()} export")
        viewModelScope.launch {
            val result = container.liveSession.writingRoomLibraryExport(projectId, documentId, format)
            result.fold(
                onSuccess = {
                    _writingWorkspace.value = _writingWorkspace.value.copy(
                        busy = false,
                        busyLabel = "",
                        pendingExport = it,
                        exportMessage = null,
                        error = null,
                    )
                },
                onFailure = ::writingWorkspaceError,
            )
        }
    }

    fun savePendingWritingExport(uri: Uri) {
        val pending = _writingWorkspace.value.pendingExport ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val bytes = Base64.decode(pending.data_base64, Base64.DEFAULT)
                require(bytes.size == pending.size_bytes) { "Export size verification failed" }
                val digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(bytes)
                    .joinToString("") { "%02x".format(it) }
                require(digest.equals(pending.sha256, ignoreCase = true)) {
                    "Export SHA-256 verification failed"
                }
                app.contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(bytes)
                    output.flush()
                } ?: error("Could not open the selected destination")
            }.fold(
                onSuccess = {
                    _writingWorkspace.value = _writingWorkspace.value.copy(
                        pendingExport = null,
                        exportMessage = "Saved ${pending.filename}",
                        error = null,
                    )
                },
                onFailure = { error ->
                    _writingWorkspace.value = _writingWorkspace.value.copy(
                        pendingExport = null,
                        exportMessage = null,
                        error = error.message ?: "Could not save export",
                    )
                },
            )
        }
    }

    fun cancelPendingWritingExport() {
        _writingWorkspace.value = _writingWorkspace.value.copy(pendingExport = null)
    }

    sealed interface ImageGenerationState {
        data object Idle : ImageGenerationState
        data object Busy : ImageGenerationState
        data class Error(val message: String) : ImageGenerationState
    }

    data class ImageGenerationDetails(
        val provider: String,
        val model: String,
        val mode: String,
        val fallbackUsed: Boolean,
        val attemptCount: Int,
        val durationMs: Long,
    )

    private val _imageGenerationState = MutableStateFlow<ImageGenerationState>(ImageGenerationState.Idle)
    val imageGenerationState: StateFlow<ImageGenerationState> = _imageGenerationState
    private val _lastImageGenerationDetails = MutableStateFlow<ImageGenerationDetails?>(null)
    val lastImageGenerationDetails: StateFlow<ImageGenerationDetails?> = _lastImageGenerationDetails

    sealed interface WikiPrimaryState {
        data object Idle : WikiPrimaryState
        data object Busy : WikiPrimaryState
        data class Success(
            val assetId: String,
            val characterId: String,
            val revision: Int,
        ) : WikiPrimaryState
        data class Error(val message: String) : WikiPrimaryState
    }

    private val _wikiPrimaryState = MutableStateFlow<WikiPrimaryState>(WikiPrimaryState.Idle)
    val wikiPrimaryState: StateFlow<WikiPrimaryState> = _wikiPrimaryState

    private val _wikiVisualAttachments = MutableStateFlow<Map<String, String>>(emptyMap())
    val wikiVisualAttachments: StateFlow<Map<String, String>> = _wikiVisualAttachments

    data class CharacterStudioState(
        val projectId: String = "",
        val characterId: String = "",
        val busy: Boolean = false,
        val busyLabel: String = "",
        val detail: VisualStudioCharacterDetail? = null,
        val batch: VisualCharacterBatch? = null,
        val assets: List<VisualStudioAsset> = emptyList(),
        val attachmentIds: Map<String, String> = emptyMap(),
        val loadingAssetIds: Set<String> = emptySet(),
        val assetErrors: Map<String, String> = emptyMap(),
        val notice: String? = null,
        val error: String? = null,
    )

    // Invalidate late media results even when the user switches away and back.
    private var characterStudioScopeVersion = 0L
    private var characterBatchPollJob: Job? = null
    private val _characterStudio = MutableStateFlow(CharacterStudioState())
    val characterStudio: StateFlow<CharacterStudioState> = _characterStudio

    private fun deleteVisualStudioAttachments(ids: Collection<String>) {
        if (ids.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            ids.distinct().forEach { id ->
                runCatching { container.attachmentStore.delete(id) }
            }
        }
    }

    data class LocationStudioState(
        val projectId: String = "",
        val locationId: String = "",
        val busy: Boolean = false,
        val busyLabel: String = "",
        val detail: VisualStudioLocationDetail? = null,
        val assets: List<VisualStudioAsset> = emptyList(),
        val attachmentIds: Map<String, String> = emptyMap(),
        val loadingAssetIds: Set<String> = emptySet(),
        val assetErrors: Map<String, String> = emptyMap(),
        val notice: String? = null,
        val error: String? = null,
    )

    private var locationStudioScopeVersion = 0L
    private val _locationStudio = MutableStateFlow(LocationStudioState())
    val locationStudio: StateFlow<LocationStudioState> = _locationStudio

    data class WorldMapConflictNotice(
        val currentVersion: Int? = null,
        val currentHash: String? = null,
        val message: String = "",
    )

    data class WorldMapState(
        val projectId: String = "",
        val busy: Boolean = false,
        val busyLabel: String = "",
        val status: WorldMapStatusResponse? = null,
        val map: WorldMapResponse? = null,
        val conflict: WorldMapConflictNotice? = null,
        val notice: String? = null,
        val error: String? = null,
    )

    private val _worldMap = MutableStateFlow(WorldMapState())
    val worldMap: StateFlow<WorldMapState> = _worldMap

    data class SceneBuilderState(
        val projectId: String = "",
        val busy: Boolean = false,
        val busyLabel: String = "",
        val context: VisualSceneContext? = null,
        val capabilities: VisualSceneGenerationCapabilities? = null,
        val directorStatus: String = "",
        val directorCandidates: List<VisualSceneDirectorCandidate> = emptyList(),
        val directorResolverModel: String = "",
        val directorJob: VisualSceneDirectorJob? = null,
        val generated: VisualStudioGeneratedImage? = null,
        val generatedAttachmentId: String? = null,
        val attachmentIds: Set<String> = emptySet(),
        val notice: String? = null,
        val error: String? = null,
    )

    private var sceneBuilderScopeVersion = 0L
    private var sceneDirectorPollJob: Job? = null
    private val _sceneBuilder = MutableStateFlow(SceneBuilderState())
    val sceneBuilder: StateFlow<SceneBuilderState> = _sceneBuilder

    private fun resetVisualStudioProtectedMedia() {
        characterBatchPollJob?.cancel()
        characterBatchPollJob = null
        sceneDirectorPollJob?.cancel()
        sceneDirectorPollJob = null
        characterStudioScopeVersion++
        locationStudioScopeVersion++
        sceneBuilderScopeVersion++
        _imageEditState.value = ImageEditState.Idle
        _lastImageEditDetails.value = null
        val ids = (
            _characterStudio.value.attachmentIds.values +
                _locationStudio.value.attachmentIds.values +
                _sceneBuilder.value.attachmentIds +
                listOfNotNull(_sceneBuilder.value.generatedAttachmentId) +
                _wikiVisualAttachments.value.values
        ).distinct()
        _characterStudio.value = CharacterStudioState()
        _locationStudio.value = LocationStudioState()
        _worldMap.value = WorldMapState()
        _sceneBuilder.value = SceneBuilderState()
        _wikiVisualAttachments.value = emptyMap()
        deleteVisualStudioAttachments(ids)
    }

    fun openCharacterStudio(projectId: String, characterId: String) {
        val cleanProject = projectId.trim()
        val cleanCharacter = characterId.trim()
        if (cleanProject.isBlank() || cleanCharacter.isBlank()) return
        val current = _characterStudio.value
        if (current.projectId != cleanProject || current.characterId != cleanCharacter) {
            characterBatchPollJob?.cancel()
            characterBatchPollJob = null
            characterStudioScopeVersion++
            _imageEditState.value = ImageEditState.Idle
            _lastImageEditDetails.value = null
            val oldIds = current.attachmentIds.values
            _characterStudio.value = CharacterStudioState(
                projectId = cleanProject,
                characterId = cleanCharacter,
            )
            deleteVisualStudioAttachments(oldIds)
        }
        refreshCharacterStudio(cleanProject, cleanCharacter)
    }

    fun clearCharacterStudioMessage() {
        _characterStudio.value = _characterStudio.value.copy(notice = null, error = null)
    }

    fun refreshCharacterStudio(projectId: String, characterId: String) {
        val cleanProject = projectId.trim()
        val cleanCharacter = characterId.trim()
        if (cleanProject.isBlank() || cleanCharacter.isBlank()) return
        val current = _characterStudio.value
        if (current.projectId != cleanProject || current.characterId != cleanCharacter) {
            characterBatchPollJob?.cancel()
            characterBatchPollJob = null
            characterStudioScopeVersion++
            _imageEditState.value = ImageEditState.Idle
            _lastImageEditDetails.value = null
        }
        _characterStudio.value = current.copy(
            projectId = cleanProject,
            characterId = cleanCharacter,
            busy = true,
            busyLabel = "Actualizando estudio visual",
            error = null,
        )
        viewModelScope.launch {
            reloadCharacterStudio(cleanProject, cleanCharacter)
        }
    }

    private suspend fun reloadCharacterStudio(projectId: String, characterId: String) {
        val detail = container.liveSession.visualCharacterDetail(projectId, characterId)
        val assets = container.liveSession.visualAssetList(projectId, characterId)
        val batchResult = container.liveSession.visualCharacterBatchStatus(projectId, characterId)
        if (
            _characterStudio.value.projectId != projectId ||
            _characterStudio.value.characterId != characterId
        ) return
        val failure = detail.exceptionOrNull() ?: assets.exceptionOrNull() ?: batchResult.exceptionOrNull()
        val loadedBatch = batchResult.getOrNull()?.batch
        _characterStudio.value = _characterStudio.value.copy(
            busy = false,
            busyLabel = "",
            detail = detail.getOrNull() ?: _characterStudio.value.detail,
            batch = if (batchResult.isSuccess) loadedBatch else _characterStudio.value.batch,
            assets = assets.getOrNull()?.assets ?: _characterStudio.value.assets,
            error = failure?.message,
        )
        if (
            loadedBatch != null &&
            loadedBatch.pending_count > 0 &&
            loadedBatch.status == "READY"
        ) {
            completeCharacterViews(
                projectId = projectId,
                characterId = characterId,
                perspectives = loadedBatch.requested_perspectives,
                adjustment = loadedBatch.adjustment,
                resumeBatchId = loadedBatch.batch_id,
            )
        } else if (loadedBatch?.status == "RUNNING") {
            startCharacterBatchPolling(
                projectId,
                characterId,
                loadedBatch.batch_id,
            )
        }
    }

    private fun startCharacterBatchPolling(
        projectId: String,
        characterId: String,
        batchId: String,
    ) {
        if (batchId.isBlank()) return
        characterBatchPollJob?.cancel()
        val scopeVersion = characterStudioScopeVersion
        characterBatchPollJob = viewModelScope.launch {
            while (true) {
                delay(1_800)
                if (
                    scopeVersion != characterStudioScopeVersion ||
                    _characterStudio.value.projectId != projectId ||
                    _characterStudio.value.characterId != characterId
                ) return@launch
                val result = container.liveSession.visualCharacterBatchStatus(
                    projectId,
                    characterId,
                    batchId,
                )
                val response = result.getOrNull()
                val batch = response?.batch
                if (batch != null) {
                    _characterStudio.update {
                        it.copy(
                            busy = false,
                            busyLabel = "",
                            batch = batch,
                            notice = when (batch.status) {
                                "RUNNING" ->
                                    "JARVIS está generando y evaluando las vistas en segundo plano."
                                "READY_FOR_REVIEW" ->
                                    "Lote evaluado y listo para revisión conjunta."
                                "BLOCKED" ->
                                    "El lote se detuvo de forma segura; no se repetirá una operación de pago ambigua."
                                "COMPLETED" ->
                                    "Las vistas del lote están aprobadas."
                                else -> it.notice
                            },
                            error = null,
                        )
                    }
                    if (batch.status != "RUNNING" && batch.status != "READY") {
                        reloadCharacterStudio(projectId, characterId)
                        return@launch
                    }
                } else if (result.isFailure) {
                    _characterStudio.update {
                        it.copy(
                            busy = false,
                            busyLabel = "",
                            notice = "El lote sigue guardado en el servidor; se reintentará consultar su estado.",
                        )
                    }
                }
            }
        }
    }

    fun loadCharacterStudioAsset(projectId: String, assetId: String, retry: Boolean = false) {
        val clean = assetId.trim()
        val state = _characterStudio.value
        if (clean.isBlank() || state.projectId != projectId ||
            clean in state.loadingAssetIds ||
            (!retry && (state.attachmentIds.containsKey(clean) || state.assetErrors.containsKey(clean)))
        ) return
        val scopeVersion = characterStudioScopeVersion
        val entityId = state.characterId
        val oldId = if (retry) state.attachmentIds[clean] else null
        _characterStudio.update {
            it.copy(
                attachmentIds = if (retry) it.attachmentIds - clean else it.attachmentIds,
                loadingAssetIds = it.loadingAssetIds + clean,
                assetErrors = it.assetErrors - clean,
            )
        }
        oldId?.let { deleteVisualStudioAttachments(listOf(it)) }
        viewModelScope.launch {
            val result = loadVisualAsset(
                fetch = { container.liveSession.writingRoomVisualAssetFetch(projectId, clean) },
                stage = { content ->
                    withContext(Dispatchers.IO) {
                        container.attachmentStore.stageVisualAssetBase64(
                            content.image_base64, content.sha256,
                        )
                    }
                },
            )
            if (characterStudioScopeVersion != scopeVersion ||
                _characterStudio.value.projectId != projectId ||
                _characterStudio.value.characterId != entityId
            ) {
                result.getOrNull()?.let { deleteVisualStudioAttachments(listOf(it.attachmentId)) }
                return@launch
            }
            _characterStudio.update {
                val staged = result.getOrNull()
                it.copy(
                    loadingAssetIds = it.loadingAssetIds - clean,
                    attachmentIds = if (staged != null) it.attachmentIds + (clean to staged.attachmentId) else it.attachmentIds,
                    assetErrors = if (staged == null) it.assetErrors + (clean to
                        (result.exceptionOrNull()?.message ?: "No se pudo cargar la imagen."))
                        else it.assetErrors - clean,
                )
            }
        }
    }

    private suspend fun rememberCharacterStudioCandidate(
        projectId: String,
        characterId: String,
        reply: VisualStudioGeneratedImage,
    ) {
        val asset = reply.visual_asset ?: return
        val staged = withContext(Dispatchers.IO) {
            container.attachmentStore.stageVisualAssetBase64(reply.data_base64, asset.sha256)
        }
        if (
            staged != null &&
            _characterStudio.value.projectId == projectId &&
            _characterStudio.value.characterId == characterId
        ) {
            _characterStudio.update {
                it.copy(
                    attachmentIds = it.attachmentIds + (asset.asset_id to staged.attachmentId),
                    notice = if (reply.storage_retry_required) {
                        "La imagen se generó y quedó como candidata, pero Drive necesita reintentar el guardado. No se regenerará el modelo."
                    } else {
                        "Nueva imagen candidata guardada. Aún no forma parte del canon visual."
                    },
                )
            }
        }
    }

    fun completeCharacterViews(
        projectId: String,
        characterId: String,
        perspectives: List<String>,
        adjustment: String = "",
        resumeBatchId: String = "",
    ) {
        if (_characterStudio.value.busy || perspectives.isEmpty()) return
        _characterStudio.update {
            it.copy(
                busy = true,
                busyLabel = if (resumeBatchId.isBlank()) {
                    "Preparando trabajo visual"
                } else {
                    "Reanudando trabajo visual"
                },
                notice = null,
                error = null,
            )
        }
        viewModelScope.launch {
            container.liveSession.visualCompleteCharacterViews(
                projectId = projectId,
                characterId = characterId,
                perspectives = perspectives,
                adjustment = adjustment,
                batchId = resumeBatchId,
            ).fold(
                onSuccess = { response ->
                    val batch = response.batch
                    _characterStudio.update {
                        it.copy(
                            busy = false,
                            busyLabel = "",
                            batch = batch,
                            notice = when (batch?.status) {
                                "RUNNING" ->
                                    "Trabajo iniciado. JARVIS generará, evaluará y corregirá en segundo plano."
                                "READY_FOR_REVIEW" ->
                                    "Lote evaluado y listo para revisión conjunta."
                                "BLOCKED" ->
                                    "El lote se detuvo de forma segura; no se repetirá una generación con resultado ambiguo."
                                "COMPLETED" ->
                                    "Las vistas solicitadas ya están aprobadas."
                                else ->
                                    "El lote quedó guardado y puede recuperarse sin duplicar salidas."
                            },
                        )
                    }
                    if (batch?.status == "RUNNING") {
                        startCharacterBatchPolling(
                            projectId,
                            characterId,
                            batch.batch_id,
                        )
                    } else {
                        reloadCharacterStudio(projectId, characterId)
                    }
                },
                onFailure = { error ->
                    _characterStudio.update {
                        it.copy(
                            busy = false,
                            busyLabel = "",
                            error = error.message
                                ?: "No se pudo preparar el lote de vistas.",
                        )
                    }
                },
            )
        }
    }

    fun approveCharacterViewBatch(projectId: String, characterId: String) {
        val batch = _characterStudio.value.batch ?: return
        if (_characterStudio.value.busy || batch.batch_id.isBlank()) return
        val assetIds = batch.items
            .filter { it.status == "CANDIDATE" || it.status == "CANDIDATE_EXISTING" }
            .map { it.candidate_asset_id }
            .filter { it.isNotBlank() }
        if (assetIds.isEmpty()) {
            _characterStudio.update { it.copy(error = "No hay vistas candidatas almacenadas para aprobar.") }
            return
        }
        _characterStudio.update {
            it.copy(busy = true, busyLabel = "Aprobando lote exacto", notice = null, error = null)
        }
        viewModelScope.launch {
            container.liveSession.visualApproveCharacterViewBatch(
                projectId = projectId,
                characterId = characterId,
                batchId = batch.batch_id,
                assetIds = assetIds,
            ).fold(
                onSuccess = { response ->
                    val approvedCount = response.approved.size
                    val failedCount = response.failed.size
                    _characterStudio.update {
                        it.copy(
                            batch = response.batch,
                            notice = if (failedCount == 0) {
                                approvedCount.toString() + " vistas aprobadas por hash exacto."
                            } else {
                                approvedCount.toString() + " aprobadas; " + failedCount.toString() + " pendientes."
                            },
                        )
                    }
                    reloadCharacterStudio(projectId, characterId)
                },
                onFailure = { error ->
                    _characterStudio.update {
                        it.copy(
                            busy = false,
                            busyLabel = "",
                            error = error.message ?: "No se pudo aprobar el lote de vistas.",
                        )
                    }
                },
            )
        }
    }

    fun generateCharacterVisual(
        projectId: String,
        characterId: String,
        prompt: String,
        kind: String,
        perspective: String,
        parentAsset: VisualStudioAsset? = null,
    ) {
        val cleanPrompt = prompt.trim()
        if (_characterStudio.value.busy) return
        _characterStudio.update {
            it.copy(
                busy = true,
                busyLabel = if (kind == "PRIMARY_REFERENCE") {
                    "Generando candidato de master"
                } else {
                    "Generando vista ${perspective.replace('_', ' ')}"
                },
                notice = null,
                error = null,
            )
        }
        viewModelScope.launch {
            val result = container.liveSession.generateVisualAssetImage(
                projectId = projectId,
                characterId = characterId,
                prompt = cleanPrompt,
                kind = kind,
                perspective = perspective,
                mode = "quality",
                aspectRatio = "portrait",
                referencePerspectives = if (kind == "IDENTITY_PACK") listOf("front") else emptyList(),
                referencesPerCharacter = 1,
                parentAssetId = parentAsset?.asset_id.orEmpty(),
                parentSha256 = parentAsset?.sha256.orEmpty(),
                derivation = if (parentAsset == null) "" else "REGENERATION",
            )
            if (result.isFailure) {
                _characterStudio.update {
                    it.copy(
                        busy = false,
                        busyLabel = "",
                        error = result.exceptionOrNull()?.message ?: "No se pudo generar la imagen.",
                    )
                }
                return@launch
            }
            rememberCharacterStudioCandidate(projectId, characterId, result.getOrThrow())
            reloadCharacterStudio(projectId, characterId)
        }
    }

    fun uploadCharacterMaster(
        projectId: String,
        characterId: String,
        uri: Uri,
    ) {
        if (_characterStudio.value.busy) return
        _characterStudio.update {
            it.copy(
                busy = true,
                busyLabel = "Guardando candidato manual",
                notice = null,
                error = null,
            )
        }
        viewModelScope.launch {
            val staged = withContext(Dispatchers.IO) { container.attachmentStore.stageFrom(uri) }
            if (staged == null) {
                _characterStudio.update {
                    it.copy(
                        busy = false,
                        busyLabel = "",
                        error = "No se pudo leer la imagen seleccionada.",
                    )
                }
                return@launch
            }
            val source = withContext(Dispatchers.IO) {
                runCatching {
                    val file = container.attachmentStore.resolve(staged.attachmentId)
                        ?: error("La imagen temporal ya no está disponible.")
                    val bytes = file.readBytes()
                    require(bytes.isNotEmpty() && bytes.size <= 12 * 1024 * 1024) {
                        "La imagen es demasiado grande."
                    }
                    Base64.encodeToString(bytes, Base64.NO_WRAP)
                }
            }
            if (source.isFailure) {
                withContext(Dispatchers.IO) { container.attachmentStore.delete(staged.attachmentId) }
                _characterStudio.update {
                    it.copy(
                        busy = false,
                        busyLabel = "",
                        error = source.exceptionOrNull()?.message ?: "No se pudo leer la imagen.",
                    )
                }
                return@launch
            }
            val mime = app.contentResolver.getType(uri)
                ?.lowercase()
                ?.takeIf { it in setOf("image/png", "image/jpeg", "image/webp") }
                ?: "image/jpeg"
            val result = container.liveSession.visualAssetIngest(
                projectId = projectId,
                imageBase64 = source.getOrThrow(),
                mimeType = mime,
                kind = "PRIMARY_REFERENCE",
                source = "MANUAL_UPLOAD",
                characterId = characterId,
                perspective = "front",
            )
            result.fold(
                onSuccess = { response ->
                    _characterStudio.update {
                        it.copy(
                            attachmentIds = it.attachmentIds +
                                (response.asset.asset_id to staged.attachmentId),
                            notice = "Master manual guardado como candidato. Revisa y aprueba el hash exacto.",
                        )
                    }
                    reloadCharacterStudio(projectId, characterId)
                },
                onFailure = { error ->
                    withContext(Dispatchers.IO) {
                        container.attachmentStore.delete(staged.attachmentId)
                    }
                    _characterStudio.update {
                        it.copy(
                            busy = false,
                            busyLabel = "",
                            error = error.message ?: "No se pudo guardar el master.",
                        )
                    }
                },
            )
        }
    }

    fun uploadCharacterTurnaround(
        projectId: String,
        characterId: String,
        perspective: String,
        uri: Uri,
    ) {
        val cleanPerspective = perspective.trim()
        val scopeVersion = characterStudioScopeVersion
        fun scopeIsCurrent(): Boolean =
            scopeVersion == characterStudioScopeVersion &&
                _characterStudio.value.projectId == projectId &&
                _characterStudio.value.characterId == characterId
        if (
            !scopeIsCurrent() ||
            _characterStudio.value.busy ||
            cleanPerspective.isBlank()
        ) return
        _characterStudio.update {
            it.copy(
                busy = true,
                busyLabel = "Guardando vista manual",
                notice = null,
                error = null,
            )
        }
        viewModelScope.launch {
            val staged = withContext(Dispatchers.IO) { container.attachmentStore.stageFrom(uri) }
            if (!scopeIsCurrent()) {
                staged?.let { deleteVisualStudioAttachments(listOf(it.attachmentId)) }
                return@launch
            }
            if (staged == null) {
                _characterStudio.update {
                    it.copy(
                        busy = false,
                        busyLabel = "",
                        error = "No se pudo leer la vista seleccionada.",
                    )
                }
                return@launch
            }
            val source = withContext(Dispatchers.IO) {
                runCatching {
                    val file = container.attachmentStore.resolve(staged.attachmentId)
                        ?: error("La imagen temporal ya no está disponible.")
                    val bytes = file.readBytes()
                    require(bytes.isNotEmpty() && bytes.size <= 12 * 1024 * 1024) {
                        "La imagen es demasiado grande."
                    }
                    Base64.encodeToString(bytes, Base64.NO_WRAP)
                }
            }
            if (!scopeIsCurrent()) {
                deleteVisualStudioAttachments(listOf(staged.attachmentId))
                return@launch
            }
            if (source.isFailure) {
                withContext(Dispatchers.IO) { container.attachmentStore.delete(staged.attachmentId) }
                if (!scopeIsCurrent()) return@launch
                _characterStudio.update {
                    it.copy(
                        busy = false,
                        busyLabel = "",
                        error = source.exceptionOrNull()?.message ?: "No se pudo leer la vista.",
                    )
                }
                return@launch
            }
            val mime = app.contentResolver.getType(uri)
                ?.lowercase()
                ?.takeIf { it in setOf("image/png", "image/jpeg", "image/webp") }
                ?: "image/jpeg"
            container.liveSession.visualAssetIngest(
                projectId = projectId,
                imageBase64 = source.getOrThrow(),
                mimeType = mime,
                kind = "IDENTITY_PACK",
                source = "MANUAL_UPLOAD",
                characterId = characterId,
                perspective = cleanPerspective,
            ).fold(
                onSuccess = { response ->
                    if (!scopeIsCurrent()) {
                        deleteVisualStudioAttachments(listOf(staged.attachmentId))
                        return@fold
                    }
                    _characterStudio.update {
                        it.copy(
                            attachmentIds = it.attachmentIds +
                                (response.asset.asset_id to staged.attachmentId),
                            notice = "Vista manual guardada como CANDIDATE. Revisa y aprueba su hash exacto antes de incluirla en un Reference Pack.",
                        )
                    }
                    reloadCharacterStudio(projectId, characterId)
                },
                onFailure = { error ->
                    withContext(Dispatchers.IO) {
                        container.attachmentStore.delete(staged.attachmentId)
                    }
                    if (!scopeIsCurrent()) return@fold
                    _characterStudio.update {
                        it.copy(
                            busy = false,
                            busyLabel = "",
                            error = error.message ?: "No se pudo guardar la vista manual.",
                        )
                    }
                },
            )
        }
    }

    fun approveCharacterVisual(asset: VisualStudioAsset) {
        val state = _characterStudio.value
        if (state.busy || asset.status != "CANDIDATE" || asset.sha256.isBlank()) return
        val nextRevision = state.assets
            .filter {
                it.status == "APPROVED" &&
                    it.kind == asset.kind &&
                    it.perspective == asset.perspective
            }
            .maxOfOrNull { it.visual_revision }
            ?.plus(1)
            ?: 1
        _characterStudio.update {
            it.copy(
                busy = true,
                busyLabel = "Aprobando hash exacto",
                notice = null,
                error = null,
            )
        }
        viewModelScope.launch {
            container.liveSession.visualAssetApproveExact(
                projectId = asset.project_id,
                assetId = asset.asset_id,
                assetSha256 = asset.sha256,
                visualRevision = nextRevision,
            ).fold(
                onSuccess = {
                    _characterStudio.update {
                        it.copy(notice = "Imagen aprobada. El asset quedó ligado a su SHA-256 exacto.")
                    }
                    reloadCharacterStudio(asset.project_id, state.characterId)
                },
                onFailure = { error ->
                    _characterStudio.update {
                        it.copy(
                            busy = false,
                            busyLabel = "",
                            error = error.message ?: "No se pudo aprobar la imagen.",
                        )
                    }
                },
            )
        }
    }

    fun retryCharacterVisualStorage(asset: VisualStudioAsset) {
        val state = _characterStudio.value
        if (state.busy || asset.storage.state == "stored") return
        val attachmentId = state.attachmentIds[asset.asset_id]
        if (attachmentId.isNullOrBlank()) {
            _characterStudio.update {
                it.copy(
                    error = "Los bytes locales del candidato ya no están disponibles. JARVIS no regenerará la imagen automáticamente.",
                )
            }
            return
        }
        _characterStudio.update {
            it.copy(
                busy = true,
                busyLabel = "Reintentando guardado en Drive",
                notice = null,
                error = null,
            )
        }
        viewModelScope.launch {
            val encoded = withContext(Dispatchers.IO) {
                runCatching {
                    val file = container.attachmentStore.resolve(attachmentId)
                        ?: error("Los bytes locales del candidato ya no están disponibles.")
                    Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
                }
            }
            if (encoded.isFailure) {
                _characterStudio.update {
                    it.copy(
                        busy = false,
                        busyLabel = "",
                        error = encoded.exceptionOrNull()?.message,
                    )
                }
                return@launch
            }
            container.liveSession.visualAssetIngest(
                projectId = asset.project_id,
                imageBase64 = encoded.getOrThrow(),
                mimeType = asset.mime_type,
                kind = asset.kind,
                source = asset.source,
                characterId = state.characterId,
                perspective = asset.perspective,
                assetId = asset.asset_id,
                parentAssetId = asset.parent_asset_id,
                parentSha256 = asset.parent_sha256,
                derivation = asset.derivation,
            ).fold(
                onSuccess = {
                    _characterStudio.update {
                        it.copy(notice = "Guardado en Drive reanudado sin repetir la generación.")
                    }
                    reloadCharacterStudio(asset.project_id, state.characterId)
                },
                onFailure = { error ->
                    _characterStudio.update {
                        it.copy(
                            busy = false,
                            busyLabel = "",
                            error = error.message ?: "Drive sigue sin poder guardar el candidato.",
                        )
                    }
                },
            )
        }
    }

    fun editCharacterVisual(
        projectId: String,
        characterId: String,
        parentAsset: VisualStudioAsset,
        instruction: String,
    ) {
        val clean = instruction.trim()
        if (
            clean.isBlank() ||
            _characterStudio.value.busy ||
            parentAsset.status != "APPROVED"
        ) return
        _characterStudio.update {
            it.copy(
                busy = true,
                busyLabel = "Creando edición hija",
                notice = null,
                error = null,
            )
        }
        viewModelScope.launch {
            val source = container.liveSession.writingRoomVisualAssetFetch(
                projectId,
                parentAsset.asset_id,
            )
            if (source.isFailure) {
                _characterStudio.update {
                    it.copy(
                        busy = false,
                        busyLabel = "",
                        error = source.exceptionOrNull()?.message ?: "No se pudo cargar el parent aprobado.",
                    )
                }
                return@launch
            }
            val content = source.getOrThrow()
            val result = container.liveSession.editVisualAssetImage(
                imageBase64 = content.image_base64,
                mimeType = content.mime_type,
                projectId = projectId,
                characterId = characterId,
                instruction = clean,
                kind = parentAsset.kind,
                perspective = parentAsset.perspective,
                parentAssetId = parentAsset.asset_id,
                parentSha256 = parentAsset.sha256,
            )
            if (result.isFailure) {
                _characterStudio.update {
                    it.copy(
                        busy = false,
                        busyLabel = "",
                        error = result.exceptionOrNull()?.message ?: "No se pudo editar la imagen.",
                    )
                }
                return@launch
            }
            rememberCharacterStudioCandidate(projectId, characterId, result.getOrThrow())
            reloadCharacterStudio(projectId, characterId)
        }
    }

    fun editCharacterVisualInStudio(
        parentAsset: VisualStudioAsset,
        characterId: String,
        referenceAttachmentId: String,
        instruction: String,
        mode: String = "quality",
        model: String? = null,
        preserveIdentity: String = "high",
        aspectRatio: String = "portrait",
    ) {
        val clean = instruction.trim()
        val state = _characterStudio.value
        val scopeVersion = characterStudioScopeVersion
        fun scopeIsCurrent(): Boolean =
            scopeVersion == characterStudioScopeVersion &&
                _characterStudio.value.projectId == parentAsset.project_id &&
                _characterStudio.value.characterId == characterId
        if (
            clean.isBlank() ||
            referenceAttachmentId.isBlank() ||
            _imageEditState.value is ImageEditState.Busy ||
            state.busy ||
            parentAsset.project_id != state.projectId ||
            characterId != state.characterId ||
            parentAsset.status != "APPROVED"
        ) {
            if (parentAsset.status != "APPROVED") {
                _imageEditState.value = ImageEditState.Error(
                    "Aprueba la imagen antes de crear una edición hija.",
                )
            }
            return
        }
        _imageEditState.value = ImageEditState.Busy
        _lastImageEditDetails.value = null
        viewModelScope.launch {
            val source = withContext(Dispatchers.IO) {
                runCatching {
                    val file = container.attachmentStore.resolve(referenceAttachmentId)
                        ?: error("La imagen base ya no está disponible en el dispositivo.")
                    val bytes = file.readBytes()
                    require(bytes.isNotEmpty() && bytes.size <= 12 * 1024 * 1024) {
                        "La imagen base es demasiado grande."
                    }
                    Base64.encodeToString(bytes, Base64.NO_WRAP)
                }
            }
            if (!scopeIsCurrent()) return@launch
            if (source.isFailure) {
                _imageEditState.value = ImageEditState.Error(
                    source.exceptionOrNull()?.message
                        ?: "No se pudo leer la referencia aprobada.",
                )
                return@launch
            }
            container.liveSession.editVisualAssetImage(
                imageBase64 = source.getOrThrow(),
                mimeType = parentAsset.mime_type,
                projectId = parentAsset.project_id,
                characterId = characterId,
                instruction = clean,
                kind = parentAsset.kind,
                perspective = parentAsset.perspective,
                parentAssetId = parentAsset.asset_id,
                parentSha256 = parentAsset.sha256,
                derivation = "EDIT",
                mode = mode,
                model = model,
                preserveIdentity = preserveIdentity,
                aspectRatio = aspectRatio,
            ).fold(
                onSuccess = { reply ->
                    if (!scopeIsCurrent()) return@fold
                    val staged = withContext(Dispatchers.IO) {
                        container.attachmentStore.stageGeneratedBase64(reply.data_base64)
                    }
                    if (!scopeIsCurrent()) {
                        staged?.let { deleteVisualStudioAttachments(listOf(it.attachmentId)) }
                        return@fold
                    }
                    val child = reply.visual_asset
                    if (staged == null || child == null) {
                        staged?.let { deleteVisualStudioAttachments(listOf(it.attachmentId)) }
                        _imageEditState.value = ImageEditState.Error(
                            "JARVIS creó la edición, pero Android no pudo conservar el candidato.",
                        )
                        return@fold
                    }
                    _lastImageEditDetails.value = ImageGenerationDetails(
                        reply.provider,
                        reply.model,
                        reply.requested_mode,
                        reply.fallback_used,
                        reply.attempt_count,
                        reply.duration_ms,
                    )
                    _characterStudio.update { current ->
                        if (
                            current.projectId != parentAsset.project_id ||
                            current.characterId != characterId
                        ) {
                            current
                        } else {
                            current.copy(
                                attachmentIds = current.attachmentIds +
                                    (child.asset_id to staged.attachmentId),
                                notice = if (reply.storage_retry_required) {
                                    "La edición hija quedó como CANDIDATE, pero Drive requiere reintento. El parent aprobado permanece intacto."
                                } else {
                                    "Edición hija guardada como CANDIDATE. El parent aprobado permanece intacto hasta otra aprobación humana."
                                },
                                error = null,
                            )
                        }
                    }
                    reloadCharacterStudio(parentAsset.project_id, characterId)
                    if (scopeIsCurrent()) {
                        _imageEditState.value = ImageEditState.Success(staged.attachmentId)
                    }
                },
                onFailure = { error ->
                    if (!scopeIsCurrent()) return@fold
                    _imageEditState.value = ImageEditState.Error(
                        error.message ?: "No se pudo editar la referencia visual.",
                    )
                },
            )
        }
    }

    fun createCharacterReferencePack(projectId: String, characterId: String) {
        val state = _characterStudio.value
        if (state.busy) return
        val approved = state.assets.filter { it.status == "APPROVED" }
        val ordering = compareBy<VisualStudioAsset>({ it.visual_revision }, { it.approved_utc })
        val master = approved
            .filter { it.kind == "PRIMARY_REFERENCE" }
            .maxWithOrNull(ordering)
        val views = approved
            .filter { it.kind == "IDENTITY_PACK" && it.perspective.isNotBlank() }
            .groupBy { it.perspective }
            .values
            .mapNotNull { items -> items.maxWithOrNull(ordering) }
            .sortedBy { it.perspective }
        if (master == null) {
            _characterStudio.update {
                it.copy(error = "Aprueba primero un master del personaje.")
            }
            return
        }
        if (views.isEmpty()) {
            _characterStudio.update {
                it.copy(error = "Aprueba al menos una vista de turnaround antes de crear el pack.")
            }
            return
        }
        _characterStudio.update {
            it.copy(
                busy = true,
                busyLabel = "Creando revisión de Reference Pack",
                notice = null,
                error = null,
            )
        }
        viewModelScope.launch {
            val created = container.liveSession.visualReferencePackCreate(
                projectId = projectId,
                characterId = characterId,
                masterAssetId = master.asset_id,
                masterSha256 = master.sha256,
                parentPackId = state.detail?.active_reference_pack?.pack_id.orEmpty(),
            )
            if (created.isFailure) {
                _characterStudio.update {
                    it.copy(
                        busy = false,
                        busyLabel = "",
                        error = created.exceptionOrNull()?.message ?: "No se pudo crear el pack.",
                    )
                }
                return@launch
            }
            var pack = created.getOrThrow().pack
            for (view in views) {
                val added = container.liveSession.visualReferencePackAddSlot(
                    projectId = projectId,
                    packId = pack.pack_id,
                    slotKey = view.perspective,
                    assetId = view.asset_id,
                    assetSha256 = view.sha256,
                    required = true,
                )
                if (added.isFailure) {
                    _characterStudio.update {
                        it.copy(
                            busy = false,
                            busyLabel = "",
                            error = added.exceptionOrNull()?.message
                                ?: "No se pudo agregar ${view.perspective} al pack.",
                        )
                    }
                    return@launch
                }
                pack = added.getOrThrow().pack
            }
            _characterStudio.update {
                it.copy(
                    busy = false,
                    busyLabel = "",
                    notice = "Pack revisión ${pack.revision} creado en DRAFT. Todavía no está aprobado.",
                )
            }
            reloadCharacterStudio(projectId, characterId)
        }
    }

    fun prepareCharacterReferencePack(projectId: String, characterId: String, packId: String) {
        if (_characterStudio.value.busy) return
        _characterStudio.update {
            it.copy(
                busy = true,
                busyLabel = "Validando pack",
                notice = null,
                error = null,
            )
        }
        viewModelScope.launch {
            container.liveSession.visualReferencePackPrepare(projectId, packId).fold(
                onSuccess = { result ->
                    _characterStudio.update {
                        it.copy(
                            notice = "Pack revisión ${result.pack.revision} listo para aprobación humana.",
                        )
                    }
                    reloadCharacterStudio(projectId, characterId)
                },
                onFailure = { error ->
                    _characterStudio.update {
                        it.copy(
                            busy = false,
                            busyLabel = "",
                            error = error.message ?: "El pack no está listo para aprobación.",
                        )
                    }
                },
            )
        }
    }

    fun approveCharacterReferencePack(projectId: String, characterId: String, packId: String) {
        if (_characterStudio.value.busy) return
        _characterStudio.update {
            it.copy(
                busy = true,
                busyLabel = "Aprobando Reference Pack",
                notice = null,
                error = null,
            )
        }
        viewModelScope.launch {
            container.liveSession.visualReferencePackApprove(projectId, packId).fold(
                onSuccess = { result ->
                    _characterStudio.update {
                        it.copy(
                            notice = "Reference Pack revisión ${result.pack.revision} aprobado y activo.",
                        )
                    }
                    reloadCharacterStudio(projectId, characterId)
                    refreshWritingWorkspace(projectId)
                },
                onFailure = { error ->
                    _characterStudio.update {
                        it.copy(
                            busy = false,
                            busyLabel = "",
                            error = error.message ?: "No se pudo aprobar el Reference Pack.",
                        )
                    }
                },
            )
        }
    }

    fun openLocationStudio(projectId: String, locationId: String) {
        val cleanProject = projectId.trim()
        val cleanLocation = locationId.trim()
        if (cleanProject.isBlank() || cleanLocation.isBlank()) return
        val current = _locationStudio.value
        if (current.projectId != cleanProject || current.locationId != cleanLocation) {
            locationStudioScopeVersion++
            val oldIds = current.attachmentIds.values
            _locationStudio.value = LocationStudioState(
                projectId = cleanProject,
                locationId = cleanLocation,
            )
            deleteVisualStudioAttachments(oldIds)
        }
        refreshLocationStudio(cleanProject, cleanLocation)
    }

    fun clearLocationStudioMessage() {
        _locationStudio.value = _locationStudio.value.copy(notice = null, error = null)
    }

    fun refreshLocationStudio(projectId: String, locationId: String) {
        val cleanProject = projectId.trim()
        val cleanLocation = locationId.trim()
        if (cleanProject.isBlank() || cleanLocation.isBlank()) return
        if (_locationStudio.value.projectId != cleanProject || _locationStudio.value.locationId != cleanLocation) {
            locationStudioScopeVersion++
        }
        _locationStudio.value = _locationStudio.value.copy(
            projectId = cleanProject,
            locationId = cleanLocation,
            busy = true,
            busyLabel = "Actualizando Location Studio",
            error = null,
        )
        viewModelScope.launch {
            reloadLocationStudio(cleanProject, cleanLocation)
        }
    }

    private suspend fun reloadLocationStudio(projectId: String, locationId: String) {
        val detail = container.liveSession.visualLocationDetail(projectId, locationId)
        val assets = container.liveSession.visualLocationAssetList(projectId, locationId)
        if (
            _locationStudio.value.projectId != projectId ||
            _locationStudio.value.locationId != locationId
        ) return
        val failure = detail.exceptionOrNull() ?: assets.exceptionOrNull()
        _locationStudio.value = _locationStudio.value.copy(
            busy = false,
            busyLabel = "",
            detail = detail.getOrNull() ?: _locationStudio.value.detail,
            assets = assets.getOrNull()?.assets ?: _locationStudio.value.assets,
            error = failure?.message,
        )
    }

    fun loadLocationStudioAsset(projectId: String, assetId: String, retry: Boolean = false) {
        val clean = assetId.trim()
        val state = _locationStudio.value
        if (clean.isBlank() || state.projectId != projectId ||
            clean in state.loadingAssetIds ||
            (!retry && (state.attachmentIds.containsKey(clean) || state.assetErrors.containsKey(clean)))
        ) return
        val scopeVersion = locationStudioScopeVersion
        val entityId = state.locationId
        val oldId = if (retry) state.attachmentIds[clean] else null
        _locationStudio.update {
            it.copy(
                attachmentIds = if (retry) it.attachmentIds - clean else it.attachmentIds,
                loadingAssetIds = it.loadingAssetIds + clean,
                assetErrors = it.assetErrors - clean,
            )
        }
        oldId?.let { deleteVisualStudioAttachments(listOf(it)) }
        viewModelScope.launch {
            val result = loadVisualAsset(
                fetch = { container.liveSession.writingRoomVisualAssetFetch(projectId, clean) },
                stage = { content ->
                    withContext(Dispatchers.IO) {
                        container.attachmentStore.stageVisualAssetBase64(
                            content.image_base64, content.sha256,
                        )
                    }
                },
            )
            if (locationStudioScopeVersion != scopeVersion ||
                _locationStudio.value.projectId != projectId ||
                _locationStudio.value.locationId != entityId
            ) {
                result.getOrNull()?.let { deleteVisualStudioAttachments(listOf(it.attachmentId)) }
                return@launch
            }
            _locationStudio.update {
                val staged = result.getOrNull()
                it.copy(
                    loadingAssetIds = it.loadingAssetIds - clean,
                    attachmentIds = if (staged != null) it.attachmentIds + (clean to staged.attachmentId) else it.attachmentIds,
                    assetErrors = if (staged == null) it.assetErrors + (clean to
                        (result.exceptionOrNull()?.message ?: "No se pudo cargar la imagen."))
                        else it.assetErrors - clean,
                )
            }
        }
    }

    private suspend fun rememberLocationStudioCandidate(
        projectId: String,
        locationId: String,
        reply: VisualStudioGeneratedImage,
    ) {
        val asset = reply.visual_asset ?: return
        val staged = withContext(Dispatchers.IO) {
            container.attachmentStore.stageVisualAssetBase64(reply.data_base64, asset.sha256)
        }
        if (
            staged != null &&
            _locationStudio.value.projectId == projectId &&
            _locationStudio.value.locationId == locationId
        ) {
            _locationStudio.update {
                it.copy(
                    attachmentIds = it.attachmentIds +
                        (asset.asset_id to staged.attachmentId),
                    notice = if (reply.storage_retry_required) {
                        "La imagen de locación quedó como candidata, pero Drive necesita reintentar el guardado. No se repetirá la generación."
                    } else {
                        "Nueva imagen de locación guardada como candidata. Aún no forma parte del canon visual."
                    },
                )
            }
        }
    }

    fun generateLocationVisual(
        projectId: String,
        locationId: String,
        prompt: String,
        kind: String,
        perspective: String,
        parentAsset: VisualStudioAsset? = null,
    ) {
        val cleanPrompt = prompt.trim()
        if (cleanPrompt.isBlank() || _locationStudio.value.busy) return
        _locationStudio.update {
            it.copy(
                busy = true,
                busyLabel = if (kind == "LOCATION_REFERENCE") {
                    "Generando master de locación"
                } else {
                    "Generando variante ${perspective.replace('_', ' ')}"
                },
                notice = null,
                error = null,
            )
        }
        viewModelScope.launch {
            val result = container.liveSession.generateLocationVisualAssetImage(
                projectId = projectId,
                locationId = locationId,
                prompt = cleanPrompt,
                kind = kind,
                perspective = perspective,
                mode = "quality",
                aspectRatio = "landscape",
                referencePerspectives = if (kind == "LOCATION_VARIANT") {
                    listOf("establishing")
                } else {
                    emptyList()
                },
                referencesPerLocation = 2,
                parentAssetId = parentAsset?.asset_id.orEmpty(),
                parentSha256 = parentAsset?.sha256.orEmpty(),
                derivation = if (parentAsset == null) "" else "REGENERATION",
            )
            if (result.isFailure) {
                _locationStudio.update {
                    it.copy(
                        busy = false,
                        busyLabel = "",
                        error = result.exceptionOrNull()?.message
                            ?: "No se pudo generar la locación.",
                    )
                }
                return@launch
            }
            rememberLocationStudioCandidate(
                projectId,
                locationId,
                result.getOrThrow(),
            )
            reloadLocationStudio(projectId, locationId)
        }
    }

    fun uploadLocationMaster(
        projectId: String,
        locationId: String,
        uri: Uri,
    ) {
        if (_locationStudio.value.busy) return
        _locationStudio.update {
            it.copy(
                busy = true,
                busyLabel = "Guardando master manual",
                notice = null,
                error = null,
            )
        }
        viewModelScope.launch {
            val staged = withContext(Dispatchers.IO) {
                container.attachmentStore.stageFrom(uri)
            }
            if (staged == null) {
                _locationStudio.update {
                    it.copy(
                        busy = false,
                        busyLabel = "",
                        error = "No se pudo leer la imagen seleccionada.",
                    )
                }
                return@launch
            }
            val source = withContext(Dispatchers.IO) {
                runCatching {
                    val file = container.attachmentStore.resolve(staged.attachmentId)
                        ?: error("La imagen temporal ya no está disponible.")
                    val bytes = file.readBytes()
                    require(bytes.isNotEmpty() && bytes.size <= 12 * 1024 * 1024) {
                        "La imagen es demasiado grande."
                    }
                    Base64.encodeToString(bytes, Base64.NO_WRAP)
                }
            }
            if (source.isFailure) {
                withContext(Dispatchers.IO) {
                    container.attachmentStore.delete(staged.attachmentId)
                }
                _locationStudio.update {
                    it.copy(
                        busy = false,
                        busyLabel = "",
                        error = source.exceptionOrNull()?.message
                            ?: "No se pudo leer la imagen.",
                    )
                }
                return@launch
            }
            val mime = app.contentResolver.getType(uri)
                ?.lowercase()
                ?.takeIf { it in setOf("image/png", "image/jpeg", "image/webp") }
                ?: "image/jpeg"
            container.liveSession.visualLocationAssetIngest(
                projectId = projectId,
                imageBase64 = source.getOrThrow(),
                mimeType = mime,
                kind = "LOCATION_REFERENCE",
                source = "MANUAL_UPLOAD",
                locationId = locationId,
                perspective = "establishing",
            ).fold(
                onSuccess = { response ->
                    _locationStudio.update {
                        it.copy(
                            attachmentIds = it.attachmentIds +
                                (response.asset.asset_id to staged.attachmentId),
                            notice = "Master de locación guardado como candidato. Revisa y aprueba el hash exacto.",
                        )
                    }
                    reloadLocationStudio(projectId, locationId)
                },
                onFailure = { error ->
                    withContext(Dispatchers.IO) {
                        container.attachmentStore.delete(staged.attachmentId)
                    }
                    _locationStudio.update {
                        it.copy(
                            busy = false,
                            busyLabel = "",
                            error = error.message
                                ?: "No se pudo guardar el master de locación.",
                        )
                    }
                },
            )
        }
    }

    fun approveLocationVisual(asset: VisualStudioAsset) {
        val state = _locationStudio.value
        if (state.busy || asset.status != "CANDIDATE" || asset.sha256.isBlank()) return
        val nextRevision = state.assets
            .filter {
                it.status == "APPROVED" &&
                    it.kind == asset.kind &&
                    it.perspective == asset.perspective
            }
            .maxOfOrNull { it.visual_revision }
            ?.plus(1)
            ?: 1
        _locationStudio.update {
            it.copy(
                busy = true,
                busyLabel = "Aprobando hash exacto",
                notice = null,
                error = null,
            )
        }
        viewModelScope.launch {
            container.liveSession.visualAssetApproveExact(
                projectId = asset.project_id,
                assetId = asset.asset_id,
                assetSha256 = asset.sha256,
                visualRevision = nextRevision,
            ).fold(
                onSuccess = {
                    _locationStudio.update {
                        it.copy(
                            notice = "Imagen de locación aprobada y ligada a su SHA-256 exacto.",
                        )
                    }
                    reloadLocationStudio(asset.project_id, state.locationId)
                },
                onFailure = { error ->
                    _locationStudio.update {
                        it.copy(
                            busy = false,
                            busyLabel = "",
                            error = error.message
                                ?: "No se pudo aprobar la imagen de locación.",
                        )
                    }
                },
            )
        }
    }

    fun retryLocationVisualStorage(asset: VisualStudioAsset) {
        val state = _locationStudio.value
        if (state.busy || asset.storage.state == "stored") return
        val attachmentId = state.attachmentIds[asset.asset_id]
        if (attachmentId.isNullOrBlank()) {
            _locationStudio.update {
                it.copy(
                    error = "Los bytes locales del candidato ya no están disponibles. JARVIS no regenerará la imagen automáticamente.",
                )
            }
            return
        }
        _locationStudio.update {
            it.copy(
                busy = true,
                busyLabel = "Reintentando guardado en Drive",
                notice = null,
                error = null,
            )
        }
        viewModelScope.launch {
            val encoded = withContext(Dispatchers.IO) {
                runCatching {
                    val file = container.attachmentStore.resolve(attachmentId)
                        ?: error("Los bytes locales del candidato ya no están disponibles.")
                    Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
                }
            }
            if (encoded.isFailure) {
                _locationStudio.update {
                    it.copy(
                        busy = false,
                        busyLabel = "",
                        error = encoded.exceptionOrNull()?.message,
                    )
                }
                return@launch
            }
            container.liveSession.visualLocationAssetIngest(
                projectId = asset.project_id,
                imageBase64 = encoded.getOrThrow(),
                mimeType = asset.mime_type,
                kind = asset.kind,
                source = asset.source,
                locationId = state.locationId,
                perspective = asset.perspective,
                assetId = asset.asset_id,
                parentAssetId = asset.parent_asset_id,
                parentSha256 = asset.parent_sha256,
                derivation = asset.derivation,
            ).fold(
                onSuccess = {
                    _locationStudio.update {
                        it.copy(
                            notice = "Guardado de locación en Drive reanudado sin repetir la generación.",
                        )
                    }
                    reloadLocationStudio(asset.project_id, state.locationId)
                },
                onFailure = { error ->
                    _locationStudio.update {
                        it.copy(
                            busy = false,
                            busyLabel = "",
                            error = error.message
                                ?: "Drive sigue sin poder guardar el candidato.",
                        )
                    }
                },
            )
        }
    }

    fun editLocationVisual(
        projectId: String,
        locationId: String,
        parentAsset: VisualStudioAsset,
        instruction: String,
    ) {
        val clean = instruction.trim()
        if (
            clean.isBlank() ||
            _locationStudio.value.busy ||
            parentAsset.status != "APPROVED"
        ) return
        _locationStudio.update {
            it.copy(
                busy = true,
                busyLabel = "Creando edición hija de locación",
                notice = null,
                error = null,
            )
        }
        viewModelScope.launch {
            val source = container.liveSession.writingRoomVisualAssetFetch(
                projectId,
                parentAsset.asset_id,
            )
            if (source.isFailure) {
                _locationStudio.update {
                    it.copy(
                        busy = false,
                        busyLabel = "",
                        error = source.exceptionOrNull()?.message
                            ?: "No se pudo cargar la referencia aprobada.",
                    )
                }
                return@launch
            }
            val content = source.getOrThrow()
            val result = container.liveSession.editLocationVisualAssetImage(
                imageBase64 = content.image_base64,
                mimeType = content.mime_type,
                projectId = projectId,
                locationId = locationId,
                instruction = clean,
                kind = parentAsset.kind,
                perspective = parentAsset.perspective,
                parentAssetId = parentAsset.asset_id,
                parentSha256 = parentAsset.sha256,
            )
            if (result.isFailure) {
                _locationStudio.update {
                    it.copy(
                        busy = false,
                        busyLabel = "",
                        error = result.exceptionOrNull()?.message
                            ?: "No se pudo editar la locación.",
                    )
                }
                return@launch
            }
            rememberLocationStudioCandidate(
                projectId,
                locationId,
                result.getOrThrow(),
            )
            reloadLocationStudio(projectId, locationId)
        }
    }

    fun createLocationReferencePack(projectId: String, locationId: String) {
        val state = _locationStudio.value
        if (state.busy) return
        val approved = state.assets.filter { it.status == "APPROVED" }
        val ordering = compareBy<VisualStudioAsset>(
            { it.visual_revision },
            { it.approved_utc },
        )
        val master = approved
            .filter { it.kind == "LOCATION_REFERENCE" }
            .maxWithOrNull(ordering)
        val variants = approved
            .filter {
                it.kind == "LOCATION_VARIANT" &&
                    it.perspective.isNotBlank()
            }
            .groupBy { it.perspective }
            .values
            .mapNotNull { items -> items.maxWithOrNull(ordering) }
            .sortedBy { it.perspective }
        if (master == null) {
            _locationStudio.update {
                it.copy(
                    error = "Aprueba primero un master de la locación.",
                )
            }
            return
        }
        _locationStudio.update {
            it.copy(
                busy = true,
                busyLabel = "Creando revisión del Location Reference Pack",
                notice = null,
                error = null,
            )
        }
        viewModelScope.launch {
            val created = container.liveSession.visualLocationReferencePackCreate(
                projectId = projectId,
                locationId = locationId,
                masterAssetId = master.asset_id,
                masterSha256 = master.sha256,
                parentPackId = state.detail
                    ?.active_reference_pack
                    ?.pack_id
                    .orEmpty(),
            )
            if (created.isFailure) {
                _locationStudio.update {
                    it.copy(
                        busy = false,
                        busyLabel = "",
                        error = created.exceptionOrNull()?.message
                            ?: "No se pudo crear el pack de locación.",
                    )
                }
                return@launch
            }
            var pack = created.getOrThrow().pack
            for (variant in variants) {
                val added = container.liveSession.visualLocationReferencePackAddSlot(
                    projectId = projectId,
                    packId = pack.pack_id,
                    slotKey = variant.perspective,
                    assetId = variant.asset_id,
                    assetSha256 = variant.sha256,
                    required = false,
                )
                if (added.isFailure) {
                    _locationStudio.update {
                        it.copy(
                            busy = false,
                            busyLabel = "",
                            error = added.exceptionOrNull()?.message
                                ?: "No se pudo agregar ${variant.perspective} al pack.",
                        )
                    }
                    return@launch
                }
                pack = added.getOrThrow().pack
            }
            _locationStudio.update {
                it.copy(
                    busy = false,
                    busyLabel = "",
                    notice = "Location Pack revisión ${pack.revision} creado en DRAFT.",
                )
            }
            reloadLocationStudio(projectId, locationId)
        }
    }

    fun prepareLocationReferencePack(
        projectId: String,
        locationId: String,
        packId: String,
    ) {
        if (_locationStudio.value.busy) return
        _locationStudio.update {
            it.copy(
                busy = true,
                busyLabel = "Validando Location Pack",
                notice = null,
                error = null,
            )
        }
        viewModelScope.launch {
            container.liveSession.visualLocationReferencePackPrepare(
                projectId,
                packId,
            ).fold(
                onSuccess = { result ->
                    _locationStudio.update {
                        it.copy(
                            notice = "Location Pack revisión ${result.pack.revision} listo para aprobación humana.",
                        )
                    }
                    reloadLocationStudio(projectId, locationId)
                },
                onFailure = { error ->
                    _locationStudio.update {
                        it.copy(
                            busy = false,
                            busyLabel = "",
                            error = error.message
                                ?: "El Location Pack no está listo.",
                        )
                    }
                },
            )
        }
    }

    fun approveLocationReferencePack(
        projectId: String,
        locationId: String,
        packId: String,
    ) {
        if (_locationStudio.value.busy) return
        _locationStudio.update {
            it.copy(
                busy = true,
                busyLabel = "Aprobando Location Pack",
                notice = null,
                error = null,
            )
        }
        viewModelScope.launch {
            container.liveSession.visualLocationReferencePackApprove(
                projectId,
                packId,
            ).fold(
                onSuccess = { result ->
                    _locationStudio.update {
                        it.copy(
                            notice = "Location Pack revisión ${result.pack.revision} aprobado y activo.",
                        )
                    }
                    reloadLocationStudio(projectId, locationId)
                    refreshWritingWorkspace(projectId)
                },
                onFailure = { error ->
                    _locationStudio.update {
                        it.copy(
                            busy = false,
                            busyLabel = "",
                            error = error.message
                                ?: "No se pudo aprobar el Location Pack.",
                        )
                    }
                },
            )
        }
    }

    fun openWorldMap(projectId: String) {
        val cleanProject = projectId.trim()
        if (cleanProject.isBlank()) return
        if (_worldMap.value.projectId != cleanProject) {
            _worldMap.value = WorldMapState(projectId = cleanProject)
        }
        refreshWorldMap(cleanProject)
    }

    fun clearWorldMapMessage() {
        _worldMap.value = _worldMap.value.copy(
            notice = null,
            error = null,
            conflict = null,
        )
    }

    fun refreshWorldMap(projectId: String) {
        val cleanProject = projectId.trim()
        if (cleanProject.isBlank()) return
        _worldMap.value = _worldMap.value.copy(
            projectId = cleanProject,
            busy = true,
            busyLabel = "Actualizando World Map",
            error = null,
        )
        viewModelScope.launch {
            reloadWorldMap(cleanProject)
        }
    }

    private suspend fun reloadWorldMap(projectId: String) {
        val statusResult = container.liveSession.worldMapStatus(projectId)
        if (_worldMap.value.projectId != projectId) return
        if (statusResult.isFailure) {
            _worldMap.value = _worldMap.value.copy(
                busy = false,
                busyLabel = "",
                error = statusResult.exceptionOrNull()?.message
                    ?: "No se pudo cargar el estado del World Map.",
            )
            return
        }
        val status = statusResult.getOrThrow()
        val targetRevision = status.draft?.revision_id
            ?.takeIf { it.isNotBlank() }
            ?: status.active_revision_id.takeIf { it.isNotBlank() }
        if (targetRevision == null) {
            _worldMap.value = _worldMap.value.copy(
                busy = false,
                busyLabel = "",
                status = status,
                map = null,
                error = null,
            )
            return
        }
        val mapResult = container.liveSession.worldMapGet(
            projectId,
            targetRevision,
        )
        if (_worldMap.value.projectId != projectId) return
        _worldMap.value = if (mapResult.isSuccess) {
            _worldMap.value.copy(
                busy = false,
                busyLabel = "",
                status = status,
                map = mapResult.getOrThrow(),
                error = null,
            )
        } else {
            _worldMap.value.copy(
                busy = false,
                busyLabel = "",
                status = status,
                error = mapResult.exceptionOrNull()?.message
                    ?: "No se pudo cargar la revisión del World Map.",
            )
        }
    }

    private suspend fun handleWorldMapFailure(
        projectId: String,
        error: Throwable?,
        fallbackMessage: String,
    ) {
        if (error is WorldMapConflictException) {
            _worldMap.value = _worldMap.value.copy(
                busy = false,
                busyLabel = "",
                conflict = WorldMapConflictNotice(
                    currentVersion = error.currentVersion,
                    currentHash = error.currentHash,
                    message = "El mapa cambió en el servidor. Se actualizó antes de sobrescribir una revisión más nueva.",
                ),
                error = null,
            )
            reloadWorldMap(projectId)
            return
        }
        _worldMap.value = _worldMap.value.copy(
            busy = false,
            busyLabel = "",
            error = error?.message ?: fallbackMessage,
        )
    }

    fun createWorldMapDraft(projectId: String) {
        val state = _worldMap.value
        if (state.busy) return
        _worldMap.value = state.copy(
            projectId = projectId,
            busy = true,
            busyLabel = "Creando revisión del World Map",
            notice = null,
            error = null,
            conflict = null,
        )
        viewModelScope.launch {
            container.liveSession.worldMapCreateRevision(
                projectId = projectId,
                title = "World Map",
                parentRevisionId = state.status?.active_revision_id.orEmpty(),
            ).fold(
                onSuccess = { result ->
                    _worldMap.value = _worldMap.value.copy(
                        busy = false,
                        busyLabel = "",
                        map = result,
                        notice = "Nueva revisión del World Map creada en DRAFT.",
                    )
                    reloadWorldMap(projectId)
                },
                onFailure = { error ->
                    handleWorldMapFailure(
                        projectId,
                        error,
                        "No se pudo crear la revisión del World Map.",
                    )
                },
            )
        }
    }

    fun upsertWorldMapNode(
        projectId: String,
        nodeId: String?,
        nodeType: String,
        name: String,
        parentNodeId: String,
        locationId: String,
        x: Double,
        y: Double,
        z: Double = 0.0,
    ) {
        val state = _worldMap.value
        val current = state.map ?: return
        if (state.busy || current.revision.state != "DRAFT") return
        val cleanName = name.trim()
        if (cleanName.isBlank()) return
        _worldMap.value = state.copy(
            busy = true,
            busyLabel = "Guardando posición en World Map",
            notice = null,
            error = null,
            conflict = null,
        )
        viewModelScope.launch {
            var visualAssetId = ""
            var visualAssetSha256 = ""
            if (
                nodeType in setOf("LOCATION", "SUBLOCATION") &&
                locationId.isNotBlank()
            ) {
                val detail = container.liveSession.visualLocationDetail(
                    projectId,
                    locationId,
                ).getOrNull()
                val approvedPrimary = detail?.gallery?.primary
                    ?.takeIf { it.status == "APPROVED" }
                if (approvedPrimary != null) {
                    visualAssetId = approvedPrimary.asset_id
                    visualAssetSha256 = approvedPrimary.sha256
                }
            }
            container.liveSession.worldMapUpsertNode(
                projectId = projectId,
                revisionId = current.revision.revision_id,
                expectedVersion = current.revision.version,
                nodeId = nodeId,
                nodeType = nodeType,
                name = cleanName,
                parentNodeId = parentNodeId,
                locationId = locationId,
                x = x,
                y = y,
                z = z,
                placementSource = "MANUAL",
                visualAssetId = visualAssetId,
                visualAssetSha256 = visualAssetSha256,
            ).fold(
                onSuccess = { result ->
                    _worldMap.value = _worldMap.value.copy(
                        busy = false,
                        busyLabel = "",
                        map = result,
                        notice = "World Map actualizado.",
                    )
                    reloadWorldMap(projectId)
                },
                onFailure = { error ->
                    handleWorldMapFailure(
                        projectId,
                        error,
                        "No se pudo actualizar el World Map.",
                    )
                },
            )
        }
    }

    fun upsertWorldMapPresence(
        projectId: String,
        characterId: String,
        nodeId: String,
        temporalKind: String,
        temporalRef: String,
        evidenceSourceId: String,
        evidenceSourceType: String = "chapter",
        evidenceLines: String = "",
        presenceId: String? = null,
    ) {
        val state = _worldMap.value
        val current = state.map ?: return
        if (state.busy || current.revision.state != "DRAFT") return
        val cleanCharacter = characterId.trim()
        val cleanNode = nodeId.trim()
        val cleanSource = evidenceSourceId.trim()
        val cleanTemporal = temporalKind.trim().uppercase()
        val cleanTemporalRef = temporalRef.trim()
        if (
            cleanCharacter.isBlank() ||
            cleanNode.isBlank() ||
            cleanSource.isBlank() ||
            cleanTemporal !in setOf("OCCURRED", "FUTURE", "UNKNOWN") ||
            (cleanTemporal != "UNKNOWN" && cleanTemporalRef.isBlank())
        ) return
        _worldMap.value = state.copy(
            busy = true,
            busyLabel = "Guardando presencia con evidencia",
            notice = null,
            error = null,
            conflict = null,
        )
        viewModelScope.launch {
            container.liveSession.worldMapUpsertPresence(
                projectId = projectId,
                revisionId = current.revision.revision_id,
                expectedVersion = current.revision.version,
                presenceId = presenceId,
                characterId = cleanCharacter,
                nodeId = cleanNode,
                temporalKind = cleanTemporal,
                temporalRef = cleanTemporalRef,
                evidence = listOf(
                    WorldMapEvidence(
                        source_id = cleanSource,
                        source_type = evidenceSourceType.trim()
                            .lowercase()
                            .ifBlank { "chapter" },
                        chapter_id = if (
                            evidenceSourceType.equals(
                                "chapter",
                                ignoreCase = true,
                            )
                        ) {
                            cleanSource
                        } else {
                            ""
                        },
                        lines = evidenceLines.trim(),
                    ),
                ),
            ).fold(
                onSuccess = { result ->
                    _worldMap.value = _worldMap.value.copy(
                        busy = false,
                        busyLabel = "",
                        map = result,
                        notice = "Presencia guardada con evidencia explícita.",
                    )
                    reloadWorldMap(projectId)
                },
                onFailure = { error ->
                    handleWorldMapFailure(
                        projectId,
                        error,
                        "No se pudo guardar la presencia.",
                    )
                },
            )
        }
    }

    fun removeWorldMapPresence(
        projectId: String,
        presenceId: String,
    ) {
        val state = _worldMap.value
        val current = state.map ?: return
        if (
            state.busy ||
            current.revision.state != "DRAFT" ||
            presenceId.isBlank()
        ) return
        _worldMap.value = state.copy(
            busy = true,
            busyLabel = "Eliminando presencia del World Map",
            notice = null,
            error = null,
            conflict = null,
        )
        viewModelScope.launch {
            container.liveSession.worldMapRemovePresence(
                projectId = projectId,
                revisionId = current.revision.revision_id,
                expectedVersion = current.revision.version,
                presenceId = presenceId,
            ).fold(
                onSuccess = { result ->
                    _worldMap.value = _worldMap.value.copy(
                        busy = false,
                        busyLabel = "",
                        map = result,
                        notice = "Presencia eliminada de la revisión DRAFT.",
                    )
                    reloadWorldMap(projectId)
                },
                onFailure = { error ->
                    handleWorldMapFailure(
                        projectId,
                        error,
                        "No se pudo eliminar la presencia.",
                    )
                },
            )
        }
    }

    fun removeWorldMapNode(projectId: String, nodeId: String) {
        val state = _worldMap.value
        val current = state.map ?: return
        if (state.busy || current.revision.state != "DRAFT") return
        _worldMap.value = state.copy(
            busy = true,
            busyLabel = "Eliminando nodo del World Map",
            notice = null,
            error = null,
            conflict = null,
        )
        viewModelScope.launch {
            container.liveSession.worldMapRemoveNode(
                projectId = projectId,
                revisionId = current.revision.revision_id,
                expectedVersion = current.revision.version,
                nodeId = nodeId,
            ).fold(
                onSuccess = { result ->
                    _worldMap.value = _worldMap.value.copy(
                        busy = false,
                        busyLabel = "",
                        map = result,
                        notice = "Nodo eliminado de la revisión DRAFT.",
                    )
                    reloadWorldMap(projectId)
                },
                onFailure = { error ->
                    handleWorldMapFailure(
                        projectId,
                        error,
                        "No se pudo eliminar el nodo.",
                    )
                },
            )
        }
    }

    fun approveWorldMapRevision(projectId: String) {
        val state = _worldMap.value
        val current = state.map ?: return
        if (
            state.busy ||
            current.revision.state != "DRAFT" ||
            current.revision.content_hash.isBlank()
        ) return
        _worldMap.value = state.copy(
            busy = true,
            busyLabel = "Aprobando revisión del World Map",
            notice = null,
            error = null,
            conflict = null,
        )
        viewModelScope.launch {
            container.liveSession.worldMapApproveRevision(
                projectId = projectId,
                revisionId = current.revision.revision_id,
                expectedVersion = current.revision.version,
                expectedHash = current.revision.content_hash,
            ).fold(
                onSuccess = { result ->
                    _worldMap.value = _worldMap.value.copy(
                        busy = false,
                        busyLabel = "",
                        map = result,
                        notice = "World Map revisión ${result.revision.revision} aprobada y activa.",
                    )
                    reloadWorldMap(projectId)
                },
                onFailure = { error ->
                    handleWorldMapFailure(
                        projectId,
                        error,
                        "No se pudo aprobar la revisión del World Map.",
                    )
                },
            )
        }
    }

    sealed interface ImageEditState {
        data object Idle : ImageEditState
        data object Busy : ImageEditState
        data class Success(val attachmentId: String) : ImageEditState
        data class Error(val message: String) : ImageEditState
    }

    private val _imageEditState = MutableStateFlow<ImageEditState>(ImageEditState.Idle)
    val imageEditState: StateFlow<ImageEditState> = _imageEditState
    private val _lastImageEditDetails = MutableStateFlow<ImageGenerationDetails?>(null)
    val lastImageEditDetails: StateFlow<ImageGenerationDetails?> = _lastImageEditDetails

    fun generateImage(prompt: String, mode: String = "speed", model: String? = null) {
        val clean = prompt.trim()
        if (clean.isBlank() || _imageGenerationState.value is ImageGenerationState.Busy) return
        val conversationId = _conversationId.value ?: conversations.newConversationId().also {
            _conversationId.value = it
        }
        _imageGenerationState.value = ImageGenerationState.Busy
        _lastImageGenerationDetails.value = null
        viewModelScope.launch {
            container.liveSession.generateImage(clean, mode, model).fold(
                onSuccess = { reply ->
                    val staged = withContext(Dispatchers.IO) {
                        container.attachmentStore.stageGeneratedBase64(reply.dataBase64)
                    }
                    if (staged == null) {
                        _imageGenerationState.value = ImageGenerationState.Error(
                            "JARVIS generated an image, but Android could not decode it.",
                        )
                    } else {
                        conversations.recordGeneratedImage(
                            conversationId = conversationId,
                            prompt = clean,
                            attachmentId = staged.attachmentId,
                        )
                        _lastImageGenerationDetails.value = ImageGenerationDetails(
                            reply.provider, reply.model, reply.requestedMode,
                            reply.fallbackUsed, reply.attemptCount, reply.durationMs,
                        )
                        _imageGenerationState.value = ImageGenerationState.Idle
                    }
                },
                onFailure = { error ->
                    _imageGenerationState.value = ImageGenerationState.Error(
                        error.message ?: "Image generation failed",
                    )
                },
            )
        }
    }


    fun editImage(
        referenceAttachmentId: String,
        instruction: String,
        mode: String = "quality",
        model: String? = null,
        preserveIdentity: String = "high",
        aspectRatio: String = "square",
    ) {
        val clean = instruction.trim()
        if (clean.isBlank() || _imageEditState.value is ImageEditState.Busy) return
        // Image Studio is an isolated authoring surface. Its prompts and generated
        // versions must never be written into the active chat conversation.
        _imageEditState.value = ImageEditState.Busy
        _lastImageEditDetails.value = null

        viewModelScope.launch {
            val source = withContext(Dispatchers.IO) {
                runCatching {
                    val file = container.attachmentStore.resolve(referenceAttachmentId)
                        ?: error("Reference image is no longer available")
                    val bytes = file.readBytes()
                    require(bytes.isNotEmpty() && bytes.size <= 12 * 1024 * 1024) {
                        "Reference image is too large"
                    }
                    Base64.encodeToString(bytes, Base64.NO_WRAP)
                }
            }
            if (source.isFailure) {
                _imageEditState.value = ImageEditState.Error(
                    source.exceptionOrNull()?.message ?: "Could not read reference image",
                )
                return@launch
            }

            container.liveSession.editImage(
                imageBase64 = source.getOrThrow(),
                mimeType = "image/jpeg",
                instruction = clean,
                mode = mode,
                model = model,
                preserveIdentity = preserveIdentity,
                aspectRatio = aspectRatio,
            ).fold(
                onSuccess = { reply ->
                    val staged = withContext(Dispatchers.IO) {
                        container.attachmentStore.stageGeneratedBase64(reply.dataBase64)
                    }
                    if (staged == null) {
                        _imageEditState.value = ImageEditState.Error(
                            "JARVIS edited the image, but Android could not decode it.",
                        )
                    } else {
                        // Keep the edited version in Image Studio's local version chain only.
                        // The originating chat image remains in chat, but Studio derivatives do not.
                        _lastImageEditDetails.value = ImageGenerationDetails(
                            reply.provider,
                            reply.model,
                            reply.requestedMode,
                            reply.fallbackUsed,
                            reply.attemptCount,
                            reply.durationMs,
                        )
                        _imageEditState.value = ImageEditState.Success(staged.attachmentId)
                    }
                },
                onFailure = { error ->
                    _imageEditState.value = ImageEditState.Error(
                        error.message ?: "Image edit failed",
                    )
                },
            )
        }
    }

    fun resetImageEditState() {
        _imageEditState.value = ImageEditState.Idle
    }

    fun setWikiPrimaryReference(
        attachmentId: String,
        character: String,
        projectId: String = "prj_story",
    ) {
        startWikiPrimaryPublish(attachmentId, character, projectId, cleanupAfter = false)
    }

    fun setWikiPrimaryReferenceFromUri(
        uri: Uri,
        character: String,
        projectId: String = "prj_story",
    ) {
        if (_wikiPrimaryState.value is WikiPrimaryState.Busy) return
        _wikiPrimaryState.value = WikiPrimaryState.Busy
        viewModelScope.launch {
            val staged = withContext(Dispatchers.IO) { container.attachmentStore.stageFrom(uri) }
            if (staged == null) {
                _wikiPrimaryState.value = WikiPrimaryState.Error(
                    "No se pudo leer la imagen seleccionada.",
                )
                return@launch
            }
            publishWikiPrimaryReference(
                staged.attachmentId,
                character,
                projectId,
                cleanupAfter = true,
            )
        }
    }

    fun loadWikiVisual(projectId: String, assetId: String) {
        val clean = assetId.trim()
        if (clean.isBlank() || _wikiVisualAttachments.value.containsKey(clean)) return
        viewModelScope.launch {
            container.liveSession.writingRoomVisualAssetFetch(projectId, clean).fold(
                onSuccess = { content ->
                    val staged = withContext(Dispatchers.IO) {
                        container.attachmentStore.stageGeneratedBase64(content.image_base64)
                    }
                    if (staged != null) {
                        _wikiVisualAttachments.update { it + (clean to staged.attachmentId) }
                    }
                },
                onFailure = { /* Metadata still renders even if the portrait fetch is unavailable. */ },
            )
        }
    }

    private fun startWikiPrimaryPublish(
        attachmentId: String,
        character: String,
        projectId: String,
        cleanupAfter: Boolean,
    ) {
        if (_wikiPrimaryState.value is WikiPrimaryState.Busy) return
        _wikiPrimaryState.value = WikiPrimaryState.Busy
        viewModelScope.launch {
            publishWikiPrimaryReference(attachmentId, character, projectId, cleanupAfter)
        }
    }

    private suspend fun publishWikiPrimaryReference(
        attachmentId: String,
        character: String,
        projectId: String,
        cleanupAfter: Boolean,
    ) {
        val cleanCharacter = character.trim()
        if (cleanCharacter.isBlank()) {
            _wikiPrimaryState.value = WikiPrimaryState.Error("Selecciona un personaje de la Wiki.")
            if (cleanupAfter) withContext(Dispatchers.IO) { container.attachmentStore.delete(attachmentId) }
            return
        }
        val canonical = if (cleanCharacter.contains(":")) {
            cleanCharacter.lowercase()
        } else {
            "character:" + cleanCharacter.lowercase().replace(" ", "-")
        }
        val source = withContext(Dispatchers.IO) {
            runCatching {
                val file = container.attachmentStore.resolve(attachmentId)
                    ?: error("La imagen ya no está disponible.")
                val bytes = file.readBytes()
                require(bytes.isNotEmpty() && bytes.size <= 12 * 1024 * 1024) {
                    "La imagen es demasiado grande."
                }
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            }
        }
        if (source.isFailure) {
            _wikiPrimaryState.value = WikiPrimaryState.Error(
                source.exceptionOrNull()?.message ?: "No se pudo leer la imagen.",
            )
            if (cleanupAfter) withContext(Dispatchers.IO) { container.attachmentStore.delete(attachmentId) }
            return
        }

        container.liveSession.setWikiPrimaryReference(
            imageBase64 = source.getOrThrow(),
            mimeType = "image/jpeg",
            projectId = projectId,
            characterId = canonical,
            alt = "Referencia visual principal de " + cleanCharacter.substringAfter(":"),
        ).fold(
            onSuccess = { reply ->
                _wikiPrimaryState.value = WikiPrimaryState.Success(
                    reply.assetId, reply.characterId, reply.visualRevision,
                )
                // Keep the just-uploaded local copy keyed by the authoritative asset id
                // so the portrait renders immediately while the structured Wiki refreshes.
                _wikiVisualAttachments.update { it + (reply.assetId to attachmentId) }
                refreshWritingWorkspace(projectId)
            },
            onFailure = { error ->
                _wikiPrimaryState.value = WikiPrimaryState.Error(
                    error.message ?: "No se pudo actualizar la referencia visual de la Wiki.",
                )
                if (cleanupAfter) {
                    withContext(Dispatchers.IO) { container.attachmentStore.delete(attachmentId) }
                }
            },
        )
    }

    fun resetWikiPrimaryState() {
        _wikiPrimaryState.value = WikiPrimaryState.Idle
    }


    fun clearImageGenerationError() {
        if (_imageGenerationState.value is ImageGenerationState.Error) {
            _imageGenerationState.value = ImageGenerationState.Idle
        }
    }

    fun startNewConversation(onReady: (String) -> Unit = {}) {
        conversations.newConversation { id ->
            _conversationId.value = id
            onReady(id)
        }
    }

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val id = _conversationId.value ?: run {
            conversations.newConversation { newId ->
                _conversationId.value = newId
                conversations.send(newId, trimmed)
            }
            return
        }
        conversations.send(id, trimmed)
    }

    // ---- AND-W6 attachments ---------------------------------------------------

    private val _pendingAttachmentsByConversation =
        MutableStateFlow<Map<String, List<StagedAttachment>>>(emptyMap())

    /**
     * Pending media is conversation-local. Switching from chat A to chat B must
     * never carry an unsent attachment across the boundary.
     */
    val pendingAttachments: StateFlow<List<StagedAttachment>> = combine(
        _conversationId,
        _pendingAttachmentsByConversation,
    ) { conversationId, pendingByConversation ->
        conversationId?.let { pendingByConversation[it].orEmpty() }.orEmpty()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val attachmentStore get() = container.attachmentStore

    /**
     * Copy picked image into private storage and strip EXIF. File copy + JPEG
     * re-encode run on IO — never the main thread.
     */
    fun stageAttachment(uri: Uri) {
        viewModelScope.launch {
            val conversationId = _conversationId.value ?: conversations.newConversationId().also {
                _conversationId.value = it
            }
            val staged = withContext(Dispatchers.IO) {
                container.attachmentStore.stageFrom(uri)
            } ?: return@launch
            _pendingAttachmentsByConversation.update { current ->
                current + (conversationId to (current[conversationId].orEmpty() + staged))
            }
            if (container.transportMode != AppContainer.TransportMode.LIVE) {
                session.uploadAttachment(
                    attachmentId = staged.attachmentId,
                    conversationId = conversationId,
                    filename = staged.filename,
                    mimeType = staged.mimeType,
                    sizeBytes = staged.sizeBytes,
                )
            }
        }
    }

    fun removePendingAttachment(attachmentId: String) {
        val conversationId = _conversationId.value ?: return
        _pendingAttachmentsByConversation.update { current ->
            val remaining = current[conversationId].orEmpty()
                .filterNot { it.attachmentId == attachmentId }
            if (remaining.isEmpty()) current - conversationId
            else current + (conversationId to remaining)
        }
        viewModelScope.launch(Dispatchers.IO) { container.attachmentStore.delete(attachmentId) }
    }

    /** Send text and/or pending attachments (AND-W6: attachment IDs, never paths). */
    fun sendWithAttachments(text: String) {
        val trimmed = text.trim()
        val id = _conversationId.value ?: conversations.newConversationId().also { _conversationId.value = it }
        val attachments = _pendingAttachmentsByConversation.value[id].orEmpty()
        if (trimmed.isEmpty() && attachments.isEmpty()) return
        val ids = attachments.map { it.attachmentId }
        val body = trimmed.ifBlank { "(photo)" }
        conversations.send(id, body, ids)
        _pendingAttachmentsByConversation.update { it - id }
    }

    fun cancel(clientRequestId: String) = conversations.cancelRequest(clientRequestId)

    fun retry(clientRequestId: String) = conversations.retry(clientRequestId)

    fun resolveApproval(approvalId: String, outcome: ApprovalOutcome) =
        session.resolveApproval(approvalId, outcome)

    fun reconnect() = session.reconnectNow()

    /** AND-W3 skeleton: provision the non-exportable device keypair. */
    // ---- PCB-LIVE-1: PC-A authenticated app session --------------------------

    private val _liveAuth = MutableStateFlow<LiveAuthState>(LiveAuthState.Idle)
    val liveAuth: StateFlow<LiveAuthState> = _liveAuth

    /**
     * Daily has one owner front door. The login UI intentionally does not ask
     * users for infrastructure addresses; provider/server details stay hidden
     * behind the JARVIS identity.
     */
    fun liveLogin(user: String, password: String, onDone: (Boolean) -> Unit = {}) {
        _liveAuth.value = LiveAuthState.Busy
        viewModelScope.launch {
            val result = container.liveSession.login(DEFAULT_CONTROL_PLANE_URL, user, password)
            if (result.isSuccess) {
                container.settings.setPaired(true, container.deviceIdentity.provision())
                container.settings.setUseFake(false)
                container.settings.setBaseUrl(container.liveSession.baseUrl)
                // The front door that authenticated becomes the next launch's
                // starting point, so the phone stops defaulting to the home PC.
                container.settings.setLastControlPlaneUrl(container.liveSession.baseUrl)
                _liveAuth.value = LiveAuthState.Authed(result.getOrThrow().username)
                onDone(true)
                // Transport mode is resolved once at startup (existing V1
                // limitation); a cold restart activates the LIVE seam so the
                // session/ViewModel graphs are built around the live transport.
                relaunch()
            } else {
                _liveAuth.value = LiveAuthState.Error(result.exceptionOrNull()?.message ?: "login failed")
                onDone(false)
            }
        }
    }

    /** True when this install can recover an expired access session without a password. */
    val canBiometricReauth: Boolean
        get() = container.liveSession.hasRefreshCredential

    /**
     * Called only after BiometricPrompt/device-credential verification.
     * Exchanges the encrypted, device-bound refresh credential for a fresh
     * bearer, then reconnects the existing LIVE transport.
     */
    fun biometricReauth(onDone: (Boolean) -> Unit = {}) {
        if (_liveAuth.value is LiveAuthState.Busy) return
        _liveAuth.value = LiveAuthState.Busy
        viewModelScope.launch {
            val result = container.liveSession.refresh()
            if (result.isSuccess) {
                container.settings.setPaired(true, container.deviceIdentity.provision())
                container.settings.setUseFake(false)
                container.settings.setBaseUrl(container.liveSession.baseUrl)
                container.settings.setLastControlPlaneUrl(container.liveSession.baseUrl)
                _liveAuth.value = LiveAuthState.Authed(result.getOrThrow().username)
                session.reconnectNow()
                onDone(true)
            } else {
                _liveAuth.value = LiveAuthState.Error(
                    result.exceptionOrNull()?.message ?: "device reauthentication failed",
                )
                onDone(false)
            }
        }
    }

    /** Explicit escape hatch when the device credential is no longer usable. */
    fun pairAgain() {
        container.liveSession.clear()
        _liveAuth.value = LiveAuthState.Idle
        viewModelScope.launch {
            container.settings.setPaired(false)
        }
    }

    /** PCB-LIVE-4: server-driven profile catalog (from /api/app/status). */
    private val _chatAccess = MutableStateFlow<com.jarvis.android.transport.live.JarvisAppSession.ChatAccess?>(null)
    val chatAccess: StateFlow<com.jarvis.android.transport.live.JarvisAppSession.ChatAccess?> = _chatAccess

    // Conversation-local profile selection (PA-7M isolation): re-derived
    // whenever the open conversation changes.
    private val _chatProfile = MutableStateFlow(container.liveSession.chatProfileFor(_conversationId.value ?: ""))
    val chatProfile: StateFlow<String> = _chatProfile

    init {
        viewModelScope.launch {
            _conversationId.collect { id ->
                _chatProfile.value = container.liveSession.chatProfileFor(id ?: "")
            }
        }
    }

    fun refreshChatAccess() {
        if (container.transportMode != AppContainer.TransportMode.LIVE) return
        viewModelScope.launch {
            val access = container.liveSession.fetchChatAccess()
            _chatAccess.value = access
            // If the stored conversation profile no longer exists on the
            // server catalog, fall back to normal/first. Selection stays local
            // to this conversation (never touches other conversations).
            val convId = _conversationId.value ?: return@launch
            val current = container.liveSession.chatProfileFor(convId)
            if (access != null && access.entries.isNotEmpty() &&
                access.entries.none { it.profile == current }
            ) {
                val fallback = "normal".takeIf { f -> access.entries.any { it.profile == f } }
                    ?: access.entries.first().profile
                container.liveSession.setChatProfileFor(convId, fallback)
                _chatProfile.value = fallback
            }
        }
    }

    fun setChatProfile(profile: String) {
        val convId = _conversationId.value ?: return
        container.liveSession.setChatProfileFor(convId, profile)
        _chatProfile.value = profile
    }

    sealed interface ProviderUsageState {
        data object Idle : ProviderUsageState
        data object Loading : ProviderUsageState
        data class Ready(val snapshot: JarvisAppSession.ProviderUsageSnapshot) : ProviderUsageState
        data class Error(val message: String) : ProviderUsageState
    }

    private val _providerUsage = MutableStateFlow<ProviderUsageState>(ProviderUsageState.Idle)
    val providerUsage: StateFlow<ProviderUsageState> = _providerUsage

    fun refreshProviderUsage() {
        if (container.transportMode != AppContainer.TransportMode.LIVE) {
            _providerUsage.value = ProviderUsageState.Error("Provider usage is available only from the live JARVIS server.")
            return
        }
        _providerUsage.value = ProviderUsageState.Loading
        viewModelScope.launch {
            container.liveSession.fetchProviderUsage().fold(
                onSuccess = { _providerUsage.value = ProviderUsageState.Ready(it) },
                onFailure = {
                    _providerUsage.value = ProviderUsageState.Error(
                        it.message ?: "Provider usage could not be loaded.",
                    )
                },
            )
        }
    }

    fun liveLogout(onDone: () -> Unit = {}) {
        resetVisualStudioProtectedMedia()
        viewModelScope.launch {
            container.liveSession.logout()
            container.settings.setPaired(false)
            _liveAuth.value = LiveAuthState.Idle
            onDone()
        }
    }

    sealed interface LiveAuthState {
        data object Idle : LiveAuthState
        data object Busy : LiveAuthState
        data class Authed(val username: String) : LiveAuthState
        data class Error(val message: String) : LiveAuthState
    }

    private fun relaunch() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
            kotlinx.coroutines.delay(350)
            val ctx = app.applicationContext
            val intent = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
            intent?.addFlags(
                android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                    android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK,
            )
            ctx.startActivity(intent)
            Runtime.getRuntime().exit(0)
        }
    }

    /** AND-W3 skeleton: provision the non-exportable device keypair. */
    fun pairDemoDevice(onDeviceId: (String) -> Unit) {
        viewModelScope.launch {
            val id = container.deviceIdentity.provision()
            container.settings.setPaired(true, id)
            onDeviceId(id)
        }
    }

    fun unpair() {
        resetVisualStudioProtectedMedia()
        viewModelScope.launch {
            // A live session must end on the server, not just locally —
            // otherwise "revoke" leaves a valid bearer token behind.
            if (container.liveSession.isAuthenticated) {
                runCatching { container.liveSession.logout() }
                _liveAuth.value = LiveAuthState.Idle
            }
            container.deviceIdentity.wipe()
            container.settings.setPaired(false)
        }
    }

    fun setUseFake(value: Boolean) = viewModelScope.launch { container.settings.setUseFake(value) }

    fun setBaseUrl(value: String) = viewModelScope.launch { container.settings.setBaseUrl(value) }

    fun setAppLock(value: Boolean) = viewModelScope.launch { container.settings.setAppLock(value) }

    fun setReducedMotion(value: Boolean) = viewModelScope.launch { container.settings.setReducedMotion(value) }

    fun setAppLanguage(lang: String) = viewModelScope.launch { container.settings.setAppLanguage(lang) }

    /**
     * Raise or lower the floating brain. The overlay grant is the owner's to
     * give, so when it is missing this only reports that and changes nothing.
     * Returns true when the bubble actually changed state.
     */
    fun setFloatingBubble(enabled: Boolean): Boolean {
        val context = app
        if (enabled && !com.jarvis.android.overlay.FloatingBubbleService.canDraw(context)) {
            return false
        }
        viewModelScope.launch { container.settings.setFloatingBubble(enabled) }
        if (enabled) com.jarvis.android.overlay.FloatingBubbleService.start(context)
        else com.jarvis.android.overlay.FloatingBubbleService.stop(context)
        return true
    }

    /** Point the owner at the system screen where the overlay grant lives. */
    fun requestOverlayPermission() {
        val context = app
        val intent = android.content.Intent(
            android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            android.net.Uri.parse("package:${context.packageName}"),
        ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** Let the bubble show what the conversation is doing right now. */
    fun updateBubble(activity: com.jarvis.android.ui.components.OrbActivity) {
        val context = app
        if (!com.jarvis.android.overlay.FloatingBubbleService.canDraw(context)) return
        com.jarvis.android.overlay.FloatingBubbleService.setActivity(context, activity)
    }

    fun setOwnerName(value: String) = viewModelScope.launch { container.settings.setOwnerName(value.trim()) }

    fun setIsOwner(value: Boolean) = viewModelScope.launch { container.settings.setIsOwner(value) }

    /**
     * Bumped whenever the on-disk avatar changes so [rememberOwnerAvatar]
     * reloads. The JPEG itself is never held in the ViewModel.
     */
    private val _avatarEpoch = MutableStateFlow(0)
    val avatarEpoch: StateFlow<Int> = _avatarEpoch

    fun setAvatar(uri: Uri) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { container.attachmentStore.saveAvatar(uri) }
            if (ok) {
                container.settings.setHasAvatar(true)
                _avatarEpoch.value += 1
            }
        }
    }

    fun clearAvatar() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { container.attachmentStore.clearAvatar() }
            container.settings.setHasAvatar(false)
            _avatarEpoch.value += 1
        }
    }

    // ---- app shell gating -----------------------------------------------------

    private val _bootShown = MutableStateFlow(false)
    val bootShown: StateFlow<Boolean> = _bootShown

    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked

    fun onBootFinished() { _bootShown.value = true }

    fun onUnlocked() { _unlocked.value = true }

    /** Re-arm the lock when the app leaves the foreground. */
    fun relock() {
        if (settings.value.appLockEnabled) _unlocked.value = false
    }

    // ---- Fake Gateway scenario control (dev builds; plan §7) ------------------

    var fakeScenario: FakeScenario = FakeScenario.HAPPY
        private set

    val transportMode: AppContainer.TransportMode get() = container.transportMode
    val liveAuthenticated: Boolean get() = container.liveSession.isAuthenticated

    private val _healthStatus = MutableStateFlow<String?>(null)
    val healthStatus: StateFlow<String?> = _healthStatus

    /** Lane E: scenario picker, fake-gateway toggle and diagnostics are dev-only. */
    val developerOptionsEnabled: Boolean = com.jarvis.android.BuildConfig.DEBUG

    fun applyFakeScenario(scenario: FakeScenario) {
        fakeScenario = scenario
        if (container.transportMode == AppContainer.TransportMode.FAKE) {
            container.fakeGateway.config = scenario.config
        }
    }

    /** Sends a scripted prompt through the normal pipeline so the scenario is visible. */
    fun runFakeScenarioNow() {
        val id = _conversationId.value ?: conversations.newConversationId().also {
            _conversationId.value = it
        }
        conversations.send(id, "[${fakeScenario.name}] demo prompt")
    }

    fun checkHealth() {
        viewModelScope.launch {
            _healthStatus.value = "checking…"
            _healthStatus.value = session.health().fold(
                onSuccess = { "ok (protocol ${it.protocolVersion}, caps: ${it.capabilities.joinToString()})" },
                onFailure = { "error: ${it.message}" },
            )
        }
    }

    /** AND-W2 demo seed so the conversation list is never empty on first run. */
    fun seedDemoIfEmpty() {
        viewModelScope.launch {
            if (conversations.observeConversations().first().isNotEmpty()) return@launch
            val now = System.currentTimeMillis()
            container.dao.upsertConversation(
                ConversationEntity("conv_welcome", "Getting started", now, now)
            )
            container.dao.insertMessage(
                com.jarvis.android.data.local.MessageEntity(
                    conversationId = "conv_welcome",
                    clientRequestId = "seed_user",
                    role = "user",
                    text = "What can you do?",
                    status = "Completed",
                    createdAtMs = now - 2000,
                )
            )
            container.dao.insertMessage(
                com.jarvis.android.data.local.MessageEntity(
                    conversationId = "conv_welcome",
                    clientRequestId = "seed_assistant",
                    role = "assistant",
                    text = "I'm running against the Fake Gateway, so nothing leaves this device yet. " +
                        "Once the PC-A Gateway contract is frozen I can chat with your Jarvis, stream " +
                        "replies, cancel requests, and approve or deny PC actions from here.",
                    status = "Completed",
                    createdAtMs = now - 1000,
                )
            )
        }
    }

    fun clearSceneBuilderMessage() {
        _sceneBuilder.value = _sceneBuilder.value.copy(
            notice = null,
            error = null,
        )
    }

    fun refreshSceneGenerationCapabilities(projectId: String) {
        viewModelScope.launch {
            container.liveSession.visualSceneGenerationCapabilities(
                projectId = projectId,
            ).fold(
                onSuccess = { capabilities ->
                    if (
                        _sceneBuilder.value.projectId.isBlank() ||
                        _sceneBuilder.value.projectId == projectId
                    ) {
                        _sceneBuilder.update {
                            it.copy(
                                projectId = projectId,
                                capabilities = capabilities,
                            )
                        }
                    }
                },
                onFailure = {
                    // Backward compatibility: an older backend may not expose
                    // the capability route yet. Existing Cloud flow remains
                    // usable; Local stays unavailable until explicitly reported.
                },
            )
        }
    }

    fun clearSceneBuilderContext(projectId: String) {
        sceneDirectorPollJob?.cancel()
        sceneDirectorPollJob = null
        sceneBuilderScopeVersion++
        _sceneBuilder.value.generatedAttachmentId?.let { attachmentId ->
            deleteVisualStudioAttachments(listOf(attachmentId))
        }
        _sceneBuilder.value = SceneBuilderState(projectId = projectId)
    }

    fun directSceneVisual(
        projectId: String,
        requestText: String,
        selectedEvidenceId: String = "",
    ) {
        val cleanProject = projectId.trim()
        val cleanRequest = requestText.trim()
        if (cleanProject.isBlank() || cleanRequest.isBlank()) {
            _sceneBuilder.update {
                it.copy(
                    projectId = cleanProject,
                    error = "Describe la escena o momento que quieres ilustrar.",
                )
            }
            return
        }
        if (_sceneBuilder.value.busy) return
        sceneDirectorPollJob?.cancel()
        sceneDirectorPollJob = null
        sceneBuilderScopeVersion++
        val scopeVersion = sceneBuilderScopeVersion
        _sceneBuilder.value.generatedAttachmentId?.let { attachmentId ->
            deleteVisualStudioAttachments(listOf(attachmentId))
        }
        _sceneBuilder.update {
            it.copy(
                projectId = cleanProject,
                busy = true,
                busyLabel = "Buscando el pasaje exacto y referencias",
                context = null,
                directorStatus = "RESOLVING",
                directorCandidates = emptyList(),
                directorResolverModel = "",
                directorJob = null,
                generated = null,
                generatedAttachmentId = null,
                attachmentIds = emptySet(),
                notice = null,
                error = null,
            )
        }
        viewModelScope.launch {
            container.liveSession.visualSceneDirectorResolve(
                projectId = cleanProject,
                requestText = cleanRequest,
                selectedEvidenceId = selectedEvidenceId,
            ).fold(
                onSuccess = { response ->
                    if (
                        scopeVersion != sceneBuilderScopeVersion ||
                        _sceneBuilder.value.projectId != cleanProject
                    ) return@fold
                    val context = response.context
                    _sceneBuilder.update {
                        it.copy(
                            busy = false,
                            busyLabel = "",
                            context = context,
                            directorStatus = response.status,
                            directorCandidates = response.candidates,
                            directorResolverModel = response.resolver_model,
                            notice = when (response.status) {
                                "AMBIGUOUS" ->
                                    "Encontré más de un pasaje válido. Elige cuál quieres ilustrar."
                                "MISSING" ->
                                    "No encontré ese evento en texto autorizado. No se generó ninguna imagen."
                                "BLOCKED_REFERENCES" ->
                                    "Encontré el pasaje exacto, pero faltan referencias visuales aprobadas. No se generó ninguna imagen."
                                "READY" ->
                                    "Pasaje y referencias congelados. JARVIS iniciará un trabajo durable de generación y evaluación."
                                else ->
                                    "JARVIS terminó de resolver la solicitud de escena."
                            },
                            error = null,
                        )
                    }
                    if (
                        response.status == "READY" &&
                        context != null &&
                        context.generation_ready
                    ) {
                        startSceneDirectorGeneration(
                            projectId = cleanProject,
                            engine = response.recommended_engine.ifBlank { "cloud" },
                            mode = "quality",
                            aspectRatio = "landscape",
                            maxCorrections = response.generation_max_corrections
                                .coerceIn(0, 2),
                            expectedScopeVersion = scopeVersion,
                        )
                    }
                },
                onFailure = { error ->
                    if (
                        scopeVersion != sceneBuilderScopeVersion ||
                        _sceneBuilder.value.projectId != cleanProject
                    ) return@fold
                    _sceneBuilder.update {
                        it.copy(
                            busy = false,
                            busyLabel = "",
                            directorStatus = "ERROR",
                            error = error.message
                                ?: "No se pudo resolver la escena contra el texto autorizado.",
                        )
                    }
                },
            )
        }
    }

    private fun startSceneDirectorGeneration(
        projectId: String,
        engine: String,
        mode: String,
        model: String? = null,
        aspectRatio: String,
        maxCorrections: Int,
        expectedScopeVersion: Long,
    ) {
        val context = _sceneBuilder.value.context ?: return
        if (
            expectedScopeVersion != sceneBuilderScopeVersion ||
            context.project_id != projectId ||
            !context.generation_ready
        ) return
        _sceneBuilder.update {
            it.copy(
                busy = true,
                busyLabel = "Encolando generación durable de escena",
                directorStatus = "QUEUED",
                notice = "El trabajo quedará guardado en el servidor aunque Android deje de esperar la petición.",
                error = null,
            )
        }
        viewModelScope.launch {
            container.liveSession.visualSceneDirectorGenerate(
                projectId = projectId,
                contextId = context.context_id,
                contextHash = context.context_hash,
                engine = engine,
                mode = mode,
                model = model,
                aspectRatio = aspectRatio,
                maxCorrections = maxCorrections.coerceIn(0, 2),
            ).fold(
                onSuccess = { response ->
                    if (
                        expectedScopeVersion != sceneBuilderScopeVersion ||
                        _sceneBuilder.value.projectId != projectId
                    ) return@fold
                    val job = response.job
                    _sceneBuilder.update {
                        it.copy(
                            directorJob = job,
                            directorStatus = job?.status ?: "ERROR",
                            busy = (
                                job?.status == "QUEUED" ||
                                    job?.status == "RUNNING" ||
                                    job?.status == "READY_FOR_REVIEW"
                            ),
                            busyLabel = when (job?.status) {
                                "QUEUED" -> "Escena en cola"
                                "RUNNING" -> "Generando y evaluando escena"
                                "READY_FOR_REVIEW" -> "Recuperando candidato durable"
                                else -> ""
                            },
                            error = null,
                        )
                    }
                    if (response.result != null) {
                        applySceneDirectorResult(
                            projectId,
                            expectedScopeVersion,
                            response,
                        )
                        return@fold
                    }
                    if (job == null || job.job_id.isBlank()) {
                        _sceneBuilder.update {
                            it.copy(
                                busy = false,
                                busyLabel = "",
                                directorStatus = "ERROR",
                                error = "El servidor no devolvió el trabajo durable de la escena.",
                            )
                        }
                        return@fold
                    }
                    when (job.status) {
                        "QUEUED", "RUNNING", "READY_FOR_REVIEW" ->
                            startSceneDirectorJobPolling(
                                projectId,
                                job.job_id,
                                expectedScopeVersion,
                            )
                        "OUTCOME_UNKNOWN" -> _sceneBuilder.update {
                            it.copy(
                                busy = false,
                                busyLabel = "",
                                notice = "El proveedor pudo haber procesado una generación cuyo resultado no puede confirmarse. JARVIS no la repetirá automáticamente.",
                            )
                        }
                        "FAILED_FINAL" -> _sceneBuilder.update {
                            it.copy(
                                busy = false,
                                busyLabel = "",
                                error = job.last_error.ifBlank {
                                    "La generación durable de la escena terminó con error."
                                },
                            )
                        }
                    }
                },
                onFailure = { error ->
                    if (
                        expectedScopeVersion != sceneBuilderScopeVersion ||
                        _sceneBuilder.value.projectId != projectId
                    ) return@fold
                    _sceneBuilder.update {
                        it.copy(
                            busy = false,
                            busyLabel = "",
                            directorStatus = "ERROR",
                            error = error.message
                                ?: "No se pudo iniciar el trabajo durable de la escena.",
                        )
                    }
                },
            )
        }
    }

    private fun startSceneDirectorJobPolling(
        projectId: String,
        jobId: String,
        expectedScopeVersion: Long,
    ) {
        if (jobId.isBlank()) return
        sceneDirectorPollJob?.cancel()
        sceneDirectorPollJob = viewModelScope.launch {
            while (true) {
                delay(1_800)
                if (
                    expectedScopeVersion != sceneBuilderScopeVersion ||
                    _sceneBuilder.value.projectId != projectId
                ) return@launch
                val response = container.liveSession.visualSceneDirectorJobStatus(
                    projectId = projectId,
                    jobId = jobId,
                ).getOrNull()
                if (response == null) {
                    _sceneBuilder.update {
                        it.copy(
                            busy = true,
                            busyLabel = "Trabajo guardado; reintentando estado",
                            notice = "La consulta falló temporalmente, pero el trabajo durable sigue en el servidor.",
                        )
                    }
                    continue
                }
                val job = response.job
                _sceneBuilder.update {
                    it.copy(
                        directorJob = job,
                        directorStatus = job?.status ?: it.directorStatus,
                        error = null,
                    )
                }
                if (response.result != null) {
                    applySceneDirectorResult(
                        projectId,
                        expectedScopeVersion,
                        response,
                    )
                    return@launch
                }
                when (job?.status) {
                    "QUEUED" -> _sceneBuilder.update {
                        it.copy(
                            busy = true,
                            busyLabel = "Escena en cola",
                            notice = "JARVIS conserva el contexto y el trabajo durable mientras espera ejecución.",
                        )
                    }
                    "RUNNING" -> _sceneBuilder.update {
                        it.copy(
                            busy = true,
                            busyLabel = "Generando y evaluando escena",
                            notice = "JARVIS está generando, evaluando y corrigiendo con el pasaje y referencias congelados.",
                        )
                    }
                    "READY_FOR_REVIEW" -> _sceneBuilder.update {
                        it.copy(
                            busy = true,
                            busyLabel = "Recuperando candidato durable",
                            notice = "La escena terminó; JARVIS está recuperando el candidato exacto para revisión.",
                        )
                    }
                    "OUTCOME_UNKNOWN" -> {
                        _sceneBuilder.update {
                            it.copy(
                                busy = false,
                                busyLabel = "",
                                notice = "Resultado de proveedor ambiguo. El trabajo se detuvo de forma segura y no se repetirá automáticamente.",
                            )
                        }
                        return@launch
                    }
                    "FAILED_FINAL" -> {
                        _sceneBuilder.update {
                            it.copy(
                                busy = false,
                                busyLabel = "",
                                error = job.last_error.ifBlank {
                                    "La generación durable de la escena terminó con error."
                                },
                            )
                        }
                        return@launch
                    }
                    null -> {
                        _sceneBuilder.update {
                            it.copy(
                                busy = false,
                                busyLabel = "",
                                error = "No se encontró el trabajo durable de la escena.",
                            )
                        }
                        return@launch
                    }
                }
            }
        }
    }

    private suspend fun applySceneDirectorResult(
        projectId: String,
        expectedScopeVersion: Long,
        response: VisualSceneDirectorJobResponse,
    ) {
        val reply = response.result ?: return
        if (
            expectedScopeVersion != sceneBuilderScopeVersion ||
            _sceneBuilder.value.projectId != projectId
        ) return
        if (reply.data_base64.isBlank()) {
            _sceneBuilder.update {
                it.copy(
                    busy = false,
                    busyLabel = "",
                    error = "El trabajo terminó, pero el candidato visual no pudo recuperarse.",
                )
            }
            return
        }
        val staged = withContext(Dispatchers.IO) {
            container.attachmentStore.stageGeneratedBase64(reply.data_base64)
        }
        if (
            expectedScopeVersion != sceneBuilderScopeVersion ||
            _sceneBuilder.value.projectId != projectId
        ) {
            staged?.attachmentId?.let { deleteVisualStudioAttachments(listOf(it)) }
            return
        }
        if (staged == null) {
            _sceneBuilder.update {
                it.copy(
                    busy = false,
                    busyLabel = "",
                    error = "La escena quedó guardada en el servidor, pero Android no pudo preparar la vista previa.",
                )
            }
            return
        }
        val oldAttachments = _sceneBuilder.value.attachmentIds +
            listOfNotNull(_sceneBuilder.value.generatedAttachmentId)
        deleteVisualStudioAttachments(
            oldAttachments.filter { it != staged.attachmentId },
        )
        _sceneBuilder.update {
            it.copy(
                busy = false,
                busyLabel = "",
                directorJob = response.job,
                directorStatus = response.job?.status ?: "READY_FOR_REVIEW",
                generated = reply,
                generatedAttachmentId = staged.attachmentId,
                attachmentIds = setOf(staged.attachmentId),
                notice = when {
                    reply.scene_provider_outcome_unknown ->
                        "Una corrección tuvo resultado ambiguo. JARVIS conservó el último candidato conocido y no repetirá automáticamente esa operación."
                    reply.storage_retry_required ->
                        "La escena se generó como candidata, pero Drive necesita reintentar el guardado. No se repetirá la generación."
                    reply.scene_evaluation.verdict == "PASS" ->
                        "Escena generada y evaluada contra el pasaje exacto. Lista para revisión humana."
                    reply.scene_evaluation.verdict == "CORRECT" ->
                        "JARVIS agotó las correcciones permitidas; conserva el candidato final para revisión humana."
                    reply.scene_evaluation.verdict == "UNAVAILABLE" ->
                        "La escena quedó guardada, pero el evaluador visual no estuvo disponible. No se gastó otra generación."
                    else ->
                        "Escena durable guardada como CANDIDATE. No modifica canon narrativo ni visual hasta aprobación humana."
                },
                error = null,
            )
        }
    }

    fun previewSceneVisualContext(
        projectId: String,
        chapterId: String,
        sceneId: String,
        expectedAggregateVersion: Int,
        briefRevisionId: String,
        draftRevisionId: String,
        characterIds: List<String>,
        locationId: String,
        era: String = "",
        state: String = "",
        time: String = "",
        weather: String = "",
        composition: String = "",
        instruction: String = "",
    ) {
        if (
            projectId.isBlank() ||
            chapterId.isBlank() ||
            sceneId.isBlank() ||
            expectedAggregateVersion < 1 ||
            (briefRevisionId.isBlank() == draftRevisionId.isBlank()) ||
            (characterIds.isEmpty() && locationId.isBlank())
        ) {
            _sceneBuilder.value = _sceneBuilder.value.copy(
                projectId = projectId,
                busy = false,
                error = "Selecciona un capítulo con Brief/Draft y al menos un personaje o locación.",
            )
            return
        }
        _sceneBuilder.value.generatedAttachmentId?.let { attachmentId ->
            deleteVisualStudioAttachments(listOf(attachmentId))
        }
        _sceneBuilder.value = _sceneBuilder.value.copy(
            projectId = projectId,
            busy = true,
            busyLabel = "Congelando contexto visual",
            context = null,
            generated = null,
            generatedAttachmentId = null,
            notice = null,
            error = null,
        )
        viewModelScope.launch {
            container.liveSession.visualSceneContextPreview(
                projectId = projectId,
                chapterId = chapterId,
                sceneId = sceneId,
                expectedAggregateVersion = expectedAggregateVersion,
                briefRevisionId = briefRevisionId,
                draftRevisionId = draftRevisionId,
                characterIds = characterIds,
                locationId = locationId,
                era = era,
                state = state,
                time = time,
                weather = weather,
                composition = composition,
                instruction = instruction,
            ).fold(
                onSuccess = { response ->
                    if (_sceneBuilder.value.projectId != projectId) return@fold
                    val context = response.context
                    _sceneBuilder.value = _sceneBuilder.value.copy(
                        busy = false,
                        busyLabel = "",
                        context = context,
                        notice = if (context.generation_ready) {
                            "Contexto congelado. Las referencias exactas están listas para generación."
                        } else {
                            "Contexto congelado, pero hay referencias que requieren selección explícita."
                        },
                        error = null,
                    )
                },
                onFailure = { error ->
                    if (_sceneBuilder.value.projectId != projectId) return@fold
                    _sceneBuilder.value = _sceneBuilder.value.copy(
                        busy = false,
                        busyLabel = "",
                        context = null,
                        error = error.message ?: "No se pudo preparar el contexto visual de la escena.",
                    )
                },
            )
        }
    }

    fun generateSceneVisual(
        projectId: String,
        engine: String = "cloud",
        mode: String = "quality",
        model: String? = null,
        aspectRatio: String = "landscape",
        maxCorrections: Int = 0,
    ) {
        val state = _sceneBuilder.value
        val context = state.context ?: return
        if (
            state.busy ||
            context.project_id != projectId ||
            !context.generation_ready ||
            context.exploratory ||
            context.selection.instruction.isBlank()
        ) return
        _sceneBuilder.update {
            it.copy(
                busy = true,
                busyLabel = "Generando escena con referencias congeladas",
                notice = null,
                error = null,
            )
        }
        viewModelScope.launch {
            container.liveSession.generateSceneVisualAssetImage(
                projectId = projectId,
                contextId = context.context_id,
                contextHash = context.context_hash,
                engine = engine,
                mode = mode,
                model = model,
                aspectRatio = aspectRatio,
                maxCorrections = maxCorrections.coerceIn(0, 2),
            ).fold(
                onSuccess = { reply ->
                    val oldAttachments = _sceneBuilder.value.attachmentIds +
                        listOfNotNull(_sceneBuilder.value.generatedAttachmentId)
                    val staged = withContext(Dispatchers.IO) {
                        container.attachmentStore.stageGeneratedBase64(
                            reply.data_base64,
                        )
                    }
                    val keep = staged?.attachmentId
                    deleteVisualStudioAttachments(
                        oldAttachments.filter { it != keep },
                    )
                    _sceneBuilder.update {
                        it.copy(
                            busy = false,
                            busyLabel = "",
                            generated = reply,
                            generatedAttachmentId = staged?.attachmentId,
                            attachmentIds = staged?.attachmentId?.let(::setOf)
                                ?: emptySet(),
                            notice = when {
                                reply.scene_provider_outcome_unknown ->
                                    "Una corrección tuvo resultado ambiguo. JARVIS conservó el último candidato conocido y no repetirá automáticamente esa operación."
                                reply.storage_retry_required ->
                                    "La escena se generó como candidata, pero Drive necesita reintentar el guardado. No se repetirá la generación."
                                reply.scene_evaluation.verdict == "PASS" ->
                                    "Escena generada y evaluada contra el pasaje exacto. Lista para revisión humana."
                                reply.scene_evaluation.verdict == "CORRECT" ->
                                    "JARVIS agotó las correcciones permitidas; conserva el candidato final para revisión humana."
                                reply.scene_evaluation.verdict == "UNAVAILABLE" ->
                                    "La escena quedó guardada, pero el evaluador visual no estuvo disponible. No se gastó otra generación."
                                else ->
                                    "Escena generada y guardada como CANDIDATE. No modifica canon narrativo ni visual hasta aprobación humana."
                            },
                            error = null,
                        )
                    }
                },
                onFailure = { error ->
                    _sceneBuilder.update {
                        it.copy(
                            busy = false,
                            busyLabel = "",
                            error = error.message
                                ?: "No se pudo generar la escena.",
                        )
                    }
                },
            )
        }
    }

    fun editSceneVisual(
        parentAsset: VisualStudioAsset,
        referenceAttachmentId: String,
        instruction: String,
        mode: String = "quality",
        model: String? = null,
        preserveIdentity: String = "high",
        aspectRatio: String = "landscape",
    ) {
        val clean = instruction.trim()
        val state = _sceneBuilder.value
        if (
            clean.isBlank() ||
            _imageEditState.value is ImageEditState.Busy ||
            state.busy ||
            parentAsset.project_id != state.projectId ||
            parentAsset.kind != "SCENE_ART" ||
            parentAsset.status != "APPROVED"
        ) {
            if (parentAsset.status != "APPROVED") {
                _imageEditState.value = ImageEditState.Error(
                    "Aprueba la escena visual antes de crear una edición hija.",
                )
            }
            return
        }
        _imageEditState.value = ImageEditState.Busy
        _lastImageEditDetails.value = null
        viewModelScope.launch {
            val source = withContext(Dispatchers.IO) {
                runCatching {
                    val file = container.attachmentStore.resolve(referenceAttachmentId)
                        ?: error("La escena base ya no está disponible en el dispositivo.")
                    val bytes = file.readBytes()
                    require(bytes.isNotEmpty() && bytes.size <= 12 * 1024 * 1024) {
                        "La escena base es demasiado grande."
                    }
                    Base64.encodeToString(bytes, Base64.NO_WRAP)
                }
            }
            if (source.isFailure) {
                _imageEditState.value = ImageEditState.Error(
                    source.exceptionOrNull()?.message
                        ?: "No se pudo leer la escena aprobada.",
                )
                return@launch
            }
            container.liveSession.editSceneVisualAssetImage(
                imageBase64 = source.getOrThrow(),
                mimeType = parentAsset.mime_type,
                projectId = parentAsset.project_id,
                instruction = clean,
                parentAsset = parentAsset,
                mode = mode,
                model = model,
                preserveIdentity = preserveIdentity,
                aspectRatio = aspectRatio,
            ).fold(
                onSuccess = { reply ->
                    val staged = withContext(Dispatchers.IO) {
                        container.attachmentStore.stageGeneratedBase64(
                            reply.data_base64,
                        )
                    }
                    if (staged == null) {
                        _imageEditState.value = ImageEditState.Error(
                            "JARVIS creó la edición, pero Android no pudo leerla.",
                        )
                        return@fold
                    }
                    _lastImageEditDetails.value = ImageGenerationDetails(
                        reply.provider,
                        reply.model,
                        reply.requested_mode,
                        reply.fallback_used,
                        reply.attempt_count,
                        reply.duration_ms,
                    )
                    _sceneBuilder.update { current ->
                        if (current.projectId != parentAsset.project_id) {
                            current
                        } else {
                            current.copy(
                                generated = reply,
                                generatedAttachmentId = staged.attachmentId,
                                attachmentIds = current.attachmentIds +
                                    referenceAttachmentId +
                                    staged.attachmentId,
                                notice = if (reply.storage_retry_required) {
                                    "La edición quedó como CANDIDATE, pero Drive requiere reintento. El parent aprobado permanece intacto."
                                } else {
                                    "Edición hija guardada como CANDIDATE. El parent aprobado permanece intacto hasta otra aprobación humana."
                                },
                                error = null,
                            )
                        }
                    }
                    _imageEditState.value = ImageEditState.Success(
                        staged.attachmentId,
                    )
                },
                onFailure = { error ->
                    _imageEditState.value = ImageEditState.Error(
                        error.message ?: "No se pudo editar la escena visual.",
                    )
                },
            )
        }
    }

    fun approveSceneVisual() {
        val state = _sceneBuilder.value
        val asset = state.generated?.visual_asset ?: return
        if (
            state.busy ||
            asset.status != "CANDIDATE" ||
            asset.storage.state != "stored"
        ) return
        _sceneBuilder.update {
            it.copy(
                busy = true,
                busyLabel = "Aprobando escena visual",
                notice = null,
                error = null,
            )
        }
        viewModelScope.launch {
            container.liveSession.visualAssetApproveExact(
                projectId = asset.project_id,
                assetId = asset.asset_id,
                assetSha256 = asset.sha256,
                visualRevision = asset.visual_revision,
            ).fold(
                onSuccess = { response ->
                    _sceneBuilder.update { current ->
                        current.copy(
                            busy = false,
                            busyLabel = "",
                            generated = current.generated?.copy(
                                visual_asset = response.asset,
                            ),
                            notice = "Escena visual aprobada. La aprobación no crea hechos narrativos nuevos.",
                            error = null,
                        )
                    }
                },
                onFailure = { error ->
                    _sceneBuilder.update {
                        it.copy(
                            busy = false,
                            busyLabel = "",
                            error = error.message
                                ?: "No se pudo aprobar la escena visual.",
                        )
                    }
                },
            )
        }
    }

    fun retrySceneVisualStorage() {
        val state = _sceneBuilder.value
        val reply = state.generated ?: return
        val asset = reply.visual_asset ?: return
        val attachmentId = state.generatedAttachmentId
        if (state.busy || asset.storage.state == "stored") return
        if (attachmentId.isNullOrBlank()) {
            _sceneBuilder.update {
                it.copy(
                    error = "Los bytes locales de la escena ya no están disponibles. JARVIS no regenerará la imagen automáticamente.",
                )
            }
            return
        }
        _sceneBuilder.update {
            it.copy(
                busy = true,
                busyLabel = "Reintentando guardado de escena en Drive",
                notice = null,
                error = null,
            )
        }
        viewModelScope.launch {
            val encoded = withContext(Dispatchers.IO) {
                runCatching {
                    val file = container.attachmentStore.resolve(attachmentId)
                        ?: error("Los bytes locales de la escena ya no están disponibles.")
                    Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
                }
            }
            if (encoded.isFailure) {
                _sceneBuilder.update {
                    it.copy(
                        busy = false,
                        busyLabel = "",
                        error = encoded.exceptionOrNull()?.message,
                    )
                }
                return@launch
            }
            container.liveSession.visualSceneAssetIngest(
                projectId = asset.project_id,
                imageBase64 = encoded.getOrThrow(),
                asset = asset,
            ).fold(
                onSuccess = { response ->
                    _sceneBuilder.update { current ->
                        current.copy(
                            busy = false,
                            busyLabel = "",
                            generated = current.generated?.copy(
                                visual_asset = response.asset,
                                storage_retry_required = false,
                            ),
                            notice = "Guardado en Drive reanudado sin repetir la generación.",
                            error = null,
                        )
                    }
                },
                onFailure = { error ->
                    _sceneBuilder.update {
                        it.copy(
                            busy = false,
                            busyLabel = "",
                            error = error.message
                                ?: "Drive sigue sin poder guardar la escena candidata.",
                        )
                    }
                },
            )
        }
    }

}

private const val DEFAULT_CONTROL_PLANE_URL = "https://vps-8817149e.tail6eec63.ts.net:8443"

class JarvisViewModelFactory(private val app: JarvisApp) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        JarvisViewModel(app) as T
}
