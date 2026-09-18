package com.jarvis.android.ui.screens

import android.content.Intent
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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jarvis.android.data.local.ConversationListItem
import com.jarvis.android.data.media.AttachmentChipModel
import com.jarvis.android.data.media.AttachmentPhase
import com.jarvis.android.data.media.AttachmentUx
import com.jarvis.android.data.media.StagedAttachmentRef
import com.jarvis.android.data.repo.ChatMessage
import com.jarvis.android.data.state.ConnectionState
import com.jarvis.android.data.state.RequestStatus
import com.jarvis.android.ui.recovery.RecoveryUx
import com.jarvis.android.ui.JarvisViewModel
import com.jarvis.android.ui.components.JarvisOrb
import com.jarvis.android.ui.home.JarvisVisualState
import com.jarvis.android.ui.components.TypingDots
import com.jarvis.android.ui.components.streamingText
import com.jarvis.android.ui.shared.OwnerAvatar
import com.jarvis.android.ui.shared.rememberAttachmentThumb
import com.jarvis.android.ui.theme.HudTextStyle
import com.jarvis.android.ui.theme.LocalJarvisAccents
import com.jarvis.android.ui.theme.LocalReducedMotion
import com.jarvis.android.ui.theme.jarvisTextFieldColors
import com.jarvis.android.voice.VoicePhase
import kotlinx.coroutines.launch

@Composable
fun ConversationsScreen(vm: JarvisViewModel) {
    var showList by rememberSaveable { mutableStateOf(true) }
    val conversationId by vm.conversationId.collectAsStateWithLifecycle()
    val threadOpenNonce by vm.threadOpenNonce.collectAsStateWithLifecycle()
    val enterChat by vm.enterChatOnConversations.collectAsStateWithLifecycle()

    LaunchedEffect(threadOpenNonce, enterChat) {
        if ((threadOpenNonce > 0 || enterChat) && conversationId != null) {
            showList = false
            if (enterChat) vm.consumeEnterChatOnConversations()
        }
    }

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
    val items by vm.visibleConversations.collectAsStateWithLifecycle()
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val showHidden by vm.showHidden.collectAsStateWithLifecycle()
    val query by vm.searchQuery.collectAsStateWithLifecycle()
    val reduced = LocalReducedMotion.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var selecting by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var renameTarget by remember { mutableStateOf<ConversationListItem?>(null) }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Text(if (showHidden) "Hidden on this device" else "Chats")
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color(0xFFE8EEF8),
                    navigationIconContentColor = Color(0xFFE8EEF8),
                    actionIconContentColor = Color(0xFFE8EEF8),
                ),
                windowInsets = WindowInsets(0),
                actions = {
                    IconButton(
                        onClick = {
                            vm.setShowHidden(!showHidden)
                            selecting = false
                            selected = emptySet()
                        },
                        modifier = Modifier.semantics {
                            contentDescription = ConversationA11y.HIDDEN_FILTER
                        },
                    ) {
                        Icon(
                            if (showHidden) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                            contentDescription = ConversationA11y.HIDDEN_FILTER,
                        )
                    }
                    if (selecting) {
                        TextButton(onClick = {
                            selecting = false
                            selected = emptySet()
                        }) { Text("Done") }
                    } else {
                        TextButton(onClick = { selecting = true }) {
                            Text("Select", modifier = Modifier.semantics {
                                contentDescription = ConversationA11y.SELECT_CHAT
                            })
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (selecting && selected.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = {
                        val hiding = !showHidden
                        vm.setHiddenOnDevice(selected, hidden = hiding)
                        scope.launch {
                            snackbar.showSnackbar(
                                if (hiding) ConversationA11y.HIDE_CONFIRMATION
                                else ConversationA11y.UNHIDE_CONFIRMATION,
                            )
                        }
                        selected = emptySet()
                        selecting = false
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    icon = {
                        Icon(
                            if (showHidden) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                            contentDescription = if (showHidden) {
                                ConversationA11y.UNHIDE_ON_DEVICE
                            } else {
                                ConversationA11y.HIDE_ON_DEVICE
                            },
                        )
                    },
                    text = {
                        Text(if (showHidden) "Show on this device" else "Hide on this device")
                    },
                )
            } else if (!showHidden) {
                ExtendedFloatingActionButton(
                    onClick = onNew,
                    containerColor = MaterialTheme.colorScheme.primary,
                    icon = { Icon(Icons.Filled.Add, contentDescription = ConversationA11y.NEW_CHAT) },
                    text = { Text("New chat") },
                )
            }
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            ConversationStatusBanner(snapshot.connection)
            OutlinedTextField(
                value = query,
                onValueChange = vm::setSearchQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp)
                    .semantics { contentDescription = ConversationA11y.SEARCH_CHATS },
                placeholder = { Text("Search chats", color = Color(0xFFB7C7DC)) },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                colors = jarvisTextFieldColors(),
            )
            if (items.isEmpty()) {
                ConversationListEmpty(
                    querying = query.isNotBlank(),
                    showHidden = showHidden,
                )
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    itemsIndexed(items, key = { _, c -> c.conversationId }) { _, c ->
                        ConversationRow(
                            item = c,
                            selecting = selecting,
                            checked = c.conversationId in selected,
                            modifier = if (reduced) Modifier else Modifier.animateItem(),
                            onOpen = { onOpen(c.conversationId) },
                            onToggleSelect = {
                                selected = if (c.conversationId in selected) {
                                    selected - c.conversationId
                                } else {
                                    selected + c.conversationId
                                }
                            },
                            onPin = { vm.setPinned(c.conversationId, !c.pinned) },
                            onHide = {
                                val hiding = !c.archived
                                vm.setHiddenOnDevice(listOf(c.conversationId), hidden = hiding)
                                scope.launch {
                                    snackbar.showSnackbar(
                                        if (hiding) ConversationA11y.HIDE_CONFIRMATION
                                        else ConversationA11y.UNHIDE_CONFIRMATION,
                                    )
                                }
                            },
                            onRename = { renameTarget = c },
                        )
                    }
                }
            }
        }
    }

    val renaming = renameTarget
    if (renaming != null) {
        RenameOnDeviceDialog(
            current = renaming.displayTitle,
            onDismiss = { renameTarget = null },
            onConfirm = { title ->
                vm.renameOnDevice(renaming.conversationId, title)
                renameTarget = null
            },
        )
    }
}

@Composable
private fun ConversationStatusBanner(connection: ConnectionState) {
    val (text, color) = when (connection) {
        ConnectionState.OFFLINE, ConnectionState.DISCONNECTED ->
            ConversationA11y.OFFLINE to MaterialTheme.colorScheme.onSurfaceVariant
        ConnectionState.AUTH_EXPIRED, ConnectionState.DEVICE_REVOKED,
        ConnectionState.PROTOCOL_MISMATCH ->
            ConversationA11y.CONNECTION_ERROR to MaterialTheme.colorScheme.error
        else -> return
    }
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 4.dp)
            .semantics { contentDescription = text },
    )
}

@Composable
private fun ConversationListEmpty(querying: Boolean, showHidden: Boolean) {
    val title: String
    val body: String
    val description: String
    when {
        querying -> {
            title = "Nothing matches"
            body = "Try a different title. Search stays on this device."
            description = ConversationA11y.EMPTY_SEARCH
        }
        showHidden -> {
            title = "Nothing hidden"
            body = "Hide is local only. Server history is unchanged."
            description = ConversationA11y.EMPTY_HIDDEN
        }
        else -> {
            title = "Ready when you are"
            body = "Start a conversation and I'll stream the reply here."
            description = ConversationA11y.EMPTY_CHATS
        }
    }
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp)
            .semantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        JarvisOrb(
            size = 130.dp,
            visualState = JarvisVisualState.IDLE,
            contentDescription = description,
        )
        Spacer(Modifier.height(24.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationRow(
    item: ConversationListItem,
    selecting: Boolean,
    checked: Boolean,
    onOpen: () -> Unit,
    onToggleSelect: () -> Unit,
    onPin: () -> Unit,
    onHide: () -> Unit,
    onRename: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accents = LocalJarvisAccents.current
    var menu by remember { mutableStateOf(false) }
    val rowDescription = ConversationA11y.conversationRow(
        title = item.displayTitle,
        pinned = item.pinned,
        activitySinceOpen = item.activitySinceOpen,
    )
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = rowDescription }
            .combinedClickable(
                onClick = { if (selecting) onToggleSelect() else onOpen() },
                onLongClick = { if (!selecting) menu = true },
            ),
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selecting) {
                Checkbox(checked = checked, onCheckedChange = { onToggleSelect() })
                Spacer(Modifier.size(8.dp))
            }
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(accents.orbGlow.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                JarvisOrb(
                    size = 30.dp,
                    visualState = JarvisVisualState.IDLE,
                )
            }
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.displayTitle, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    if (item.pinned) {
                        Spacer(Modifier.size(6.dp))
                        Icon(
                            Icons.Filled.PushPin,
                            contentDescription = ConversationA11y.PIN_ON_DEVICE,
                            modifier = Modifier.size(14.dp),
                            tint = accents.orbGlow,
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    buildString {
                        append(
                            java.text.DateFormat
                                .getTimeInstance(java.text.DateFormat.SHORT)
                                .format(java.util.Date(item.updatedAtMs)),
                        )
                        if (item.locallyRenamed) append(" · on this device")
                        if (item.activitySinceOpen) append(" · new since last opened")
                    },
                    style = HudTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Chat actions")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(if (item.pinned) "Unpin on this device" else "Pin on this device") },
                        onClick = { menu = false; onPin() },
                    )
                    DropdownMenuItem(
                        text = { Text("Rename on this device") },
                        onClick = { menu = false; onRename() },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(if (item.archived) "Show on this device" else "Hide on this device")
                        },
                        onClick = { menu = false; onHide() },
                    )
                }
            }
        }
    }
}

@Composable
private fun RenameOnDeviceDialog(
    current: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by rememberSaveable(current) { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename on this device") },
        text = {
            Column {
                Text(
                    ConversationA11y.LOCAL_ONLY_HINT,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it.take(80) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = ConversationA11y.RENAME_ON_DEVICE },
                    colors = jarvisTextFieldColors(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun ChatScreen(vm: JarvisViewModel, onBack: () -> Unit) {
    val messages by vm.messages.collectAsStateWithLifecycle()
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val pendingAttachments by vm.pendingAttachments.collectAsStateWithLifecycle()
    val avatarEpoch by vm.avatarEpoch.collectAsStateWithLifecycle()
    val input by vm.composerDraft.collectAsStateWithLifecycle()
    val openItem by vm.openConversationItem.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val accents = LocalJarvisAccents.current
    val reduced = LocalReducedMotion.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var renameOpen by remember { mutableStateOf(false) }

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> vm.onVoicePermissionResult(granted) }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let { vm.stageAttachment(it) } }

    var previewAttachmentId by remember { mutableStateOf<String?>(null) }

    val liveRequest = snapshot.session.requests.values.firstOrNull { !it.status.isTerminal }
    val streaming = liveRequest != null
    val voice by vm.voiceUi.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val home by vm.home.collectAsStateWithLifecycle()

    androidx.compose.runtime.DisposableEffect(Unit) {
        vm.setVoiceChatVisible(true)
        onDispose {
            vm.setVoiceChatVisible(false)
            vm.stopVoice()
        }
    }

    LaunchedEffect(Unit) { vm.refreshChatAccess() }

    LaunchedEffect(messages.size, messages.lastOrNull()?.text?.length) {
        if (messages.isNotEmpty()) {
            if (reduced) listState.scrollToItem(messages.lastIndex)
            else listState.animateScrollToItem(messages.lastIndex)
        }
    }

    val imeVisible = WindowInsets.isImeVisible
    LaunchedEffect(imeVisible) {
        if (imeVisible && messages.isNotEmpty()) {
            if (reduced) listState.scrollToItem(messages.lastIndex)
            else listState.animateScrollToItem(messages.lastIndex)
        }
    }

    val awayFromLatest by remember {
        derivedStateOf {
            val last = messages.lastIndex
            last >= 0 && listState.firstVisibleItemIndex < (last - 2).coerceAtLeast(0)
        }
    }
    val awayFromTop by remember {
        derivedStateOf { messages.size > 8 && listState.firstVisibleItemIndex > 2 }
    }

    fun shareOrCopy(text: String, share: Boolean) {
        val safe = vm.shareableMessageText(text)
        if (safe == null) {
            scope.launch { snackbar.showSnackbar(ConversationA11y.SHARE_BLOCKED) }
            return
        }
        if (share) {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, safe)
            }
            context.startActivity(Intent.createChooser(intent, ConversationA11y.SHARE_MESSAGE))
        } else {
            clipboard.setText(AnnotatedString(safe))
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
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
                    IconButton(onClick = {
                        vm.stopVoice()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        JarvisOrb(
                            size = 30.dp,
                            visualState = home.visualState,
                        )
                        Spacer(Modifier.size(10.dp))
                        Column {
                            Text(
                                openItem?.displayTitle ?: "JARVIS",
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                            )
                            val link = RecoveryUx.link(
                                snapshot, snapshot.reconnectAttempt, snapshot.reconnectBudget,
                            )
                            Text(
                                when {
                                    streaming -> "responding…"
                                    openItem?.locallyRenamed == true -> ConversationA11y.LOCAL_ONLY_HINT
                                    link.kind != RecoveryUx.LinkKind.ONLINE ->
                                        link.detail.ifBlank { link.headline.lowercase() }
                                    else -> "ready"
                                },
                                style = HudTextStyle,
                                color = if (streaming) accents.orbGlow
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    val item = openItem
                    if (item != null) {
                        IconButton(
                            onClick = { vm.setPinned(item.conversationId, !item.pinned) },
                            modifier = Modifier.semantics {
                                contentDescription = ConversationA11y.pinAction(item.pinned)
                            },
                        ) {
                            Icon(
                                Icons.Filled.PushPin,
                                contentDescription = ConversationA11y.pinAction(item.pinned),
                                tint = if (item.pinned) accents.orbGlow
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(
                            onClick = { renameOpen = true },
                            modifier = Modifier.semantics {
                                contentDescription = ConversationA11y.RENAME_ON_DEVICE
                            },
                        ) {
                            Icon(Icons.Filled.MoreVert, contentDescription = ConversationA11y.RENAME_ON_DEVICE)
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                if (awayFromTop) {
                    FloatingActionButton(
                        onClick = {
                            scope.launch {
                                if (reduced) listState.scrollToItem(0)
                                else listState.animateScrollToItem(0)
                            }
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .semantics { contentDescription = ConversationA11y.JUMP_TO_TOP },
                    ) {
                        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = ConversationA11y.JUMP_TO_TOP)
                    }
                    Spacer(Modifier.height(8.dp))
                }
                if (awayFromLatest) {
                    FloatingActionButton(
                        onClick = {
                            scope.launch {
                                if (messages.isNotEmpty()) {
                                    if (reduced) listState.scrollToItem(messages.lastIndex)
                                    else listState.animateScrollToItem(messages.lastIndex)
                                }
                            }
                        },
                        modifier = Modifier.semantics {
                            contentDescription = ConversationA11y.JUMP_TO_LATEST
                        },
                    ) {
                        Icon(
                            Icons.Filled.KeyboardArrowDown,
                            contentDescription = ConversationA11y.JUMP_TO_LATEST,
                        )
                    }
                }
            }
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
            if (messages.isEmpty()) {
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(32.dp)
                        .semantics { contentDescription = ConversationA11y.EMPTY_THREAD },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        "No messages yet",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (snapshot.connection.isUsable) {
                            "Send a message to start this chat."
                        } else {
                            ConversationA11y.OFFLINE
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(messages, key = { "${it.role}_${it.clientRequestId}" }) { msg ->
                        MessageBubble(
                            msg = msg,
                            onRetry = { vm.retry(msg.clientRequestId) },
                            onCopy = { shareOrCopy(msg.text, share = false) },
                            onShare = { shareOrCopy(msg.text, share = true) },
                            attachmentState = { id -> snapshot.session.attachments[id] },
                            attachmentStore = vm.attachmentStore,
                            avatarEpoch = avatarEpoch,
                            modifier = if (reduced) Modifier else Modifier.animateItem(),
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = pendingAttachments.isNotEmpty(),
                enter = if (reduced) fadeIn() else fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut(),
            ) {
                PendingAttachmentStrip(
                    attachments = pendingAttachments,
                    attachmentState = { id -> snapshot.session.attachments[id] },
                    onRemove = { vm.removePendingAttachment(it.attachmentId) },
                    onRetry = { vm.retryAttachment(it.attachmentId) },
                    onPreview = { previewAttachmentId = it.attachmentId },
                    attachmentStore = vm.attachmentStore,
                )
            }

            ProfileChipRow(vm)

            if (settings.voiceInputEnabled && voice.phase != VoicePhase.IDLE) {
                VoiceReviewBar(
                    phase = voice.phase,
                    partial = voice.partial,
                    draft = voice.draft,
                    error = voice.error,
                    onDraft = vm::editVoiceDraft,
                    onSend = {
                        vm.consumeVoiceSend()?.let { vm.send(it) }
                    },
                    onCancel = vm::cancelVoice,
                    onRetry = vm::retryVoice,
                )
            }

            Composer(
                input = input,
                onInput = vm::updateDraft,
                connected = snapshot.connection.isUsable,
                streaming = streaming,
                canSend = input.isNotBlank() || pendingAttachments.isNotEmpty(),
                voiceEnabled = settings.voiceInputEnabled,
                voicePhase = voice.phase,
                onMic = {
                    val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                        context, android.Manifest.permission.RECORD_AUDIO,
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    when (vm.voiceMicPermission.onMicTapped(granted)) {
                        com.jarvis.android.voice.VoiceMicPermission.Action.START -> vm.onVoiceMic()
                        com.jarvis.android.voice.VoiceMicPermission.Action.REQUEST ->
                            micPermission.launch(android.Manifest.permission.RECORD_AUDIO)
                        com.jarvis.android.voice.VoiceMicPermission.Action.FAIL -> vm.onVoiceMic()
                    }
                },
                onPickPhoto = {
                    photoPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                onStop = { liveRequest?.let { vm.cancel(it.clientRequestId) } },
                onSend = { vm.sendWithAttachments(input) },
            )
        }
    }

    val previewing = previewAttachmentId
    if (previewing != null) {
        AttachmentPreviewDialog(
            attachmentId = previewing,
            model = AttachmentUx.chip(previewing, snapshot.session.attachments[previewing]),
            attachmentStore = vm.attachmentStore,
            onDismiss = { previewAttachmentId = null },
        )
    }

    val renaming = openItem
    if (renameOpen && renaming != null) {
        RenameOnDeviceDialog(
            current = renaming.displayTitle,
            onDismiss = { renameOpen = false },
            onConfirm = { title ->
                vm.renameOnDevice(renaming.conversationId, title)
                renameOpen = false
            },
        )
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

@Composable
private fun VoiceReviewBar(
    phase: VoicePhase,
    partial: String,
    draft: String,
    error: String?,
    onDraft: (String) -> Unit,
    onSend: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Text(
            when (phase) {
                VoicePhase.REQUESTING_PERMISSION -> "REQUESTING PERMISSION"
                VoicePhase.LISTENING -> "LISTENING"
                VoicePhase.PROCESSING -> "PROCESSING"
                VoicePhase.TRANSCRIPT_READY -> "REVIEW BEFORE SEND"
                VoicePhase.ERROR -> "VOICE ERROR"
                VoicePhase.IDLE -> ""
            },
            style = HudTextStyle,
            color = MaterialTheme.colorScheme.primary,
        )
        when (phase) {
            VoicePhase.LISTENING, VoicePhase.PROCESSING -> Text(
                partial.ifBlank { "…" },
                style = MaterialTheme.typography.bodyMedium,
            )
            VoicePhase.TRANSCRIPT_READY -> {
                OutlinedTextField(
                    value = draft,
                    onValueChange = onDraft,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Transcript") },
                )
                Row {
                    TextButton(onClick = onSend, enabled = draft.isNotBlank()) { Text("Send") }
                    TextButton(onClick = onRetry) { Text("Retry") }
                    TextButton(onClick = onCancel) { Text("Cancel") }
                }
            }
            VoicePhase.ERROR -> {
                Text(error ?: "Voice failed", color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onRetry) { Text("Dismiss") }
            }
            else -> Unit
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
    voiceEnabled: Boolean = false,
    voicePhase: VoicePhase = VoicePhase.IDLE,
    onMic: () -> Unit = {},
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
        if (voiceEnabled) {
            IconButton(onClick = onMic) {
                Icon(
                    Icons.Filled.Mic,
                    contentDescription = if (voicePhase == VoicePhase.LISTENING) "Stop listening" else "Push to talk",
                    tint = if (voicePhase == VoicePhase.LISTENING) accents.orbGlow
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
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
                val reducedMotion = LocalReducedMotion.current
                val scale by animateFloatAsState(
                    targetValue = if (canSend || reducedMotion) 1f else 0.85f,
                    label = "sendScale",
                )
                FilledIconButton(
                    onClick = onSend,
                    enabled = canSend,
                    modifier = Modifier.size((44 * scale).dp),
                ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send") }
            }
        }
    }
}

@Composable
private fun MessageBubble(
    msg: ChatMessage,
    onRetry: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
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
    val canExport = msg.text.isNotBlank() && !isStreaming && !awaiting

    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (!isUser) {
            Box(Modifier.padding(end = 8.dp, bottom = 2.dp)) {
                JarvisOrb(
                    size = 28.dp,
                    visualState = if (isStreaming) JarvisVisualState.RESPONDING
                    else if (awaiting) JarvisVisualState.THINKING
                    else JarvisVisualState.IDLE,
                    contentDescription = if (isStreaming || awaiting) "JARVIS is responding" else null,
                )
            }
        }
        Column(horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
            Box(Modifier.widthIn(max = 320.dp)) {
                Surface(
                    color = if (isUser) Color.Transparent else accents.assistantBubble,
                    contentColor = if (isUser) Color.White else Color(0xFFE8EEF8),
                    shape = RoundedCornerShape(
                        topStart = 20.dp,
                        topEnd = if (isUser) 6.dp else 20.dp,
                        bottomStart = if (isUser) 20.dp else 6.dp,
                        bottomEnd = 20.dp,
                    ),
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
                            msg.text.isNotBlank() -> SelectionContainer {
                                Text(
                                    streamingText(msg.text, isStreaming),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            }

            if (canExport) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onCopy,
                        modifier = Modifier.size(32.dp).semantics {
                            contentDescription = ConversationA11y.COPY_MESSAGE
                        },
                    ) {
                        Icon(
                            Icons.Filled.ContentCopy,
                            contentDescription = ConversationA11y.COPY_MESSAGE,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    IconButton(
                        onClick = onShare,
                        modifier = Modifier.size(32.dp).semantics {
                            contentDescription = ConversationA11y.SHARE_MESSAGE
                        },
                    ) {
                        Icon(
                            Icons.Filled.Share,
                            contentDescription = ConversationA11y.SHARE_MESSAGE,
                            modifier = Modifier.size(14.dp),
                        )
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
                            Icon(Icons.Filled.Refresh, contentDescription = "Retry", modifier = Modifier.size(15.dp))
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
    onPreview: () -> Unit = {},
) {
    val model = AttachmentUx.chip(attachmentId, state)
    val thumb by rememberAttachmentThumb(attachmentId, attachmentStore)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(92.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface)
                .clickable(onClick = onPreview)
                .semantics { contentDescription = attachmentChipDescription(model) },
            contentAlignment = Alignment.Center,
        ) {
            val bitmap = thumb
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(Icons.Filled.Image, contentDescription = null)
            }
            if (model.showProgress) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(
            model.label,
            style = HudTextStyle,
            color = if (model.phase == AttachmentPhase.FAILED)
                MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Spoken/visible description. Never includes a path, URI, or the raw id. */
internal fun attachmentChipDescription(model: AttachmentChipModel): String =
    "Attachment, ${model.label.lowercase()}. ${model.detail}"

@Composable
private fun PendingAttachmentStrip(
    attachments: List<StagedAttachmentRef>,
    attachmentState: (String) -> com.jarvis.android.data.state.AttachmentUiState?,
    onRemove: (StagedAttachmentRef) -> Unit,
    onRetry: (StagedAttachmentRef) -> Unit,
    onPreview: (StagedAttachmentRef) -> Unit,
    attachmentStore: com.jarvis.android.data.media.AttachmentStore,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(attachments, key = { it.attachmentId }) { a ->
            val model = AttachmentUx.chip(a.attachmentId, attachmentState(a.attachmentId))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(76.dp)) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { onPreview(a) }
                            .semantics { contentDescription = attachmentChipDescription(model) },
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
                                Icon(Icons.Filled.Image, contentDescription = null)
                            }
                        }
                        if (model.showProgress) {
                            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                            }
                        }
                    }
                    if (model.canRemove) {
                        FilledIconButton(
                            onClick = { onRemove(a) },
                            modifier = Modifier.align(Alignment.TopEnd).size(24.dp),
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Remove attachment", modifier = Modifier.size(14.dp))
                        }
                    }
                    if (model.canRetry) {
                        FilledIconButton(
                            onClick = { onRetry(a) },
                            modifier = Modifier.align(Alignment.BottomStart).size(24.dp),
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Retry upload", modifier = Modifier.size(14.dp))
                        }
                    }
                }
                Text(
                    model.label,
                    style = HudTextStyle,
                    color = if (model.phase == AttachmentPhase.FAILED)
                        MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Full local preview. The image shown is the EXIF-stripped private copy; the
 * dialog states the phase explicitly so a local preview is never read as an
 * uploaded attachment.
 */
@Composable
private fun AttachmentPreviewDialog(
    attachmentId: String,
    model: AttachmentChipModel,
    attachmentStore: com.jarvis.android.data.media.AttachmentStore,
    onDismiss: () -> Unit,
) {
    val thumb by rememberAttachmentThumb(attachmentId, attachmentStore, maxSize = 512)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(model.label) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val bitmap = thumb
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = attachmentChipDescription(model),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    Box(
                        Modifier.fillMaxWidth().height(120.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (model.showProgress) {
                            CircularProgressIndicator()
                        } else {
                            Icon(Icons.Filled.Image, contentDescription = null)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(model.detail, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

