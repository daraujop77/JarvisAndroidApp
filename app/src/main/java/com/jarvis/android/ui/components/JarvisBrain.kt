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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jarvis.android.ui.theme.LocalReducedMotion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * The holographic brain: a 2D projection of the same field the Windows viewer
 * renders in 3D (`windows/jarvis-brain/index.html`). One shared contract,
 * [BrainDrive]; this canvas only eases toward it.
 *
 * What makes it read as thought rather than a spinner:
 *  - three incommensurate angles (1 : sqrt(2) : pi/2), so the pose never repeats
 *  - a neural lattice whose activations propagate two hops and decay slowly,
 *    leaving a visible trail of the signal
 *  - a fresnel rim, scanlines and one sweeping band for the hologram read
 *
 * Colour is one family. Luminance carries the state; violet is a thin accent on
 * the hottest nodes, amber appears only while speaking.
 *
 * Below [HERO_MIN] the lattice cannot be read, so the mark falls back to the
 * concentric arcs. Callers that pass a tiny size (chat avatars) stay crisp.
 *
 * All motion is inside the draw phase. [LocalReducedMotion] freezes the field
 * and holds the lattice at rest.
 */
private val HERO_MIN = 72.dp

private const val NODE_COUNT = 46
private const val NEIGHBOURS = 3

private val Cyan = Color(0xFF3FD3EC)
private val Pale = Color(0xFFA9ECF7)
private val Deep = Color(0xFF0D5F74)
private val Accent = Color(0xFF7C6CF0)
private val Warm = Color(0xFFFFB257)

private data class BrainDrive(
    val energy: Float,
    val focus: Float,
    val tempo: Float,
    val coherence: Float,
    val warmth: Float,
)

private fun OrbActivity.drive(): BrainDrive = when (this) {
    OrbActivity.IDLE -> BrainDrive(0.28f, 0.15f, 0.55f, 0.35f, 0.04f)
    OrbActivity.LISTENING -> BrainDrive(0.55f, 0.75f, 0.90f, 0.66f, 0.10f)
    OrbActivity.THINKING -> BrainDrive(1.00f, 0.18f, 1.70f, 0.06f, 0.08f)
    OrbActivity.OFFLINE -> BrainDrive(0.05f, 0.00f, 0.12f, 0.92f, 0.00f)
}

/** Same sweeps as the original arc mark, so the silhouette stays recognisable. */
private data class ArcSpec(val radius: Float, val start: Float, val sweep: Float, val tilt: Float, val width: Float, val dir: Float)

private val ARCS = listOf(
    ArcSpec(0.86f, 0f, 70f, 66f, 0.030f, 1f),
    ArcSpec(0.86f, 100f, 55f, 66f, 0.030f, 1f),
    ArcSpec(0.86f, 185f, 80f, 66f, 0.030f, 1f),
    ArcSpec(0.86f, 290f, 40f, 66f, 0.030f, 1f),
    ArcSpec(0.66f, 20f, 120f, 108f, 0.020f, -1f),
    ArcSpec(0.66f, 175f, 95f, 108f, 0.020f, -1f),
    ArcSpec(0.66f, 300f, 45f, 108f, 0.020f, -1f),
)

private class BrainField {
    val nodeX = FloatArray(NODE_COUNT)
    val nodeY = FloatArray(NODE_COUNT)
    val nodeZ = FloatArray(NODE_COUNT)
    val act = FloatArray(NODE_COUNT)
    val neighbours = Array(NODE_COUNT) { IntArray(NEIGHBOURS) }
    var fireAcc = 0f

    var energy = 0.28f
    var focus = 0.15f
    var tempo = 0.55f
    var coherence = 0.35f
    var warmth = 0.04f
    var sim = 0.0

    init {
        // Fibonacci sphere: even coverage, no clumps at the poles.
        val golden = PI * (3 - sqrt(5.0))
        for (i in 0 until NODE_COUNT) {
            val y = 1.0 - (i.toDouble() / (NODE_COUNT - 1)) * 2.0
            val rad = sqrt((1.0 - y * y).coerceAtLeast(0.0))
            val theta = golden * i
            val r = 0.62 + (i % 3) * 0.05
            nodeX[i] = (cos(theta) * rad * r).toFloat()
            nodeY[i] = (y * r).toFloat()
            nodeZ[i] = (sin(theta) * rad * r).toFloat()
        }
        val dist = FloatArray(NODE_COUNT)
        for (i in 0 until NODE_COUNT) {
            for (j in 0 until NODE_COUNT) {
                if (i == j) { dist[j] = Float.MAX_VALUE; continue }
                val dx = nodeX[i] - nodeX[j]
                val dy = nodeY[i] - nodeY[j]
                val dz = nodeZ[i] - nodeZ[j]
                dist[j] = dx * dx + dy * dy + dz * dz
            }
            val order = (0 until NODE_COUNT).sortedBy { dist[it] }
            for (k in 0 until NEIGHBOURS) neighbours[i][k] = order[k]
        }
    }

    /** Advance the field. [dt] is seconds; pass 0 to hold the pose. */
    fun step(dt: Float, target: BrainDrive) {
        val k = if (dt <= 0f) 1f else (1.0 - exp(-dt * 2.4)).toFloat()
        energy += (target.energy - energy) * k
        focus += (target.focus - focus) * k
        tempo += (target.tempo - tempo) * k
        coherence += (target.coherence - coherence) * k
        warmth += (target.warmth - warmth) * k
        if (dt <= 0f) return

        sim += dt * (0.25 + tempo)

        val decay = exp(-dt * (0.75 + tempo * 0.5)).toFloat()
        for (i in 0 until NODE_COUNT) act[i] *= decay
        fireAcc += dt * (2f + energy * 26f)
        while (fireAcc >= 1f) {
            fireAcc -= 1f
            val seed = Random.nextInt(NODE_COUNT)
            act[seed] = 1f
            for (nb in neighbours[seed]) {
                if (act[nb] < 0.7f) act[nb] = 0.7f
                for (nb2 in neighbours[nb]) if (act[nb2] < 0.34f) act[nb2] = 0.34f
            }
        }
    }
}

@Composable
fun JarvisBrain(
    modifier: Modifier = Modifier,
    size: Dp = 168.dp,
    activity: OrbActivity = OrbActivity.IDLE,
    /** 0f..1f extra intensity, e.g. streaming progress. Folds into energy. */
    intensity: Float = 0f,
    contentDescription: String? = null,
) {
    if (size < HERO_MIN) {
        JarvisMiniBrain(modifier, size, activity, intensity, contentDescription)
        return
    }

    val reduced = LocalReducedMotion.current
    val field = remember { BrainField() }
    val clock = remember { mutableFloatStateOf(0f) }

    LaunchedEffect(activity, reduced) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1_000_000_000.0).toFloat().coerceAtMost(0.05f)
                last = now
                val base = activity.drive()
                val boosted = base.copy(energy = (base.energy + intensity * 0.25f).coerceAtMost(1f))
                field.step(if (reduced) 0f else dt, boosted)
                clock.floatValue = field.sim.toFloat()
            }
        }
    }

    val brainModifier = if (contentDescription != null) {
        modifier.semantics { this.contentDescription = contentDescription }
    } else modifier

    Canvas(brainModifier.size(size)) {
        // read the clock so this lambda re-runs each frame without recomposing
        // anything outside the canvas
        @Suppress("UNUSED_VARIABLE")
        val frame = clock.floatValue
        drawBrain(field)
    }
}

private fun DrawScope.drawBrain(f: BrainField) {
    val c = Offset(size.width / 2f, size.height / 2f)
    val r = size.minDimension / 2f
    val dim = if (f.energy < 0.1f) 0.4f else 1f

    val a = f.sim * 0.37
    val b = f.sim * 0.37 * sqrt(2.0)
    val cc = f.sim * 0.37 * PI * 0.5

    // outer halo — one colour, intensity follows energy
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Cyan.copy(alpha = 0.16f * (0.4f + f.energy) * dim), Color.Transparent),
            center = c, radius = r,
        ),
        radius = r, center = c,
    )

    drawTicks(c, r, a, f)
    drawArcs(c, r, a, b, f, dim)
    drawLattice(c, r, a, b, f, dim)
    drawCore(c, r, a, b, cc, f, dim)
    drawHologramRim(c, r, f, dim)
}

private fun DrawScope.drawTicks(c: Offset, r: Float, a: Double, f: BrainField) {
    val alpha = (0.16f + f.focus * 0.42f)
    rotate(Math.toDegrees(-a * 0.5).toFloat(), c) {
        for (i in 0 until 48) {
            val t = (i / 48.0) * 2.0 * PI
            val long = i % 8 == 0
            val inner = r * 0.90f
            val outer = inner + r * (if (long) 0.075f else 0.034f)
            drawLine(
                color = Pale.copy(alpha = alpha * if (long) 1f else 0.6f),
                start = Offset(c.x + cos(t).toFloat() * inner, c.y + sin(t).toFloat() * inner),
                end = Offset(c.x + cos(t).toFloat() * outer, c.y + sin(t).toFloat() * outer),
                strokeWidth = if (long) r * 0.012f else r * 0.006f,
                cap = StrokeCap.Round,
            )
        }
    }
}

private fun DrawScope.drawArcs(c: Offset, r: Float, a: Double, b: Double, f: BrainField, dim: Float) {
    ARCS.forEachIndexed { i, spec ->
        val wobble = sin(b * 1.4 + i * 0.8) * (1.0 - f.coherence) * 14.0
        val spin = Math.toDegrees(spec.dir * a * (0.6 + i * 0.05)) + spec.start
        val scaleY = cos(Math.toRadians(spec.tilt.toDouble()) + wobble * (1 - f.focus) * 0.02).toFloat()
            .let { if (it < 0f) -it else it }.coerceIn(0.18f, 1f)
        val alpha = (0.30f + f.energy * 0.6f) * dim
        withTransform({
            translate(c.x, c.y)
            rotate(spin.toFloat(), Offset.Zero)
            scale(1f, scaleY, Offset.Zero)
        }) {
            drawArc(
                color = Cyan.copy(alpha = alpha),
                startAngle = -spec.sweep / 2f,
                sweepAngle = spec.sweep,
                useCenter = false,
                topLeft = Offset(-r * spec.radius, -r * spec.radius),
                size = androidx.compose.ui.geometry.Size(r * spec.radius * 2, r * spec.radius * 2),
                style = Stroke(width = r * spec.width, cap = StrokeCap.Round),
            )
        }
    }
}

private fun DrawScope.drawLattice(c: Offset, r: Float, a: Double, b: Double, f: BrainField, dim: Float) {
    val yaw = a * 0.18
    val pitch = sin(b * 0.3) * 0.2
    val cy = cos(yaw).toFloat(); val sy = sin(yaw).toFloat()
    val cxp = cos(pitch).toFloat(); val sxp = sin(pitch).toFloat()
    val net = (1f - f.focus * 0.12f)

    // project once, reuse for edges and nodes
    val px = FloatArray(NODE_COUNT)
    val py = FloatArray(NODE_COUNT)
    val depth = FloatArray(NODE_COUNT)
    for (i in 0 until NODE_COUNT) {
        var x = f.nodeX[i]; var y = f.nodeY[i]; var z = f.nodeZ[i]
        val x1 = x * cy + z * sy
        val z1 = -x * sy + z * cy
        x = x1; z = z1
        val y1 = y * cxp - z * sxp
        val z2 = y * sxp + z * cxp
        y = y1; z = z2
        px[i] = c.x + x * r * net
        py[i] = c.y + y * r * net * 0.86f
        depth[i] = z
    }

    val drawn = HashSet<Long>()
    for (i in 0 until NODE_COUNT) {
        for (nb in f.neighbours[i]) {
            val key = if (i < nb) i.toLong() shl 32 or nb.toLong() else nb.toLong() shl 32 or i.toLong()
            if (!drawn.add(key)) continue
            val vi = f.act[i]; val vj = f.act[nb]
            val hot = maxOf(vi, vj)
            val alpha = ((0.10f + f.coherence * 0.06f) + hot * 0.8f) * dim
            val col = lerpColor(Deep, Pale, hot)
            drawLine(
                color = col.copy(alpha = alpha.coerceAtMost(1f)),
                start = Offset(px[i], py[i]),
                end = Offset(px[nb], py[nb]),
                strokeWidth = r * (0.004f + hot * 0.010f),
                cap = StrokeCap.Round,
            )
        }
    }

    for (i in 0 until NODE_COUNT) {
        val v = f.act[i]
        var col = lerpColor(Deep, Cyan, v)
        col = lerpColor(col, Pale, v * v)
        if (v > 0.8f) col = lerpColor(col, Accent, (v - 0.8f) * 2f * (1f - f.coherence))
        val radius = r * (0.012f + v * 0.020f) * (0.8f + depth[i] * 0.3f)
        drawCircle(color = col.copy(alpha = (0.45f + v * 0.55f) * dim), radius = radius, center = Offset(px[i], py[i]))
    }
}

private fun DrawScope.drawCore(c: Offset, r: Float, a: Double, b: Double, cc: Double, f: BrainField, dim: Float) {
    val breathe = 1.0 + sin(f.sim * 1.7) * 0.03 * (0.4 + f.energy)
    val coreR = (r * 0.30 * breathe * (1 + f.focus * 0.05)).toFloat()

    // deformed membrane: the same low-frequency sum the 3D core uses, sampled
    // around the silhouette so the outline never sits still
    val path = Path()
    val steps = 64
    for (i in 0..steps) {
        val t = (i.toDouble() / steps) * 2.0 * PI
        val x = cos(t); val y = sin(t)
        val n = sin(x * 3.1 + a) * cos(y * 2.7 + b) * sin(cc)
        val s = 1.0 + n * (0.01 + f.energy * 0.08 * (1 - f.coherence * 0.65))
        val px = c.x + (x * coreR * s).toFloat()
        val py = c.y + (y * coreR * s * 0.94).toFloat()
        if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
    }
    path.close()
    val membrane = lerpColor(Cyan, Warm, f.warmth * 0.4f)
    drawPath(path, brush = Brush.radialGradient(
        listOf(Pale.copy(alpha = 0.85f * dim), membrane.copy(alpha = (0.25f + f.energy * 0.4f) * dim), Color.Transparent),
        center = c, radius = coreR * 1.8f,
    ))

    // inner ember
    val emberR = coreR * (0.34f + f.energy * 0.08f)
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color.White.copy(alpha = 0.9f * dim), lerpColor(Pale, Warm, f.warmth).copy(alpha = 0.5f * dim), Color.Transparent),
            center = c, radius = emberR * 2f,
        ),
        radius = emberR * 2f, center = c,
    )

    // geodesic cage: icosahedron edges projected, rotating against the lattice
    drawCage(c, coreR * 1.34f, -a * 0.35, sin(cc * 0.4) * 0.25, (0.10f + f.coherence * 0.20f) * dim)
}

/** Twelve vertices of an icosahedron, projected and wired by their fixed edges. */
private fun DrawScope.drawCage(c: Offset, radius: Float, yaw: Double, pitch: Double, alpha: Float) {
    val phi = (1.0 + sqrt(5.0)) / 2.0
    val raw = arrayOf(
        doubleArrayOf(-1.0, phi, 0.0), doubleArrayOf(1.0, phi, 0.0), doubleArrayOf(-1.0, -phi, 0.0), doubleArrayOf(1.0, -phi, 0.0),
        doubleArrayOf(0.0, -1.0, phi), doubleArrayOf(0.0, 1.0, phi), doubleArrayOf(0.0, -1.0, -phi), doubleArrayOf(0.0, 1.0, -phi),
        doubleArrayOf(phi, 0.0, -1.0), doubleArrayOf(phi, 0.0, 1.0), doubleArrayOf(-phi, 0.0, -1.0), doubleArrayOf(-phi, 0.0, 1.0),
    )
    val norm = sqrt(1 + phi * phi)
    val cy = cos(yaw); val sy = sin(yaw)
    val cxp = cos(pitch); val sxp = sin(pitch)
    val p = Array(12) { i ->
        var x = raw[i][0] / norm; var y = raw[i][1] / norm; var z = raw[i][2] / norm
        val x1 = x * cy + z * sy; val z1 = -x * sy + z * cy; x = x1; z = z1
        val y1 = y * cxp - z * sxp; y = y1
        Offset(c.x + (x * radius).toFloat(), c.y + (y * radius).toFloat())
    }
    val edges = arrayOf(
        0 to 1, 0 to 5, 0 to 7, 0 to 10, 0 to 11, 1 to 5, 1 to 7, 1 to 8, 1 to 9,
        2 to 3, 2 to 4, 2 to 6, 2 to 10, 2 to 11, 3 to 4, 3 to 6, 3 to 8, 3 to 9,
        4 to 5, 4 to 9, 5 to 11, 6 to 7, 6 to 10, 7 to 8, 8 to 9, 10 to 11,
    )
    // a vertex links to another only when they share an edge length; the list
    // above is that fixed topology, so it never has to be recomputed
    for ((i, j) in edges) {
        drawLine(Cyan.copy(alpha = alpha), p[i], p[j], strokeWidth = radius * 0.012f, cap = StrokeCap.Round)
    }
}

private fun DrawScope.drawHologramRim(c: Offset, r: Float, f: BrainField, dim: Float) {
    val rim = r * 0.95f
    // fresnel analogue in 2D: a bright ring with a soft falloff inward
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color.Transparent, Color.Transparent, Cyan.copy(alpha = (0.22f + f.energy * 0.30f) * dim)),
            center = c, radius = rim,
        ),
        radius = rim, center = c, style = Stroke(width = r * 0.02f),
    )
    drawCircle(
        color = Pale.copy(alpha = (0.35f + f.energy * 0.4f) * dim),
        radius = rim, center = c, style = Stroke(width = r * 0.004f),
    )

    // one scan band travelling upward, clipped to the rim disc
    val band = ((f.sim * 0.11) % 1.0).toFloat()
    val y = c.y + rim * (1f - 2f * band)
    drawLine(
        brush = Brush.horizontalGradient(
            listOf(Color.Transparent, Pale.copy(alpha = 0.55f * dim), Color.Transparent),
            startX = c.x - rim, endX = c.x + rim,
        ),
        start = Offset(c.x - rim, y), end = Offset(c.x + rim, y),
        strokeWidth = r * 0.012f,
    )
}

private fun lerpColor(a: Color, b: Color, t: Float): Color {
    val k = t.coerceIn(0f, 1f)
    return Color(
        red = a.red + (b.red - a.red) * k,
        green = a.green + (b.green - a.green) * k,
        blue = a.blue + (b.blue - a.blue) * k,
        alpha = a.alpha + (b.alpha - a.alpha) * k,
    )
}
