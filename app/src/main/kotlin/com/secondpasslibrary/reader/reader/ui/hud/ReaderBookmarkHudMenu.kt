package com.secondpasslibrary.reader.reader.ui.hud

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.secondpasslibrary.reader.design.icons.AppIcon
import com.secondpasslibrary.reader.design.icons.AppIconGraphic
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotation
import com.secondpasslibrary.reader.reader.appearance.ReaderPalette

private const val MAX_VISIBLE_BOOKMARK_BADGE_COUNT = 9

@Composable
internal fun ReaderBookmarkHudIcon(count: Int, writable: Boolean, palette: ReaderPalette) {
    val presentation = bookmarkHudPresentation(count, writable)
    Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
        AppIconGraphic(
            presentation.icon,
            presentation.contentDescription,
            Modifier.size(24.dp)
        )
        presentation.badgeText?.let { badgeText ->
            Box(
                modifier = Modifier.align(Alignment.TopEnd)
                    .height(16.dp)
                    .widthIn(min = 16.dp)
                    .background(palette.selectedSurface, RoundedCornerShape(8.dp))
                    .padding(horizontal = 3.dp)
                    .clearAndSetSemantics { },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = badgeText,
                    color = palette.primaryForeground,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        lineHeight = 10.sp
                    )
                )
            }
        }
    }
}

internal data class ReaderBookmarkHudPresentation(
    val icon: AppIcon,
    val contentDescription: String,
    val badgeText: String?
)

internal fun bookmarkHudPresentation(count: Int, writable: Boolean): ReaderBookmarkHudPresentation {
    val safeCount = count.coerceAtLeast(0)
    val readOnlySuffix = if (writable) "" else ", read only"
    val description = when (safeCount) {
        0 -> if (writable) "Add bookmark" else "No bookmarks on this page, read only"
        1 -> "1 bookmark on this page$readOnlySuffix"
        else -> "$safeCount bookmarks on this page$readOnlySuffix"
    }
    val icon = when {
        safeCount > 0 -> AppIcon.BookmarkFilled
        writable -> AppIcon.AddBookmark
        else -> AppIcon.Bookmark
    }
    val badgeText = when {
        safeCount <= 1 -> null
        safeCount > MAX_VISIBLE_BOOKMARK_BADGE_COUNT -> "$MAX_VISIBLE_BOOKMARK_BADGE_COUNT+"
        else -> safeCount.toString()
    }
    return ReaderBookmarkHudPresentation(icon, description, badgeText)
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
            val bookmarkLabel = bookmark.locationLabel ?: "bookmark ${index + 1}"
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
                    AppIconGraphic(AppIcon.JumpToLocation, "Go to $bookmarkLabel")
                }
                if (writable) {
                    IconButton(onClick = { onRemove(bookmark) }) {
                        AppIconGraphic(AppIcon.Delete, "Remove $bookmarkLabel")
                    }
                }
            }
        }
    }
}
