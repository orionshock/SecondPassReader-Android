package com.secondpasslibrary.reader.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

@Composable
internal fun LibraryBooksResults(
    state: LibraryBooksState,
    onLoadNextPage: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    when {
        state.books.isEmpty() && state.initialLoading -> LoadingLibrary(modifier)

        state.books.isEmpty() && state.error != null ->
            LibraryFailure(state.error, onRetry, modifier)

        state.books.isEmpty() && state.currentPage > 0 -> EmptyLibrary(modifier)

        else ->
            Column(modifier) {
                ReplacementFeedback(state, onRetry)
                val stateHolder = rememberSaveableStateHolder()
                stateHolder.SaveableStateProvider(state.layout) {
                    when (state.layout) {
                        LibraryBooksLayout.LIST ->
                            LibraryBooksList(state, onLoadNextPage, onRetry, Modifier.weight(1f))

                        LibraryBooksLayout.GRID ->
                            LibraryBooksGrid(state, onLoadNextPage, onRetry, Modifier.weight(1f))
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
    modifier: Modifier
) {
    val listState = rememberLazyListState()
    NextPageEffect(listState, state, onLoadNextPage)
    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)
    ) {
        items(state.books, key = { it.id }) { book ->
            LibraryBookRow(book.toLibraryPresentation())
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        item { NextPageFooter(state, onRetry) }
    }
}

@Composable
private fun LibraryBooksGrid(
    state: LibraryBooksState,
    onLoadNextPage: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier
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
            LibraryBookGridCard(book.toLibraryPresentation())
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
            .map { shouldRequestNextPage(it, state.books.size) && state.hasNext }
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
            .map { shouldRequestNextPage(it, state.books.size) && state.hasNext }
            .distinctUntilChanged()
            .filter { it }
            .collect { onLoadNextPage() }
    }
}
