package com.jarvis.android.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.jarvis.android.ui.theme.LocalJarvisAccents
import com.jarvis.android.ui.theme.LocalReducedMotion
import kotlin.math.sin

/**
 * Three-dot "thinking" indicator used before the first delta arrives.
 * Animates in the draw phase only.
 */
@Composable
fun TypingDots(modifier: Modifier = Modifier) {
    val accents = LocalJarvisAccents.current
    val reduced = LocalReducedMotion.current
    val transition = rememberInfiniteTransition(label = "typing")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
        label = "t",
    )
    val phase = if (reduced) 0f else t

    Canvas(modifier.size(width = 34.dp, height = 12.dp)) {
        val r = size.height / 4.5f
        val gap = size.width / 3f
        repeat(3) { i ->
            val lift = if (reduced) 0f else sin(phase - i * 0.7f).coerceAtLeast(0f)
            drawCircle(
                color = accents.orbGlow.copy(alpha = 0.45f + 0.55f * lift),
                radius = r * (0.85f + 0.35f * lift),
                center = Offset(gap * (i + 0.5f), size.height / 2f - lift * r),
            )
        }
    }
}

/**
 * Streaming text plus a blinking caret, so a partial reply reads as live rather
 * than truncated. The caret is part of the same [AnnotatedString] to avoid a
 * second text node reflowing the bubble.
 */
@Composable
fun streamingText(text: String, streaming: Boolean): AnnotatedString {
    val accents = LocalJarvisAccents.current
    val reduced = LocalReducedMotion.current
    val transition = rememberInfiniteTransition(label = "caret")
    val blink by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Restart),
        label = "blink",
    )
    val show = streaming && (reduced || blink < 0.55f)
    return buildAnnotatedString {
        append(text)
        if (show) {
            withStyle(SpanStyle(color = accents.orbGlow)) { append("▌") }
        }
    }
}

/** Thin animated scanline used as a section divider / activity hint. */
@Composable
fun ScanLine(modifier: Modifier = Modifier, active: Boolean = true) {
    val accents = LocalJarvisAccents.current
    val reduced = LocalReducedMotion.current
    val transition = rememberInfiniteTransition(label = "scan")
    val x by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart),
        label = "x",
    )
    Row(modifier.height(2.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.width(1.dp).height(2.dp)) {}
        Canvas(Modifier.height(2.dp).width(4000.dp)) {
            drawLine(
                color = accents.grid,
                start = Offset(0f, size.height / 2),
                end = Offset(size.width, size.height / 2),
                strokeWidth = size.height,
            )
            if (active && !reduced) {
                val cx = size.width * x
                drawLine(
                    color = accents.orbGlow.copy(alpha = 0.8f),
                    start = Offset((cx - 60f).coerceAtLeast(0f), size.height / 2),
                    end = Offset(cx, size.height / 2),
                    strokeWidth = size.height,
                )
            }
        }
    }
}

internal val Transparent = Color.Transparent
