package com.secondpasslibrary.reader.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private const val BOOK_COVER_ASPECT_RATIO = 2f / 3f

@Composable
internal fun LibraryBookRow(book: LibraryBookPresentation) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        LibraryBookCover(
            cover = book.cover,
            title = book.title,
            modifier = Modifier.size(width = 64.dp, height = 96.dp)
        )
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
            book.subtitle?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            BookMetadata(book)
        }
    }
}

@Composable
internal fun LibraryBookGridCard(book: LibraryBookPresentation) {
    OutlinedCard {
        Column {
            LibraryBookCover(
                cover = book.cover,
                title = book.title,
                modifier = Modifier.fillMaxWidth().aspectRatio(BOOK_COVER_ASPECT_RATIO)
            )
            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    book.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                book.authors?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun BookMetadata(book: LibraryBookPresentation) {
    book.authors?.let { MetadataLine(it) }
    book.series?.let { MetadataLine(it) }
    book.publisher?.let { MetadataLine(it) }
}

@Composable
private fun MetadataLine(value: String) {
    Text(
        value,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}
