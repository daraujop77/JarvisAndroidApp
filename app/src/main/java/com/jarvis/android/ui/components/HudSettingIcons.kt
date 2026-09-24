package com.jarvis.android.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 100% custom holographic HUD vector icons for the Jarvis Settings and Telemetry panels.
 * Rendered natively via Canvas draw calls without standard Material design glyphs.
 */

@Composable
fun HudLanguageIcon(
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val c = Offset(w / 2f, h / 2f)
        val stroke = 1.35.dp.toPx()

        // Outer globe perimeter
        drawCircle(
            color = tint,
            radius = w * 0.44f,
            style = Stroke(width = stroke),
        )

        // Equator line
        drawLine(
            color = tint.copy(alpha = 0.8f),
            start = Offset(w * 0.08f, c.y),
            end = Offset(w * 0.92f, c.y),
            strokeWidth = stroke * 0.8f,
        )

        // Vertical meridian ellipse
        drawOval(
            color = tint.copy(alpha = 0.85f),
            topLeft = Offset(w * 0.28f, h * 0.08f),
            size = Size(w * 0.44f, h * 0.84f),
            style = Stroke(width = stroke * 0.85f),
        )

        // Orbit locator node
        drawCircle(
            color = Color.White.copy(alpha = 0.9f),
            radius = 1.4.dp.toPx(),
            center = Offset(w * 0.82f, h * 0.28f),
        )
    }
}

@Composable
fun HudIdentityIcon(
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val c = Offset(w / 2f, h / 2f)
        val stroke = 1.35.dp.toPx()
        val corner = w * 0.22f

        // Top-left reticle bracket
        val tl = Path().apply {
            moveTo(w * 0.08f, h * 0.08f + corner)
            lineTo(w * 0.08f, h * 0.08f)
            lineTo(w * 0.08f + corner, h * 0.08f)
        }
        drawPath(tl, tint, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))

        // Top-right reticle bracket
        val tr = Path().apply {
            moveTo(w * 0.92f - corner, h * 0.08f)
            lineTo(w * 0.92f, h * 0.08f)
            lineTo(w * 0.92f, h * 0.08f + corner)
        }
        drawPath(tr, tint, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))

        // Bottom-left reticle bracket
        val bl = Path().apply {
            moveTo(w * 0.08f, h * 0.92f - corner)
            lineTo(w * 0.08f, h * 0.92f)
            lineTo(w * 0.08f + corner, h * 0.92f)
        }
        drawPath(bl, tint, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))

        // Bottom-right reticle bracket
        val br = Path().apply {
            moveTo(w * 0.92f - corner, h * 0.92f)
            lineTo(w * 0.92f, h * 0.92f)
            lineTo(w * 0.92f, h * 0.92f - corner)
        }
        drawPath(br, tint, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))

        // Central biometric user node
        drawCircle(
            color = tint,
            radius = w * 0.16f,
            center = Offset(c.x, h * 0.40f),
            style = Stroke(stroke),
        )
        // Shoulder arc
        drawArc(
            color = tint,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(w * 0.22f, h * 0.58f),
            size = Size(w * 0.56f, h * 0.44f),
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
    }
}

@Composable
fun HudSecurityIcon(
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val c = Offset(w / 2f, h / 2f)
        val stroke = 1.35.dp.toPx()

        // Cybernetic shield perimeter
        val shield = Path().apply {
            moveTo(c.x, h * 0.08f)
            lineTo(w * 0.88f, h * 0.22f)
            lineTo(w * 0.88f, h * 0.54f)
            cubicTo(w * 0.88f, h * 0.78f, c.x, h * 0.94f, c.x, h * 0.94f)
            cubicTo(c.x, h * 0.94f, w * 0.12f, h * 0.78f, w * 0.12f, h * 0.54f)
            lineTo(w * 0.12f, h * 0.22f)
            close()
        }
        drawPath(shield, tint, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))

        // Inner security core node
        drawCircle(
            color = tint,
            radius = w * 0.12f,
            center = Offset(c.x, h * 0.46f),
            style = Stroke(stroke * 0.85f),
        )
        // Keyhole pin slot
        drawLine(
            color = tint,
            start = Offset(c.x, h * 0.52f),
            end = Offset(c.x, h * 0.68f),
            strokeWidth = stroke * 1.1f,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
fun HudBubbleIcon(
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val c = Offset(w / 2f, h / 2f)
        val stroke = 1.35.dp.toPx()

        // Primary core brain orb
        drawCircle(
            color = tint,
            radius = w * 0.30f,
            center = c,
            style = Stroke(stroke),
        )
        // Center nucleus
        drawCircle(
            color = tint.copy(alpha = 0.8f),
            radius = w * 0.12f,
            center = c,
        )

        // Detached floating satellite ring (top-right aura)
        drawArc(
            color = tint,
            startAngle = 270f,
            sweepAngle = 100f,
            useCenter = false,
            topLeft = Offset(w * 0.10f, h * 0.10f),
            size = Size(w * 0.80f, h * 0.80f),
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
        drawArc(
            color = tint.copy(alpha = 0.6f),
            startAngle = 110f,
            sweepAngle = 80f,
            useCenter = false,
            topLeft = Offset(w * 0.10f, h * 0.10f),
            size = Size(w * 0.80f, h * 0.80f),
            style = Stroke(stroke * 0.8f, cap = StrokeCap.Round),
        )

        // Outward floating vector ray
        drawLine(
            color = Color.White.copy(alpha = 0.9f),
            start = Offset(w * 0.68f, h * 0.32f),
            end = Offset(w * 0.88f, h * 0.12f),
            strokeWidth = stroke * 0.9f,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
fun HudAppearanceIcon(
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val c = Offset(w / 2f, h / 2f)
        val stroke = 1.35.dp.toPx()

        // Central holographic prism
        val prism = Path().apply {
            moveTo(c.x, h * 0.14f)
            lineTo(w * 0.86f, h * 0.78f)
            lineTo(w * 0.14f, h * 0.78f)
            close()
        }
        drawPath(prism, tint, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))

        // Spectrum light ray entering from left
        drawLine(
            color = tint.copy(alpha = 0.6f),
            start = Offset(w * 0.05f, h * 0.52f),
            end = Offset(w * 0.38f, h * 0.48f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )

        // Refracted rays emanating to the right
        drawLine(
            color = Color.White.copy(alpha = 0.85f),
            start = Offset(w * 0.62f, h * 0.48f),
            end = Offset(w * 0.95f, h * 0.38f),
            strokeWidth = stroke * 0.9f,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.64f, h * 0.54f),
            end = Offset(w * 0.95f, h * 0.58f),
            strokeWidth = stroke * 0.9f,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
fun HudUsageIcon(
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val c = Offset(w / 2f, h * 0.55f)
        val stroke = 1.35.dp.toPx()

        // Circular gauge perimeter (270 degree sweep)
        drawArc(
            color = tint,
            startAngle = 135f,
            sweepAngle = 270f,
            useCenter = false,
            topLeft = Offset(w * 0.12f, h * 0.12f),
            size = Size(w * 0.76f, h * 0.76f),
            style = Stroke(stroke, cap = StrokeCap.Round),
        )

        // Needle pointing to ~65% position (top-right)
        drawLine(
            color = Color.White.copy(alpha = 0.95f),
            start = c,
            end = Offset(w * 0.72f, h * 0.30f),
            strokeWidth = stroke * 1.1f,
            cap = StrokeCap.Round,
        )
        // Center hub pivot
        drawCircle(
            color = tint,
            radius = w * 0.10f,
            center = c,
        )

        // Gauge tick marks
        drawLine(
            color = tint.copy(alpha = 0.65f),
            start = Offset(w * 0.24f, h * 0.72f),
            end = Offset(w * 0.30f, h * 0.66f),
            strokeWidth = stroke * 0.8f,
        )
        drawLine(
            color = tint.copy(alpha = 0.65f),
            start = Offset(c.x, h * 0.14f),
            end = Offset(c.x, h * 0.22f),
            strokeWidth = stroke * 0.8f,
        )
    }
}

@Composable
fun HudUpdateIcon(
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val c = Offset(w / 2f, h / 2f)
        val stroke = 1.35.dp.toPx()

        // Downward quantum download arrow
        drawLine(
            color = tint,
            start = Offset(c.x, h * 0.10f),
            end = Offset(c.x, h * 0.62f),
            strokeWidth = stroke * 1.1f,
            cap = StrokeCap.Round,
        )
        val arrow = Path().apply {
            moveTo(w * 0.26f, h * 0.44f)
            lineTo(c.x, h * 0.66f)
            lineTo(w * 0.74f, h * 0.44f)
        }
        drawPath(arrow, tint, style = Stroke(stroke * 1.1f, cap = StrokeCap.Round, join = StrokeJoin.Round))

        // Horizontal telemetry receiver platform pulses
        drawLine(
            color = tint.copy(alpha = 0.9f),
            start = Offset(w * 0.14f, h * 0.82f),
            end = Offset(w * 0.86f, h * 0.82f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = tint.copy(alpha = 0.45f),
            start = Offset(w * 0.28f, h * 0.94f),
            end = Offset(w * 0.72f, h * 0.94f),
            strokeWidth = stroke * 0.8f,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
fun HudConnectionIcon(
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val c = Offset(w / 2f, h / 2f)
        val stroke = 1.35.dp.toPx()

        // Radiating radar/wave arcs
        drawArc(
            color = tint,
            startAngle = 225f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(w * 0.10f, h * 0.08f),
            size = Size(w * 0.80f, h * 0.80f),
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
        drawArc(
            color = tint.copy(alpha = 0.75f),
            startAngle = 230f,
            sweepAngle = 80f,
            useCenter = false,
            topLeft = Offset(w * 0.24f, h * 0.22f),
            size = Size(w * 0.52f, h * 0.52f),
            style = Stroke(stroke * 0.9f, cap = StrokeCap.Round),
        )
        drawArc(
            color = tint.copy(alpha = 0.5f),
            startAngle = 235f,
            sweepAngle = 70f,
            useCenter = false,
            topLeft = Offset(w * 0.36f, h * 0.34f),
            size = Size(w * 0.28f, h * 0.28f),
            style = Stroke(stroke * 0.8f, cap = StrokeCap.Round),
        )

        // Beacon transmitter node
        drawCircle(
            color = Color.White.copy(alpha = 0.95f),
            radius = 1.8.dp.toPx(),
            center = Offset(c.x, h * 0.74f),
        )
        // Transmitter base
        val base = Path().apply {
            moveTo(w * 0.34f, h * 0.92f)
            lineTo(c.x, h * 0.74f)
            lineTo(w * 0.66f, h * 0.92f)
        }
        drawPath(base, tint, style = Stroke(stroke * 0.8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun HudSimulationIcon(
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val c = Offset(w / 2f, h / 2f)
        val stroke = 1.35.dp.toPx()

        // Center quantum core
        drawCircle(
            color = Color.White.copy(alpha = 0.95f),
            radius = w * 0.12f,
            center = c,
        )

        // First diagonal orbital ring
        val path1 = Path().apply {
            addOval(androidx.compose.ui.geometry.Rect(w * 0.12f, h * 0.32f, w * 0.88f, h * 0.68f))
        }
        rotate(45f, c) {
            drawPath(path1, tint, style = Stroke(stroke, cap = StrokeCap.Round))
        }

        // Second diagonal orbital ring
        rotate(-45f, c) {
            drawPath(path1, tint.copy(alpha = 0.75f), style = Stroke(stroke * 0.85f, cap = StrokeCap.Round))
        }
    }
}

@Composable
fun HudDiagnosticsIcon(
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val c = Offset(w / 2f, h / 2f)
        val stroke = 1.35.dp.toPx()

        // Micro-processor square package
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.22f, h * 0.22f),
            size = Size(w * 0.56f, h * 0.56f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
            style = Stroke(stroke),
        )

        // Central silicon die
        drawCircle(
            color = Color.White.copy(alpha = 0.9f),
            radius = w * 0.11f,
            center = c,
        )

        // Terminal pin leads (top, bottom, left, right)
        val pinLen = w * 0.14f
        // Top pins
        drawLine(tint, Offset(w * 0.36f, h * 0.08f), Offset(w * 0.36f, h * 0.22f), stroke * 0.85f)
        drawLine(tint, Offset(w * 0.64f, h * 0.08f), Offset(w * 0.64f, h * 0.22f), stroke * 0.85f)
        // Bottom pins
        drawLine(tint, Offset(w * 0.36f, h * 0.78f), Offset(w * 0.36f, h * 0.92f), stroke * 0.85f)
        drawLine(tint, Offset(w * 0.64f, h * 0.78f), Offset(w * 0.64f, h * 0.92f), stroke * 0.85f)
        // Left pins
        drawLine(tint, Offset(w * 0.08f, h * 0.36f), Offset(w * 0.22f, h * 0.36f), stroke * 0.85f)
        drawLine(tint, Offset(w * 0.08f, h * 0.64f), Offset(w * 0.22f, h * 0.64f), stroke * 0.85f)
        // Right pins
        drawLine(tint, Offset(w * 0.78f, h * 0.36f), Offset(w * 0.92f, h * 0.36f), stroke * 0.85f)
        drawLine(tint, Offset(w * 0.78f, h * 0.64f), Offset(w * 0.92f, h * 0.64f), stroke * 0.85f)
    }
}
