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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
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
        // While a reply is still arriving the markers are incomplete, so the
        // raw text stays. A finished reply is styled. Fenced code is never
        // touched, so a sample that mentions **bold** stays literal.
        if (streaming) append(text) else append(renderInlineMarkdown(text, accents.orbGlow))
        if (show) {
            withStyle(SpanStyle(color = accents.orbGlow)) { append("▌") }
        }
    }
}

/** One line of a reply, after the block structure has been decided. */
internal sealed interface MarkdownBlock {
    data class Paragraph(val text: String) : MarkdownBlock
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class Bullet(val text: String) : MarkdownBlock
    data class Numbered(val number: String, val text: String) : MarkdownBlock
    data class Blockquote(val text: String) : MarkdownBlock
    data class Code(val text: String, val language: String? = null) : MarkdownBlock
}

/**
 * Split a finished reply into blocks. Fenced code is taken whole, headings and
 * bullets are single lines, and everything else stays a paragraph so inline
 * styling can run over it. A fence that never closes is left as text: a reply
 * that ends mid-sample must not swallow the rest.
 */
internal fun markdownBlocks(source: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val paragraph = StringBuilder()
    fun flushParagraph() {
        val text = paragraph.toString().trim('\n')
        if (text.isNotEmpty()) blocks += MarkdownBlock.Paragraph(text)
        paragraph.clear()
    }

    var i = 0
    val lines = source.split('\n')
    while (i < lines.size) {
        val line = lines[i]
        if (line.startsWith("```")) {
            val end = (i + 1 until lines.size).firstOrNull { lines[it].startsWith("```") }
            if (end == null) {
                paragraph.append(lines.subList(i, lines.size).joinToString("\n"))
                break
            }
            flushParagraph()
            val language = line.removePrefix("```").trim().takeIf { it.isNotEmpty() }
            blocks += MarkdownBlock.Code(lines.subList(i + 1, end).joinToString("\n"), language = language)
            i = end + 1
            continue
        }
        val heading = Regex("^(#{1,3})\\s+(\\S.*)$").find(line)
        val bullet = Regex("^[-*]\\s+(\\S.*)$").find(line)
        val numbered = Regex("^([0-9]+)\\.\\s+(\\S.*)$").find(line)
        val quote = Regex("^>\\s*(.*)$").find(line)
        when {
            heading != null -> {
                flushParagraph()
                blocks += MarkdownBlock.Heading(heading.groupValues[1].length, heading.groupValues[2])
            }
            quote != null -> {
                flushParagraph()
                blocks += MarkdownBlock.Blockquote(quote.groupValues[1])
            }
            bullet != null -> {
                flushParagraph()
                blocks += MarkdownBlock.Bullet(bullet.groupValues[1])
            }
            numbered != null -> {
                flushParagraph()
                blocks += MarkdownBlock.Numbered(numbered.groupValues[1], numbered.groupValues[2])
            }
            else -> {
                if (paragraph.isNotEmpty()) paragraph.append('\n')
                paragraph.append(line)
            }
        }
        i++
    }
    flushParagraph()
    return blocks
}

/**
 * Inline Markdown: `code`, **bold** and *italic*. Fenced blocks are handled by
 * [markdownBlocks] and never reach here, so a sample that mentions **bold**
 * stays literal. Unclosed markers are left as typed.
 */
internal fun renderInlineMarkdown(source: String, codeColor: Color): AnnotatedString {
    val code = SpanStyle(
        fontFamily = FontFamily.Monospace,
        color = codeColor,
        background = codeColor.copy(alpha = 0.14f),
    )
    val bold = SpanStyle(fontWeight = FontWeight.SemiBold)
    val italic = SpanStyle(fontStyle = FontStyle.Italic)
    return buildAnnotatedString {
        val fence = Regex("```[\\s\\S]*?```")
        var cursor = 0
        for (block in fence.findAll(source)) {
            appendStyled(source.substring(cursor, block.range.first), code, bold, italic)
            withStyle(code) { append(block.value) }
            cursor = block.range.last + 1
        }
        appendStyled(source.substring(cursor), code, bold, italic)
    }
}

private fun AnnotatedString.Builder.appendStyled(
    text: String,
    code: SpanStyle,
    bold: SpanStyle,
    italic: SpanStyle,
) {
    val token = Regex("`[^`\\n]+`|\\*\\*[^*\\n]+\\*\\*|\\*[^*\\n]+\\*")
    var cursor = 0
    for (match in token.findAll(text)) {
        append(text.substring(cursor, match.range.first))
        val raw = match.value
        when {
            raw.startsWith("**") -> withStyle(bold) { append(raw.removeSurrounding("**")) }
            raw.startsWith("`") -> withStyle(code) { append(raw.removeSurrounding("`")) }
            else -> withStyle(italic) { append(raw.removeSurrounding("*")) }
        }
        cursor = match.range.last + 1
    }
    append(text.substring(cursor))
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
