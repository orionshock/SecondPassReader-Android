package com.secondpasslibrary.reader.design.book

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.secondpasslibrary.reader.design.components.AnchoredOverflowMenu

private const val BOOK_COVER_ASPECT_RATIO = 2f / 3f

@Composable
internal fun CompactBookRow(
    book: CompactBookPresentation,
    actions: List<BookCardAction> = emptyList(),
    onAction: (BookCardAction) -> Unit = {},
    layout: CompactBookRowLayout = CompactBookRowLayout.COMPACT,
    onClick: (() -> Unit)?
) {
    var menuExpanded by remember(book.id) { mutableStateOf(false) }
    when (layout) {
        CompactBookRowLayout.COMPACT ->
            CompactBookRowContent(book, actions, onAction, onClick, menuExpanded) {
                menuExpanded = it
            }

        CompactBookRowLayout.WIDE ->
            WideCompactBookRowContent(book, actions, onAction, onClick, menuExpanded) {
                menuExpanded = it
            }
    }
}

internal enum class CompactBookRowLayout {
    COMPACT,
    WIDE
}

@Composable
private fun CompactBookRowContent(
    book: CompactBookPresentation,
    actions: List<BookCardAction>,
    onAction: (BookCardAction) -> Unit,
    onClick: (() -> Unit)?,
    menuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(COMPACT_BOOK_ROW_TAG)
                .bookCardInteractions(onClick, actions.isNotEmpty()) {
                    onMenuExpandedChange(true)
                }
                .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(Modifier.size(width = 64.dp, height = 96.dp)) {
            PublicBookCover(
                reference = book.cover,
                title = book.title,
                modifier = Modifier.fillMaxSize(),
                contentDescription = null
            )
            BookOverflow(
                book.title,
                actions,
                menuExpanded,
                onMenuExpandedChange,
                onAction,
                Modifier.align(Alignment.TopEnd).padding(4.dp)
            )
        }
        Column(
            modifier = Modifier.weight(1f).padding(vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                book.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            book.subtitle?.let { MetadataLine(it, MaterialTheme.typography.bodySmall) }
            book.authors?.let { MetadataLine(it) }
            book.series?.let { MetadataLine(it) }
            book.publisher?.let { MetadataLine(it) }
        }
    }
}

@Composable
private fun WideCompactBookRowContent(
    book: CompactBookPresentation,
    actions: List<BookCardAction>,
    onAction: (BookCardAction) -> Unit,
    onClick: (() -> Unit)?,
    menuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(WIDE_BOOK_ROW_TAG)
                .bookCardInteractions(onClick, actions.isNotEmpty()) {
                    onMenuExpandedChange(true)
                }
                .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PublicBookCover(
            reference = book.cover,
            title = book.title,
            modifier =
                Modifier.size(width = 60.dp, height = 90.dp)
                    .testTag(COMPACT_BOOK_COVER_TAG),
            contentDescription = null
        )
        Column(
            modifier = Modifier.weight(1f).padding(vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                book.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            book.authors?.let { MetadataLine(it, MaterialTheme.typography.bodySmall) }
            book.subtitle?.let { MetadataLine(it) }
        }
        WideBookFacts(book, Modifier.width(260.dp))
        BookOverflow(
            book.title,
            actions,
            menuExpanded,
            onMenuExpandedChange,
            onAction,
            Modifier.size(48.dp)
        )
    }
}

@Composable
private fun WideBookFacts(book: CompactBookPresentation, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        book.series?.let { MetadataLine(it) }
        book.publisher?.let { MetadataLine(it) }
        listOfNotNull(book.language, book.format)
            .takeIf(List<String>::isNotEmpty)
            ?.joinToString(" · ")
            ?.let { MetadataLine(it) }
    }
}

@Composable
internal fun CompactBookGridCard(
    book: CompactBookPresentation,
    actions: List<BookCardAction> = emptyList(),
    onAction: (BookCardAction) -> Unit = {},
    onClick: (() -> Unit)?
) {
    var menuExpanded by remember(book.id) { mutableStateOf(false) }
    OutlinedCard {
        Column(
            Modifier.bookCardInteractions(
                onClick,
                actions.isNotEmpty()
            ) {
                menuExpanded = true
            }
        ) {
            Box {
                PublicBookCover(
                    reference = book.cover,
                    title = book.title,
                    modifier = Modifier.fillMaxWidth().aspectRatio(BOOK_COVER_ASPECT_RATIO),
                    contentDescription = null
                )
                BookOverflow(
                    book.title,
                    actions,
                    menuExpanded,
                    { menuExpanded = it },
                    onAction,
                    Modifier.align(Alignment.TopEnd).padding(8.dp)
                )
            }
            BookGridMetadata(book)
        }
    }
}

@Composable
private fun BookGridMetadata(book: CompactBookPresentation) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(68.dp)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            book.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        book.authors?.let { MetadataLine(it) }
    }
}

@Composable
private fun BookOverflow(
    bookTitle: String,
    actions: List<BookCardAction>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onAction: (BookCardAction) -> Unit,
    modifier: Modifier
) {
    AnchoredOverflowMenu(
        items = actions,
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        label = BookCardAction::menuLabel,
        onSelected = onAction,
        modifier = modifier,
        contentDescription = "Book actions for $bookTitle"
    )
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.bookCardInteractions(
    onClick: (() -> Unit)?,
    hasActions: Boolean,
    onLongClick: () -> Unit
): Modifier = when {
    onClick != null && hasActions ->
        combinedClickable(
            onClick = onClick,
            onLongClickLabel = "Book actions",
            onLongClick = onLongClick
        ).semantics(mergeDescendants = true) { }

    onClick != null -> combinedClickable(onClick = onClick).semantics(mergeDescendants = true) { }

    else -> this
}

@Composable
private fun MetadataLine(
    value: String,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.labelMedium
) {
    Text(
        value,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = style,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

internal const val COMPACT_BOOK_ROW_TAG = "compact-book-row"
internal const val WIDE_BOOK_ROW_TAG = "wide-book-row"
internal const val COMPACT_BOOK_COVER_TAG = "compact-book-cover"
