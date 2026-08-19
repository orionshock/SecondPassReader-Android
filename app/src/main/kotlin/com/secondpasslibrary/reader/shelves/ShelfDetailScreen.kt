package com.secondpasslibrary.reader.shelves

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.secondpasslibrary.client.ShelfItemOrdering
import com.secondpasslibrary.reader.design.book.CompactBookGridCard
import com.secondpasslibrary.reader.design.book.CompactBookRow
import com.secondpasslibrary.reader.design.book.toCompactBookPresentation
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

@Composable
internal fun ShelfDetailContent(
    state: ShelfDetailState,
    onOrderingSelected: (ShelfItemOrdering) -> Unit,
    onLayoutSelected: (ShelfBooksLayout) -> Unit,
    onLoadNextPage: () -> Unit,
    onRetryDetail: () -> Unit,
    onRetryItems: () -> Unit,
    onBookSelected: (String) -> Unit,
    canManage: Boolean,
    onManageContents: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.padding(horizontal = 20.dp)) {
        ShelfDetailHeader(
            state.detail,
            onRetryDetail,
            canManage,
            onManageContents,
            onEdit,
            onDelete,
            Modifier.padding(top = 14.dp)
        )
        ShelfItemControls(
            state.items,
            onOrderingSelected,
            onLayoutSelected,
            Modifier.padding(top = 12.dp, bottom = 8.dp)
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        ShelfItemResults(
            state.items,
            onLoadNextPage,
            onRetryItems,
            onBookSelected,
            Modifier.weight(1f)
        )
    }
}

@Composable
private fun ShelfItemResults(
    state: ShelfItemsState,
    onLoadNextPage: () -> Unit,
    onRetry: () -> Unit,
    onBookSelected: (String) -> Unit,
    modifier: Modifier
) {
    when {
        state.items.isEmpty() && state.initialLoading -> ShelvesLoading(modifier)

        state.items.isEmpty() && state.error != null -> ShelvesFailure(
            state.error,
            onRetry,
            modifier
        )

        state.items.isEmpty() && state.currentPage > 0 ->
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("This shelf has no visible books.")
            }

        else -> {
            val stateHolder = rememberSaveableStateHolder()
            stateHolder.SaveableStateProvider(state.layout) {
                when (state.layout) {
                    ShelfBooksLayout.LIST ->
                        ShelfItemList(state, onLoadNextPage, onRetry, onBookSelected, modifier)

                    ShelfBooksLayout.GRID ->
                        ShelfItemGrid(state, onLoadNextPage, onRetry, onBookSelected, modifier)
                }
            }
        }
    }
}

@Composable
private fun ShelfItemList(
    state: ShelfItemsState,
    onLoadNextPage: () -> Unit,
    onRetry: () -> Unit,
    onBookSelected: (String) -> Unit,
    modifier: Modifier
) {
    val listState = rememberLazyListState()
    ShelfItemNextPageEffect(listState, state, onLoadNextPage)
    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(state.items, key = { it.id }) { item ->
            CompactBookRow(item.book.toCompactBookPresentation()) { onBookSelected(item.book.id) }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        item { ShelvesNextPageFooter(state.nextPageLoading, state.error, onRetry) }
    }
}

@Composable
private fun ShelfItemGrid(
    state: ShelfItemsState,
    onLoadNextPage: () -> Unit,
    onRetry: () -> Unit,
    onBookSelected: (String) -> Unit,
    modifier: Modifier
) {
    val gridState = rememberLazyGridState()
    ShelfItemNextPageEffect(gridState, state, onLoadNextPage)
    LazyVerticalGrid(
        columns = GridCells.Adaptive(148.dp),
        state = gridState,
        modifier = modifier,
        contentPadding = PaddingValues(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(state.items, key = { it.id }) { item ->
            CompactBookGridCard(item.book.toCompactBookPresentation()) {
                onBookSelected(item.book.id)
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            ShelvesNextPageFooter(state.nextPageLoading, state.error, onRetry)
        }
    }
}

@Composable
private fun ShelfItemNextPageEffect(
    listState: androidx.compose.foundation.lazy.LazyListState,
    state: ShelfItemsState,
    onLoadNextPage: () -> Unit
) {
    LaunchedEffect(listState, state.items.size, state.hasNext) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .map { shouldRequestShelfNextPage(it, state.items.size) && state.hasNext }
            .distinctUntilChanged()
            .filter { it }
            .collect { onLoadNextPage() }
    }
}

@Composable
private fun ShelfItemNextPageEffect(
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    state: ShelfItemsState,
    onLoadNextPage: () -> Unit
) {
    LaunchedEffect(gridState, state.items.size, state.hasNext) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .map { shouldRequestShelfNextPage(it, state.items.size) && state.hasNext }
            .distinctUntilChanged()
            .filter { it }
            .collect { onLoadNextPage() }
    }
}
