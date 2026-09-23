package com.jarvis.android.ui.shared

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.android.data.repo.SessionSnapshot
import com.jarvis.android.data.state.ConnectionState
import com.jarvis.android.ui.i18n.AppStrings
import com.jarvis.android.ui.i18n.LocalAppStrings
import com.jarvis.android.ui.theme.HudTextStyle
import com.jarvis.android.ui.theme.JarvisMotion
import com.jarvis.android.ui.theme.LocalJarvisAccents
import com.jarvis.android.ui.theme.LocalReducedMotion

private fun ConnectionState.label(strings: AppStrings): String = when (this) {
    ConnectionState.ONLINE -> strings.statusOnline
    ConnectionState.DEGRADED -> strings.statusDegraded
    ConnectionState.CONNECTING -> strings.statusConnecting
    ConnectionState.RECONNECTING -> strings.statusReconnecting
    ConnectionState.OFFLINE -> strings.statusOffline
    ConnectionState.DISCONNECTED -> strings.statusDisconnected
    ConnectionState.AUTH_EXPIRED -> strings.statusAuthRequired
    ConnectionState.DEVICE_REVOKED -> strings.statusDeviceRevoked
    ConnectionState.PROTOCOL_MISMATCH -> strings.statusUpdateRequired
}

/**
 * Daily v1: the control plane and the home PC fail differently, and the owner
 * must be able to tell which one is down. Cloud chat survives a dark PC;
 * a dark control plane does not. The detail string is what the transport
 * already reports — this only reads it, it never invents a cause.
 */
private fun failureHint(detail: String?, strings: AppStrings): String? {
    val text = detail?.lowercase() ?: return null
    return when {
        "pc_worker_offline" in text || "pc worker" in text -> strings.hintPcOffline
        "control_plane_unavailable" in text || "control plane" in text -> strings.hintControlPlane
        else -> null
    }
}

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
    val strings = LocalAppStrings.current
    val accents = LocalJarvisAccents.current
    val conn = snapshot.connection

    val tint = when (conn) {
        ConnectionState.ONLINE -> accents.online
        ConnectionState.DEGRADED -> accents.degraded
        ConnectionState.CONNECTING, ConnectionState.RECONNECTING -> accents.orbGlow
        ConnectionState.OFFLINE, ConnectionState.DISCONNECTED -> accents.offline
        else -> MaterialTheme.colorScheme.error
    }
    val animatedTint by animateColorAsState(tint, JarvisMotion.standard(), label = "statusTint")
    val busy = conn == ConnectionState.CONNECTING || conn == ConnectionState.RECONNECTING
    val canRetry = onReconnect != null &&
        (conn == ConnectionState.OFFLINE || conn == ConnectionState.DISCONNECTED)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(animatedTint.copy(alpha = 0.12f), Color.Transparent),
                    )
                )
                // The app draws edge-to-edge and this bar is a plain Row, so it has to
                // consume the status bar inset itself or it slides under the clock.
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = animatedTint.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, animatedTint.copy(alpha = 0.22f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PulseDot(
                        color = animatedTint,
                        pulsing = busy,
                        isOnline = conn == ConnectionState.ONLINE,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        conn.label(strings),
                        style = HudTextStyle.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.3.sp,
                        ),
                        color = animatedTint,
                    )
                }
            }
            failureHint(snapshot.session.connectionDetail, strings)?.let { hint ->
                Spacer(Modifier.width(8.dp))
                Text(hint, style = HudTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.weight(1f))

            val active = snapshot.session.activeRequestCount
            val reduced = LocalReducedMotion.current
            AnimatedVisibility(
                visible = active > 0,
                enter = fadeIn(JarvisMotion.standard()) +
                    expandVertically(if (reduced) tween(0) else JarvisMotion.standard()),
                exit = fadeOut(JarvisMotion.standard(160)) +
                    shrinkVertically(if (reduced) tween(0) else JarvisMotion.standard(160)),
            ) {
                Text(
                    strings.activeCount(active),
                    style = HudTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (canRetry) {
                TextButton(onClick = { onReconnect?.invoke() }) { Text(strings.retry) }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            animatedTint.copy(alpha = 0.28f),
                            animatedTint.copy(alpha = 0.08f),
                            Color.Transparent,
                        )
                    )
                )
        )
    }
}

/** Status dot with an expanding halo while negotiating, and a gentle living breath when online. */
@Composable
private fun PulseDot(
    color: Color,
    pulsing: Boolean,
    isOnline: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val reduced = LocalReducedMotion.current
    val transition = rememberInfiniteTransition(label = "dot")
    val wave by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Restart),
        label = "wave",
    )
    val breathe by transition.animateFloat(
        initialValue = 0.18f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(
            tween(2400, easing = FastOutSlowInEasing),
            RepeatMode.Reverse,
        ),
        label = "breathe",
    )
    val animate = pulsing && !reduced
    val liveBreathe = isOnline && !reduced

    Canvas(modifier.size(12.dp)) {
        val r = size.minDimension / 2f
        if (animate) {
            drawCircle(
                color = color.copy(alpha = (1f - wave) * 0.45f),
                radius = r * (0.45f + wave * 0.9f),
            )
        } else if (liveBreathe) {
            drawCircle(color = color.copy(alpha = breathe), radius = r * 0.85f)
        } else {
            drawCircle(color = color.copy(alpha = 0.22f), radius = r)
        }
        drawCircle(color = color, radius = r * 0.44f)
    }
}
