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

    private val appUpdateManager = AppUpdateManager(
        app,
        container.liveSession,
        baseUrlProvider = {
            settings.value.lastControlPlaneUrl.ifBlank { settings.value.gatewayBaseUrl }
        },
    )
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

    data class WritingWorkspaceState(
        val busy: Boolean = false,
        val busyLabel: String = "",
        val overview: WritingRoomOverview? = null,
        val chat: WritingRoomAutoChat? = null,
        val streamingText: String = "",
        val wikiHome: WritingWikiHome? = null,
        val wiki: WritingWikiSearch? = null,
        val plans: List<WritingPlanItem> = emptyList(),
        val chapters: List<WritingChapterSummary> = emptyList(),
        val activeChapter: WritingChapter? = null,
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
        writingWorkspaceBusy("Loading workspace")
        viewModelScope.launch {
            val overview = container.liveSession.writingRoomOverview(projectId)
            val wikiHome = container.liveSession.writingRoomWikiHome(projectId)
            val plans = container.liveSession.writingRoomPlanList(projectId)
            val chapters = container.liveSession.writingRoomChapterList(projectId)
            val library = container.liveSession.writingRoomLibraryList(projectId)
            val failure = listOf(overview, wikiHome, plans, chapters, library).firstOrNull { it.isFailure }
            if (failure != null) {
                writingWorkspaceError(failure.exceptionOrNull())
                return@launch
            }
            _writingWorkspace.value = _writingWorkspace.value.copy(
                busy = false,
                busyLabel = "",
                overview = overview.getOrNull(),
                wikiHome = wikiHome.getOrNull(),
                plans = plans.getOrNull()?.items.orEmpty(),
                chapters = chapters.getOrNull()?.items.orEmpty(),
                library = library.getOrNull(),
                error = null,
            )
        }
    }

    fun runWritingRoomAutoChat(
        projectId: String,
        projectTitle: String,
        prompt: String,
        room: String = "chat",
    ) {
        val clean = prompt.trim()
        if (clean.isEmpty()) return
        writingWorkspaceBusy("JARVIS is routing the Writing Room task")
        _writingWorkspace.value = _writingWorkspace.value.copy(
            chat = null,
            streamingText = "",
        )
        viewModelScope.launch {
            val result = container.liveSession.writingRoomAutoChatStream(
                projectId = projectId,
                projectTitle = projectTitle,
                prompt = clean,
                room = room,
                onDelta = { delta ->
                    _writingWorkspace.value = _writingWorkspace.value.copy(
                        streamingText = _writingWorkspace.value.streamingText + delta,
                        busyLabel = "Streaming response",
                    )
                },
            )
            result.fold(
                onSuccess = {
                    _writingWorkspace.value = _writingWorkspace.value.copy(
                        busy = false,
                        busyLabel = "",
                        chat = it,
                        streamingText = "",
                        error = null,
                    )
                },
                onFailure = {
                    val partial = _writingWorkspace.value.streamingText
                    _writingWorkspace.value = _writingWorkspace.value.copy(
                        busy = false,
                        busyLabel = "",
                        streamingText = partial,
                        error = it.message ?: "Writing Room request failed",
                    )
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
        writingWorkspaceBusy("Building Story State Brief")
        viewModelScope.launch {
            val result = container.liveSession.writingRoomChapterStart(
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
            if (result.isFailure) {
                writingWorkspaceError(result.exceptionOrNull())
                return@launch
            }
            val chapter = result.getOrThrow().chapter
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
            _writingWorkspace.value = _writingWorkspace.value.copy(
                busy = false,
                busyLabel = "",
                activeChapter = chapter,
                engineReview = if (step == "review") action.engine_review else null,
                chapters = list.getOrNull()?.items ?: _writingWorkspace.value.chapters,
                error = list.exceptionOrNull()?.message,
            )
        }
    }

    fun saveWritingChapterDraft(projectId: String, chapterId: String, draftText: String) {
        writingWorkspaceBusy("Saving draft")
        viewModelScope.launch {
            val result = container.liveSession.writingRoomSaveDraft(projectId, chapterId, draftText)
            result.fold(
                onSuccess = {
                    _writingWorkspace.value = _writingWorkspace.value.copy(
                        busy = false,
                        busyLabel = "",
                        activeChapter = it.chapter,
                        engineReview = null,
                        error = null,
                    )
                },
                onFailure = ::writingWorkspaceError,
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

    private val _imageGenerationState = MutableStateFlow<ImageGenerationState>(ImageGenerationState.Idle)
    val imageGenerationState: StateFlow<ImageGenerationState> = _imageGenerationState

    fun generateImage(prompt: String) {
        val clean = prompt.trim()
        if (clean.isBlank() || _imageGenerationState.value is ImageGenerationState.Busy) return
        val conversationId = _conversationId.value ?: conversations.newConversationId().also {
            _conversationId.value = it
        }
        _imageGenerationState.value = ImageGenerationState.Busy
        viewModelScope.launch {
            container.liveSession.generateImage(clean).fold(
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

    private val _pendingAttachments = MutableStateFlow<List<StagedAttachment>>(emptyList())
    val pendingAttachments: StateFlow<List<StagedAttachment>> = _pendingAttachments

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
            _pendingAttachments.value = _pendingAttachments.value + staged
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
        _pendingAttachments.value = _pendingAttachments.value.filterNot { it.attachmentId == attachmentId }
        viewModelScope.launch(Dispatchers.IO) { container.attachmentStore.delete(attachmentId) }
    }

    /** Send text and/or pending attachments (AND-W6: attachment IDs, never paths). */
    fun sendWithAttachments(text: String) {
        val attachments = _pendingAttachments.value
        val trimmed = text.trim()
        if (trimmed.isEmpty() && attachments.isEmpty()) return
        val ids = attachments.map { it.attachmentId }
        val body = trimmed.ifBlank { "(photo)" }
        val id = _conversationId.value ?: conversations.newConversationId().also { _conversationId.value = it }
        conversations.send(id, body, ids)
        _pendingAttachments.value = emptyList()
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

private const val DEFAULT_CONTROL_PLANE_URL = "https://vps-8817149e.tail6eec63.ts.net"

class JarvisViewModelFactory(private val app: JarvisApp) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        JarvisViewModel(app) as T
}
