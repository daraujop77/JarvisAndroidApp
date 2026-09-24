package com.jarvis.android.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.jarvis.android.ui.theme.LocalReducedMotion
import kotlin.math.cos
import kotlin.math.sin

/**
 * Compact JARVIS HUD treatment for top-level navigation icons.
 *
 * Only the selected item moves, and even then the motion is intentionally slow:
 * one partial arc and one tiny locator dot orbit the glyph. Reduce Motion freezes
 * the HUD while retaining the selected-state glow.
 */
@Composable
fun HolographicHudIcon(
    imageVector: ImageVector,
    contentDescription: String,
    selected: Boolean,
    activeColor: Color,
    inactiveColor: Color,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = LocalReducedMotion.current
    val transition = rememberInfiniteTransition(label = "holoNav")
    val orbit by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12_000, easing = LinearEasing),
        ),
        label = "holoNavOrbit",
    )
    val breathe by transition.animateFloat(
        initialValue = 0.72f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2_400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "holoNavBreathe",
    )

    val motionAngle = if (selected && !reduceMotion) orbit else 24f
    val glow = if (selected) {
        if (reduceMotion) 0.88f else breathe
    } else {
        0.24f
    }
    val ringColor = if (selected) activeColor else inactiveColor

    Box(
        modifier = modifier.size(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.matchParentSize()) {
            val d = size.minDimension
            val centerRadius = d * 0.34f
            val outerRadius = d * 0.45f

            if (selected) {
                drawCircle(
                    color = activeColor.copy(alpha = 0.10f + (0.10f * glow)),
                    radius = d * 0.49f,
                )
                drawCircle(
                    color = activeColor.copy(alpha = 0.10f * glow),
                    radius = d * 0.40f,
                )
            }

            drawCircle(
                color = ringColor.copy(alpha = if (selected) 0.48f * glow else 0.24f),
                radius = outerRadius,
                style = Stroke(width = if (selected) 1.45.dp.toPx() else 1.dp.toPx()),
            )

            drawArc(
                color = ringColor.copy(alpha = if (selected) 0.95f * glow else 0.30f),
                startAngle = motionAngle,
                sweepAngle = if (selected) 92f else 54f,
                useCenter = false,
                topLeft = Offset(d * 0.10f, d * 0.10f),
                size = Size(d * 0.80f, d * 0.80f),
                style = Stroke(
                    width = if (selected) 2.dp.toPx() else 1.15.dp.toPx(),
                    cap = StrokeCap.Round,
                ),
            )

            drawArc(
                color = ringColor.copy(alpha = if (selected) 0.55f * glow else 0.16f),
                startAngle = motionAngle + 178f,
                sweepAngle = 46f,
                useCenter = false,
                topLeft = Offset(d * 0.18f, d * 0.18f),
                size = Size(d * 0.64f, d * 0.64f),
                style = Stroke(width = 1.dp.toPx(), cap = StrokeCap.Round),
            )

            if (selected) {
                val theta = Math.toRadians(motionAngle.toDouble())
                val point = Offset(
                    x = center.x + cos(theta).toFloat() * centerRadius,
                    y = center.y + sin(theta).toFloat() * centerRadius,
                )
                drawCircle(
                    color = activeColor.copy(alpha = 0.90f),
                    radius = 2.1.dp.toPx(),
                    center = point,
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.75f),
                    radius = 0.85.dp.toPx(),
                    center = point,
                )
            }
        }

        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = if (selected) activeColor else inactiveColor,
            modifier = Modifier.size(if (selected) 25.dp else 23.dp),
        )
    }
}

/**
 * Primary chat action styled as a small holographic emitter.
 * The send semantics are unchanged; this component only owns presentation.
 */
@Composable
fun HolographicSendButton(
    enabled: Boolean,
    contentDescription: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = LocalReducedMotion.current
    val transition = rememberInfiniteTransition(label = "holoSend")
    val orbit by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8_500, easing = LinearEasing),
        ),
        label = "holoSendOrbit",
    )
    val breathe by transition.animateFloat(
        initialValue = 0.76f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "holoSendBreathe",
    )

    val alpha = when {
        !enabled -> 0.34f
        reduceMotion -> 0.92f
        else -> breathe
    }
    val angle = if (enabled && !reduceMotion) orbit else 18f

    Box(
        modifier = modifier
            .size(50.dp)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.matchParentSize()) {
            val d = size.minDimension
            drawCircle(
                color = accent.copy(alpha = if (enabled) 0.13f * alpha else 0.05f),
                radius = d * 0.49f,
            )
            drawCircle(
                color = accent.copy(alpha = if (enabled) 0.58f * alpha else 0.20f),
                radius = d * 0.42f,
                style = Stroke(width = 1.4.dp.toPx()),
            )
            drawArc(
                color = accent.copy(alpha = if (enabled) 0.98f * alpha else 0.28f),
                startAngle = angle,
                sweepAngle = 108f,
                useCenter = false,
                topLeft = Offset(d * 0.08f, d * 0.08f),
                size = Size(d * 0.84f, d * 0.84f),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
            )
            drawArc(
                color = Color.White.copy(alpha = if (enabled) 0.48f * alpha else 0.10f),
                startAngle = angle + 205f,
                sweepAngle = 34f,
                useCenter = false,
                topLeft = Offset(d * 0.17f, d * 0.17f),
                size = Size(d * 0.66f, d * 0.66f),
                style = Stroke(width = 1.dp.toPx(), cap = StrokeCap.Round),
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.Send,
            contentDescription = contentDescription,
            tint = if (enabled) accent else accent.copy(alpha = 0.42f),
            modifier = Modifier.size(22.dp),
        )
    }
}
