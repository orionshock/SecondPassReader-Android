package com.secondpasslibrary.reader.bookdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.secondpasslibrary.client.LibraryBookDetail
import com.secondpasslibrary.reader.design.book.PublicBookCover

private const val BOOK_COVER_ASPECT_RATIO = 2f / 3f
private const val COLLAPSED_DESCRIPTION_LINES = 8
private const val EXPANDABLE_DESCRIPTION_LENGTH = 500
private const val METADATA_SEPARATOR = " \u00b7 "

@Composable
internal fun BookDetailCover(book: LibraryBookDetail, modifier: Modifier = Modifier) {
    PublicBookCover(
        reference = book.cover,
        title = book.title,
        modifier = modifier.aspectRatio(BOOK_COVER_ASPECT_RATIO),
        contentScale = ContentScale.Fit
    )
}

@Composable
internal fun BookDetailMetadata(
    book: LibraryBookDetail,
    onAuthorSelected: (String) -> Unit,
    onSeriesSelected: (String) -> Unit,
    onTagSelected: (String, String) -> Unit,
    onReadingSessions: () -> Unit,
    onAddToShelf: () -> Unit,
    modifier: Modifier = Modifier
) {
    val presentation = book.toPresentation()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(presentation.title, style = MaterialTheme.typography.headlineLarge)
        presentation.subtitle?.let {
            Text(
                it,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        book.series?.let { series ->
            TextButton(onClick = { onSeriesSelected(series.id) }) {
                Text(requireNotNull(presentation.seriesLabel))
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            book.authors.forEach { author ->
                TextButton(onClick = { onAuthorSelected(author.id) }) { Text(author.name) }
            }
        }
        SupportingMetadata(book, presentation)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            book.catalogTags.forEach { tag ->
                AssistChip(
                    onClick = { onTagSelected(tag.id, tag.slug) },
                    label = { Text(tag.name) }
                )
            }
        }
        Description(presentation.description)
        BookActions(book.file != null, onReadingSessions, onAddToShelf)
        GroupMemberships(book)
    }
}

@Composable
private fun SupportingMetadata(book: LibraryBookDetail, presentation: BookDetailPresentation) {
    val values = listOfNotNull(book.publisher, book.language, presentation.publicationLabel)
        .filter(String::isNotBlank)
    if (values.isNotEmpty()) {
        Text(
            values.joinToString(METADATA_SEPARATOR),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    presentation.fileLabel?.let {
        Text(
            it,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun Description(description: String?) {
    if (description == null) return
    var expanded by rememberSaveable(description) { mutableStateOf(false) }
    Text("Description", style = MaterialTheme.typography.titleMedium)
    Text(
        description,
        maxLines = if (expanded) Int.MAX_VALUE else COLLAPSED_DESCRIPTION_LINES,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.bodyMedium
    )
    if (description.length > EXPANDABLE_DESCRIPTION_LENGTH) {
        TextButton(onClick = { expanded = !expanded }) {
            Text(if (expanded) "Show less" else "Show more")
        }
    }
}

@Composable
private fun BookActions(hasFile: Boolean, onReadingSessions: () -> Unit, onAddToShelf: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.small
    ) {
        Column(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(if (hasFile) "Reader actions are not available yet." else "EPUB unavailable")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {}, enabled = false) { Text("Open reader") }
                OutlinedButton(onClick = onReadingSessions) { Text("Reading sessions") }
                OutlinedButton(onClick = onAddToShelf) { Text("Add to shelf") }
            }
        }
    }
}

@Composable
private fun GroupMemberships(book: LibraryBookDetail) {
    if (book.groups.isEmpty()) return
    Text("Available in", style = MaterialTheme.typography.titleMedium)
    Text(
        book.groups.joinToString(METADATA_SEPARATOR) { it.name },
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
