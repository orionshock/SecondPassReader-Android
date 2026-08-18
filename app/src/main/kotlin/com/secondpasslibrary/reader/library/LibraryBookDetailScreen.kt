package com.secondpasslibrary.reader.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.secondpasslibrary.reader.design.icons.AppIcon
import com.secondpasslibrary.reader.design.icons.AppIconGraphic

@Composable
internal fun LibraryBookDetailScreen(
    state: LibraryBookDetailState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onAuthorSelected: (String) -> Unit,
    onSeriesSelected: (String) -> Unit,
    onTagSelected: (String, String) -> Unit,
    availableTagIds: Set<String>
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                AppIconGraphic(AppIcon.Back, "Back to Library")
            }
            Text("Book Detail", style = MaterialTheme.typography.titleMedium)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        when {
            state.loading -> DetailLoading()

            state.failure != null -> DetailFailure(onRetry)

            state.detail != null ->
                BookDetailHero(
                    state.detail,
                    onAuthorSelected,
                    onSeriesSelected,
                    onTagSelected,
                    availableTagIds
                )
        }
    }
}

@Composable
private fun DetailLoading() {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Text("Loading book…", modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun DetailFailure(onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Book details could not be loaded.")
        TextButton(onClick = onRetry) { Text("Retry") }
    }
}

@Composable
private fun BookDetailHero(
    book: com.secondpasslibrary.client.LibraryBookDetail,
    onAuthorSelected: (String) -> Unit,
    onSeriesSelected: (String) -> Unit,
    onTagSelected: (String, String) -> Unit,
    availableTagIds: Set<String>
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 720.dp && maxWidth > maxHeight
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp)
        ) {
            item {
                if (wide) {
                    Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                        BookDetailCover(book, Modifier.width(300.dp))
                        BookDetailMetadata(
                            book,
                            onAuthorSelected,
                            onSeriesSelected,
                            onTagSelected,
                            availableTagIds,
                            Modifier.weight(1f)
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            BookDetailCover(book, Modifier.widthIn(max = 360.dp))
                        }
                        BookDetailMetadata(
                            book,
                            onAuthorSelected,
                            onSeriesSelected,
                            onTagSelected,
                            availableTagIds
                        )
                    }
                }
            }
        }
    }
}
