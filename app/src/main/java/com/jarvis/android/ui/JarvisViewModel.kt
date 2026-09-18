package com.jarvis.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jarvis.android.JarvisApp
import com.jarvis.android.contract.ApprovalOutcome
import com.jarvis.android.data.local.ConversationEntity
import com.jarvis.android.data.local.ConversationListItem
import com.jarvis.android.data.local.ShareSafeText
import com.jarvis.android.data.media.StagedAttachment
import com.jarvis.android.data.repo.ChatMessage
import com.jarvis.android.data.repo.ConversationSummary
import com.jarvis.android.data.repo.SessionSnapshot
import com.jarvis.android.di.AppContainer
import com.jarvis.android.transport.fake.FakeScenario
import android.net.Uri
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

    val messages: StateFlow<List<ChatMessage>> = _conversationId
        .flatMapLatest { id ->
            if (id == null) kotlinx.coroutines.flow.flowOf(emptyList())
            else conversations.observeMessages(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val settings = container.settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), com.jarvis.android.data.prefs.SettingsStore.Settings())

    fun openConversation(id: String) {
        setOpenConversation(id)
    }

    private val _threadOpenNonce = MutableStateFlow(0)
    val threadOpenNonce: StateFlow<Int> = _threadOpenNonce

    /** Open a conversation and ask the chat surface to show the thread, not the list. */
    fun openConversationThread(id: String) {
        setOpenConversation(id)
        _threadOpenNonce.value += 1
    }

    private fun setOpenConversation(id: String) {
        _conversationId.value = id
        viewModelScope.launch { productivity.markOpened(id) }
    }

    // ---- local drafts and search (no transport, never sent on their own) ------

    private val drafts = container.drafts
    private val productivity = container.productivity

    /** The unsent text of the open conversation, empty when none is open. */
    val composerDraft: StateFlow<String> = _conversationId
        .flatMapLatest { id ->
            if (id == null) kotlinx.coroutines.flow.flowOf("")
            else drafts.observeDraft(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _showHidden = MutableStateFlow(false)
    val showHidden: StateFlow<Boolean> = _showHidden

    /**
     * Local pin/hide/title overlay on the stored conversation list. Hidden
     * rows stay in Room; this only filters the list on this device.
     */
    val visibleConversations: StateFlow<List<ConversationListItem>> =
        kotlinx.coroutines.flow.combine(
            conversationList,
            productivity.observeMeta(),
            _searchQuery,
            _showHidden,
        ) { list, meta, query, hidden ->
            productivity.overlay(list, meta, query, hidden)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val openConversationItem: StateFlow<ConversationListItem?> =
        kotlinx.coroutines.flow.combine(
            conversationList,
            productivity.observeMeta(),
            _conversationId,
        ) { list, meta, id ->
            if (id == null) null
            else productivity.decorate(list, meta).firstOrNull { it.conversationId == id }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setShowHidden(value: Boolean) {
        _showHidden.value = value
    }

    fun setPinned(conversationId: String, pinned: Boolean) {
        viewModelScope.launch { productivity.setPinned(conversationId, pinned) }
    }

    fun setHiddenOnDevice(conversationIds: Collection<String>, hidden: Boolean) {
        viewModelScope.launch { productivity.setArchived(conversationIds, hidden) }
    }

    fun renameOnDevice(conversationId: String, title: String) {
        viewModelScope.launch { productivity.setLocalTitle(conversationId, title) }
    }

    /** Null when the body looks like a credential or internal payload. */
    fun shareableMessageText(text: String): String? = ShareSafeText.visibleChatText(text)

    /** Persists the composer text for the open conversation only. */
    fun updateDraft(text: String) {
        val id = _conversationId.value ?: return
        viewModelScope.launch { drafts.saveDraft(id, text) }
    }

    // ---- AND-W9 Projects shell (Lane F: backend NOT_CONNECTED) ----------------

    private val _projectsEpoch = MutableStateFlow(0)
    val projects = _projectsEpoch
        .flatMapLatest { container.projectsRepository.observeProjects() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), com.jarvis.android.data.projects.ProjectsResult.Loading)

    fun refreshProjects() { _projectsEpoch.value += 1 }

    fun conversationsFor(projectId: com.jarvis.android.data.projects.ProjectId) =
        container.projectsRepository.conversationsFor(projectId)

    fun startNewConversation(onReady: (String) -> Unit = {}) {
        conversations.newConversation { id ->
            setOpenConversation(id)
            onReady(id)
        }
    }

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val id = _conversationId.value ?: run {
            conversations.newConversation { newId ->
                setOpenConversation(newId)
                conversations.send(newId, trimmed)
            }
            return
        }
        conversations.send(id, trimmed)
        viewModelScope.launch { drafts.saveDraft(id, "") }
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
                setOpenConversation(it)
            }
            val staged = withContext(Dispatchers.IO) {
                container.attachmentStore.stageFrom(uri)
            } ?: return@launch
            _pendingAttachments.value = _pendingAttachments.value + staged
            session.uploadAttachment(
                attachmentId = staged.attachmentId,
                conversationId = conversationId,
                filename = staged.filename,
                mimeType = staged.mimeType,
                sizeBytes = staged.sizeBytes,
            )
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
        val id = _conversationId.value ?: conversations.newConversationId().also { setOpenConversation(it) }
        conversations.send(id, body, ids)
        _pendingAttachments.value = emptyList()
        viewModelScope.launch { drafts.saveDraft(id, "") }
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

    /** Log in to the existing PC-A /api/app surface over the private front door. */
    fun liveLogin(base: String, user: String, password: String, onDone: (Boolean) -> Unit = {}) {
        _liveAuth.value = LiveAuthState.Busy
        viewModelScope.launch {
            val result = container.liveSession.login(base, user, password)
            if (result.isSuccess) {
                container.settings.setPaired(true, container.deviceIdentity.provision())
                container.settings.setUseFake(false)
                container.settings.setBaseUrl(container.liveSession.baseUrl)
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

    fun setVoiceInputEnabled(value: Boolean) = viewModelScope.launch { container.settings.setVoiceInputEnabled(value) }
    fun setPreferOnDeviceRecognition(value: Boolean) =
        viewModelScope.launch { container.settings.setPreferOnDeviceRecognition(value) }
    fun setReadRepliesAloud(value: Boolean) = viewModelScope.launch { container.settings.setReadRepliesAloud(value) }
    fun setTtsRate(value: Float) = viewModelScope.launch { container.settings.setTtsRate(value) }

    private val _voiceUi = MutableStateFlow(com.jarvis.android.voice.VoiceUiState())
    val voiceUi: StateFlow<com.jarvis.android.voice.VoiceUiState> = _voiceUi

    private lateinit var voice: com.jarvis.android.voice.VoiceController
    private val voiceSpeaker = com.jarvis.android.voice.AndroidLocalSpeaker(
        context = app,
        rate = { settings.value.ttsRate },
        onDone = { id ->
            if (::voice.isInitialized) {
                voice.onSpeakDone(id)
                pushVoice()
            }
        },
    )
    init {
        val listener = object : com.jarvis.android.voice.SpeechListener {
            override fun onPartial(text: String) {
                voice.onPartial(text)
                pushVoice()
            }
            override fun onProcessing() {
                voice.onProcessing()
                pushVoice()
            }
            override fun onFinal(text: String) {
                voice.onFinal(text)
                pushVoice()
            }
            override fun onError(safeMessage: String) {
                voice.onRecognizerError(safeMessage)
                pushVoice()
            }
        }
        voice = com.jarvis.android.voice.VoiceController(
            recognizer = com.jarvis.android.voice.AndroidSpeechRecognizerClient(app, listener),
            speaker = voiceSpeaker,
            readAloud = { settings.value.readRepliesAloud },
        )
        val ttsGate = com.jarvis.android.voice.AssistantTtsGate()
        viewModelScope.launch {
            messages.collect { list ->
                val convId = conversationId.value
                val terminals = list
                    .filter { it.role == "assistant" && it.status.isTerminal }
                    .map {
                        com.jarvis.android.voice.AssistantTtsGate.Terminal(
                            conversationId = convId.orEmpty(),
                            messageId = it.clientRequestId,
                            text = it.text,
                            isError = it.status is com.jarvis.android.data.state.RequestStatus.Failed,
                        )
                    }
                ttsGate.onSnapshot(convId, terminals).forEach { fresh ->
                    voice.onAssistantCompleted(fresh.messageId, fresh.text, isError = fresh.isError, isFinal = true)
                }
                pushVoice()
            }
        }
    }

    fun voiceOrbCue() = voice.orbCue

    private fun pushVoice() { _voiceUi.value = voice.state }

    fun onVoiceMic() {
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
            app, android.Manifest.permission.RECORD_AUDIO,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        voice.onMicTapped(granted)
        pushVoice()
    }

    fun editVoiceDraft(text: String) {
        voice.editDraft(text)
        pushVoice()
    }

    fun consumeVoiceSend(): String? {
        val text = voice.consumeSend()
        pushVoice()
        return text
    }

    fun cancelVoice() {
        voice.cancelReview()
        pushVoice()
    }

    fun retryVoice() {
        voice.retry()
        pushVoice()
    }

    fun stopVoice() {
        voice.abandon()
        voice.stopSpeaking()
        pushVoice()
    }

    fun setVoiceChatVisible(visible: Boolean) {
        voice.setChatVisible(visible)
        if (!visible) voice.stopSpeaking()
        pushVoice()
    }

    fun setVoiceAppForeground(foreground: Boolean) {
        voice.setAppForeground(foreground)
        if (!foreground) voice.stopSpeaking()
        pushVoice()
    }

    val voiceMicPermission = com.jarvis.android.voice.VoiceMicPermission()

    fun onVoicePermissionResult(granted: Boolean) {
        when (voiceMicPermission.onRequestResult(granted)) {
            com.jarvis.android.voice.VoiceMicPermission.Action.START -> {
                voice.onMicTapped(true)
                pushVoice()
            }
            com.jarvis.android.voice.VoiceMicPermission.Action.FAIL -> {
                voice.onMicTapped(false)
                pushVoice()
            }
            com.jarvis.android.voice.VoiceMicPermission.Action.REQUEST -> Unit
        }
    }

    override fun onCleared() {
        voice.shutdown()
        super.onCleared()
    }

    fun setReducedMotion(value: Boolean) = viewModelScope.launch { container.settings.setReducedMotion(value) }

    fun setAnimationIntensity(value: Float) =
        viewModelScope.launch { container.settings.setAnimationIntensity(value) }

    fun setCompactDensity(value: Boolean) =
        viewModelScope.launch { container.settings.setCompactDensity(value) }

    /** Appearance only. Never touches pairing, keys, role, lock or the gateway URL. */
    fun resetLocalUiPreferences() = viewModelScope.launch { container.settings.resetLocalUiPreferences() }

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

    /**
     * Home command center. Assembled from already-available local/session
     * flows only — never invents server counts or model labels.
     */
    val home: StateFlow<com.jarvis.android.ui.home.HomeSnapshot> =
        kotlinx.coroutines.flow.combine(
            kotlinx.coroutines.flow.combine(
                bootShown,
                snapshot,
                voiceUi,
                settings,
                chatAccess,
            ) { boot, snap, voice, set, access ->
                HomeHead(
                    bootShown = boot,
                    snapshot = snap,
                    voice = voice,
                    isOwner = set.isOwner,
                    voiceInputEnabled = set.voiceInputEnabled,
                    catalog = access?.entries?.map { entry ->
                        com.jarvis.android.ui.home.ServerProfileEntry(
                            profile = entry.profile,
                            label = entry.label,
                            model = entry.model,
                            state = entry.state,
                        )
                    },
                )
            },
            chatProfile,
            visibleConversations,
            projects,
        ) { head, profileId, convos, projectsResult ->
            com.jarvis.android.ui.home.HomeCommandCenter.snapshot(
                bootShown = head.bootShown,
                connection = head.snapshot.connection,
                phaseName = head.snapshot.phase.name,
                session = head.snapshot.session,
                voicePhase = if (head.voiceInputEnabled) head.voice.phase else com.jarvis.android.voice.VoicePhase.IDLE,
                speaking = head.voiceInputEnabled && head.voice.speaking,
                isOwner = head.isOwner,
                catalog = head.catalog,
                selectedProfileId = profileId,
                conversations = convos,
                debugBuild = com.jarvis.android.BuildConfig.DEBUG,
                projects = projectsResult,
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            com.jarvis.android.ui.home.HomeCommandCenter.snapshot(
                bootShown = false,
                connection = com.jarvis.android.data.state.ConnectionState.DISCONNECTED,
                phaseName = com.jarvis.android.data.repo.SessionPhase.DISCONNECTED.name,
                session = com.jarvis.android.data.state.SessionState(),
                voicePhase = com.jarvis.android.voice.VoicePhase.IDLE,
                speaking = false,
                isOwner = false,
                catalog = null,
                selectedProfileId = "",
                conversations = emptyList(),
                debugBuild = com.jarvis.android.BuildConfig.DEBUG,
                projects = com.jarvis.android.data.projects.ProjectsResult.Loading,
            ),
        )

    private val _enterChatOnConversations = MutableStateFlow(false)
    val enterChatOnConversations: StateFlow<Boolean> = _enterChatOnConversations

    fun openConversationAndShowChat(id: String) {
        openConversation(id)
        _enterChatOnConversations.value = true
    }

    fun consumeEnterChatOnConversations() {
        _enterChatOnConversations.value = false
    }

    /** Re-arm the lock when the app leaves the foreground. */
    fun relock() {
        if (settings.value.appLockEnabled) _unlocked.value = false
    }

    // ---- Fake Gateway scenario control (dev builds; plan §7) ------------------

    var fakeScenario: FakeScenario = FakeScenario.HAPPY
        private set

    val transportMode: AppContainer.TransportMode get() = container.transportMode

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
            setOpenConversation(it)
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

private data class HomeHead(
    val bootShown: Boolean,
    val snapshot: SessionSnapshot,
    val voice: com.jarvis.android.voice.VoiceUiState,
    val isOwner: Boolean,
    val voiceInputEnabled: Boolean,
    val catalog: List<com.jarvis.android.ui.home.ServerProfileEntry>?,
)

class JarvisViewModelFactory(private val app: JarvisApp) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        JarvisViewModel(app) as T
}
