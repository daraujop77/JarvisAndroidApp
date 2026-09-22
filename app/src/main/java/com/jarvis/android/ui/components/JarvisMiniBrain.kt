package com.jarvis.android.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jarvis.android.ui.theme.LocalReducedMotion
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Compact body of the current JARVIS brain.
 *
 * It is intentionally not a different logo and never falls back to the legacy
 * orb. At small sizes the full neural lattice becomes visual noise, so this
 * body keeps the same cyan core, instrument ticks and three irrational moving
 * arcs while omitting the dense lattice.
 */
@Composable
fun JarvisMiniBrain(
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    activity: OrbActivity = OrbActivity.IDLE,
    intensity: Float = 0f,
    contentDescription: String? = null,
) {
    val reduced = LocalReducedMotion.current
    val clock = remember { mutableFloatStateOf(0f) }

    LaunchedEffect(activity, reduced) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1_000_000_000.0).toFloat().coerceAtMost(0.05f)
                last = now
                if (!reduced) {
                    val tempo = when (activity) {
                        OrbActivity.IDLE -> 0.55f
                        OrbActivity.LISTENING -> 0.90f
                        OrbActivity.THINKING -> 1.70f
                        OrbActivity.OFFLINE -> 0.12f
                    }
                    clock.floatValue += dt * (0.25f + tempo)
                }
            }
        }
    }

    val energy = when (activity) {
        OrbActivity.IDLE -> 0.28f
        OrbActivity.LISTENING -> 0.55f
        OrbActivity.THINKING -> 1.0f
        OrbActivity.OFFLINE -> 0.05f
    }.let { (it + intensity.coerceIn(0f, 1f) * 0.18f).coerceAtMost(1f) }

    val semantics = if (contentDescription != null) {
        modifier.semantics { this.contentDescription = contentDescription }
    } else modifier

    Canvas(semantics.size(size)) {
        val t = clock.floatValue.toDouble()
        val c = Offset(this.size.width / 2f, this.size.height / 2f)
        val r = this.size.minDimension / 2f
        val dim = if (activity == OrbActivity.OFFLINE) 0.38f else 1f
        val cyan = Color(0xFF3FD3EC)
        val pale = Color(0xFFA9ECF7)
        val deep = Color(0xFF0D5F74)

        drawCircle(
            brush = Brush.radialGradient(
                listOf(cyan.copy(alpha = (0.14f + energy * 0.14f) * dim), Color.Transparent),
                center = c,
                radius = r,
            ),
            radius = r,
            center = c,
        )

        // Instrument ticks: same machine language as the full brain.
        rotate((-t * 8.0).toFloat(), c) {
            repeat(12) { i ->
                val a = Math.PI * 2.0 * i / 12.0
                val inner = r * 0.78f
                val outer = r * if (i % 3 == 0) 0.92f else 0.87f
                drawLine(
                    color = pale.copy(alpha = (0.22f + energy * 0.34f) * dim),
                    start = Offset(c.x + cos(a).toFloat() * inner, c.y + sin(a).toFloat() * inner),
                    end = Offset(c.x + cos(a).toFloat() * outer, c.y + sin(a).toFloat() * outer),
                    strokeWidth = r * if (i % 3 == 0) 0.025f else 0.014f,
                    cap = StrokeCap.Round,
                )
            }
        }

        // Three irrational rotations. No loop point, no legacy spinner.
        val angles = floatArrayOf(
            Math.toDegrees(t * 0.37).toFloat(),
            Math.toDegrees(t * 0.37 * sqrt(2.0)).toFloat(),
            Math.toDegrees(t * 0.37 * Math.PI / 2.0).toFloat(),
        )
        val radii = floatArrayOf(0.72f, 0.58f, 0.43f)
        val sweeps = floatArrayOf(155f, 112f, 82f)
        angles.forEachIndexed { i, angle ->
            rotate(angle * if (i == 1) -1f else 1f, c) {
                val rr = r * radii[i]
                drawArc(
                    color = cyan.copy(alpha = (0.32f + energy * 0.55f) * dim),
                    startAngle = 205f + i * 41f,
                    sweepAngle = sweeps[i],
                    useCenter = false,
                    topLeft = Offset(c.x - rr, c.y - rr),
                    size = androidx.compose.ui.geometry.Size(rr * 2f, rr * 2f),
                    style = Stroke(width = r * (0.045f - i * 0.006f), cap = StrokeCap.Round),
                )
            }
        }

        val pulse = (1f + sin(t * 1.7).toFloat() * 0.035f * (0.5f + energy))
        val coreR = r * 0.27f * pulse
        drawCircle(
            brush = Brush.radialGradient(
                listOf(
                    Color.White.copy(alpha = (0.82f + energy * 0.12f) * dim),
                    pale.copy(alpha = (0.62f + energy * 0.22f) * dim),
                    deep.copy(alpha = 0.10f * dim),
                    Color.Transparent,
                ),
                center = c,
                radius = coreR * 2.2f,
            ),
            radius = coreR * 2.2f,
            center = c,
        )
        drawCircle(
            color = pale.copy(alpha = (0.42f + energy * 0.42f) * dim),
            radius = r * 0.76f,
            center = c,
            style = Stroke(width = r * 0.012f),
        )
    }
}
