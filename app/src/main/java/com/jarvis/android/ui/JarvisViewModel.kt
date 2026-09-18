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
        _conversationId.value = id
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

    fun setReducedMotion(value: Boolean) = viewModelScope.launch { container.settings.setReducedMotion(value) }

    fun setOwnerName(value: String) = viewModelScope.launch { container.settings.setOwnerName(value.trim()) }

    fun setIsOwner(value: Boolean) = viewModelScope.launch { container.settings.setIsOwner(value) }

    // ---- local conversation management (no gateway contract for this) --------

    fun renameConversation(id: String, title: String) = conversations.renameConversation(id, title)

    fun deleteConversation(id: String) {
        conversations.deleteConversation(id)
        if (_conversationId.value == id) _conversationId.value = null
    }

    /**
     * Grades one Learning Room answer on the device. Returns whether it was
     * accepted; a correct answer is recorded so its reward stays unlocked.
     */
    fun submitLesson(lessonId: String, attempt: String): Boolean {
        val done = com.jarvis.android.data.learning.LessonProgress
            .completedFor(settings.value.lessonProgress, settings.value.activeLearnerId)
        val accepted = com.jarvis.android.data.learning.LearningCatalog.check(lessonId, attempt, done)
        if (accepted) {
            val profile = settings.value.activeLearnerId
            viewModelScope.launch { container.settings.completeLesson(profile, lessonId) }
        }
        return accepted
    }

    fun selectLearner(profileId: String) = viewModelScope.launch { container.settings.setActiveLearner(profileId) }

    fun addLearner(name: String) = viewModelScope.launch { container.settings.addLearner(name) }

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

class JarvisViewModelFactory(private val app: JarvisApp) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        JarvisViewModel(app) as T
}
