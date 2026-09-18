package com.jarvis.android.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jarvis.android.ui.home.JarvisVisualState
import com.jarvis.android.ui.home.toOrbActivity
import com.jarvis.android.ui.theme.LocalJarvisAccents
import com.jarvis.android.ui.theme.LocalReducedMotion

/** Visual activity level of the orb; drives speed and intensity. */
enum class OrbActivity { IDLE, LISTENING, THINKING, SPEAKING, PROCESSING, ERROR, OFFLINE }

/**
 * The JARVIS mark: concentric arc-reactor rings that rotate in opposite
 * directions around a breathing core.
 *
 * All animation happens inside the draw phase (Canvas reads the animated
 * floats), so the surrounding UI never recomposes because of the orb.
 * Honors [LocalReducedMotion] by freezing rotation and pulse.
 */
@Composable
fun JarvisOrb(
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    activity: OrbActivity = OrbActivity.IDLE,
    visualState: JarvisVisualState? = null,
    /** 0f..1f — extra intensity, e.g. streaming progress or audio level. */
    intensity: Float = 0f,
    /**
     * Optional TalkBack label. The orb is a Canvas (no semantics), so where it is
     * the *only* status affordance (e.g. a thinking avatar with empty text) callers
     * pass a description. Leave null on decorative orbs to avoid announcement noise.
     */
    contentDescription: String? = null,
) {
    val accents = LocalJarvisAccents.current
    val reduced = LocalReducedMotion.current
    val transition = rememberInfiniteTransition(label = "orb")
    val resolvedActivity = visualState?.toOrbActivity() ?: activity

    val speed = when (resolvedActivity) {
        OrbActivity.IDLE -> 1f
        OrbActivity.LISTENING -> 1.8f
        OrbActivity.PROCESSING -> 2.2f
        OrbActivity.THINKING -> 2.6f
        OrbActivity.SPEAKING -> 2.4f
        OrbActivity.ERROR -> 0.6f
        OrbActivity.OFFLINE -> 0.45f
    }

    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            tween((9000 / speed).toInt(), easing = LinearEasing),
            RepeatMode.Restart,
        ),
        label = "spin",
    )
    val counterSpin by transition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            tween((14000 / speed).toInt(), easing = LinearEasing),
            RepeatMode.Restart,
        ),
        label = "counterSpin",
    )
    val pulse by transition.animateFloat(
        initialValue = 0.86f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            tween((1800 / speed).toInt(), easing = LinearEasing),
            RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    val tint = when (resolvedActivity) {
        OrbActivity.OFFLINE -> accents.offline
        OrbActivity.ERROR -> androidx.compose.material3.MaterialTheme.colorScheme.error
        else -> accents.orbGlow
    }
    val liveSpin = if (reduced) 0f else spin
    val liveCounter = if (reduced) 0f else counterSpin
    val livePulse = if (reduced) 1f else pulse

    val orbModifier = if (contentDescription != null) {
        modifier.semantics { this.contentDescription = contentDescription }
    } else {
        modifier
    }

    Canvas(modifier = orbModifier.size(size)) {
        val c = Offset(this.size.width / 2f, this.size.height / 2f)
        val r = this.size.minDimension / 2f
        val boost = 1f + intensity * 0.25f

        // Outer halo
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(tint.copy(alpha = 0.30f * boost), Color.Transparent),
                center = c,
                radius = r,
            ),
            radius = r,
            center = c,
        )

        // Outer ring: broken arcs, clockwise
        rotate(liveSpin, c) {
            listOf(0f to 70f, 100f to 55f, 185f to 80f, 290f to 40f).forEach { (start, sweep) ->
                drawArc(
                    color = tint.copy(alpha = 0.85f),
                    startAngle = start,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(c.x - r * 0.88f, c.y - r * 0.88f),
                    size = Size(r * 1.76f, r * 1.76f),
                    style = Stroke(width = r * 0.055f),
                )
            }
        }

        // Mid ring: counter-clockwise, thinner + dimmer
        rotate(liveCounter, c) {
            listOf(20f to 120f, 175f to 95f, 300f to 45f).forEach { (start, sweep) ->
                drawArc(
                    color = tint.copy(alpha = 0.55f),
                    startAngle = start,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(c.x - r * 0.66f, c.y - r * 0.66f),
                    size = Size(r * 1.32f, r * 1.32f),
                    style = Stroke(width = r * 0.035f),
                )
            }
        }

        // Inner static ring
        drawCircle(
            color = tint.copy(alpha = 0.35f),
            radius = r * 0.44f,
            center = c,
            style = Stroke(width = r * 0.02f),
        )

        // Breathing core
        val coreR = r * 0.26f * livePulse * boost
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = 0.95f), tint, tint.copy(alpha = 0f)),
                center = c,
                radius = coreR * 2.2f,
            ),
            radius = coreR * 2.2f,
            center = c,
        )
        drawCircle(color = Color.White.copy(alpha = 0.9f), radius = coreR * 0.5f, center = c)
    }
}
