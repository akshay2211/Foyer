package com.foyerlauncher.foyer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/**
 * Renders the small markdown subset foyer's bundled documents use — headings,
 * paragraphs, bullet lists, tables and inline bold / italic / code — in the
 * settings design language. Tables become grouped card rows: first cell as the
 * title, remaining cells joined into the subtitle.
 */
@Composable
fun MarkdownDocument(
    markdown: String,
    skipFirstHeading: Boolean = false,
) {
    val blocks = remember(markdown, skipFirstHeading) { parseMarkdown(markdown, skipFirstHeading) }
    Column {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> MdHeading(block)
                is MdBlock.Paragraph -> MdParagraph(block.text)
                is MdBlock.Bullets -> block.items.forEach { MdParagraph("•  $it") }
                is MdBlock.Table -> MdTable(block.rows)
            }
        }
    }
}

private sealed interface MdBlock {
    data class Heading(
        val level: Int,
        val text: String,
    ) : MdBlock

    data class Paragraph(
        val text: String,
    ) : MdBlock

    data class Bullets(
        val items: List<String>,
    ) : MdBlock

    data class Table(
        val rows: List<List<String>>,
    ) : MdBlock
}

private fun parseMarkdown(
    markdown: String,
    skipFirstHeading: Boolean,
): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val paragraph = mutableListOf<String>()
    val bullets = mutableListOf<String>()
    val tableRows = mutableListOf<List<String>>()

    fun flush() {
        if (paragraph.isNotEmpty()) {
            blocks += MdBlock.Paragraph(paragraph.joinToString(" "))
            paragraph.clear()
        }
        if (bullets.isNotEmpty()) {
            blocks += MdBlock.Bullets(bullets.toList())
            bullets.clear()
        }
        if (tableRows.isNotEmpty()) {
            blocks += MdBlock.Table(tableRows.toList())
            tableRows.clear()
        }
    }

    markdown.lines().forEach { raw ->
        val line = raw.trim()
        when {
            line.isEmpty() -> flush()
            line.startsWith("#") -> {
                flush()
                val level = line.takeWhile { it == '#' }.length
                blocks += MdBlock.Heading(level, line.dropWhile { it == '#' }.trim())
            }
            line.startsWith("|") -> {
                val cells = line.trim('|').split('|').map { it.trim() }
                // Skip the |---|---| separator; the header row is kept as row zero.
                if (cells.none { it.isNotEmpty() && it.all { c -> c == '-' || c == ':' } }) {
                    tableRows += cells
                }
            }
            line.startsWith("- ") || line.startsWith("* ") -> bullets += line.drop(2).trim()
            else -> paragraph += line
        }
    }
    flush()

    return if (skipFirstHeading && (blocks.firstOrNull() as? MdBlock.Heading)?.level == 1) {
        blocks.drop(1)
    } else {
        blocks
    }
}

// ---- Block rendering ---------------------------------------------------------

@Composable
private fun MdHeading(heading: MdBlock.Heading) {
    Text(
        inlineStyled(heading.text),
        style = if (heading.level <= 2) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 18.dp, top = 26.dp, bottom = 2.dp),
    )
}

@Composable
private fun MdParagraph(text: String) {
    Text(
        inlineStyled(text),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
    )
}

@Composable
private fun MdTable(rows: List<List<String>>) {
    val body = rows.drop(1) // the header names the columns; the rows carry the content
    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(top = 8.dp)) {
        body.forEachIndexed { index, cells ->
            Surface(
                shape = tableShape(index, body.size),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(inlineStyled(cells.firstOrNull().orEmpty()), style = MaterialTheme.typography.titleMedium)
                        val detail = cells.drop(1).filter { it.isNotEmpty() }.joinToString(" · ")
                        if (detail.isNotEmpty()) {
                            Text(
                                inlineStyled(detail),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun tableShape(
    index: Int,
    count: Int,
): Shape {
    val outer = 22.dp
    val inner = 6.dp
    return when {
        count == 1 -> RoundedCornerShape(outer)
        index == 0 -> RoundedCornerShape(outer, outer, inner, inner)
        index == count - 1 -> RoundedCornerShape(inner, inner, outer, outer)
        else -> RoundedCornerShape(inner)
    }
}

// ---- Inline styling ------------------------------------------------------------

private val inlineMarks = Regex("""\*\*(.+?)\*\*|\*(.+?)\*|`(.+?)`""")

/** Resolves `**bold**`, `*italic*` and `` `code` `` spans. */
private fun inlineStyled(text: String): AnnotatedString =
    buildAnnotatedString {
        var cursor = 0
        inlineMarks.findAll(text).forEach { match ->
            append(text.substring(cursor, match.range.first))
            val (bold, italic, code) = match.destructured
            when {
                bold.isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(bold) }
                italic.isNotEmpty() -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(italic) }
                else -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(code) }
            }
            cursor = match.range.last + 1
        }
        append(text.substring(cursor))
    }
