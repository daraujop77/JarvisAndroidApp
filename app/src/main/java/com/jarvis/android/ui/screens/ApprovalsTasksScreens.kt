package com.jarvis.android.ui.screens

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jarvis.android.contract.ApprovalOutcome
import com.jarvis.android.contract.ApprovalTier
import com.jarvis.android.contract.TaskStatus
import com.jarvis.android.data.state.ApprovalUiState
import com.jarvis.android.ui.JarvisViewModel
import com.jarvis.android.ui.components.JarvisOrb
import com.jarvis.android.ui.home.JarvisVisualState
import com.jarvis.android.ui.shared.rememberBiometricGate
import com.jarvis.android.ui.shared.requiresBiometric
import com.jarvis.android.ui.theme.HudTextStyle
import com.jarvis.android.ui.theme.LocalJarvisAccents
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApprovalsScreen(vm: JarvisViewModel, isOwner: Boolean = true) {
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val pending = if (isOwner) {
        snapshot.session.approvals.values.sortedByDescending { it.expiresAtMs ?: 0 }
    } else emptyList()
    val gate = rememberBiometricGate()
    val scope = rememberCoroutineScope()
    var denied by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text("Approvals") },
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
                modifier = Modifier.padding(pad),
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
                modifier = Modifier.padding(pad),
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
                        nowMs = System.currentTimeMillis(),
                        biometricDenied = denied == a.approvalId,
                        modifier = Modifier.animateItem(),
                        onApprove = {
                            scope.launch {
                                if (gate(a.tier, a.title)) {
                                    denied = null
                                    vm.resolveApproval(a.approvalId, ApprovalOutcome.APPROVED)
                                } else {
                                    denied = a.approvalId
                                }
                            }
                        },
                        onDeny = { vm.resolveApproval(a.approvalId, ApprovalOutcome.DENIED) },
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
    val expired = a.expiresAtMs?.let { it < nowMs } == true && a.outcome == null
    val resolved = a.outcome != null
    val sensitive = a.tier == ApprovalTier.SENSITIVE || a.tier == ApprovalTier.CRITICAL
    val accent = when {
        resolved -> MaterialTheme.colorScheme.onSurfaceVariant
        sensitive -> MaterialTheme.colorScheme.error
        else -> accents.orbGlow
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
        modifier = modifier.fillMaxWidth(),
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

                when {
                    resolved -> Text(
                        if (a.resolutionInFlight) "RESOLVING ${a.outcome?.name}…" else "RESOLVED · ${a.outcome?.name}",
                        style = HudTextStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    expired -> Text("EXPIRED", style = HudTextStyle, color = MaterialTheme.colorScheme.error)
                    else -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = onApprove,
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = accents.online),
                            ) {
                                if (requiresBiometric(a.tier)) {
                                    Icon(Icons.Filled.Fingerprint, contentDescription = null, modifier = Modifier.size(17.dp))
                                    Spacer(Modifier.size(6.dp))
                                }
                                Text("Approve")
                            }
                            OutlinedButton(onClick = onDeny, shape = RoundedCornerShape(14.dp)) { Text("Deny") }
                        }
                        a.expiresAtMs?.let {
                            val secs = ((it - nowMs) / 1000).coerceAtLeast(0)
                            Spacer(Modifier.height(8.dp))
                            Text("EXPIRES IN ${secs}S", style = HudTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (biometricDenied) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Biometric confirmation required — not approved.",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error,
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
    val tasks = snapshot.session.tasks.values.sortedByDescending { it.updatedAtMs }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text("Tasks") },
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
        if (tasks.isEmpty()) {
            EmptyState(
                icon = {
                    JarvisOrb(
                        size = 110.dp,
                        visualState = JarvisVisualState.IDLE,
                    )
                },
                title = "No running tasks",
                body = "Long jobs show their progress here and keep running even if the connection drops.",
                modifier = Modifier.padding(pad),
            )
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(pad),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(14.dp),
            ) {
                items(tasks, key = { it.taskId }) { t ->
                    val accents = LocalJarvisAccents.current
                    val running = t.status == TaskStatus.RUNNING || t.status == TaskStatus.STARTED
                    val tint = when (t.status) {
                        TaskStatus.COMPLETED -> accents.online
                        TaskStatus.FAILED -> MaterialTheme.colorScheme.error
                        TaskStatus.CANCELLED -> accents.offline
                        else -> accents.orbGlow
                    }
                    val progress by animateFloatAsState(
                        targetValue = t.progress?.coerceIn(0f, 1f) ?: 0f,
                        label = "taskProgress",
                    )
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                        modifier = Modifier.fillMaxWidth().animateItem(),
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
                                Text(t.label ?: t.taskId, style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.weight(1f))
                                Text(t.status.name, style = HudTextStyle, color = tint)
                            }
                            if (t.progress != null) {
                                Spacer(Modifier.height(12.dp))
                                LinearProgressIndicator(
                                    progress = { progress },
                                    color = tint,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.fillMaxWidth().height(6.dp),
                                )
                            }
                        }
                    }
                }
            }
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
