package com.secondpasslibrary.reader.library.axis

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.secondpasslibrary.reader.design.book.PublicBookCover
import com.secondpasslibrary.reader.design.book.separatedPreviewCapacity
import com.secondpasslibrary.reader.library.LibraryFailure

@Composable
internal fun LibraryAuthorSeriesCard(
    model: LibraryAuthorSeriesCardPresentation,
    entityTypeLabel: String,
    onSelect: (String) -> Unit
) {
    OutlinedCard(
        onClick = { onSelect(model.id) },
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = "Open $entityTypeLabel ${model.name}"
        }
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val titleStyle = MaterialTheme.typography.titleMedium
            val textMeasurer = rememberTextMeasurer()
            val density = LocalDensity.current
            val titleWidth =
                with(density) {
                    textMeasurer
                        .measure(
                            text = AnnotatedString(model.name),
                            style = titleStyle,
                            maxLines = 1,
                            softWrap = false
                        ).size.width
                        .toDp()
                }
            val previewCount = model.previews.returnedBooks.size
            val capacity =
                separatedPreviewCapacity(
                    maxWidth,
                    titleWidth + AUTHOR_SERIES_ROW_CHROME_WIDTH,
                    AUTHOR_SERIES_COVER_WIDTH,
                    AUTHOR_SERIES_COVER_SPACING,
                    previewCount
                )
            Row(
                modifier = Modifier.fillMaxWidth().padding(AUTHOR_SERIES_ROW_PADDING),
                horizontalArrangement = Arrangement.spacedBy(AUTHOR_SERIES_CONTENT_SPACING),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        model.name,
                        style = titleStyle,
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        model.bookCountLabel,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                PreviewCoverRow(model.previews, capacity)
            }
        }
    }
}

@Composable
private fun PreviewCoverRow(previews: LibraryAuthorSeriesPreviewBooksPresentation, capacity: Int) {
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
                        Box(
                            Modifier
                                .size(
                                    width = AUTHOR_SERIES_COVER_WIDTH,
                                    height = AUTHOR_SERIES_COVER_HEIGHT
                                ).clearAndSetSemantics { }
                        ) {
                            PublicBookCover(
                                reference = book.cover,
                                title = book.title,
                                modifier = Modifier.matchParentSize()
                            )
                        }
                    }
                }
            }
        }
    }
}

private val LibraryAuthorSeriesPreviewBooksPresentation.returnedBooks
    get() = (this as? LibraryAuthorSeriesPreviewBooksPresentation.Returned)?.books.orEmpty()

private val AUTHOR_SERIES_ROW_PADDING = 14.dp
private val AUTHOR_SERIES_CONTENT_SPACING = 14.dp
private val AUTHOR_SERIES_ROW_CHROME_WIDTH =
    AUTHOR_SERIES_ROW_PADDING * 2 + AUTHOR_SERIES_CONTENT_SPACING
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
