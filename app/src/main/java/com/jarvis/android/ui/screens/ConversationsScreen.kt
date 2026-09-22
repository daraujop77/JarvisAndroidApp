package com.jarvis.android.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.jarvis.android.data.repo.ChatMessage
import com.jarvis.android.data.state.RequestStatus
import com.jarvis.android.ui.JarvisViewModel
import com.jarvis.android.ui.chat.AutoFollow
import com.jarvis.android.ui.components.JarvisBrain
import com.jarvis.android.ui.components.JarvisOrb
import com.jarvis.android.ui.components.OrbActivity
import com.jarvis.android.ui.components.TypingDots
import com.jarvis.android.ui.components.streamingText
import com.jarvis.android.ui.shared.OwnerAvatar
import com.jarvis.android.ui.shared.rememberAttachmentThumb
import com.jarvis.android.ui.theme.HudTextStyle
import com.jarvis.android.ui.theme.LocalJarvisAccents
import com.jarvis.android.ui.theme.jarvisTextFieldColors

@Composable
fun ConversationsScreen(vm: JarvisViewModel) {
    var showList by rememberSaveable { mutableStateOf(true) }
    val conversationId by vm.conversationId.collectAsStateWithLifecycle()

    if (showList || conversationId == null) {
        ConversationListView(
            vm = vm,
            onOpen = { vm.openConversation(it); showList = false },
            onNew = { showList = false; vm.startNewConversation() },
        )
    } else {
        ChatScreen(vm, onBack = { showList = true })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationListView(
    vm: JarvisViewModel,
    onOpen: (String) -> Unit,
    onNew: () -> Unit,
) {
    val items by vm.conversationList.collectAsStateWithLifecycle()
    val accents = LocalJarvisAccents.current
    val avatarEpoch by vm.avatarEpoch.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text("Conversations", style = MaterialTheme.typography.titleLarge) },
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
                text = { Text("New chat") },
            )
        },
    ) { pad ->
        if (items.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(pad).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                JarvisBrain(size = 130.dp, activity = OrbActivity.IDLE)
                Spacer(Modifier.height(24.dp))
                Text("Ready when you are", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Start a conversation and I'll stream the reply here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(pad),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(items, key = { _, c -> c.conversationId }) { index, c ->
                    Surface(
                        onClick = { onOpen(c.conversationId) },
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                        border = BorderStroke(1.dp, accents.orbGlow.copy(alpha = 0.16f)),
                        modifier = Modifier.fillMaxWidth().animateItem(),
                    ) {
                        Row(
                            Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(accents.orbGlow.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                JarvisOrb(size = 30.dp, activity = OrbActivity.IDLE)
                            }
                            Spacer(Modifier.size(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(c.title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    java.text.DateFormat
                                        .getTimeInstance(java.text.DateFormat.SHORT)
                                        .format(java.util.Date(c.updatedAtMs)),
                                    style = HudTextStyle,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ChatScreen(vm: JarvisViewModel, onBack: () -> Unit) {
    val messages by vm.messages.collectAsStateWithLifecycle()
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val pendingAttachments by vm.pendingAttachments.collectAsStateWithLifecycle()
    val avatarEpoch by vm.avatarEpoch.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val accents = LocalJarvisAccents.current

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let { vm.stageAttachment(it) } }

    val liveRequest = snapshot.session.requests.values.firstOrNull { !it.status.isTerminal }
    val streaming = liveRequest != null

    LaunchedEffect(Unit) { vm.refreshChatAccess() }

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
    LaunchedEffect(atBottom) { following = AutoFollow.next(following, atBottom, justSent = false) }

    LaunchedEffect(messages.size, messages.lastOrNull()?.text?.length) {
        if (AutoFollow.shouldScroll(following, messages.isNotEmpty())) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    val bubbleActivity = when {
        streaming -> OrbActivity.THINKING
        !snapshot.connection.isUsable -> OrbActivity.OFFLINE
        else -> OrbActivity.IDLE
    }
    LaunchedEffect(bubbleActivity) { vm.updateBubble(bubbleActivity) }

    val imeVisible = WindowInsets.isImeVisible
    LaunchedEffect(imeVisible) {
        if (imeVisible && AutoFollow.shouldScroll(following, messages.isNotEmpty())) {
            listState.animateScrollToItem(messages.lastIndex)
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
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        JarvisOrb(
                            size = 30.dp,
                            activity = when {
                                streaming -> OrbActivity.THINKING
                                !snapshot.connection.isUsable -> OrbActivity.OFFLINE
                                else -> OrbActivity.IDLE
                            },
                        )
                        Spacer(Modifier.size(10.dp))
                        Column {
                            Text("JARVIS", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (streaming) "responding…" else "ready",
                                style = HudTextStyle,
                                color = if (streaming) accents.orbGlow
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
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
                .padding(pad)
                // Edge-to-edge + zeroed content insets mean adjustResize does
                // nothing; this is what actually lifts the composer above the IME.
                .imePadding(),
        ) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(messages, key = { "${it.role}_${it.clientRequestId}" }) { msg ->
                        MessageBubble(
                            msg = msg,
                            onRetry = { vm.retry(msg.clientRequestId) },
                            attachmentState = { id -> snapshot.session.attachments[id] },
                            attachmentStore = vm.attachmentStore,
                            avatarEpoch = avatarEpoch,
                            modifier = Modifier.animateItem(),
                        )
                    }
                }

                // Only offered while the reader is away from the newest turn,
                // so following silently is never a dead end.
                androidx.compose.animation.AnimatedVisibility(
                    visible = !following && messages.isNotEmpty(),
                    enter = fadeIn() + scaleIn(initialScale = 0.9f),
                    exit = fadeOut() + scaleOut(targetScale = 0.9f),
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
                ) {
                    JumpToLatest(
                        streaming = streaming,
                        onClick = { scope.launch { listState.animateScrollToItem(messages.lastIndex) } },
                    )
                }
            }

            AnimatedVisibility(
                visible = pendingAttachments.isNotEmpty(),
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut(),
            ) {
                PendingAttachmentStrip(
                    attachments = pendingAttachments,
                    attachmentState = { id -> snapshot.session.attachments[id] },
                    onRemove = { vm.removePendingAttachment(it.attachmentId) },
                    attachmentStore = vm.attachmentStore,
                )
            }

            ProfileChipRow(vm)

            Composer(
                input = input,
                onInput = { input = it },
                connected = snapshot.connection.isUsable,
                streaming = streaming,
                canSend = input.isNotBlank() || pendingAttachments.isNotEmpty(),
                onPickPhoto = {
                    photoPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                onStop = { liveRequest?.let { vm.cancel(it.clientRequestId) } },
                onSend = {
                    // Sending is an explicit request to see the newest turn,
                    // even if the reader had scrolled up.
                    following = AutoFollow.next(following, atBottom, justSent = true)
                    vm.sendWithAttachments(input)
                    input = ""
                },
            )
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
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (active) accents.orbGlow.copy(alpha = 0.8f)
                    else Color(0xFF2A3A52),
                ),
            ) {
                Text(
                    entry.label.ifBlank { entry.profile },
                    style = HudTextStyle,
                    color = if (active) accents.orbGlow else Color(0xFFB7C7DC),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
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
    canSend: Boolean,
    onPickPhoto: () -> Unit,
    onStop: () -> Unit,
    onSend: () -> Unit,
) {
    val accents = LocalJarvisAccents.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        IconButton(onClick = onPickPhoto) {
            Icon(
                Icons.Filled.AddPhotoAlternate,
                contentDescription = "Attach photo",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedTextField(
            value = input,
            onValueChange = onInput,
            modifier = Modifier.weight(1f),
            placeholder = {
                Text(
                    if (connected) "Message JARVIS…" else "Offline — will retry",
                    color = Color(0xFFB7C7DC),
                )
            },
            maxLines = 4,
            shape = RoundedCornerShape(26.dp),
            colors = jarvisTextFieldColors(),
        )

        // Stop replaces Send while a reply is streaming, so the primary action
        // is always the one the user actually needs.
        androidx.compose.animation.AnimatedContent(
            targetState = streaming,
            transitionSpec = { (fadeIn() + scaleIn()).togetherWith(fadeOut() + scaleOut()) },
            label = "sendStop",
        ) { isStreaming ->
            if (isStreaming) {
                FilledIconButton(
                    onClick = onStop,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Icon(Icons.Filled.Stop, contentDescription = "Stop") }
            } else {
                val scale by animateFloatAsState(if (canSend) 1f else 0.85f, label = "sendScale")
                FilledIconButton(
                    onClick = onSend,
                    enabled = canSend,
                    modifier = Modifier.size((44 * scale).dp),
                ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send") }
            }
        }
    }
}

/**
 * Shown only when auto-follow is off. While a reply is streaming it says so,
 * because the reason to go back down is that something is still arriving.
 */
@Composable
private fun JumpToLatest(streaming: Boolean, onClick: () -> Unit) {
    val accents = LocalJarvisAccents.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, accents.orbGlow.copy(alpha = 0.45f)),
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
                if (streaming) "Jarvis is replying" else "Jump to latest",
                style = MaterialTheme.typography.labelLarge,
                color = accents.orbGlow,
            )
        }
    }
}

@Composable
private fun MessageBubble(
    msg: ChatMessage,
    onRetry: () -> Unit,
    attachmentState: (String) -> com.jarvis.android.data.state.AttachmentUiState?,
    attachmentStore: com.jarvis.android.data.media.AttachmentStore,
    avatarEpoch: Int,
    modifier: Modifier = Modifier,
) {
    val accents = LocalJarvisAccents.current
    val isUser = msg.role == "user"
    val isStreaming = msg.status == RequestStatus.Streaming
    val awaiting = !isUser && msg.text.isEmpty() &&
        (msg.status == RequestStatus.Pending || msg.status == RequestStatus.Accepted)

    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (!isUser) {
            Box(Modifier.padding(end = 8.dp, bottom = 2.dp)) {
                JarvisOrb(size = 28.dp, activity = if (isStreaming || awaiting) OrbActivity.THINKING else OrbActivity.IDLE)
            }
        }
        Column(horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
            Box(Modifier.widthIn(max = 320.dp)) {
                val bubbleShape = RoundedCornerShape(
                    topStart = 22.dp,
                    topEnd = if (isUser) 8.dp else 22.dp,
                    bottomStart = if (isUser) 22.dp else 8.dp,
                    bottomEnd = 22.dp,
                )
                Surface(
                    color = if (isUser) Color.Transparent else accents.assistantBubble,
                    contentColor = if (isUser) Color.White else Color(0xFFE8EEF8),
                    shape = bubbleShape,
                    border = if (isUser) null else BorderStroke(1.dp, accents.orbGlow.copy(alpha = 0.14f)),
                    modifier = if (isUser) {
                        Modifier.background(
                            accents.userBubble,
                            RoundedCornerShape(
                                topStart = 20.dp, topEnd = 6.dp,
                                bottomStart = 20.dp, bottomEnd = 20.dp,
                            ),
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
                                    )
                                }
                            }
                            if (msg.text.isNotBlank()) Spacer(Modifier.height(8.dp))
                        }
                        when {
                            awaiting -> TypingDots()
                            msg.text.isNotBlank() -> Text(
                                streamingText(msg.text, isStreaming),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }

            val footer: (@Composable () -> Unit)? = when {
                isUser -> null
                msg.status == RequestStatus.Cancelling -> ({
                    Text("stopping…", style = HudTextStyle, color = accents.degraded)
                })
                msg.status == RequestStatus.Cancelled -> ({
                    Text("CANCELLED", style = HudTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                })
                msg.status is RequestStatus.Failed -> ({
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("FAILED", style = HudTextStyle, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = onRetry) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                            Text(" Retry")
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
) {
    val thumb by rememberAttachmentThumb(attachmentId, attachmentStore)
    val statusText = when {
        state == null -> "STAGED"
        state.uploading -> "UPLOADING"
        state.ready -> "ATTACHED"
        else -> "FAILED"
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(92.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface),
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
                Icon(Icons.Filled.Image, contentDescription = null)
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
                                Icon(Icons.Filled.Image, contentDescription = null)
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
