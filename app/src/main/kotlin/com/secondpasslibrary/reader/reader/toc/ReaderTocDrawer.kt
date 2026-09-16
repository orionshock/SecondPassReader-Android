package com.secondpasslibrary.reader.reader.toc

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.secondpasslibrary.reader.design.icons.AppIcon
import com.secondpasslibrary.reader.design.icons.AppIconGraphic
import com.secondpasslibrary.reader.reader.appearance.ReaderPalette

@Composable
internal fun ReaderTocDrawer(
    bookTitle: String,
    entries: List<ReaderTocEntry>,
    currentResource: ReaderPublicationResource?,
    palette: ReaderPalette,
    onEntrySelected: (ReaderPublicationTarget) -> Unit,
    onDismiss: () -> Unit,
    onCloseBook: () -> Unit
) {
    val rows = remember(entries) { entries.flattenForPresentation() }
    ModalDrawerSheet(
        modifier = Modifier.fillMaxHeight().widthIn(max = DRAWER_MAX_WIDTH),
        drawerContainerColor = palette.panelSurface,
        drawerContentColor = palette.primaryForeground
    ) {
        Column(Modifier.fillMaxHeight()) {
            ReaderTocHeader(bookTitle, palette, onDismiss)
            HorizontalDivider(color = palette.border)
            ReaderTocBody(rows, currentResource, palette, onEntrySelected)
            ReaderTocFooter(palette, onCloseBook)
        }
    }
}

@Composable
private fun ReaderTocHeader(bookTitle: String, palette: ReaderPalette, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 10.dp)
            .testTag(READER_TOC_HEADER_TAG),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(bottom = 10.dp)) {
            Text(
                text = "Table of contents",
                modifier = Modifier.testTag(READER_TOC_EYEBROW_TAG),
                color = palette.secondaryForeground,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
            Text(
                text = bookTitle,
                modifier = Modifier.padding(top = 3.dp).testTag(READER_TOC_TITLE_TAG),
                color = palette.primaryForeground,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onDismiss) {
            AppIconGraphic(AppIcon.Close, "Close Table of Contents")
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.ReaderTocBody(
    rows: List<ReaderTocPresentationRow>,
    currentResource: ReaderPublicationResource?,
    palette: ReaderPalette,
    onEntrySelected: (ReaderPublicationTarget) -> Unit
) {
    if (rows.isEmpty()) {
        Text(
            text = "No table of contents.",
            modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp)
                .testTag(READER_TOC_BODY_TAG),
            color = palette.secondaryForeground,
            style = MaterialTheme.typography.bodyMedium
        )
    } else {
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().testTag(READER_TOC_BODY_TAG)
        ) {
            items(rows, key = { it.key }) { row ->
                ReaderTocRow(
                    row,
                    current = row.entry.resource == currentResource && currentResource != null,
                    palette,
                    onEntrySelected
                )
            }
        }
    }
}

@Composable
private fun ReaderTocFooter(palette: ReaderPalette, onCloseBook: () -> Unit) {
    Column(Modifier.fillMaxWidth().testTag(READER_TOC_FOOTER_TAG)) {
        HorizontalDivider(color = palette.border)
        ReaderDrawerAction(
            label = "Close Book",
            icon = AppIcon.Book,
            onClick = onCloseBook
        )
    }
}

@Composable
private fun ReaderDrawerAction(label: String, icon: AppIcon, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIconGraphic(icon, contentDescription = null)
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun ReaderTocRow(
    row: ReaderTocPresentationRow,
    current: Boolean,
    palette: ReaderPalette,
    onEntrySelected: (ReaderPublicationTarget) -> Unit
) {
    val target = row.entry.target
    val interaction = if (target == null) {
        Modifier
    } else {
        Modifier
            .semantics { contentDescription = "Open ${row.entry.title}" }
            .clickable(role = Role.Button) { onEntrySelected(target) }
    }
    Row(
        modifier = interaction
            .fillMaxWidth()
            .background(if (current) palette.selectedSurface else Color.Transparent)
            .semantics { selected = current }
            .heightIn(min = 48.dp)
            .padding(
                start = (16 + row.depth.coerceAtMost(MAX_INDENT_DEPTH) * 18).dp,
                end = 16.dp,
                top = 8.dp,
                bottom = 8.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = row.entry.title,
            color = if (target == null) {
                palette.secondaryForeground
            } else {
                palette.primaryForeground
            },
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private data class ReaderTocPresentationRow(
    val entry: ReaderTocEntry,
    val depth: Int,
    val key: String
)

private fun List<ReaderTocEntry>.flattenForPresentation(): List<ReaderTocPresentationRow> {
    val rows = mutableListOf<ReaderTocPresentationRow>()
    fun append(entries: List<ReaderTocEntry>, depth: Int, parentKey: String) {
        entries.forEachIndexed { index, entry ->
            val key = "$parentKey/$index:${entry.target?.reference.orEmpty()}"
            rows += ReaderTocPresentationRow(entry, depth, key)
            append(entry.children, depth + 1, key)
        }
    }
    append(this, 0, "toc")
    return rows
}

private val DRAWER_MAX_WIDTH = 380.dp
private const val MAX_INDENT_DEPTH = 5

internal const val READER_TOC_HEADER_TAG = "reader_toc_header"
internal const val READER_TOC_EYEBROW_TAG = "reader_toc_eyebrow"
internal const val READER_TOC_TITLE_TAG = "reader_toc_title"
internal const val READER_TOC_BODY_TAG = "reader_toc_body"
internal const val READER_TOC_FOOTER_TAG = "reader_toc_footer"
