package com.secondpasslibrary.reader.library.axis

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.secondpasslibrary.reader.design.book.PublicBookCover
import com.secondpasslibrary.reader.design.book.separatedPreviewCapacity
import com.secondpasslibrary.reader.library.LibraryFailure

@Composable
internal fun LibraryAuthorSeriesCard(
    model: LibraryAuthorSeriesCardPresentation,
    onSelect: (String) -> Unit,
    onBookSelected: (String) -> Unit
) {
    OutlinedCard(onClick = { onSelect(model.id) }) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val previewCount = model.previews.returnedBooks.size
            val capacity =
                separatedPreviewCapacity(
                    maxWidth,
                    AUTHOR_SERIES_PRIMARY_CONTENT_WIDTH,
                    AUTHOR_SERIES_COVER_WIDTH,
                    AUTHOR_SERIES_COVER_SPACING,
                    previewCount
                )
            Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        model.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        model.bookCountLabel,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                PreviewCoverStack(model.previews, capacity, onBookSelected)
            }
        }
    }
}

@Composable
private fun PreviewCoverStack(
    previews: LibraryAuthorSeriesPreviewBooksPresentation,
    capacity: Int,
    onBookSelected: (String) -> Unit
) {
    when (previews) {
        LibraryAuthorSeriesPreviewBooksPresentation.Omitted -> Unit

        is LibraryAuthorSeriesPreviewBooksPresentation.Returned -> {
            if (previews.books.isEmpty()) {
                Text(
                    "No previews",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall
                )
            } else {
                val visibleBooks = previews.books.take(capacity)
                Row(horizontalArrangement = Arrangement.spacedBy(AUTHOR_SERIES_COVER_SPACING)) {
                    visibleBooks.forEach { book ->
                        PublicBookCover(
                            reference = book.cover,
                            title = book.title,
                            modifier =
                                Modifier
                                    .size(
                                        width = AUTHOR_SERIES_COVER_WIDTH,
                                        height = AUTHOR_SERIES_COVER_HEIGHT
                                    )
                                    .clickable { onBookSelected(book.id) }
                        )
                    }
                }
            }
        }
    }
}

private val LibraryAuthorSeriesPreviewBooksPresentation.returnedBooks
    get() = (this as? LibraryAuthorSeriesPreviewBooksPresentation.Returned)?.books.orEmpty()

private val AUTHOR_SERIES_PRIMARY_CONTENT_WIDTH = 220.dp
private val AUTHOR_SERIES_COVER_WIDTH = 48.dp
private val AUTHOR_SERIES_COVER_HEIGHT = 72.dp
private val AUTHOR_SERIES_COVER_SPACING = 8.dp

@Composable
internal fun SelectedAuthorSeriesHeader(
    detail: LibraryAuthorSeriesDetailPresentation,
    onRetry: () -> Unit
) {
    when (detail) {
        is LibraryAuthorSeriesDetailPresentation.Loading -> Unit

        is LibraryAuthorSeriesDetailPresentation.Failure -> EntityDetailFailure(
            detail.failure,
            onRetry
        )

        is LibraryAuthorSeriesDetailPresentation.Content -> detail.description?.let { description ->
            SelectedAuthorSeriesDescription(detail.id, description)
        }
    }
}

@Composable
private fun EntityDetailFailure(failure: LibraryFailure, onRetry: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            authorSeriesFailureMessage(failure, "details"),
            color = MaterialTheme.colorScheme.error
        )
        OutlinedButton(onClick = onRetry) { Text("Retry") }
    }
}

@Composable
private fun SelectedAuthorSeriesDescription(id: String, description: String) {
    var expanded by rememberSaveable(id) { mutableStateOf(false) }
    var canExpand by rememberSaveable(id) { mutableStateOf(false) }
    OutlinedCard(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            CollapsibleDescriptionText(description, expanded) { canExpand = it }
            if (canExpand) {
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Less" else "More")
                }
            }
        }
    }
}

@Composable
private fun CollapsibleDescriptionText(
    description: String,
    expanded: Boolean,
    onOverflowChanged: (Boolean) -> Unit
) {
    Text(
        description,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = if (expanded) Int.MAX_VALUE else 3,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { result ->
            if (!expanded) onOverflowChanged(result.hasVisualOverflow)
        }
    )
}
