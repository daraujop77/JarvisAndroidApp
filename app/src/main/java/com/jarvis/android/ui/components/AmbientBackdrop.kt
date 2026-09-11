package com.jarvis.android.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.jarvis.android.ui.theme.LocalJarvisAccents
import com.jarvis.android.ui.theme.LocalReducedMotion
import kotlin.math.cos
import kotlin.math.sin

/**
 * Ambient app background: gradient wash plus two slow-drifting light blooms.
 *
 * Drawn with [drawBehind] so the animation stays in the draw phase and never
 * recomposes children. Blooms are skipped entirely under reduced motion.
 */
@Composable
fun AmbientBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxWithConstraintsScope.() -> Unit,
) {
    val accents = LocalJarvisAccents.current
    val reduced = LocalReducedMotion.current
    val transition = rememberInfiniteTransition(label = "backdrop")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(26_000, easing = LinearEasing), RepeatMode.Restart),
        label = "drift",
    )
    val phase = if (reduced) 0f else drift

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(accents.backdrop)
            .drawBehind {
                if (reduced) return@drawBehind
                val w = size.width
                val h = size.height
                val bloom = { cx: Float, cy: Float, radius: Float, tint: Color ->
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(tint, Color.Transparent),
                            center = Offset(cx, cy),
                            radius = radius,
                        ),
                        radius = radius,
                        center = Offset(cx, cy),
                    )
                }
                bloom(
                    w * (0.22f + 0.10f * cos(phase)),
                    h * (0.16f + 0.05f * sin(phase)),
                    w * 0.75f,
                    accents.orbGlow.copy(alpha = 0.10f),
                )
                bloom(
                    w * (0.82f + 0.08f * sin(phase * 0.8f)),
                    h * (0.80f + 0.06f * cos(phase * 0.6f)),
                    w * 0.62f,
                    accents.orbGlow.copy(alpha = 0.06f),
                )
            },
        content = content,
    )
}
