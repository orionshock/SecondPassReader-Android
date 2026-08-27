package com.secondpasslibrary.reader.reader.toc

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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
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
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f).padding(bottom = 8.dp)) {
                Text(
                    text = bookTitle,
                    color = palette.secondaryForeground,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Table of Contents",
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            IconButton(onClick = onDismiss) {
                AppIconGraphic(AppIcon.Close, "Close table of contents")
            }
        }
        ReaderDrawerAction(
            label = "Close book",
            icon = AppIcon.Book,
            onClick = onCloseBook
        )
        HorizontalDivider(color = palette.border)
        if (rows.isEmpty()) {
            Text(
                text = "No table of contents",
                modifier = Modifier.padding(16.dp),
                color = palette.secondaryForeground,
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            LazyColumn(Modifier.fillMaxWidth()) {
                items(rows, key = { it.key }) { row ->
                    ReaderTocRow(row, palette, onEntrySelected)
                }
            }
        }
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
