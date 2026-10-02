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
        val plans: List<WritingPlanItem> = emptyList(),
        val planningCouncil: WritingPlanningCouncil? = null,
        val planningCouncilSessions: List<WritingPlanningCouncilSession> = emptyList(),
        val planningV2ChapterId: String? = null,
        val planningV2AggregateVersion: Int = 0,
        val planningV2Turns: List<WritingPlanningTurnItem> = emptyList(),
        val planningV2Direction: WritingDirectionStateResult? = null,
        val draftV2: WritingDraftExecutionResult? = null,
        val approvalV2: WritingApprovalStateResult? = null,
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
            val wikiTimeline = container.liveSession.writingRoomWikiTimeline(projectId)
            val canonExplorer = container.liveSession.writingRoomWikiExplorer(projectId)
            val plans = container.liveSession.writingRoomPlanList(projectId)
            val councilSessions = container.liveSession.writingRoomPlanningCouncilList(projectId)
            val chapters = container.liveSession.writingRoomChapterList(projectId)
            val library = container.liveSession.writingRoomLibraryList(projectId)
            val failure = listOf(
                overview, wikiHome, wikiCharacters, wikiTimeline, canonExplorer,
                plans, councilSessions, chapters, library,
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

    data class VisualPendingUpload(
        val slotKey: String,
        val perspective: String,
        val required: Boolean,
        val assetId: String,
        val attachmentId: String,
        val mimeType: String,
    )

    data class VisualCharacterStudioState(
        val projectId: String? = null,
        val characterId: String? = null,
        val busy: Boolean = false,
        val detail: WritingVisualCharacterDetail? = null,
        val candidate: WritingVisualAsset? = null,
        val pendingAssetId: String? = null,
        val pendingAttachmentId: String? = null,
        val pendingMimeType: String = "image/jpeg",
        val viewCandidates: Map<String, WritingVisualAsset> = emptyMap(),
        val pendingViewUploads: Map<String, VisualPendingUpload> = emptyMap(),
        val message: String? = null,
        val error: String? = null,
    )

    private val _visualCharacterStudio = MutableStateFlow(VisualCharacterStudioState())
    val visualCharacterStudio: StateFlow<VisualCharacterStudioState> = _visualCharacterStudio

    fun loadVisualCharacter(projectId: String, characterId: String) {
        val project = projectId.trim()
        val character = characterId.trim()
        if (project.isEmpty() || character.isEmpty()) return
        val current = _visualCharacterStudio.value
        _visualCharacterStudio.value = current.copy(
            projectId = project,
            characterId = character,
            busy = true,
            error = null,
            message = null,
        )
        viewModelScope.launch {
            container.liveSession.writingRoomVisualCharacterDetail(project, character).fold(
                onSuccess = { detail ->
                    _visualCharacterStudio.value = _visualCharacterStudio.value.copy(
                        projectId = project,
                        characterId = character,
                        busy = false,
                        detail = detail,
                        error = null,
                    )
                    val assetIds = buildList {
                        detail.gallery?.primary?.asset_id?.takeIf { it.isNotBlank() }?.let(::add)
                        detail.active_reference_pack?.slots.orEmpty()
                            .map { it.asset_id }
                            .filter { it.isNotBlank() }
                            .forEach(::add)
                    }.distinct()
                    assetIds.forEach { loadWikiVisual(project, it) }
                },
                onFailure = { error ->
                    _visualCharacterStudio.value = _visualCharacterStudio.value.copy(
                        busy = false,
                        error = error.message ?: "No se pudo cargar Character Studio.",
                    )
                },
            )
        }
    }

    fun generateVisualMasterCandidate(
        projectId: String,
        characterId: String,
        characterName: String,
        prompt: String,
        mode: String = "quality",
        model: String? = null,
    ) {
        val project = projectId.trim()
        val character = characterId.trim()
        val cleanPrompt = prompt.trim()
        if (
            project.isEmpty() ||
            character.isEmpty() ||
            cleanPrompt.isEmpty() ||
            _visualCharacterStudio.value.busy
        ) return
        _visualCharacterStudio.value = _visualCharacterStudio.value.copy(
            projectId = project,
            characterId = character,
            busy = true,
            candidate = null,
            pendingAssetId = null,
            pendingAttachmentId = null,
            message = null,
            error = null,
        )
        viewModelScope.launch {
            container.liveSession.generateImage(
                prompt = cleanPrompt,
                mode = mode,
                model = model,
                visualAsset = JarvisAppSession.ImageVisualAssetRequest(
                    projectId = project,
                    kind = "PRIMARY_REFERENCE",
                    characterIds = listOf(character),
                    perspective = "front",
                    surface = "character_creator",
                    alt = "Referencia visual candidata de " +
                        characterName.ifBlank { character.substringAfter(":") },
                ),
            ).fold(
                onSuccess = { reply ->
                    val staged = withContext(Dispatchers.IO) {
                        container.attachmentStore.stageGeneratedBase64(reply.dataBase64)
                    }
                    val candidate = reply.visualAsset
                    if (staged == null || candidate == null) {
                        _visualCharacterStudio.value = _visualCharacterStudio.value.copy(
                            busy = false,
                            error = "La generación terminó, pero no devolvió un candidato visual durable.",
                        )
                        return@fold
                    }
                    _wikiVisualAttachments.update {
                        it + (candidate.asset_id to staged.attachmentId)
                    }
                    _visualCharacterStudio.value = _visualCharacterStudio.value.copy(
                        busy = false,
                        candidate = candidate,
                        pendingAssetId = candidate.asset_id,
                        pendingAttachmentId = staged.attachmentId,
                        pendingMimeType = reply.mimeType,
                        message = if (reply.storageRetryRequired) {
                            "La imagen fue generada una sola vez. Drive quedó pendiente; usa Reintentar storage sin regenerar."
                        } else {
                            "Candidato cloud guardado. Revisa la imagen antes de aprobarla."
                        },
                        error = null,
                    )
                },
                onFailure = { error ->
                    _visualCharacterStudio.value = _visualCharacterStudio.value.copy(
                        busy = false,
                        error = error.message ?: "No se pudo generar el master visual.",
                    )
                },
            )
        }
    }

    fun generateVisualPackSlotCandidate(
        slotKey: String,
        perspective: String,
        characterName: String,
        required: Boolean = true,
        mode: String = "quality",
        model: String? = null,
    ) {
        val state = _visualCharacterStudio.value
        val projectId = state.projectId ?: return
        val characterId = state.characterId ?: return
        val draftPack = state.detail?.reference_packs
            ?.filter { it.state == "DRAFT" }
            ?.maxByOrNull { it.revision }
            ?: return
        if (state.busy || slotKey.isBlank() || perspective.isBlank()) return

        val label = perspective.replace("_", " ")
        val prompt = buildString {
            append("Canonical character turnaround for ")
            append(characterName.ifBlank { characterId.substringAfter(":") })
            append(", ")
            append(label)
            append(". Preserve the exact approved identity, face, hair, age, body proportions, ")
            append("distinctive features and canonical outfit. Neutral studio background, full-body ")
            append("character reference, consistent lighting, no text, no redesign.")
        }
        _visualCharacterStudio.value = state.copy(
            busy = true,
            message = null,
            error = null,
        )
        viewModelScope.launch {
            container.liveSession.generateImage(
                prompt = prompt,
                mode = mode,
                model = model,
                visualAsset = JarvisAppSession.ImageVisualAssetRequest(
                    projectId = projectId,
                    kind = "IDENTITY_PACK",
                    characterIds = listOf(characterId),
                    perspective = perspective,
                    surface = "character_creator",
                    alt = label + " de " + characterName.ifBlank { characterId.substringAfter(":") },
                ),
            ).fold(
                onSuccess = { reply ->
                    val staged = withContext(Dispatchers.IO) {
                        container.attachmentStore.stageGeneratedBase64(reply.dataBase64)
                    }
                    val candidate = reply.visualAsset
                    if (staged == null || candidate == null) {
                        _visualCharacterStudio.value = _visualCharacterStudio.value.copy(
                            busy = false,
                            error = "La vista fue generada, pero no devolvió un candidato visual durable.",
                        )
                        return@fold
                    }
                    val pending = VisualPendingUpload(
                        slotKey = slotKey,
                        perspective = perspective,
                        required = required,
                        assetId = candidate.asset_id,
                        attachmentId = staged.attachmentId,
                        mimeType = reply.mimeType,
                    )
                    _wikiVisualAttachments.update {
                        it + (candidate.asset_id to staged.attachmentId)
                    }
                    _visualCharacterStudio.value = _visualCharacterStudio.value.copy(
                        busy = false,
                        viewCandidates = _visualCharacterStudio.value.viewCandidates +
                            (slotKey to candidate),
                        pendingViewUploads = _visualCharacterStudio.value.pendingViewUploads +
                            (slotKey to pending),
                        message = if (reply.storageRetryRequired) {
                            "La vista " + label +
                                " se generó una sola vez. Drive quedó pendiente; reintenta storage sin regenerar."
                        } else {
                            "Vista " + label + " guardada como candidata. Falta aprobación humana."
                        },
                        error = null,
                    )
                },
                onFailure = { error ->
                    _visualCharacterStudio.value = _visualCharacterStudio.value.copy(
                        busy = false,
                        error = error.message ?: "No se pudo generar la vista " + label + ".",
                    )
                },
            )
        }
    }

    fun stageVisualMasterFromUri(
        uri: Uri,
        projectId: String,
        characterId: String,
        characterName: String,
    ) {
        val project = projectId.trim()
        val character = characterId.trim()
        if (project.isEmpty() || character.isEmpty() || _visualCharacterStudio.value.busy) return
        _visualCharacterStudio.value = _visualCharacterStudio.value.copy(
            projectId = project,
            characterId = character,
            busy = true,
            candidate = null,
            message = null,
            error = null,
        )
        viewModelScope.launch {
            val staged = withContext(Dispatchers.IO) { container.attachmentStore.stageFrom(uri) }
            if (staged == null) {
                _visualCharacterStudio.value = _visualCharacterStudio.value.copy(
                    busy = false,
                    error = "No se pudo leer la imagen seleccionada.",
                )
                return@launch
            }
            val assetId = "va_android_" + java.util.UUID.randomUUID().toString().replace("-", "")
            _visualCharacterStudio.value = _visualCharacterStudio.value.copy(
                pendingAssetId = assetId,
                pendingAttachmentId = staged.attachmentId,
                pendingMimeType = staged.mimeType.ifBlank { "image/jpeg" },
            )
            uploadPendingVisualMaster(project, character, characterName)
        }
    }

    fun retryVisualMasterUpload(characterName: String) {
        val state = _visualCharacterStudio.value
        val project = state.projectId ?: return
        val character = state.characterId ?: return
        if (state.pendingAssetId.isNullOrBlank() || state.pendingAttachmentId.isNullOrBlank() || state.busy) return
        _visualCharacterStudio.value = state.copy(busy = true, error = null, message = null)
        viewModelScope.launch {
            uploadPendingVisualMaster(project, character, characterName)
        }
    }

    private suspend fun uploadPendingVisualMaster(
        projectId: String,
        characterId: String,
        characterName: String,
    ) {
        val state = _visualCharacterStudio.value
        val assetId = state.pendingAssetId ?: return
        val attachmentId = state.pendingAttachmentId ?: return
        val encoded = withContext(Dispatchers.IO) {
            runCatching {
                val file = container.attachmentStore.resolve(attachmentId)
                    ?: error("La imagen candidata ya no está disponible.")
                val bytes = file.readBytes()
                require(bytes.isNotEmpty() && bytes.size <= 12 * 1024 * 1024) {
                    "La imagen candidata es demasiado grande."
                }
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            }
        }
        if (encoded.isFailure) {
            _visualCharacterStudio.value = _visualCharacterStudio.value.copy(
                busy = false,
                error = encoded.exceptionOrNull()?.message ?: "No se pudo preparar la imagen.",
            )
            return
        }

        container.liveSession.writingRoomVisualIngest(
            projectId = projectId,
            assetId = assetId,
            imageBase64 = encoded.getOrThrow(),
            mimeType = state.pendingMimeType,
            kind = "PRIMARY_REFERENCE",
            characterId = characterId,
            perspective = "front",
            alt = "Referencia visual candidata de " + characterName.ifBlank { characterId.substringAfter(":") },
        ).fold(
            onSuccess = { response ->
                _wikiVisualAttachments.update { it + (response.asset.asset_id to attachmentId) }
                _visualCharacterStudio.value = _visualCharacterStudio.value.copy(
                    busy = false,
                    candidate = response.asset,
                    message = "Candidato guardado. Revisa la imagen antes de aprobarla.",
                    error = null,
                )
            },
            onFailure = { error ->
                _visualCharacterStudio.value = _visualCharacterStudio.value.copy(
                    busy = false,
                    error = (error.message ?: "No se pudo guardar el candidato.") +
                        " Puedes reintentar el almacenamiento sin seleccionar otra imagen.",
                )
            },
        )
    }

    fun approveVisualMasterCandidate(
        projectId: String,
        characterId: String,
    ) {
        val candidate = _visualCharacterStudio.value.candidate ?: return
        if (_visualCharacterStudio.value.busy) return
        val nextRevision = ((_visualCharacterStudio.value.detail?.gallery?.primary?.visual_revision ?: 0) + 1)
            .coerceAtLeast(1)
        _visualCharacterStudio.value = _visualCharacterStudio.value.copy(
            busy = true,
            message = null,
            error = null,
        )
        viewModelScope.launch {
            val approved = container.liveSession.writingRoomVisualApproveExact(
                projectId = projectId,
                assetId = candidate.asset_id,
                assetSha256 = candidate.sha256,
                visualRevision = nextRevision,
            )
            if (approved.isFailure) {
                _visualCharacterStudio.value = _visualCharacterStudio.value.copy(
                    busy = false,
                    error = approved.exceptionOrNull()?.message ?: "No se pudo aprobar el master.",
                )
                return@launch
            }

            val active = _visualCharacterStudio.value.detail?.active_reference_pack
            val pack = container.liveSession.writingRoomVisualCreateReferencePack(
                projectId = projectId,
                characterId = characterId,
                masterAssetId = approved.getOrThrow().asset.asset_id,
                masterSha256 = approved.getOrThrow().asset.sha256,
                parentPackId = active?.pack_id,
            )
            if (pack.isFailure) {
                _visualCharacterStudio.value = _visualCharacterStudio.value.copy(
                    busy = false,
                    candidate = approved.getOrThrow().asset,
                    error = pack.exceptionOrNull()?.message
                        ?: "El master quedó aprobado, pero no se pudo crear la nueva revisión del pack.",
                )
                return@launch
            }
            _visualCharacterStudio.value = _visualCharacterStudio.value.copy(
                busy = false,
                candidate = null,
                pendingAssetId = null,
                pendingAttachmentId = null,
                message = "Master aprobado. Pack ${pack.getOrThrow().pack.revision} creado en DRAFT para sus vistas.",
                error = null,
            )
            loadVisualCharacter(projectId, characterId)
        }
    }

    fun stageVisualPackSlotFromUri(
        uri: Uri,
        slotKey: String,
        perspective: String,
        required: Boolean = true,
        characterName: String = "",
    ) {
        val state = _visualCharacterStudio.value
        val projectId = state.projectId ?: return
        val characterId = state.characterId ?: return
        val draftPack = state.detail?.reference_packs
            ?.filter { it.state == "DRAFT" }
            ?.maxByOrNull { it.revision }
            ?: return
        if (state.busy || slotKey.isBlank() || draftPack.pack_id.isBlank()) return
        _visualCharacterStudio.value = state.copy(busy = true, error = null, message = null)
        viewModelScope.launch {
            val staged = withContext(Dispatchers.IO) { container.attachmentStore.stageFrom(uri) }
            if (staged == null) {
                _visualCharacterStudio.value = _visualCharacterStudio.value.copy(
                    busy = false,
                    error = "No se pudo leer la vista seleccionada.",
                )
                return@launch
            }
            val pending = VisualPendingUpload(
                slotKey = slotKey,
                perspective = perspective,
                required = required,
                assetId = "va_android_" + java.util.UUID.randomUUID().toString().replace("-", ""),
                attachmentId = staged.attachmentId,
                mimeType = staged.mimeType.ifBlank { "image/jpeg" },
            )
            _visualCharacterStudio.value = _visualCharacterStudio.value.copy(
                pendingViewUploads = _visualCharacterStudio.value.pendingViewUploads + (slotKey to pending),
            )
            uploadPendingVisualPackSlot(draftPack.pack_id, pending, characterName)
        }
    }

    fun retryVisualPackSlotUpload(slotKey: String, characterName: String = "") {
        val state = _visualCharacterStudio.value
        val draftPack = state.detail?.reference_packs
            ?.filter { it.state == "DRAFT" }
            ?.maxByOrNull { it.revision }
            ?: return
        val pending = state.pendingViewUploads[slotKey] ?: return
        if (state.busy) return
        _visualCharacterStudio.value = state.copy(busy = true, error = null, message = null)
        viewModelScope.launch {
            uploadPendingVisualPackSlot(draftPack.pack_id, pending, characterName)
        }
    }

    private suspend fun uploadPendingVisualPackSlot(
        packId: String,
        pending: VisualPendingUpload,
        characterName: String,
    ) {
        val state = _visualCharacterStudio.value
        val projectId = state.projectId ?: return
        val characterId = state.characterId ?: return
        val encoded = withContext(Dispatchers.IO) {
            runCatching {
                val file = container.attachmentStore.resolve(pending.attachmentId)
                    ?: error("La vista candidata ya no está disponible.")
                val bytes = file.readBytes()
                require(bytes.isNotEmpty() && bytes.size <= 12 * 1024 * 1024) {
                    "La vista candidata es demasiado grande."
                }
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            }
        }
        if (encoded.isFailure) {
            _visualCharacterStudio.value = _visualCharacterStudio.value.copy(
                busy = false,
                error = encoded.exceptionOrNull()?.message ?: "No se pudo preparar la vista.",
            )
            return
        }

        container.liveSession.writingRoomVisualIngest(
            projectId = projectId,
            assetId = pending.assetId,
            imageBase64 = encoded.getOrThrow(),
            mimeType = pending.mimeType,
            kind = "IDENTITY_PACK",
            characterId = characterId,
            perspective = pending.perspective,
            alt = pending.perspective.replace("_", " ") + " de " +
                characterName.ifBlank { characterId.substringAfter(":") },
        ).fold(
            onSuccess = { response ->
                _wikiVisualAttachments.update {
                    it + (response.asset.asset_id to pending.attachmentId)
                }
                _visualCharacterStudio.value = _visualCharacterStudio.value.copy(
                    busy = false,
                    viewCandidates = _visualCharacterStudio.value.viewCandidates +
                        (pending.slotKey to response.asset),
                    message = "Vista " + pending.slotKey.replace("_", " ") +
                        " guardada como candidata. Falta aprobación humana.",
                    error = null,
                )
            },
            onFailure = { error ->
                _visualCharacterStudio.value = _visualCharacterStudio.value.copy(
                    busy = false,
                    error = (error.message ?: "No se pudo guardar la vista.") +
                        " Puedes reintentar storage con el mismo asset_id.",
                )
            },
        )
    }

    fun approveVisualPackSlot(slotKey: String) {
        val state = _visualCharacterStudio.value
        val projectId = state.projectId ?: return
        val draftPack = state.detail?.reference_packs
            ?.filter { it.state == "DRAFT" }
            ?.maxByOrNull { it.revision }
            ?: return
        val candidate = state.viewCandidates[slotKey] ?: return
        val pending = state.pendingViewUploads[slotKey] ?: return
        if (state.busy) return
        _visualCharacterStudio.value = state.copy(busy = true, error = null, message = null)
        viewModelScope.launch {
            val approved = container.liveSession.writingRoomVisualApproveExact(
                projectId = projectId,
                assetId = candidate.asset_id,
                assetSha256 = candidate.sha256,
                visualRevision = draftPack.revision,
            )
            if (approved.isFailure) {
                _visualCharacterStudio.value = _visualCharacterStudio.value.copy(
                    busy = false,
                    error = approved.exceptionOrNull()?.message ?: "No se pudo aprobar la vista.",
                )
                return@launch
            }
            val asset = approved.getOrThrow().asset
            val bound = container.liveSession.writingRoomVisualAddPackSlot(
                projectId = projectId,
                packId = draftPack.pack_id,
                slotKey = slotKey,
                assetId = asset.asset_id,
                assetSha256 = asset.sha256,
                required = pending.required,
            )
            if (bound.isFailure) {
                _visualCharacterStudio.value = _visualCharacterStudio.value.copy(
                    busy = false,
                    error = bound.exceptionOrNull()?.message
                        ?: "La vista quedó aprobada, pero no se pudo enlazar al pack.",
                )
                return@launch
            }
            _visualCharacterStudio.value = _visualCharacterStudio.value.copy(
                busy = false,
                viewCandidates = _visualCharacterStudio.value.viewCandidates - slotKey,
                pendingViewUploads = _visualCharacterStudio.value.pendingViewUploads - slotKey,
                message = "Vista " + slotKey.replace("_", " ") + " aprobada y enlazada al pack.",
                error = null,
            )
            loadVisualCharacter(projectId, draftPack.character_id)
        }
    }

    fun prepareVisualReferencePack(packId: String) {
        val state = _visualCharacterStudio.value
        val projectId = state.projectId ?: return
        val characterId = state.characterId ?: return
        if (state.busy || packId.isBlank()) return
        _visualCharacterStudio.value = state.copy(busy = true, error = null, message = null)
        viewModelScope.launch {
            container.liveSession.writingRoomVisualPreparePack(projectId, packId).fold(
                onSuccess = {
                    _visualCharacterStudio.value = _visualCharacterStudio.value.copy(
                        busy = false,
                        message = "Pack listo para aprobación final.",
                        error = null,
                    )
                    loadVisualCharacter(projectId, characterId)
                },
                onFailure = { error ->
                    _visualCharacterStudio.value = _visualCharacterStudio.value.copy(
                        busy = false,
                        error = error.message ?: "El pack todavía no está listo.",
                    )
                },
            )
        }
    }

    fun approveVisualReferencePack(packId: String) {
        val state = _visualCharacterStudio.value
        val projectId = state.projectId ?: return
        val characterId = state.characterId ?: return
        if (state.busy || packId.isBlank()) return
        _visualCharacterStudio.value = state.copy(busy = true, error = null, message = null)
        viewModelScope.launch {
            container.liveSession.writingRoomVisualApprovePack(projectId, packId).fold(
                onSuccess = { response ->
                    _visualCharacterStudio.value = _visualCharacterStudio.value.copy(
                        busy = false,
                        message = "VisualReferencePack rev " + response.pack.revision +
                            " aprobado y activado.",
                        error = null,
                    )
                    loadVisualCharacter(projectId, characterId)
                },
                onFailure = { error ->
                    _visualCharacterStudio.value = _visualCharacterStudio.value.copy(
                        busy = false,
                        error = error.message ?: "No se pudo aprobar el pack.",
                    )
                },
            )
        }
    }

    fun clearVisualCharacterStudioMessage() {
        _visualCharacterStudio.value = _visualCharacterStudio.value.copy(message = null, error = null)
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
}

private const val DEFAULT_CONTROL_PLANE_URL = "https://vps-8817149e.tail6eec63.ts.net:8443"

class JarvisViewModelFactory(private val app: JarvisApp) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        JarvisViewModel(app) as T
}
