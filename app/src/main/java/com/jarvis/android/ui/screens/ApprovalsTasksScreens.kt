package com.jarvis.android.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jarvis.android.contract.ApprovalOutcome
import com.jarvis.android.contract.TaskStatus
import com.jarvis.android.data.state.ApprovalUiState
import com.jarvis.android.data.state.TaskUiState
import com.jarvis.android.ui.JarvisViewModel
import com.jarvis.android.ui.components.JarvisOrb
import com.jarvis.android.ui.home.JarvisVisualState
import com.jarvis.android.ui.shared.rememberBiometricGate
import com.jarvis.android.ui.shared.requiresBiometric
import com.jarvis.android.ui.tasks.TaskApprovalUx
import com.jarvis.android.ui.theme.HudTextStyle
import com.jarvis.android.ui.theme.JarvisMotion
import com.jarvis.android.ui.theme.LocalJarvisAccents
import com.jarvis.android.ui.theme.LocalReducedMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApprovalsScreen(vm: JarvisViewModel, isOwner: Boolean = true) {
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val pending = TaskApprovalUx.visibleApprovals(isOwner, snapshot.session.approvals.values, nowMs)
    val gate = rememberBiometricGate()
    val scope = rememberCoroutineScope()
    var denied by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Approvals",
                        modifier = Modifier.semantics { contentDescription = TaskApprovalA11y.APPROVALS_SCREEN },
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color(0xFFE8EEF8),
                    navigationIconContentColor = Color(0xFFE8EEF8),
                    actionIconContentColor = Color(0xFFE8EEF8),
                ),
                windowInsets = WindowInsets(0),
            )
        },
    ) { pad ->
        if (!isOwner) {
            EmptyState(
                icon = {
                    JarvisOrb(
                        size = 110.dp,
                        visualState = JarvisVisualState.OFFLINE,
                    )
                },
                title = "Owner only",
                body = "PC-action approvals are visible only to the OWNER of this Jarvis installation. This device is signed in as a guest.",
                modifier = Modifier.padding(pad).semantics { contentDescription = TaskApprovalA11y.OWNER_ONLY },
            )
        } else if (pending.isEmpty()) {
            EmptyState(
                icon = {
                    JarvisOrb(
                        size = 110.dp,
                        visualState = JarvisVisualState.IDLE,
                    )
                },
                title = "Nothing to approve",
                body = "When JARVIS wants to act on your PC, the request appears here for you to allow or deny.",
                modifier = Modifier.padding(pad).semantics { contentDescription = TaskApprovalA11y.EMPTY_APPROVALS },
            )
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(pad),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(14.dp),
            ) {
                items(pending, key = { it.approvalId }) { a ->
                    ApprovalCard(
                        a = a,
                        nowMs = nowMs,
                        biometricDenied = denied == a.approvalId,
                        modifier = Modifier.animateItem(),
                        onApprove = {
                            if (TaskApprovalUx.canSubmit(a, nowMs)) {
                                scope.launch {
                                    if (gate(a.tier, a.title)) {
                                        denied = null
                                        vm.resolveApproval(a.approvalId, ApprovalOutcome.APPROVED)
                                    } else {
                                        denied = a.approvalId
                                    }
                                }
                            }
                        },
                        onDeny = {
                            if (TaskApprovalUx.canSubmit(a, nowMs)) {
                                vm.resolveApproval(a.approvalId, ApprovalOutcome.DENIED)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ApprovalCard(
    a: ApprovalUiState,
    nowMs: Long,
    biometricDenied: Boolean,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accents = LocalJarvisAccents.current
    val phase = TaskApprovalUx.phase(a, nowMs)
    val submitEnabled = TaskApprovalUx.canSubmit(a, nowMs)
    val sensitive = TaskApprovalUx.requiresBiometric(a.tier)
    val accent = when (phase) {
        TaskApprovalUx.ApprovalPhase.SERVER -> MaterialTheme.colorScheme.onSurfaceVariant
        TaskApprovalUx.ApprovalPhase.RESOLVING -> accents.degraded
        TaskApprovalUx.ApprovalPhase.EXPIRED_LOCAL -> MaterialTheme.colorScheme.error
        TaskApprovalUx.ApprovalPhase.REQUESTED -> if (sensitive) MaterialTheme.colorScheme.error else accents.orbGlow
    }
    val cardDescription = when (phase) {
        TaskApprovalUx.ApprovalPhase.REQUESTED -> a.title
        TaskApprovalUx.ApprovalPhase.RESOLVING -> TaskApprovalA11y.RESOLVING_NOT_AUTHORITATIVE
        TaskApprovalUx.ApprovalPhase.EXPIRED_LOCAL -> TaskApprovalA11y.EXPIRED_LOCAL
        TaskApprovalUx.ApprovalPhase.SERVER -> TaskApprovalA11y.SERVER_OUTCOME
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
        modifier = modifier.fillMaxWidth().semantics { contentDescription = cardDescription },
    ) {
        Column {
            Box(Modifier.fillMaxWidth().height(3.dp).background(accent.copy(alpha = 0.9f)))
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Shield, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(a.tier.name, style = HudTextStyle, color = accent)
                    Spacer(Modifier.weight(1f))
                    a.risk?.let { Text("RISK ${it.uppercase()}", style = HudTextStyle, color = accents.degraded) }
                }
                Spacer(Modifier.height(10.dp))
                Text(a.title, style = MaterialTheme.typography.titleMedium)
                a.description?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Spacer(Modifier.height(14.dp))
                Text(
                    TaskApprovalUx.phaseLabel(a, nowMs),
                    style = HudTextStyle,
                    color = when (phase) {
                        TaskApprovalUx.ApprovalPhase.EXPIRED_LOCAL -> MaterialTheme.colorScheme.error
                        TaskApprovalUx.ApprovalPhase.SERVER -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> accent
                    },
                )

                when (phase) {
                    TaskApprovalUx.ApprovalPhase.REQUESTED -> {
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = onApprove,
                                enabled = submitEnabled,
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = accents.online),
                            ) {
                                if (requiresBiometric(a.tier)) {
                                    Icon(Icons.Filled.Fingerprint, contentDescription = null, modifier = Modifier.size(17.dp))
                                    Spacer(Modifier.size(6.dp))
                                }
                                Text("Approve")
                            }
                            OutlinedButton(
                                onClick = onDeny,
                                enabled = submitEnabled,
                                shape = RoundedCornerShape(14.dp),
                            ) { Text("Deny") }
                        }
                        TaskApprovalUx.remainingSeconds(a.expiresAtMs, nowMs)?.let { secs ->
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "EXPIRES IN ${secs}S",
                                style = HudTextStyle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.semantics { contentDescription = TaskApprovalA11y.remaining(secs) },
                            )
                        }
                        if (biometricDenied) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Biometric confirmation required — not approved.",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.semantics { contentDescription = TaskApprovalA11y.BIOMETRIC_REQUIRED },
                            )
                        }
                    }
                    TaskApprovalUx.ApprovalPhase.RESOLVING -> {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            TaskApprovalA11y.DUPLICATE_TAP_BLOCKED,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TaskApprovalUx.ApprovalPhase.EXPIRED_LOCAL -> {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Waiting for the server outcome. Local clock expiry is not a decision.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TaskApprovalUx.ApprovalPhase.SERVER -> {
                        a.resolvedAtMs?.let {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                TaskApprovalUx.formatTimestamp(it),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(vm: JarvisViewModel) {
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val grouped = TaskApprovalUx.grouped(snapshot.session.tasks.values)
    var selected by remember { mutableStateOf<TaskUiState?>(null) }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Tasks",
                        modifier = Modifier.semantics { contentDescription = TaskApprovalA11y.TASKS_SCREEN },
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color(0xFFE8EEF8),
                    navigationIconContentColor = Color(0xFFE8EEF8),
                    actionIconContentColor = Color(0xFFE8EEF8),
                ),
                windowInsets = WindowInsets(0),
            )
        },
    ) { pad ->
        if (snapshot.session.tasks.isEmpty()) {
            EmptyState(
                icon = {
                    JarvisOrb(
                        size = 110.dp,
                        visualState = JarvisVisualState.IDLE,
                    )
                },
                title = "No running tasks",
                body = "Long jobs show their progress here and keep running even if the connection drops.",
                modifier = Modifier.padding(pad).semantics { contentDescription = TaskApprovalA11y.EMPTY_TASKS },
            )
        } else {
            Box(Modifier.fillMaxSize().padding(pad)) {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(14.dp),
                ) {
                    TaskApprovalUx.sectionOrder.forEach { group ->
                        val rows = grouped.getValue(group)
                        if (rows.isNotEmpty()) {
                            item(key = "hdr-${group.name}") {
                                Text(
                                    group.name,
                                    style = HudTextStyle,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .padding(top = 4.dp, bottom = 2.dp)
                                        .semantics { contentDescription = TaskApprovalA11y.group(group.name) },
                                )
                            }
                            items(rows, key = { it.taskId }) { t ->
                                TaskCard(
                                    t = t,
                                    onOpen = { selected = t },
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }
                    }
                }
                selected?.let { task ->
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.45f))
                            .clickable { selected = null },
                    )
                    TaskDetailSheet(
                        task = task,
                        onDismiss = { selected = null },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskCard(
    t: TaskUiState,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accents = LocalJarvisAccents.current
    val reduced = LocalReducedMotion.current
    val running = TaskApprovalUx.groupOf(t.status) == TaskApprovalUx.TaskGroup.RUNNING
    val tint = when (t.status) {
        TaskStatus.COMPLETED -> accents.online
        TaskStatus.FAILED -> MaterialTheme.colorScheme.error
        TaskStatus.CANCELLED -> accents.offline
        else -> accents.orbGlow
    }
    val held = remember(t.taskId) { mutableStateOf(t.progress) }
    var firstFrame by remember(t.taskId) { mutableStateOf(true) }
    val target = TaskApprovalUx.stableProgress(held.value, t.progress, t.status)
    androidx.compose.runtime.SideEffect {
        held.value = target
        firstFrame = false
    }
    val progress by animateFloatAsState(
        targetValue = target ?: 0f,
        animationSpec = if (firstFrame || reduced) androidx.compose.animation.core.tween<Float>(0) else JarvisMotion.standard(280),
        label = "taskProgress",
    )
    val label = t.label ?: TaskApprovalUx.sanitizedId(t.taskId)

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .semantics { contentDescription = TaskApprovalA11y.taskRow(label, t.status.name) },
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (running) {
                    JarvisOrb(
                        size = 22.dp,
                        visualState = JarvisVisualState.EXECUTING,
                    )
                    Spacer(Modifier.size(8.dp))
                }
                Text(label, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                Text(t.status.name, style = HudTextStyle, color = tint)
            }
            if (target != null) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    color = tint,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .semantics { contentDescription = TaskApprovalA11y.PROGRESS_HELD },
                )
            }
        }
    }
}

@Composable
private fun TaskDetailSheet(
    task: TaskUiState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accents = LocalJarvisAccents.current
    Surface(
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = false, onClick = {})
            .semantics { contentDescription = TaskApprovalA11y.TASK_DETAIL },
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(task.label ?: "Task", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text("STATUS · ${task.status.name}", style = HudTextStyle, color = accents.orbGlow)
            Spacer(Modifier.height(10.dp))
            Text("ID · ${TaskApprovalUx.sanitizedId(task.taskId)}", style = HudTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("REQ · ${TaskApprovalUx.sanitizedId(task.requestId)}", style = HudTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "UPDATED · ${TaskApprovalUx.formatTimestamp(task.updatedAtMs)}",
                style = HudTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            task.progress?.let {
                Spacer(Modifier.height(8.dp))
                Text("PROGRESS · ${(it * 100).toInt()}%", style = HudTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    }
}

@Composable
private fun EmptyState(
    icon: @Composable () -> Unit,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxSize().padding(36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        icon()
        Spacer(Modifier.height(22.dp))
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
