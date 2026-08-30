package com.secondpasslibrary.reader.bookdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.secondpasslibrary.reader.design.components.ContextualAppBar

private const val MEDIUM_COVER_WIDTH_FRACTION = 0.38f

@Composable
internal fun BookDetailScreen(
    state: BookDetailState,
    appBarContext: String,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onAuthorSelected: (String) -> Unit,
    onSeriesSelected: (String) -> Unit,
    onTagSelected: (String, String) -> Unit,
    onReadBook: () -> Unit,
    onReadingSessions: () -> Unit,
    onAddToShelf: () -> Unit,
    readAvailable: Boolean = true,
    serverActionsAvailable: Boolean = true
) {
    Column(Modifier.fillMaxSize()) {
        ContextualAppBar(state.appBarPresentation(appBarContext), onNavigation = onBack)
        when {
            state.loading -> DetailLoading()

            state.failure != null -> DetailFailure(onRetry)

            state.detail != null ->
                BookDetailHero(
                    state.detail,
                    onAuthorSelected,
                    onSeriesSelected,
                    onTagSelected,
                    onReadBook,
                    onReadingSessions,
                    onAddToShelf,
                    readAvailable,
                    serverActionsAvailable
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
        Text("Loading book...", modifier = Modifier.padding(top = 12.dp))
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
    onReadBook: () -> Unit,
    onReadingSessions: () -> Unit,
    onAddToShelf: () -> Unit,
    readAvailable: Boolean,
    serverActionsAvailable: Boolean
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val layout = bookDetailLayoutForWidth(maxWidth)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                when (layout) {
                    BookDetailLayout.WIDE -> BookDetailWideHero(
                        book,
                        onAuthorSelected,
                        onSeriesSelected,
                        onReadBook,
                        onReadingSessions,
                        onAddToShelf,
                        readAvailable,
                        serverActionsAvailable
                    )

                    BookDetailLayout.MEDIUM -> BookDetailMediumHero(
                        book,
                        onAuthorSelected,
                        onSeriesSelected,
                        onReadBook,
                        onReadingSessions,
                        onAddToShelf,
                        readAvailable,
                        serverActionsAvailable
                    )

                    BookDetailLayout.NARROW -> BookDetailNarrowHero(
                        book,
                        onAuthorSelected,
                        onSeriesSelected,
                        onReadBook,
                        onReadingSessions,
                        onAddToShelf,
                        readAvailable,
                        serverActionsAvailable
                    )
                }
            }
            item { BookDetailSupportingContent(book, onTagSelected) }
        }
    }
}

internal enum class BookDetailLayout { WIDE, MEDIUM, NARROW }

internal fun bookDetailLayoutForWidth(width: Dp): BookDetailLayout = when {
    width >= 900.dp -> BookDetailLayout.WIDE
    width >= 600.dp -> BookDetailLayout.MEDIUM
    else -> BookDetailLayout.NARROW
}

@Composable
private fun BookDetailWideHero(
    book: com.secondpasslibrary.client.LibraryBookDetail,
    onAuthorSelected: (String) -> Unit,
    onSeriesSelected: (String) -> Unit,
    onReadBook: () -> Unit,
    onReadingSessions: () -> Unit,
    onAddToShelf: () -> Unit,
    readAvailable: Boolean,
    serverActionsAvailable: Boolean
) {
    Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        BookDetailCover(book, Modifier.width(280.dp))
        BookDetailMetadata(
            book,
            onAuthorSelected,
            onSeriesSelected,
            Modifier.weight(1f)
        )
        BookDetailActions(
            onReadBook,
            onReadingSessions,
            onAddToShelf,
            readBookEnabled = book.hasReadableEpub && readAvailable,
            serverActionsAvailable = serverActionsAvailable,
            BookDetailActionLayout.VERTICAL,
            Modifier.width(184.dp)
        )
    }
}

@Composable
private fun BookDetailMediumHero(
    book: com.secondpasslibrary.client.LibraryBookDetail,
    onAuthorSelected: (String) -> Unit,
    onSeriesSelected: (String) -> Unit,
    onReadBook: () -> Unit,
    onReadingSessions: () -> Unit,
    onAddToShelf: () -> Unit,
    readAvailable: Boolean,
    serverActionsAvailable: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            BookDetailCover(
                book,
                Modifier.fillMaxWidth(MEDIUM_COVER_WIDTH_FRACTION).widthIn(max = 280.dp)
            )
            BookDetailMetadata(
                book,
                onAuthorSelected,
                onSeriesSelected,
                Modifier.weight(1f)
            )
        }
        BookDetailActions(
            onReadBook,
            onReadingSessions,
            onAddToShelf,
            readBookEnabled = book.hasReadableEpub && readAvailable,
            serverActionsAvailable = serverActionsAvailable,
            BookDetailActionLayout.HORIZONTAL
        )
    }
}

@Composable
private fun BookDetailNarrowHero(
    book: com.secondpasslibrary.client.LibraryBookDetail,
    onAuthorSelected: (String) -> Unit,
    onSeriesSelected: (String) -> Unit,
    onReadBook: () -> Unit,
    onReadingSessions: () -> Unit,
    onAddToShelf: () -> Unit,
    readAvailable: Boolean,
    serverActionsAvailable: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            BookDetailCover(book, Modifier.widthIn(max = 220.dp))
        }
        BookDetailMetadata(book, onAuthorSelected, onSeriesSelected)
        BookDetailActions(
            onReadBook,
            onReadingSessions,
            onAddToShelf,
            readBookEnabled = book.hasReadableEpub && readAvailable,
            serverActionsAvailable = serverActionsAvailable,
            BookDetailActionLayout.HORIZONTAL
        )
    }
}

private val com.secondpasslibrary.client.LibraryBookDetail.hasReadableEpub: Boolean
    get() = file?.format.equals("epub", ignoreCase = true)
