package com.jarvis.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jarvis.android.ui.theme.LocalJarvisAccents

/**
 * One bounded Markdown renderer shared by Chat Global and Writing Room.
 * No dynamic HTML, web loading, automatic links or model-provided actions.
 * Sources, authenticated media and document actions render separately.
 */
@Composable
fun SharedChatMarkdown(text: String, modifier: Modifier = Modifier) {
    val accent = LocalJarvisAccents.current.orbGlow
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        markdownBlocks(text.replace("\r\n", "\n")).forEach { block ->
            when (block) {
                is MarkdownBlock.Heading -> Text(
                    renderInlineMarkdown(block.text, accent),
                    style = if (block.level == 1) MaterialTheme.typography.titleMedium
                            else MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                is MarkdownBlock.Bullet -> Row {
                    Text("•  ", color = accent)
                    Text(renderInlineMarkdown(block.text, accent),
                         style = MaterialTheme.typography.bodyMedium,
                         modifier = Modifier.weight(1f))
                }
                is MarkdownBlock.Numbered -> Row {
                    Text(block.number + ".  ", color = accent)
                    Text(renderInlineMarkdown(block.text, accent),
                         style = MaterialTheme.typography.bodyMedium,
                         modifier = Modifier.weight(1f))
                }
                is MarkdownBlock.Blockquote -> Row {
                    Box(Modifier.width(3.dp).height(24.dp)
                        .background(accent, RoundedCornerShape(2.dp)))
                    Spacer(Modifier.width(10.dp))
                    Text(renderInlineMarkdown(block.text, accent),
                         style = MaterialTheme.typography.bodyMedium.copy(
                             fontStyle = FontStyle.Italic),
                         color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                is MarkdownBlock.Code -> Column(
                    modifier = Modifier.fillMaxWidth().background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        RoundedCornerShape(10.dp))
                        .padding(12.dp),
                ) {
                    block.language?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall,
                             color = accent)
                        Spacer(Modifier.height(5.dp))
                    }
                    Text(block.text, fontFamily = FontFamily.Monospace,
                         style = MaterialTheme.typography.bodySmall,
                         color = MaterialTheme.colorScheme.onSurface)
                }
                is MarkdownBlock.Paragraph -> Text(
                    renderInlineMarkdown(block.text, accent),
                    style = MaterialTheme.typography.bodyMedium,
                )
                is MarkdownBlock.Table -> Column(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(
                            alpha = 0.25f), RoundedCornerShape(8.dp))
                        .padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    val entries = listOf(block.headers) + block.rows
                    entries.forEachIndexed { index, cells ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            cells.forEach { cell ->
                                Text(renderInlineMarkdown(cell, accent),
                                     style = MaterialTheme.typography.bodySmall,
                                     fontWeight = if (index == 0)
                                         FontWeight.SemiBold else FontWeight.Normal,
                                     modifier = Modifier.width(146.dp).padding(5.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
