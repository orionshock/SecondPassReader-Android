package com.secondpasslibrary.reader.library.books

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.secondpasslibrary.reader.design.book.BookCardAction
import com.secondpasslibrary.reader.design.book.CompactBookGridCard
import com.secondpasslibrary.reader.design.book.CompactBookRow
import com.secondpasslibrary.reader.design.book.CompactBookRowLayout
import com.secondpasslibrary.reader.library.presentation.LibraryBrowseLoading
import com.secondpasslibrary.reader.library.presentation.LibraryPagingTriggerPolicy
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

@Composable
internal fun LibraryBooksResults(
    state: LibraryBooksState,
    onLoadNextPage: () -> Unit,
    onRetry: () -> Unit,
    onBookSelected: (String) -> Unit,
    onBookAction: (BookCardAction) -> Unit,
    modifier: Modifier = Modifier,
    availableBookIds: Set<String> = emptySet(),
    busyBookIds: Set<String> = emptySet()
) {
    when {
        state.books.isEmpty() && state.initialLoading -> LibraryBrowseLoading(modifier)

        state.books.isEmpty() && state.error != null ->
            LibraryFailureContent(state.error, onRetry, modifier)

        state.books.isEmpty() && state.currentPage > 0 ->
            if (state.offlineDownloadedOnly) {
                OfflineDownloadedLibraryEmpty(modifier)
            } else {
                EmptyLibrary(modifier)
            }

        else ->
            Column(modifier) {
                ReplacementFeedback(state, onRetry)
                val stateHolder = rememberSaveableStateHolder()
                stateHolder.SaveableStateProvider(state.layout) {
                    when (state.layout) {
                        LibraryBooksLayout.LIST ->
                            LibraryBooksList(
                                state,
                                onLoadNextPage,
                                onRetry,
                                onBookSelected,
                                onBookAction,
                                Modifier.weight(1f),
                                availableBookIds,
                                busyBookIds
                            )

                        LibraryBooksLayout.GRID ->
                            LibraryBooksGrid(
                                state,
                                onLoadNextPage,
                                onRetry,
                                onBookSelected,
                                onBookAction,
                                Modifier.weight(1f),
                                availableBookIds,
                                busyBookIds
                            )
                    }
                }
            }
    }
}

@Composable
private fun LibraryBooksList(
    state: LibraryBooksState,
    onLoadNextPage: () -> Unit,
    onRetry: () -> Unit,
    onBookSelected: (String) -> Unit,
    onBookAction: (BookCardAction) -> Unit,
    modifier: Modifier,
    availableBookIds: Set<String>,
    busyBookIds: Set<String>
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val rowLayout = libraryBookRowLayoutForWidth(maxWidth)
        val listState = rememberLazyListState()
        NextPageEffect(listState, state, onLoadNextPage)
        LazyColumn(
            state = listState,
            modifier = Modifier.matchParentSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)
        ) {
            items(state.books, key = { it.id }) { book ->
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(Modifier.fillMaxWidth().widthIn(max = LIBRARY_LIST_MAX_WIDTH)) {
                        CompactBookRow(
                            book = book.toLibraryCompactBookPresentation(),
                            onClick = { onBookSelected(book.id) },
                            actions = book.bookCardActions(
                                downloaded =
                                    state.offlineDownloadedOnly || book.id in availableBookIds,
                                offline = state.offlineDownloadedOnly,
                                busy = book.id in busyBookIds
                            ),
                            onAction = onBookAction,
                            layout = rowLayout
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
            item { NextPageFooter(state, onRetry) }
        }
    }
}

internal fun libraryBookRowLayoutForWidth(width: Dp): CompactBookRowLayout =
    if (width >= LIBRARY_WIDE_LIST_BREAKPOINT) {
        CompactBookRowLayout.WIDE
    } else {
        CompactBookRowLayout.COMPACT
    }

private val LIBRARY_WIDE_LIST_BREAKPOINT = 900.dp
private val LIBRARY_LIST_MAX_WIDTH = 1160.dp

@Composable
private fun LibraryBooksGrid(
    state: LibraryBooksState,
    onLoadNextPage: () -> Unit,
    onRetry: () -> Unit,
    onBookSelected: (String) -> Unit,
    onBookAction: (BookCardAction) -> Unit,
    modifier: Modifier,
    availableBookIds: Set<String>,
    busyBookIds: Set<String>
) {
    val gridState = rememberLazyGridState()
    NextPageEffect(gridState, state, onLoadNextPage)
    LazyVerticalGrid(
        columns = GridCells.Adaptive(148.dp),
        state = gridState,
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(state.books, key = { it.id }) { book ->
            CompactBookGridCard(
                book = book.toLibraryCompactBookPresentation(),
                onClick = { onBookSelected(book.id) },
                actions = book.bookCardActions(
                    downloaded = state.offlineDownloadedOnly || book.id in availableBookIds,
                    offline = state.offlineDownloadedOnly,
                    busy = book.id in busyBookIds
                ),
                onAction = onBookAction
            )
        }
        item(span = { GridItemSpan(maxLineSpan) }) { NextPageFooter(state, onRetry) }
    }
}

@Composable
private fun NextPageEffect(
    listState: LazyListState,
    state: LibraryBooksState,
    onLoadNextPage: () -> Unit
) {
    LaunchedEffect(listState, state.books.size, state.hasNext) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .map {
                LibraryPagingTriggerPolicy.shouldRequestNextPage(it, state.books.size) &&
                    state.hasNext
            }
            .distinctUntilChanged()
            .filter { it }
            .collect { onLoadNextPage() }
    }
}

@Composable
private fun NextPageEffect(
    gridState: LazyGridState,
    state: LibraryBooksState,
    onLoadNextPage: () -> Unit
) {
    LaunchedEffect(gridState, state.books.size, state.hasNext) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .map {
                LibraryPagingTriggerPolicy.shouldRequestNextPage(it, state.books.size) &&
                    state.hasNext
            }
            .distinctUntilChanged()
            .filter { it }
            .collect { onLoadNextPage() }
    }
}
