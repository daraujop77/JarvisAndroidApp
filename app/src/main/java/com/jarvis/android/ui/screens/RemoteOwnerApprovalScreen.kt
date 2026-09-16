package com.jarvis.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jarvis.android.data.remote.RemoteApprovalPhase
import com.jarvis.android.data.remote.RemoteApprovalPolicy
import com.jarvis.android.data.remote.RemoteApprovalUiModel
import com.jarvis.android.ui.theme.HudTextStyle

/**
 * POST_BASELINE_FUTURE_WORK. Not wired into the live session.
 * Approve-once / deny / emergency stop only. No "approve always".
 * Screenshot bytes are not persisted by this screen.
 */
@Composable
fun RemoteOwnerApprovalScreen(
    model: RemoteApprovalUiModel,
    nowMs: Long,
    onApproveOnce: () -> Unit,
    onDeny: () -> Unit,
    onEmergencyStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val phase = RemoteApprovalPolicy.displayedPhase(model, nowMs)
    val challenge = model.challenge
    val remaining = RemoteApprovalPolicy.remainingMs(model, nowMs)
    val approveEnabled = RemoteApprovalPolicy.canApproveOnce(model, nowMs)
    val denyEnabled = RemoteApprovalPolicy.canDeny(model, nowMs)
    val stopEnabled = RemoteApprovalPolicy.canEmergencyStop(model)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("REMOTE OWNER CHECK", style = HudTextStyle, color = MaterialTheme.colorScheme.primary)
            Text(phase.name, style = MaterialTheme.typography.headlineSmall)
            Text(
                "One action only. There is no approve-always.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (challenge == null || phase == RemoteApprovalPhase.ERROR) {
                Text(
                    "Challenge unavailable. Missing screenshot or invalid metadata.",
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                Text("Operation: ${challenge.operation}", style = MaterialTheme.typography.bodyLarge)
                Text("Target: ${challenge.targetIdentity}", style = MaterialTheme.typography.bodyMedium)
                Text("Action ID: ${challenge.actionId}", style = HudTextStyle)
                Text("Captured: ${challenge.capturedAtMs}", style = HudTextStyle)
                Text(
                    if (phase == RemoteApprovalPhase.EXPIRED) "Expired" else "Expires in ${remaining / 1000}s",
                    style = HudTextStyle,
                    color = if (phase == RemoteApprovalPhase.EXPIRED) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!challenge.screenshotPresent) {
                    Text("No target screenshot.", color = MaterialTheme.colorScheme.error)
                }
            }
            if (phase == RemoteApprovalPhase.DISCONNECTED) {
                Text("Link down. Approve and deny are disabled.", color = MaterialTheme.colorScheme.error)
            }
            if (phase == RemoteApprovalPhase.CONSUMED || phase == RemoteApprovalPhase.APPROVED || phase == RemoteApprovalPhase.DENIED) {
                Text("Decision recorded: $phase. Replay is ignored.", style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onApproveOnce, enabled = approveEnabled) { Text("APPROVE ONCE") }
                OutlinedButton(onClick = onDeny, enabled = denyEnabled) { Text("DENY") }
            }
            Button(
                onClick = onEmergencyStop,
                enabled = stopEnabled,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) { Text("EMERGENCY STOP") }
        }
    }
}
