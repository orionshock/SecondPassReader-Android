package com.secondpasslibrary.reader.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
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

@Composable
internal fun LibraryEntityCard(model: LibraryEntityCardPresentation, onSelect: (String) -> Unit) {
    OutlinedCard(onClick = { onSelect(model.id) }) {
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
            PreviewCoverStack(model.previews)
        }
    }
}

@Composable
private fun PreviewCoverStack(previews: LibraryPreviewBooksPresentation) {
    when (previews) {
        LibraryPreviewBooksPresentation.Omitted -> Unit

        is LibraryPreviewBooksPresentation.Returned -> {
            if (previews.books.isEmpty()) {
                Text(
                    "No previews",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall
                )
            } else {
                val width = 44.dp + 27.dp * (previews.books.size - 1)
                Box(Modifier.width(width).height(66.dp)) {
                    previews.books.forEachIndexed { index, book ->
                        LibraryBookCover(
                            book.cover,
                            book.title,
                            Modifier.offset(x = 27.dp * index).size(width = 44.dp, height = 66.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun SelectedEntityHeader(
    detail: LibrarySelectedEntityPresentation,
    onRetry: () -> Unit,
    returnLabel: String? = null,
    onReturn: (() -> Unit)? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (returnLabel != null && onReturn != null) {
            TextButton(onClick = onReturn) { Text(returnLabel) }
        }
        OutlinedCard(Modifier.fillMaxWidth()) {
            when (detail) {
                is LibrarySelectedEntityPresentation.Loading ->
                    Row(
                        Modifier.fillMaxWidth().padding(18.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        Text("Loading details...")
                    }

                is LibrarySelectedEntityPresentation.Failure ->
                    EntityDetailFailure(detail.failure, onRetry)

                is LibrarySelectedEntityPresentation.Content -> SelectedEntityContent(detail)
            }
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
        Text(entityFailureMessage(failure, "details"), color = MaterialTheme.colorScheme.error)
        OutlinedButton(onClick = onRetry) { Text("Retry") }
    }
}

@Composable
private fun SelectedEntityContent(detail: LibrarySelectedEntityPresentation.Content) {
    var expanded by rememberSaveable(detail.id) { mutableStateOf(false) }
    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(detail.name, style = MaterialTheme.typography.titleLarge)
        Text(
            detail.bookCountLabel,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium
        )
        detail.description?.let { description ->
            Text(
                description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (expanded) Int.MAX_VALUE else 4,
                overflow = TextOverflow.Ellipsis
            )
            if (description.length > DESCRIPTION_EXPANSION_THRESHOLD) {
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Show less" else "Show more")
                }
            }
        }
    }
}

private const val DESCRIPTION_EXPANSION_THRESHOLD = 240
