package com.jarvis.android.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.jarvis.android.data.repo.ChatMessage
import com.jarvis.android.data.state.RequestStatus
import com.jarvis.android.ui.JarvisViewModel
import com.jarvis.android.ui.chat.AutoFollow
import com.jarvis.android.ui.chat.ConversationActivity
import com.jarvis.android.ui.components.MarkdownBlock
import com.jarvis.android.ui.components.JarvisBrain
import com.jarvis.android.ui.components.HolographicSendButton
import com.jarvis.android.ui.components.markdownBlocks
import com.jarvis.android.ui.components.renderInlineMarkdown
import com.jarvis.android.ui.components.JarvisOrb
import com.jarvis.android.ui.components.OrbActivity
import com.jarvis.android.ui.components.ScanLine
import com.jarvis.android.ui.components.TypingDots
import com.jarvis.android.ui.components.streamingText
import com.jarvis.android.ui.i18n.LocalAppStrings
import com.jarvis.android.ui.i18n.EnAppStrings
import com.jarvis.android.ui.shared.OwnerAvatar
import com.jarvis.android.ui.shared.rememberAttachmentThumb
import com.jarvis.android.ui.theme.HudTextStyle
import com.jarvis.android.ui.theme.LocalJarvisAccents
import com.jarvis.android.ui.theme.LocalReducedMotion
import com.jarvis.android.ui.theme.JarvisTheme
import com.jarvis.android.ui.theme.JarvisUiTokens
import com.jarvis.android.ui.theme.jarvisScreenInset
import com.jarvis.android.ui.theme.jarvisTextFieldColors

@Composable
fun ConversationsScreen(
    vm: JarvisViewModel,
    openConversationRequest: Long = 0L,
) {
    var showList by rememberSaveable { mutableStateOf(true) }
    val conversationId by vm.conversationId.collectAsStateWithLifecycle()

    LaunchedEffect(openConversationRequest) {
        if (openConversationRequest > 0L) {
            showList = false
            if (conversationId == null) vm.startNewConversation()
        }
    }

    val reduced = LocalReducedMotion.current
    AnimatedContent(
        targetState = showList || conversationId == null,
        transitionSpec = {
            if (reduced) {
                fadeIn(tween(0)).togetherWith(fadeOut(tween(0)))
            } else if (targetState) {
                (fadeIn(tween(260)) + slideInHorizontally(tween(260)) { -it / 6 })
                    .togetherWith(fadeOut(tween(220)) + slideOutHorizontally(tween(220)) { it / 6 })
            } else {
                (fadeIn(tween(280)) + slideInHorizontally(tween(280)) { it / 6 })
                    .togetherWith(fadeOut(tween(240)) + slideOutHorizontally(tween(240)) { -it / 6 })
            }
        },
        label = "chatNav",
    ) { isList ->
        if (isList) {
            ConversationListView(
                vm = vm,
                onOpen = { vm.openConversation(it); showList = false },
                onNew = { showList = false; vm.startNewConversation() },
            )
        } else {
            ChatScreen(vm, onBack = { showList = true })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationListView(
    vm: JarvisViewModel,
    onOpen: (String) -> Unit,
    onNew: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val reducedMotion = LocalReducedMotion.current
    val screenInset = jarvisScreenInset()
    val listState by vm.conversationListState.collectAsStateWithLifecycle()
    val items = (listState as? com.jarvis.android.data.repo.ConversationListState.Ready)?.conversations
        ?: emptyList()
    val loading = listState is com.jarvis.android.data.repo.ConversationListState.Loading
    val accents = LocalJarvisAccents.current
    val avatarEpoch by vm.avatarEpoch.collectAsStateWithLifecycle()
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<com.jarvis.android.data.repo.ConversationSummary?>(null) }
    var deletingConversationId by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val activeConversationIds = ConversationActivity.activeConversationIds(
        snapshot.session.requests.values,
    )

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(strings.conversationsTitle, style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color(0xFFE8EEF8),
                    navigationIconContentColor = Color(0xFFE8EEF8),
                    actionIconContentColor = Color(0xFFE8EEF8),
                ),
                windowInsets = WindowInsets(0),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNew,
                containerColor = MaterialTheme.colorScheme.primary,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(strings.newChat) },
            )
        },
    ) { pad ->
        if (loading) {
            Column(
                Modifier.fillMaxSize().padding(pad),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(Modifier.size(28.dp), color = accents.orbGlow)
                Spacer(Modifier.height(14.dp))
                Text(
                    strings.loadingConversations,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else if (items.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(pad).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                JarvisBrain(size = 140.dp, activity = OrbActivity.IDLE)
                Spacer(Modifier.height(24.dp))
                Text(strings.readyWhenYouAre, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    strings.startConversationSubtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
                Button(onClick = onNew, modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(strings.newChat)
                }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(pad),
                contentPadding = PaddingValues(horizontal = screenInset, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(items, key = { c -> c.conversationId }) { c ->
                    val isActive = c.conversationId in activeConversationIds
                    var menuExpanded by remember(c.conversationId) { mutableStateOf(false) }
                    Surface(
                        onClick = { onOpen(c.conversationId) },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                        border = BorderStroke(
                            1.dp,
                            if (isActive) accents.orbGlow.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                        ),
                        modifier = Modifier.fillMaxWidth().then(
                            if (reducedMotion) Modifier else Modifier.animateItem(),
                        ),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Outlined.ChatBubbleOutline,
                                contentDescription = null,
                                tint = if (isActive) accents.orbGlow else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(c.title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    com.jarvis.android.ui.chat.ConversationTime.label(
                                        c.updatedAtMs,
                                        System.currentTimeMillis(),
                                    ),
                                    style = HudTextStyle,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (isActive) {
                                Text(
                                    strings.activeCount(1),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = accents.orbGlow,
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                            Box {
                                IconButton(
                                    onClick = { menuExpanded = true },
                                    enabled = !isActive && deletingConversationId == null,
                                ) {
                                    if (deletingConversationId == c.conversationId) {
                                        CircularProgressIndicator(
                                            Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = accents.orbGlow,
                                        )
                                    } else {
                                        Icon(
                                            Icons.Filled.MoreVert,
                                            contentDescription = strings.conversationOptions,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                DropdownMenu(
                                    expanded = menuExpanded,
                                    onDismissRequest = { menuExpanded = false },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(strings.deleteConversation) },
                                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                                        onClick = { menuExpanded = false; pendingDelete = c },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { conversation ->
        AlertDialog(
            onDismissRequest = { if (deletingConversationId == null) pendingDelete = null },
            title = { Text(strings.deleteConversationTitle) },
            text = { Text(strings.deleteConversationBody) },
            dismissButton = {
                TextButton(
                    onClick = { pendingDelete = null },
                    enabled = deletingConversationId == null,
                ) { Text(strings.cancel) }
            },
            confirmButton = {
                TextButton(
                    enabled = deletingConversationId == null,
                    onClick = {
                        pendingDelete = null
                        deletingConversationId = conversation.conversationId
                        scope.launch {
                            val deleted = runCatching {
                                vm.deleteConversation(conversation.conversationId)
                            }.getOrDefault(false)
                            deletingConversationId = null
                            if (!deleted) snackbarHostState.showSnackbar(strings.deleteConversationFailed)
                        }
                    },
                ) { Text(strings.deleteConversation, color = MaterialTheme.colorScheme.error) }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ChatScreen(vm: JarvisViewModel, onBack: () -> Unit) {
    val messages by vm.messages.collectAsStateWithLifecycle()
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val pendingAttachments by vm.pendingAttachments.collectAsStateWithLifecycle()
    val attachmentError by vm.attachmentError.collectAsStateWithLifecycle()
    val strings = LocalAppStrings.current
    val chatAccess by vm.chatAccess.collectAsStateWithLifecycle()
    val imageGenerationState by vm.imageGenerationState.collectAsStateWithLifecycle()
    val imageGenerationDetails by vm.lastImageGenerationDetails.collectAsStateWithLifecycle()
    val avatarEpoch by vm.avatarEpoch.collectAsStateWithLifecycle()
    val conversationId by vm.conversationId.collectAsStateWithLifecycle()
    val agentProjectId by vm.globalChatAgentProject.collectAsStateWithLifecycle()
    val projectResults by vm.projects.collectAsStateWithLifecycle()
    val availableAgentProjects = (projectResults as?
        com.jarvis.android.data.projects.ProjectsResult.Loaded)
        ?.projects?.filter { it.kind == com.jarvis.android.data.projects.ProjectKind.WRITING_ROOM }
        .orEmpty()
    var projectMenuOpen by remember(conversationId) { mutableStateOf(false) }
    var input by rememberSaveable(conversationId) { mutableStateOf("") }
    var showImageOptions by rememberSaveable(conversationId) { mutableStateOf(false) }
    var selectedImageMode by rememberSaveable(conversationId) { mutableStateOf("speed") }
    var selectedImageModel by rememberSaveable(conversationId) { mutableStateOf("auto") }
    var previewGeneratedImage by rememberSaveable(conversationId) { mutableStateOf<String?>(null) }
    var imageStudioAttachmentId by rememberSaveable(conversationId) { mutableStateOf<String?>(null) }
    var pendingSaveGeneratedImage by remember { mutableStateOf<String?>(null) }
    var imageSaveStatus by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val accents = LocalJarvisAccents.current
    val reducedMotion = LocalReducedMotion.current
    val screenInset = jarvisScreenInset()
    val context = LocalContext.current

    imageStudioAttachmentId?.let { attachmentId ->
        ImageStudioScreen(
            vm = vm,
            initialAttachmentId = attachmentId,
            onBack = {
                vm.resetImageEditState()
                imageStudioAttachmentId = null
            },
        )
        return
    }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 3),
    ) { uris -> vm.stageAttachments(uris) }
    val documentPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { vm.stageAttachment(it, document = true) } }

    val saveGeneratedImage = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/jpeg"),
    ) { uri ->
        val attachmentId = pendingSaveGeneratedImage
        if (uri != null && attachmentId != null) {
            scope.launch {
                val saved = withContext(Dispatchers.IO) {
                    runCatching {
                        val source = vm.attachmentStore.resolve(attachmentId)
                            ?: error("Generated image is no longer available")
                        context.contentResolver.openOutputStream(uri)?.use { output ->
                            source.inputStream().use { inputStream -> inputStream.copyTo(output) }
                        } ?: error("Could not open selected destination")
                    }.isSuccess
                }
                imageSaveStatus = if (saved) "Image saved" else "Could not save image"
            }
        }
        pendingSaveGeneratedImage = null
    }

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    val liveRequest = ConversationActivity.activeRequest(
        snapshot.session.requests.values,
        conversationId,
    )
    val chatProgress = liveRequest?.let { request ->
        snapshot.session.tasks["chat_${request.clientRequestId}"]?.label
    }
    val chatStreaming = liveRequest != null
    val imageGenerating = imageGenerationState is JarvisViewModel.ImageGenerationState.Busy
    val busy = chatStreaming || imageGenerating
    val imageInputReady = chatAccess?.capabilityStates?.get("image_input") == "ready"
    val documentInputReady = chatAccess?.capabilityStates?.get("document_input") == "ready"
    val imageGenerationReady = chatAccess?.capabilityStates?.get("image_generation") == "ready"
    val imageGenerationAccess = chatAccess?.imageGeneration

    LaunchedEffect(Unit) { vm.refreshChatAccess() }
    LaunchedEffect(imageGenerationAccess) {
        val access = imageGenerationAccess ?: return@LaunchedEffect
        if (access.modes.none { it.id == selectedImageMode }) {
            selectedImageMode = access.defaultMode.takeIf { d -> access.modes.any { it.id == d } }
                ?: access.modes.firstOrNull()?.id ?: "speed"
        }
        if (selectedImageMode == "model_select" &&
            access.models.none { it.id == selectedImageModel && it.state == "ready" }
        ) {
            selectedImageModel = access.models.firstOrNull { it.id == "auto" }?.id
                ?: access.models.firstOrNull { it.state == "ready" }?.id ?: "auto"
        }
    }

    // Follow the stream only while the reader is at the bottom. Scrolling up
    // to re-read stops it; coming back resumes it.
    val atBottom by remember {
        derivedStateOf {
            AutoFollow.atBottom(
                lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index,
                lastIndex = messages.lastIndex,
            )
        }
    }
    var following by remember { mutableStateOf(true) }
    LaunchedEffect(atBottom, listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            following = atBottom
        } else if (atBottom) {
            following = true
        }
    }

    LaunchedEffect(messages.size, messages.lastOrNull()?.text?.length, messages.lastOrNull()?.status, reducedMotion) {
        if (AutoFollow.shouldScroll(following, messages.isNotEmpty())) {
            listState.scrollToChatItem(messages.lastIndex, reducedMotion)
        }
    }

    val bubbleActivity = when {
        busy -> OrbActivity.THINKING
        !snapshot.connection.isUsable -> OrbActivity.OFFLINE
        else -> OrbActivity.IDLE
    }
    LaunchedEffect(bubbleActivity) { vm.updateBubble(bubbleActivity) }

    val imeVisible = WindowInsets.isImeVisible
    LaunchedEffect(imeVisible, reducedMotion) {
        if (following && messages.isNotEmpty()) {
            if (reducedMotion) {
                listState.scrollToItem(messages.lastIndex)
            } else {
                listState.animateScrollToItem(messages.lastIndex)
                delay(220)
                if (following && messages.isNotEmpty()) listState.scrollToItem(messages.lastIndex)
            }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color(0xFFE8EEF8),
                    navigationIconContentColor = Color(0xFFE8EEF8),
                    actionIconContentColor = Color(0xFFE8EEF8),
                ),
                windowInsets = WindowInsets(0),
                navigationIcon = {
                    val strings = LocalAppStrings.current
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.back)
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        JarvisOrb(
                            size = 30.dp,
                            activity = when {
                                busy -> OrbActivity.THINKING
                                !snapshot.connection.isUsable -> OrbActivity.OFFLINE
                                else -> OrbActivity.IDLE
                            },
                        )
                        Spacer(Modifier.size(10.dp))
                        Column {
                            Text("JARVIS", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (imageGenerating) "creating image…" else if (chatStreaming) (chatProgress ?: "responding…") else "ready",
                                style = HudTextStyle,
                                color = if (busy) accents.orbGlow
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    if (availableAgentProjects.isNotEmpty() && conversationId != null) {
                        Box {
                            TextButton(
                                onClick = { projectMenuOpen = true },
                                enabled = !busy,
                            ) {
                                Text(
                                    if (agentProjectId == null) "Chat general"
                                    else "Proyecto · lectura",
                                    style = HudTextStyle,
                                )
                            }
                            DropdownMenu(
                                expanded = projectMenuOpen,
                                onDismissRequest = { projectMenuOpen = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Chat general") },
                                    onClick = {
                                        vm.selectGlobalChatAgentProject(null)
                                        projectMenuOpen = false
                                    },
                                )
                                availableAgentProjects.forEach { project ->
                                    DropdownMenuItem(
                                        text = { Text(project.title + " · solo lectura") },
                                        onClick = {
                                            vm.selectGlobalChatAgentProject(project.id.value)
                                            projectMenuOpen = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                    // Summon the floating brain from the conversation itself.
                    // If the overlay grant is missing, this sends the owner to
                    // the one system screen that can give it.
                    IconButton(onClick = {
                        if (!vm.setFloatingBubble(true)) vm.requestOverlayPermission()
                    }) {
                        Icon(Icons.Filled.OpenInNew, contentDescription = "Float JARVIS")
                    }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad),
        ) {
            if (agentProjectId != null) {
                Text(
                    "Agente de proyecto · consultas de texto y canon (solo lectura). " +
                        "Los adjuntos usan el chat general.",
                    modifier = Modifier.fillMaxWidth().padding(horizontal = screenInset, vertical = 6.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = accents.orbGlow,
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (messages.isEmpty()) {
                    EmptyChatState(
                        onSelectPrompt = { prompt ->
                            input = prompt
                            vm.clearImageGenerationError()
                        },
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = screenInset, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(messages, key = { "${it.role}_${it.clientRequestId}" }) { msg ->
                            MessageBubble(
                                msg = msg,
                                showRouteDetails = chatAccess?.isOwner == true,
                                onRetry = { vm.retry(msg.clientRequestId) },
                                attachmentState = { id -> snapshot.session.attachments[id] },
                                attachmentStore = vm.attachmentStore,
                                avatarEpoch = avatarEpoch,
                                onOpenGeneratedImage = { id ->
                                    imageSaveStatus = null
                                    previewGeneratedImage = id
                                },
                            )
                        }
                    }
                }

                // Only offered while the reader is away from the newest turn,
                // so following silently is never a dead end.
                androidx.compose.animation.AnimatedVisibility(
                    visible = !following && messages.isNotEmpty(),
                    enter = if (reducedMotion) fadeIn(tween(0)) else fadeIn() + scaleIn(initialScale = 0.9f),
                    exit = if (reducedMotion) fadeOut(tween(0)) else fadeOut() + scaleOut(targetScale = 0.9f),
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
                ) {
                    JumpToLatest(
                        streaming = chatStreaming,
                        onClick = { scope.launch { listState.scrollToChatItem(messages.lastIndex, reducedMotion) } },
                    )
                }
            }

            AnimatedVisibility(
                visible = pendingAttachments.isNotEmpty(),
                enter = if (reducedMotion) fadeIn(tween(0)) else fadeIn() + slideInVertically { it / 2 },
                exit = if (reducedMotion) fadeOut(tween(0)) else fadeOut(),
            ) {
                PendingAttachmentStrip(
                    attachments = pendingAttachments,
                    attachmentState = { id -> snapshot.session.attachments[id] },
                    onRemove = { vm.removePendingAttachment(it.attachmentId) },
                    attachmentStore = vm.attachmentStore,
                )
            }

            attachmentError?.let { message ->
                val errorText = when (message) {
                    JarvisViewModel.AttachmentStageError.IMAGE -> strings.imageAttachmentFailed
                    JarvisViewModel.AttachmentStageError.DOCUMENT -> strings.documentAttachmentFailed
                    JarvisViewModel.AttachmentStageError.PARTIAL -> strings.someAttachmentsFailed
                }
                Text(
                    errorText,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = HudTextStyle,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (imageGenerationState is JarvisViewModel.ImageGenerationState.Error) {
                Text(
                    (imageGenerationState as JarvisViewModel.ImageGenerationState.Error).message,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = HudTextStyle,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            imageGenerationDetails?.let { details ->
                val fallback = if (details.fallbackUsed) " · fallback " + details.attemptCount else ""
                Text(
                    "IMAGE · " + details.mode.uppercase() + " · " +
                        details.model.ifBlank { details.provider } + " · " +
                        details.durationMs + " ms" + fallback,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 3.dp),
                    style = HudTextStyle,
                    color = accents.orbGlow,
                )
            }

            ProfileChipRow(vm)

            if (chatStreaming) {
                ScanLine(active = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp))
            }

            Composer(
                input = input,
                onInput = { input = it; vm.clearImageGenerationError(); vm.clearAttachmentError() },
                connected = snapshot.connection.isUsable,
                streaming = chatStreaming,
                generatingImage = imageGenerating,
                canSend = (input.isNotBlank() || pendingAttachments.isNotEmpty()) && !imageGenerating,
                canPickPhoto = imageInputReady && pendingAttachments.isEmpty() && !imageGenerating,
                canPickDocument = documentInputReady && pendingAttachments.isEmpty() && !imageGenerating,
                canGenerateImage = imageGenerationReady && input.isNotBlank() &&
                    pendingAttachments.isEmpty() && !chatStreaming && !imageGenerating,
                onPickPhoto = {
                    photoPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                onPickDocument = {
                    documentPicker.launch(arrayOf(
                        "text/plain", "text/markdown", "application/pdf",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    ))
                },
                onGenerateImage = {
                    keyboardController?.hide()
                    focusManager.clearFocus()
                    showImageOptions = true
                },
                onStop = { liveRequest?.let { vm.cancel(it.clientRequestId) } },
                onSend = {
                    keyboardController?.hide()
                    focusManager.clearFocus()
                    following = true
                    val textToSend = input
                    input = ""
                    vm.sendWithAttachments(textToSend)
                    scope.launch {
                        listState.scrollToChatItem(messages.lastIndex.coerceAtLeast(0), reducedMotion)
                        delay(120)
                        if (messages.isNotEmpty()) {
                            listState.scrollToChatItem(messages.lastIndex, reducedMotion)
                        }
                        delay(180)
                        if (messages.isNotEmpty()) {
                            listState.scrollToItem(messages.lastIndex)
                        }
                    }
                },
                modifier = Modifier.imePadding(),
            )
        }
    }

    if (showImageOptions) {
        ImageGenerationChooserDialog(
            prompt = input,
            access = imageGenerationAccess,
            selectedMode = selectedImageMode,
            selectedModel = selectedImageModel,
            onModeSelected = { selectedImageMode = it },
            onModelSelected = { selectedImageModel = it },
            onDismiss = { showImageOptions = false },
            onGenerate = {
                vm.generateImage(
                    input,
                    selectedImageMode,
                    selectedImageModel.takeIf { selectedImageMode == "model_select" },
                )
                input = ""
                showImageOptions = false
            },
        )
    }
    previewGeneratedImage?.let { attachmentId ->
        GeneratedImagePreviewDialog(
            attachmentId = attachmentId,
            attachmentStore = vm.attachmentStore,
            saveStatus = imageSaveStatus,
            onDismiss = { previewGeneratedImage = null; imageSaveStatus = null },
            onEdit = {
                previewGeneratedImage = null
                imageSaveStatus = null
                imageStudioAttachmentId = attachmentId
            },
            onSave = {
                pendingSaveGeneratedImage = attachmentId
                imageSaveStatus = null
                saveGeneratedImage.launch("jarvis-generated-image.jpg")
            },
            onShare = {
                val shared = runCatching {
                    val file = vm.attachmentStore.resolve(attachmentId)
                        ?: error("Generated image is no longer available")
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file,
                    )
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/jpeg"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share image"))
                }.isSuccess
                if (!shared) imageSaveStatus = "Could not share image"
            },
        )
    }
}

private suspend fun LazyListState.scrollToChatItem(index: Int, reducedMotion: Boolean) {
    if (index < 0) return
    if (reducedMotion) scrollToItem(index) else animateScrollToItem(index)
}

@Composable
private fun ImageGenerationChooserDialog(
    prompt: String,
    access: com.jarvis.android.transport.live.JarvisAppSession.ImageGenerationAccess?,
    selectedMode: String,
    selectedModel: String,
    onModeSelected: (String) -> Unit,
    onModelSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    onGenerate: () -> Unit,
) {
    val accents = LocalJarvisAccents.current
    val fallbackModes = listOf(
        com.jarvis.android.transport.live.JarvisAppSession.ImageGenerationMode("speed", "Speed"),
        com.jarvis.android.transport.live.JarvisAppSession.ImageGenerationMode("quality", "Quality"),
    )
    val modes = access?.modes?.takeIf { it.isNotEmpty() } ?: fallbackModes
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Generate image") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(prompt, style = MaterialTheme.typography.bodyMedium, maxLines = 3,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Mode", style = HudTextStyle, color = accents.orbGlow)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(modes, key = { it.id }) { mode ->
                        val active = selectedMode == mode.id
                        Surface(
                            onClick = { onModeSelected(mode.id) },
                            shape = RoundedCornerShape(50),
                            color = if (active) accents.orbGlow.copy(alpha = 0.22f)
                            else MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, if (active) accents.orbGlow else Color(0xFF30435F)),
                        ) {
                            Text(mode.label, Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                if (selectedMode == "model_select") {
                    Text("Model", style = HudTextStyle, color = accents.orbGlow)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(access?.models.orEmpty(), key = { it.id }) { model ->
                            val ready = model.state == "ready"
                            val active = selectedModel == model.id
                            Surface(
                                onClick = { if (ready) onModelSelected(model.id) },
                                shape = RoundedCornerShape(50),
                                color = if (active) accents.orbGlow.copy(alpha = 0.22f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (ready) 1f else 0.5f),
                                border = BorderStroke(1.dp, if (active) accents.orbGlow else Color(0xFF30435F)),
                            ) {
                                Text(model.label, Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (ready) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                            }
                        }
                    }
                }
                val policy = when {
                    selectedMode == "speed" -> "Fastest route. Automatic fallback is allowed if the primary route fails."
                    selectedMode == "quality" -> "Quality-first route. JARVIS can fall back only if the preferred provider fails."
                    selectedModel == "auto" -> "Auto lets JARVIS choose the first healthy image route."
                    else -> "Exact model selected. JARVIS will not silently switch to another model."
                }
                Text(policy, style = HudTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(
                onClick = onGenerate,
                enabled = prompt.isNotBlank() &&
                    (selectedMode != "model_select" ||
                        access?.models?.any { it.id == selectedModel && it.state == "ready" } == true),
            ) { Text("Generate") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun GeneratedImagePreviewDialog(
    attachmentId: String,
    attachmentStore: com.jarvis.android.data.media.AttachmentStore,
    saveStatus: String?,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
) {
    val bitmap by rememberAttachmentThumb(attachmentId, attachmentStore, maxSize = 4096)
    var zoom by remember(attachmentId) { mutableStateOf(1f) }
    var panX by remember(attachmentId) { mutableStateOf(0f) }
    var panY by remember(attachmentId) { mutableStateOf(0f) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF09111F),
            border = BorderStroke(1.dp, LocalJarvisAccents.current.orbGlow.copy(alpha = 0.55f)),
        ) {
            Column(Modifier.fillMaxSize().padding(14.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Generated image", style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Close") }
                }
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF050A12)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap!!.asImageBitmap(),
                            contentDescription = "Generated image",
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(attachmentId) {
                                    detectTransformGestures { _, pan, zoomChange, _ ->
                                        val nextZoom = (zoom * zoomChange).coerceIn(1f, 5f)
                                        zoom = nextZoom
                                        if (nextZoom <= 1.01f) {
                                            panX = 0f
                                            panY = 0f
                                        } else {
                                            panX += pan.x
                                            panY += pan.y
                                        }
                                    }
                                }
                                .pointerInput(attachmentId, zoom) {
                                    detectTapGestures(
                                        onDoubleTap = {
                                            if (zoom > 1.01f) {
                                                zoom = 1f
                                                panX = 0f
                                                panY = 0f
                                            } else {
                                                zoom = 2f
                                            }
                                        },
                                    )
                                }
                                .graphicsLayer {
                                    scaleX = zoom
                                    scaleY = zoom
                                    translationX = panX
                                    translationY = panY
                                },
                            contentScale = ContentScale.Fit,
                        )
                    } else {
                        CircularProgressIndicator()
                    }
                }
                if (bitmap != null) {
                    Text(
                        "Pinch to zoom · drag to pan · double tap to reset",
                        Modifier.fillMaxWidth().padding(top = 6.dp),
                        textAlign = TextAlign.Center,
                        style = HudTextStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                saveStatus?.let {
                    Text(it, Modifier.fillMaxWidth().padding(top = 8.dp), textAlign = TextAlign.Center,
                        style = HudTextStyle, color = LocalJarvisAccents.current.orbGlow)
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onEdit) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Edit in Image Studio")
                    }
                    TextButton(onClick = onShare) { Text("Share") }
                    TextButton(onClick = onSave) { Text("Save image") }
                }
            }
        }
    }
}

/**
 * PCB-LIVE-4: owner chat-profile chips. The catalog is **server-driven** —
 * entries come from PC-A's `/api/app/status` (`chat.models[]`) and the row is
 * hidden unless that same response grants `owner_model_selection`. The client
 * never hardcodes models.
 */
@Composable
private fun ProfileChipRow(vm: JarvisViewModel) {
    val access = vm.chatAccess.collectAsStateWithLifecycle().value ?: return
    val selected by vm.chatProfile.collectAsStateWithLifecycle()
    val accents = LocalJarvisAccents.current
    val entries = access.entries
    if (!access.ownerModelSelection || entries.size < 2) return
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(entries, key = { it.profile }) { entry ->
            val active = entry.profile == selected
            Surface(
                onClick = { vm.setChatProfile(entry.profile) },
                shape = RoundedCornerShape(50),
                color = if (active) accents.orbGlow.copy(alpha = 0.22f)
                else MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                border = BorderStroke(
                    1.dp,
                    if (active) accents.orbGlow.copy(alpha = 0.55f)
                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                ),
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(
                    entry.label.ifBlank { entry.profile },
                    style = HudTextStyle,
                    color = if (active) accents.orbGlow else Color(0xFFB7C7DC),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Composer(
    input: String,
    onInput: (String) -> Unit,
    connected: Boolean,
    streaming: Boolean,
    generatingImage: Boolean,
    canSend: Boolean,
    canPickPhoto: Boolean,
    canPickDocument: Boolean,
    canGenerateImage: Boolean,
    onPickPhoto: () -> Unit,
    onPickDocument: () -> Unit,
    onGenerateImage: () -> Unit,
    onStop: () -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accents = LocalJarvisAccents.current
    val strings = LocalAppStrings.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    val handleSend = {
        if (canSend) {
            keyboardController?.hide()
            focusManager.clearFocus()
            onSend()
        }
    }

    var attachmentMenuOpen by rememberSaveable { mutableStateOf(false) }
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box {
                IconButton(
                    onClick = { attachmentMenuOpen = true },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = strings.attachmentOptions,
                        tint = if (canPickPhoto || canPickDocument || canGenerateImage) accents.orbGlow
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(
                    expanded = attachmentMenuOpen,
                    onDismissRequest = { attachmentMenuOpen = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(strings.attachPhoto) },
                        leadingIcon = { Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null) },
                        enabled = canPickPhoto,
                        onClick = { attachmentMenuOpen = false; onPickPhoto() },
                    )
                    DropdownMenuItem(
                        text = { Text(strings.attachDocument) },
                        leadingIcon = { Icon(Icons.Filled.Description, contentDescription = null) },
                        enabled = canPickDocument,
                        onClick = { attachmentMenuOpen = false; onPickDocument() },
                    )
                    DropdownMenuItem(
                        text = { Text(strings.generateImage) },
                        leadingIcon = {
                            if (generatingImage) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else Icon(Icons.Filled.Image, contentDescription = null)
                        },
                        enabled = canGenerateImage,
                        onClick = { attachmentMenuOpen = false; onGenerateImage() },
                    )
                }
            }

            OutlinedTextField(
                value = input,
                onValueChange = onInput,
                modifier = Modifier.weight(1f).widthIn(min = 0.dp),
                placeholder = {
                    Text(
                        if (connected) strings.messagePlaceholder else strings.messagePlaceholderOffline,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Send,
                    capitalization = KeyboardCapitalization.Sentences,
                ),
                keyboardActions = KeyboardActions(onSend = { handleSend() }),
                maxLines = 4,
                shape = RoundedCornerShape(JarvisUiTokens.controlShape),
                colors = jarvisTextFieldColors(),
            )

            if (streaming) {
                FilledIconButton(
                    onClick = onStop,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                    modifier = Modifier.size(48.dp),
                ) { Icon(Icons.Filled.Stop, contentDescription = strings.stop) }
            } else {
                HolographicSendButton(
                    enabled = canSend,
                    contentDescription = strings.send,
                    accent = accents.orbGlow,
                    onClick = handleSend,
                )
            }
        }
    }
}

@Preview(name = "JARVIS chat composer", showBackground = true, backgroundColor = 0xFF060B16)
@Composable
private fun ChatComposerDesignPreview() {
    JarvisTheme {
        CompositionLocalProvider(
            LocalAppStrings provides EnAppStrings,
            LocalReducedMotion provides true,
        ) {
            Surface(Modifier.padding(12.dp)) {
                Composer(
                    input = "Help me plan my next chapter",
                    onInput = {},
                    connected = true,
                    streaming = false,
                    generatingImage = false,
                    canSend = true,
                    canPickPhoto = true,
                    canPickDocument = true,
                    canGenerateImage = true,
                    onPickPhoto = {},
                    onPickDocument = {},
                    onGenerateImage = {},
                    onStop = {},
                    onSend = {},
                )
            }
        }
    }
}

@Composable
private fun EmptyChatState(
    onSelectPrompt: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accents = LocalJarvisAccents.current
    val strings = LocalAppStrings.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        JarvisBrain(size = 96.dp, activity = OrbActivity.IDLE)
        Spacer(Modifier.height(18.dp))
        Text(strings.readyWhenYouAre, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text(
            strings.startConversationSubtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            strings.savedPreferencesHint,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(22.dp))

        val prompts = listOf(strings.quickPromptDraft, strings.quickPromptBrainstorm, strings.quickPromptStatus)

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            prompts.forEach { prompt ->
                Surface(
                    onClick = { onSelectPrompt(prompt) },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(prompt, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}

@Composable
private fun CodeBlockView(code: String, language: String?) {
    val accents = LocalJarvisAccents.current
    val clipboardManager = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    Surface(
        color = Color(0xFF09111E),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, accents.orbGlow.copy(alpha = 0.22f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0E1A2C))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Terminal,
                        contentDescription = null,
                        tint = accents.orbGlow,
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        language?.uppercase() ?: "CODE",
                        style = HudTextStyle,
                        color = accents.orbGlow,
                    )
                }
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            clipboardManager.setText(AnnotatedString(code))
                            copied = true
                            scope.launch {
                                delay(2000)
                                copied = false
                            }
                        }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        if (copied) Icons.Filled.Check else Icons.Filled.ContentCopy,
                        contentDescription = "Copy code",
                        tint = if (copied) accents.online else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(13.dp),
                    )
                    Text(
                        if (copied) "COPIED" else "COPY",
                        style = HudTextStyle,
                        color = if (copied) accents.online else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState)
                    .padding(10.dp),
            ) {
                Text(
                    text = code,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 20.sp,
                    ),
                    color = Color(0xFFD4E3F8),
                )
            }
        }
    }
}

/**
 * A finished reply, broken into the blocks [markdownBlocks] found. Headings,
 * bullets and fenced code get their own line; everything else keeps inline
 * styling. Streaming replies never reach here — their markers are incomplete.
 */
@Composable
private fun MarkdownBody(text: String) {
    val accents = LocalJarvisAccents.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (block in markdownBlocks(text)) {
            when (block) {
                is MarkdownBlock.Heading -> Text(
                    block.text,
                    style = if (block.level == 1) MaterialTheme.typography.titleMedium
                    else MaterialTheme.typography.titleSmall,
                    color = accents.orbGlow,
                )
                is MarkdownBlock.Bullet -> Text(
                    "•  " + renderInlineMarkdown(block.text, accents.orbGlow),
                    style = MaterialTheme.typography.bodyMedium,
                )
                is MarkdownBlock.Numbered -> Text(
                    "${block.number}. " + renderInlineMarkdown(block.text, accents.orbGlow),
                    style = MaterialTheme.typography.bodyMedium,
                )
                is MarkdownBlock.Blockquote -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(20.dp)
                                .background(accents.orbGlow.copy(alpha = 0.8f), RoundedCornerShape(2.dp))
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            renderInlineMarkdown(block.text, accents.orbGlow),
                            style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                is MarkdownBlock.Code -> CodeBlockView(code = block.text, language = block.language)
                is MarkdownBlock.Paragraph -> Text(
                    renderInlineMarkdown(block.text, accents.orbGlow),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun JumpToLatest(streaming: Boolean, onClick: () -> Unit) {
    val accents = LocalJarvisAccents.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = Color(0xEE0E182A),
        border = BorderStroke(1.dp, accents.orbGlow.copy(alpha = 0.5f)),
        shadowElevation = 6.dp,
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (streaming) {
                TypingDots()
                Spacer(Modifier.size(8.dp))
            }
            Text(
                if (streaming) "Jarvis is replying…" else "Jump to latest",
                style = MaterialTheme.typography.labelLarge,
                color = accents.orbGlow,
            )
        }
    }
}

private fun ownerRouteLabel(msg: ChatMessage): String? {
    val model = msg.routeModel.trim()
    val provider = when (msg.routeProvider.trim().lowercase()) {
        "openai-codex" -> "Codex"
        "xai-oauth" -> "xAI OAuth"
        "nous" -> "Nous"
        "freellmapi" -> "FreeLLMAPI"
        "ollama" -> "Ollama"
        else -> msg.routeProvider.trim()
    }
    val route = when (msg.routeDestination.trim().lowercase()) {
        "hermes_cloud" -> "Hermes"
        "pc_worker" -> "Local PC"
        "cloud_vision" -> "FreeLLMAPI"
        "cloud" -> "Cloud"
        else -> when (msg.routeKind.trim().lowercase()) {
            "hermes_cloud" -> "Hermes"
            "local" -> "Local PC"
            "cloud_vision" -> "FreeLLMAPI"
            "cloud" -> "Cloud"
            else -> msg.routeDestination.trim().ifBlank { msg.routeKind.trim() }
        }
    }
    val parts = listOf(model, route, provider)
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase() }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

@Composable
private fun MessageBubble(
    msg: ChatMessage,
    showRouteDetails: Boolean,
    onRetry: () -> Unit,
    attachmentState: (String) -> com.jarvis.android.data.state.AttachmentUiState?,
    attachmentStore: com.jarvis.android.data.media.AttachmentStore,
    avatarEpoch: Int,
    onOpenGeneratedImage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accents = LocalJarvisAccents.current
    val strings = LocalAppStrings.current
    val isUser = msg.role == "user"
    val isStreaming = msg.status == RequestStatus.Streaming
    val awaiting = !isUser && msg.text.isEmpty() &&
        (msg.status == RequestStatus.Pending || msg.status == RequestStatus.Accepted)
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var messageCopied by remember { mutableStateOf(false) }

    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (!isUser) {
            Box(Modifier.padding(end = 8.dp, bottom = 2.dp)) {
                JarvisOrb(size = 24.dp, activity = if (isStreaming || awaiting) OrbActivity.THINKING else OrbActivity.IDLE)
            }
        }
        Column(horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
            Box(Modifier.widthIn(max = 360.dp)) {
                val bubbleShape = RoundedCornerShape(
                    topStart = 18.dp,
                    topEnd = if (isUser) 6.dp else 18.dp,
                    bottomStart = if (isUser) 18.dp else 6.dp,
                    bottomEnd = 18.dp,
                )
                Surface(
                    color = if (isUser) Color.Transparent else accents.assistantBubble,
                    contentColor = if (isUser) Color.White else Color(0xFFE8EEF8),
                    shape = bubbleShape,
                    border = if (isUser) {
                        BorderStroke(1.dp, accents.orbGlow.copy(alpha = 0.28f))
                    } else {
                        BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                    },
                    modifier = if (isUser) {
                        Modifier.background(
                            accents.userBubble,
                            bubbleShape,
                        )
                    } else Modifier,
                ) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
                        if (msg.attachmentIds.isNotEmpty()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                msg.attachmentIds.forEach { id ->
                                    AttachmentChip(
                                        attachmentId = id,
                                        state = attachmentState(id),
                                        attachmentStore = attachmentStore,
                                        generated = !isUser && msg.clientRequestId.startsWith("img_"),
                                        onOpen = if (!isUser && msg.clientRequestId.startsWith("img_")) {
                                            { onOpenGeneratedImage(id) }
                                        } else null,
                                    )
                                }
                            }
                            if (msg.text.isNotBlank()) Spacer(Modifier.height(8.dp))
                        }
                        when {
                            awaiting -> TypingDots()
                            msg.text.isNotBlank() && isStreaming -> Text(
                                streamingText(msg.text, streaming = true),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            msg.text.isNotBlank() -> MarkdownBody(msg.text)
                        }
                    }
                }
            }

            if (showRouteDetails && !isUser && !isStreaming && !awaiting && msg.status !is RequestStatus.Failed) {
                ownerRouteLabel(msg)?.let { label ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        label,
                    style = MaterialTheme.typography.labelSmall,
                        color = accents.orbGlow.copy(alpha = 0.78f),
                        modifier = Modifier.padding(horizontal = 6.dp),
                    )
                }
            }

            // Utility action row for completed assistant messages
            if (!isUser && !isStreaming && !awaiting && msg.text.isNotBlank() && msg.status !is RequestStatus.Failed) {
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    TextButton(
                        modifier = Modifier.heightIn(min = 48.dp),
                        onClick = {
                                clipboardManager.setText(AnnotatedString(msg.text))
                                messageCopied = true
                                scope.launch {
                                    delay(2000)
                                    messageCopied = false
                                }
                        },
                    ) {
                        Icon(
                            if (messageCopied) Icons.Filled.Check else Icons.Filled.ContentCopy,
                            contentDescription = null,
                            tint = if (messageCopied) accents.online else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(if (messageCopied) strings.messageCopied else strings.copyMessage)
                    }

                    if (msg.createdAtMs > 0L) {
                        Text(
                            java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(msg.createdAtMs)),
                            style = HudTextStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                    }
                }
            }

            // User timestamp
            if (isUser && msg.createdAtMs > 0L) {
                Spacer(Modifier.height(2.dp))
                Text(
                    java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(msg.createdAtMs)),
                    style = HudTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
            }

            val footer: (@Composable () -> Unit)? = when {
                isUser -> null
                msg.status == RequestStatus.Cancelling -> ({
                    Text(strings.messageStopping, style = MaterialTheme.typography.bodySmall, color = accents.degraded)
                })
                msg.status == RequestStatus.Cancelled -> ({
                    Text(strings.messageCancelled, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                })
                msg.status is RequestStatus.Failed -> ({
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(strings.messageFailed, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        if ((msg.status as RequestStatus.Failed).retryable) {
                            TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
                                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                                Text(strings.retry)
                            }
                        }
                    }
                })
                else -> null
            }
            if (footer != null) {
                Spacer(Modifier.height(2.dp))
                Box(Modifier.padding(horizontal = 6.dp)) { footer() }
            }
        }
        if (isUser) {
            Box(Modifier.padding(start = 8.dp, bottom = 2.dp)) {
                OwnerAvatar(store = attachmentStore, epoch = avatarEpoch, size = 28.dp)
            }
        }
    }
}

@Composable
private fun AttachmentChip(
    attachmentId: String,
    state: com.jarvis.android.data.state.AttachmentUiState?,
    attachmentStore: com.jarvis.android.data.media.AttachmentStore,
    generated: Boolean = false,
    onOpen: (() -> Unit)? = null,
) {
    val thumb by rememberAttachmentThumb(
        attachmentId,
        attachmentStore,
        maxSize = if (generated) 1024 else 256,
    )
    val statusText = when {
        generated && state == null -> "GENERATED"
        state == null -> "STAGED"
        state.uploading -> "UPLOADING"
        state.ready -> "ATTACHED"
        else -> "FAILED"
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(if (generated) 220.dp else 92.dp)
                .clip(RoundedCornerShape(if (generated) 20.dp else 14.dp))
                .background(MaterialTheme.colorScheme.surface)
                .then(if (generated && onOpen != null) Modifier.clickable { onOpen() } else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            val bitmap = thumb
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Attachment",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    if (attachmentId.startsWith("doc_")) Icons.Filled.Description else Icons.Filled.Image,
                    contentDescription = if (attachmentId.startsWith("doc_")) "Document attachment" else null,
                )
            }
            if (state?.uploading == true) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(
            statusText,
            style = HudTextStyle,
            color = if (state == null || state.ready || state.uploading)
                MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.error,
            modifier = if (generated) Modifier.padding(top = 2.dp) else Modifier,
        )
    }
}

@Composable
private fun PendingAttachmentStrip(
    attachments: List<com.jarvis.android.data.media.StagedAttachment>,
    attachmentState: (String) -> com.jarvis.android.data.state.AttachmentUiState?,
    onRemove: (com.jarvis.android.data.media.StagedAttachment) -> Unit,
    attachmentStore: com.jarvis.android.data.media.AttachmentStore,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(attachments, key = { it.attachmentId }) { a ->
            Box(Modifier.size(76.dp)) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    val bmp by rememberAttachmentThumb(a.attachmentId, attachmentStore)
                    val bitmap = bmp
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            if (attachmentState(a.attachmentId)?.uploading == true) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(
                                    if (a.mimeType.startsWith("image/")) Icons.Filled.Image else Icons.Filled.Description,
                                    contentDescription = if (a.mimeType.startsWith("image/")) null else a.filename,
                                )
                            }
                        }
                    }
                }
                FilledIconButton(
                    onClick = { onRemove(a) },
                    modifier = Modifier.align(Alignment.TopEnd).size(24.dp),
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Remove", modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}
