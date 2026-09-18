package com.jarvis.android.ui.shared

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jarvis.android.data.repo.SessionSnapshot
import com.jarvis.android.data.state.ConnectionState
import com.jarvis.android.ui.recovery.RecoveryUx
import com.jarvis.android.ui.theme.HudTextStyle
import com.jarvis.android.ui.theme.JarvisMotion
import com.jarvis.android.ui.theme.LocalJarvisAccents
import com.jarvis.android.ui.theme.LocalReducedMotion

/**
 * Always-visible HUD strip: a live status dot that breathes while connecting
 * and holds steady when online, plus an inline retry when the link is down.
 */
@Composable
fun ConnectionBanner(
    snapshot: SessionSnapshot,
    onReconnect: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val accents = LocalJarvisAccents.current
    val conn = snapshot.connection
    val link = RecoveryUx.link(snapshot, snapshot.reconnectAttempt, snapshot.reconnectBudget)

    val tint = when (link.kind) {
        RecoveryUx.LinkKind.ONLINE -> if (conn == ConnectionState.DEGRADED) accents.degraded else accents.online
        RecoveryUx.LinkKind.RECONNECTING -> accents.orbGlow
        RecoveryUx.LinkKind.AIRPLANE, RecoveryUx.LinkKind.BACKOFF_EXHAUSTED -> accents.offline
        RecoveryUx.LinkKind.FAIL_CLOSED -> MaterialTheme.colorScheme.error
    }
    val animatedTint by animateColorAsState(tint, JarvisMotion.standard(), label = "statusTint")
    val busy = link.kind == RecoveryUx.LinkKind.RECONNECTING
    val canRetry = onReconnect != null && link.showManualRetry

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.horizontalGradient(
                    listOf(animatedTint.copy(alpha = 0.10f), Color.Transparent),
                )
            )
            // The app draws edge-to-edge and this bar is a plain Row, so it has to
            // consume the status bar inset itself or it slides under the clock.
            .statusBarsPadding()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PulseDot(color = animatedTint, pulsing = busy)
        Spacer(Modifier.width(10.dp))
        Text(link.headline, style = HudTextStyle, color = animatedTint)
        if (link.detail.isNotBlank()) {
            Spacer(Modifier.width(8.dp))
            Text(
                link.detail,
                style = HudTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.weight(1f))

        val active = snapshot.session.activeRequestCount
        AnimatedVisibility(
            visible = active > 0,
            enter = fadeIn(JarvisMotion.standard()) + expandVertically(animationSpec = JarvisMotion.standard<androidx.compose.ui.unit.IntSize>()),
            exit = fadeOut(JarvisMotion.standard()) + shrinkVertically(animationSpec = JarvisMotion.standard<androidx.compose.ui.unit.IntSize>()),
        ) {
            Text(
                "$active ACTIVE",
                style = HudTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (canRetry) {
            TextButton(onClick = { onReconnect?.invoke() }) { Text("Retry") }
        }
    }
}

/** Status dot with an expanding halo while the link is negotiating. */
@Composable
private fun PulseDot(color: Color, pulsing: Boolean, modifier: Modifier = Modifier) {
    val reduced = LocalReducedMotion.current
    val transition = rememberInfiniteTransition(label = "dot")
    val wave by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Restart),
        label = "wave",
    )
    val animate = pulsing && !reduced

    Canvas(modifier.size(14.dp)) {
        val r = size.minDimension / 2f
        if (animate) {
            drawCircle(
                color = color.copy(alpha = (1f - wave) * 0.45f),
                radius = r * (0.45f + wave * 0.9f),
            )
        } else {
            drawCircle(color = color.copy(alpha = 0.22f), radius = r)
        }
        drawCircle(color = color, radius = r * 0.42f)
    }
}
