package com.jarvis.android.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jarvis.android.contract.ApprovalOutcome
import com.jarvis.android.contract.ApprovalTier
import com.jarvis.android.contract.TaskStatus
import com.jarvis.android.data.state.ApprovalUiState
import com.jarvis.android.ui.JarvisViewModel
import com.jarvis.android.ui.components.JarvisBrain
import com.jarvis.android.ui.components.JarvisOrb
import com.jarvis.android.ui.components.OrbActivity
import com.jarvis.android.ui.i18n.LocalAppStrings
import com.jarvis.android.ui.shared.rememberBiometricGate
import com.jarvis.android.ui.shared.requiresBiometric
import com.jarvis.android.ui.theme.HudTextStyle
import com.jarvis.android.ui.theme.LocalJarvisAccents
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApprovalsScreen(vm: JarvisViewModel, isOwner: Boolean = true) {
    val strings = LocalAppStrings.current
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val pending = if (isOwner) {
        snapshot.session.approvals.values.sortedByDescending { it.expiresAtMs ?: 0 }
    } else emptyList()
    val gate = rememberBiometricGate()
    val scope = rememberCoroutineScope()
    var denied by remember { mutableStateOf<String?>(null) }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val anyCountingDown = pending.any { it.outcome == null && it.expiresAtMs != null }
    LaunchedEffect(anyCountingDown) {
        while (anyCountingDown) {
            nowMs = System.currentTimeMillis()
            delay(1_000)
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(strings.approvalsTitle) },
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
                icon = { JarvisBrain(size = 110.dp, activity = OrbActivity.OFFLINE) },
                title = strings.ownerOnlyTitle,
                body = strings.ownerOnlyBody,
                modifier = Modifier.padding(pad),
            )
        } else if (pending.isEmpty()) {
            EmptyState(
                icon = { JarvisBrain(size = 110.dp, activity = OrbActivity.IDLE) },
                title = strings.nothingToApproveTitle,
                body = strings.nothingToApproveBody,
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
                        nowMs = nowMs,
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
    val strings = LocalAppStrings.current
    val accents = LocalJarvisAccents.current
    val expired = a.expiresAtMs?.let { it < nowMs } == true && a.outcome == null
    val resolved = a.outcome != null
    val sensitive = a.tier == ApprovalTier.SENSITIVE || a.tier == ApprovalTier.CRITICAL

    val tierColor = when (a.tier) {
        ApprovalTier.CRITICAL -> MaterialTheme.colorScheme.error
        ApprovalTier.SENSITIVE -> accents.degraded
        ApprovalTier.NORMAL -> accents.orbGlow
        ApprovalTier.LOW -> accents.online
    }
    val tierIcon = when (a.tier) {
        ApprovalTier.CRITICAL -> Icons.Filled.Warning
        ApprovalTier.SENSITIVE -> Icons.Filled.Shield
        ApprovalTier.NORMAL -> Icons.Filled.Shield
        ApprovalTier.LOW -> Icons.Filled.CheckCircle
    }

    val topBarAccent = when {
        resolved -> when (a.outcome) {
            ApprovalOutcome.APPROVED -> accents.online
            ApprovalOutcome.DENIED -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        expired -> MaterialTheme.colorScheme.error
        else -> tierColor
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xCC0E182A).copy(alpha = if (resolved) 0.6f else 0.8f),
        border = BorderStroke(1.dp, topBarAccent.copy(alpha = if (resolved) 0.18f else 0.35f)),
        shadowElevation = 3.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column {
            Box(Modifier.fillMaxWidth().height(3.dp).background(topBarAccent.copy(alpha = 0.9f)))
            Column(Modifier.padding(16.dp)) {
                // Header badge row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = tierColor.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, tierColor.copy(alpha = 0.45f)),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(tierIcon, contentDescription = null, tint = tierColor, modifier = Modifier.size(13.dp))
                            Text(a.tier.name, style = HudTextStyle, color = tierColor)
                        }
                    }

                    a.risk?.let { risk ->
                        Spacer(Modifier.width(8.dp))
                        val riskColor = if (risk.equals("high", ignoreCase = true) || risk.equals("critical", ignoreCase = true)) {
                            MaterialTheme.colorScheme.error
                        } else {
                            accents.degraded
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = riskColor.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, riskColor.copy(alpha = 0.35f)),
                        ) {
                            Text(
                                "RISK: ${risk.uppercase()}",
                                style = HudTextStyle,
                                color = riskColor,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            )
                        }
                    }

                    Spacer(Modifier.weight(1f))

                    // Outcome stamp badge
                    if (resolved) {
                        val outcomeColor = when (a.outcome) {
                            ApprovalOutcome.APPROVED -> accents.online
                            ApprovalOutcome.DENIED -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        val outcomeIcon = when (a.outcome) {
                            ApprovalOutcome.APPROVED -> Icons.Filled.CheckCircle
                            ApprovalOutcome.DENIED -> Icons.Filled.Cancel
                            else -> Icons.Filled.AccessTime
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = outcomeColor.copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, outcomeColor.copy(alpha = 0.7f)),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(outcomeIcon, contentDescription = null, tint = outcomeColor, modifier = Modifier.size(12.dp))
                                Text(
                                    when (a.outcome) {
                                        ApprovalOutcome.APPROVED -> "AUTHORIZED"
                                        ApprovalOutcome.DENIED -> "REJECTED"
                                        ApprovalOutcome.EXPIRED -> strings.expired
                                        else -> a.outcome?.name ?: strings.resolved
                                    },
                                    style = HudTextStyle,
                                    color = outcomeColor,
                                )
                            }
                        }
                    } else if (expired) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
                        ) {
                            Text(
                                strings.expired,
                                style = HudTextStyle,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Text(a.title, style = MaterialTheme.typography.titleMedium)

                // Optional command / description payload inspector
                a.description?.let { desc ->
                    Spacer(Modifier.height(8.dp))
                    val isCommandOrCode = desc.contains("\n") || desc.startsWith("run ") ||
                        desc.contains("/") || desc.contains("\\") || desc.contains(".exe") ||
                        desc.contains("git") || desc.contains("adb")
                    if (isCommandOrCode) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF08101E),
                            border = BorderStroke(1.dp, accents.orbGlow.copy(alpha = 0.18f)),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(modifier = Modifier.padding(10.dp)) {
                                Icon(
                                    Icons.Filled.Terminal,
                                    contentDescription = null,
                                    tint = accents.orbGlow,
                                    modifier = Modifier.size(14.dp).padding(top = 2.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    desc,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        lineHeight = 18.sp,
                                    ),
                                    color = Color(0xFFD4E3F8),
                                )
                            }
                        }
                    } else {
                        Text(
                            desc,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (!resolved && !expired && a.expiresAtMs != null) {
                    val remainingMs = (a.expiresAtMs - nowMs).coerceAtLeast(0L)
                    // The server sends only a deadline, so the full window is whatever
                    // was left when this card first appeared.
                    val windowMs = remember(a.approvalId) { remainingMs.coerceAtLeast(1L) }
                    val progress = (remainingMs.toFloat() / windowMs).coerceIn(0f, 1f)
                    val meterColor = when {
                        progress < 0.2f -> MaterialTheme.colorScheme.error
                        progress < 0.5f -> accents.degraded
                        else -> accents.orbGlow
                    }

                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.AccessTime, contentDescription = null, tint = meterColor, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("SECURITY WINDOW", style = HudTextStyle, color = meterColor)
                        }
                        Text("${(remainingMs / 1000)}S REMAINING", style = HudTextStyle, color = meterColor)
                    }
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        color = meterColor,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                    )
                }

                Spacer(Modifier.height(14.dp))

                when {
                    resolved -> {
                        Text(
                            if (a.resolutionInFlight) "${strings.resolving} ${a.outcome?.name}…" else "${strings.resolved} · ${a.outcome?.name}",
                            style = HudTextStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    expired -> {
                        Text(strings.expired, style = HudTextStyle, color = MaterialTheme.colorScheme.error)
                    }
                    else -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Button(
                                onClick = onApprove,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = accents.online,
                                    contentColor = Color(0xFF04180A),
                                ),
                                modifier = Modifier.weight(1f).height(44.dp),
                            ) {
                                if (requiresBiometric(a.tier)) {
                                    Icon(Icons.Filled.Fingerprint, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                } else {
                                    Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                }
                                Text(
                                    strings.approve,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            OutlinedButton(
                                onClick = onDeny,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error,
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
                                modifier = Modifier.weight(1f).height(44.dp),
                            ) {
                                Icon(Icons.Filled.Cancel, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(strings.deny, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        a.expiresAtMs?.let {
                            val secs = ((it - nowMs) / 1000).coerceAtLeast(0)
                            Spacer(Modifier.height(8.dp))
                            Text(strings.expiresIn(secs), style = HudTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (biometricDenied) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                strings.biometricRequired,
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
    val strings = LocalAppStrings.current
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val tasks = snapshot.session.tasks.values.sortedByDescending { it.updatedAtMs }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(strings.tasksTitle) },
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
                icon = { JarvisBrain(size = 110.dp, activity = OrbActivity.IDLE) },
                title = strings.noTasksTitle,
                body = strings.noTasksBody,
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
                        color = Color(0xCC0E182A),
                        border = BorderStroke(1.dp, tint.copy(alpha = 0.25f)),
                        shadowElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth().animateItem(),
                    ) {
                        Column {
                            Box(Modifier.fillMaxWidth().height(2.dp).background(tint.copy(alpha = 0.8f)))
                            Column(Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (running) {
                                        JarvisOrb(size = 22.dp, activity = OrbActivity.THINKING)
                                        Spacer(Modifier.width(10.dp))
                                    }
                                    Text(
                                        t.label ?: t.taskId,
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = tint.copy(alpha = 0.12f),
                                        border = BorderStroke(1.dp, tint.copy(alpha = 0.4f)),
                                    ) {
                                        Text(
                                            t.status.name,
                                            style = HudTextStyle,
                                            color = tint,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        )
                                    }
                                }
                                if (t.progress != null) {
                                    Spacer(Modifier.height(12.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text("EXECUTION PROGRESS", style = HudTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("${(progress * 100).toInt()}%", style = HudTextStyle, color = tint)
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    LinearProgressIndicator(
                                        progress = { progress },
                                        color = tint,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(5.dp)
                                            .clip(RoundedCornerShape(3.dp)),
                                    )
                                }
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
