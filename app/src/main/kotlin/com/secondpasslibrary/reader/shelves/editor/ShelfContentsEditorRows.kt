package com.secondpasslibrary.reader.shelves.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.secondpasslibrary.client.ShelfEditorItem
import com.secondpasslibrary.reader.design.book.PublicBookCover
import com.secondpasslibrary.reader.design.book.toCompactBookPresentation
import com.secondpasslibrary.reader.design.icons.AppIcon
import com.secondpasslibrary.reader.design.icons.AppIconGraphic

@Composable
internal fun AvailableShelfEditorRow(
    entry: ShelfEditorItem.Available,
    directPositionAvailable: Boolean,
    enabled: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onMoveToPosition: () -> Unit,
    onRemove: () -> Unit
) {
    val book = entry.book.toCompactBookPresentation()
    ShelfEditorRowSurface {
        PublicBookCover(
            book.cover,
            book.title,
            Modifier.size(width = 52.dp, height = 76.dp),
            contentDescription = null
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(book.title, style = MaterialTheme.typography.titleMedium)
            book.authors?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            book.series?.let {
                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                "Position ${userFacingShelfPosition(entry.position)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium
            )
        }
        EditorMoveActions(
            book.title,
            directPositionAvailable,
            enabled,
            onMoveUp,
            onMoveDown,
            onMoveToPosition,
            onRemove
        )
    }
}

@Composable
internal fun UnavailableShelfEditorRow(
    entry: ShelfEditorItem.Unavailable,
    enabled: Boolean,
    onRemove: () -> Unit
) {
    ShelfEditorRowSurface {
        Box(Modifier.size(width = 52.dp, height = 76.dp), contentAlignment = Alignment.Center) {
            AppIconGraphic(AppIcon.Locked, "Unavailable shelf item", Modifier.size(30.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Unavailable shelf item", style = MaterialTheme.typography.titleMedium)
            Text(
                "Position ${userFacingShelfPosition(entry.position)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Book details are not available to this account.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
        IconButton(onClick = onRemove, enabled = enabled) {
            AppIconGraphic(
                AppIcon.Delete,
                "Remove unavailable shelf item at position " +
                    userFacingShelfPosition(entry.position)
            )
        }
    }
}

@Composable
private fun EditorMoveActions(
    bookTitle: String,
    directPositionAvailable: Boolean,
    enabled: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onMoveToPosition: () -> Unit,
    onRemove: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onMoveUp, enabled = enabled) {
            AppIconGraphic(AppIcon.MoveShelfItemUp, "Move $bookTitle up")
        }
        IconButton(onClick = onMoveDown, enabled = enabled) {
            AppIconGraphic(AppIcon.MoveShelfItemDown, "Move $bookTitle down")
        }
        OutlinedButton(
            onClick = onMoveToPosition,
            enabled = enabled && directPositionAvailable,
            modifier = Modifier.semantics {
                contentDescription = "Move $bookTitle to a shelf position"
            }
        ) { Text("Move to…") }
        IconButton(onClick = onRemove, enabled = enabled) {
            AppIconGraphic(AppIcon.Delete, "Remove $bookTitle from shelf")
        }
    }
}

@Composable
private fun ShelfEditorRowSurface(content: @Composable RowScope.() -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}
