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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.secondpasslibrary.reader.design.components.ContextualAppBar

private const val MEDIUM_COVER_WIDTH_FRACTION = 0.38f
private val WIDE_CONTENT_MAX_WIDTH = 1080.dp
private val WIDE_COVER_WIDTH = 252.dp

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
        Text("Loading Book", modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun DetailFailure(onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Couldn’t load Book details. Retry.")
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
                    BookDetailLayout.WIDE -> BookDetailWideContent(
                        book,
                        onAuthorSelected,
                        onSeriesSelected,
                        onTagSelected,
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
            if (layout != BookDetailLayout.WIDE) {
                item { BookDetailSupportingContent(book, onTagSelected) }
            }
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
private fun BookDetailWideContent(
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
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Row(
            modifier =
                Modifier
                    .widthIn(max = WIDE_CONTENT_MAX_WIDTH)
                    .fillMaxWidth()
                    .testTag(BOOK_DETAIL_WIDE_TAG),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(
                Modifier.width(WIDE_COVER_WIDTH),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                BookDetailCover(book, Modifier.fillMaxWidth())
                BookDetailActions(
                    onReadBook,
                    onReadingSessions,
                    onAddToShelf,
                    readBookEnabled = book.hasReadableEpub && readAvailable,
                    serverActionsAvailable = serverActionsAvailable,
                    BookDetailActionLayout.VERTICAL
                )
            }
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(28.dp)
            ) {
                BookDetailMetadata(book, onAuthorSelected, onSeriesSelected)
                BookDetailSupportingContent(book, onTagSelected)
            }
        }
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
    Column(
        Modifier.testTag(BOOK_DETAIL_MEDIUM_TAG),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
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
    Column(
        Modifier.testTag(BOOK_DETAIL_NARROW_TAG),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
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

internal const val BOOK_DETAIL_WIDE_TAG = "book-detail-wide"
internal const val BOOK_DETAIL_MEDIUM_TAG = "book-detail-medium"
internal const val BOOK_DETAIL_NARROW_TAG = "book-detail-narrow"
