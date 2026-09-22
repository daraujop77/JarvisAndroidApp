package com.jarvis.android.ui.components

import androidx.compose.ui.graphics.Color
import com.jarvis.android.ui.components.MarkdownBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Inline chat styling only. Fenced samples must stay literal. */
class InlineMarkdownTest {

    private val cyan = Color(0xFF22D3EE)

    @Test
    fun plainTextIsUnchanged() {
        assertEquals("hello", renderInlineMarkdown("hello", cyan).text)
    }

    @Test
    fun boldItalicAndCodeDropTheirMarkers() {
        val rendered = renderInlineMarkdown("say **stop** and *wait* then `run`", cyan)
        assertEquals("say stop and wait then run", rendered.text)
        assertTrue(rendered.spanStyles.isNotEmpty())
    }

    @Test
    fun fencedBlockKeepsMarkersThatWouldOtherwiseStyle() {
        val source = "before\n```\n**not bold** and `not code`\n```\nafter **is bold**"
        val rendered = renderInlineMarkdown(source, cyan)
        assertTrue(rendered.text.contains("**not bold**"))
        assertTrue(rendered.text.contains("`not code`"))
        assertTrue(rendered.text.endsWith("after is bold"))
    }

    @Test
    fun unclosedMarkerStaysLiteral() {
        assertEquals("a **b", renderInlineMarkdown("a **b", cyan).text)
    }

    @Test
    fun headingsBulletsAndCodeBecomeTheirOwnBlocks() {
        val blocks = markdownBlocks("# Title\n\n- one\n\n```\ncode\n```\n\nafter")
        assertTrue(blocks[0] is MarkdownBlock.Heading)
        assertEquals(1, (blocks[0] as MarkdownBlock.Heading).level)
        assertTrue(blocks[1] is MarkdownBlock.Bullet)
        assertEquals("code", (blocks[2] as MarkdownBlock.Code).text)
        assertTrue(blocks[3] is MarkdownBlock.Paragraph)
    }

    @Test
    fun anUnclosedFenceDoesNotSwallowTheRest() {
        val blocks = markdownBlocks("```\ncode without an end\nstill text")
        assertTrue(blocks.single() is MarkdownBlock.Paragraph)
    }
}
