package com.secondpasslibrary.reader.design.book

import androidx.compose.foundation.clickable
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
internal fun CompactBookRow(book: CompactBookPresentation, onClick: (() -> Unit)?) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick))
                .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        PublicBookCover(
            reference = book.cover,
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
            book.subtitle?.let { MetadataLine(it, MaterialTheme.typography.bodySmall) }
            book.authors?.let { MetadataLine(it) }
            book.series?.let { MetadataLine(it) }
            book.publisher?.let { MetadataLine(it) }
        }
    }
}

@Composable
internal fun CompactBookGridCard(book: CompactBookPresentation, onClick: (() -> Unit)?) {
    if (onClick == null) {
        OutlinedCard { BookGridContent(book) }
    } else {
        OutlinedCard(onClick = onClick) { BookGridContent(book) }
    }
}

@Composable
private fun BookGridContent(book: CompactBookPresentation) {
    Column {
        PublicBookCover(
            reference = book.cover,
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
            book.authors?.let { MetadataLine(it) }
        }
    }
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
