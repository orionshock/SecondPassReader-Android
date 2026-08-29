package com.secondpasslibrary.reader.reader.ui.hud

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.dp
import com.secondpasslibrary.reader.design.icons.AppIcon
import com.secondpasslibrary.reader.design.icons.AppIconGraphic
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotation
import com.secondpasslibrary.reader.reader.appearance.ReaderPalette

private const val MAX_BOOKMARK_BADGE_COUNT = 99

@Composable
internal fun ReaderBookmarkHudIcon(count: Int, palette: ReaderPalette) {
    val description = when (count) {
        0 -> "Bookmark current page"
        1 -> "Bookmark on current page"
        else -> "$count bookmarks on current page"
    }
    Box(contentAlignment = Alignment.Center) {
        AppIconGraphic(
            if (count == 0) AppIcon.AddBookmark else AppIcon.RemoveBookmark,
            description
        )
        if (count > 1) {
            Text(
                text = count.coerceAtMost(MAX_BOOKMARK_BADGE_COUNT).toString(),
                modifier = Modifier.align(Alignment.TopEnd).drawBehind {
                    drawCircle(palette.selectedSurface, radius = size.minDimension / 2)
                }.padding(horizontal = 4.dp),
                color = palette.panelSurface,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
internal fun ReaderBookmarkHudMenu(
    expanded: Boolean,
    bookmarks: List<ReaderAnnotation.Bookmark>,
    writable: Boolean,
    palette: ReaderPalette,
    onDismiss: () -> Unit,
    onNavigate: (ReaderAnnotation.Bookmark) -> Unit,
    onRemove: (ReaderAnnotation.Bookmark) -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        containerColor = palette.panelSurface,
        modifier = Modifier.widthIn(min = 240.dp, max = 340.dp)
    ) {
        bookmarks.forEachIndexed { index, bookmark ->
            if (index > 0) HorizontalDivider(color = palette.border)
            Row(
                modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = bookmark.locationLabel ?: "Bookmark ${index + 1}",
                        color = palette.primaryForeground,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2
                    )
                }
                IconButton(onClick = { onNavigate(bookmark) }) {
                    AppIconGraphic(AppIcon.JumpToLocation, "Go to bookmark")
                }
                if (writable) {
                    IconButton(onClick = { onRemove(bookmark) }) {
                        AppIconGraphic(AppIcon.Delete, "Remove bookmark")
                    }
                }
            }
        }
    }
}
