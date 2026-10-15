package com.jarvis.android.ui.shared

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jarvis.android.transport.live.JarvisAppSession
import com.jarvis.android.ui.JarvisViewModel
import com.jarvis.android.ui.theme.HudTextStyle
import com.jarvis.android.ui.theme.LocalJarvisAccents

/**
 * Account-wide notice for paid image attempts that need attention. Completed
 * receipts are recovered by the ViewModel; only Unknown outcomes offer a release.
 */
@Composable
fun PendingPaidVisualBanner(
    vm: JarvisViewModel,
    modifier: Modifier = Modifier,
) {
    val pending by vm.pendingPaidVisuals.collectAsStateWithLifecycle()
    val notice by vm.pendingPaidVisualNotice.collectAsStateWithLifecycle()
    var releaseTarget by remember { mutableStateOf<JarvisAppSession.PendingPaidVisualState.Unknown?>(null) }
    if (pending.isEmpty() && notice == null) return

    val accents = LocalJarvisAccents.current

    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(14.dp),
        color = accents.degraded.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, accents.degraded.copy(alpha = 0.35f)),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            if (pending.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        pendingCountText(pending.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = accents.degraded,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { vm.refreshPendingPaidVisuals() }) { Text("Comprobar") }
                }
                pending
                    .filterIsInstance<JarvisAppSession.PendingPaidVisualState.Unknown>()
                    .forEach { unknown ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Resultado sin confirmar · ref. ${unknown.requestId.takeLast(8)}",
                                style = HudTextStyle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { releaseTarget = unknown }) { Text("Liberar…") }
                        }
                    }
            }
            notice?.let { message ->
                Text(
                    message,
                    style = HudTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    releaseTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { releaseTarget = null },
            title = { Text("Liberar imagen pendiente") },
            text = {
                Text(
                    "Es posible que esta imagen ya se haya cobrado. " +
                        "Si la liberas podrás generar otra, pero esta no se recuperará.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    releaseTarget = null
                    vm.releasePendingPaidVisual(target.key, target.requestId)
                }) { Text("Liberar") }
            },
            dismissButton = {
                TextButton(onClick = { releaseTarget = null }) { Text("Cancelar") }
            },
        )
    }
}

private fun pendingCountText(count: Int): String =
    if (count == 1) {
        "Hay 1 imagen pendiente de confirmar."
    } else {
        "Hay $count imágenes pendientes de confirmar."
    }
