package com.secondpasslibrary.reader.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
internal fun LibraryBooksScreen(viewModel: LibraryBooksViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LibraryBooksContent(
        state = state,
        onSearch = viewModel::commitSearch,
        onOrderingSelected = { ordering -> viewModel.selectOrdering(ordering) },
        onLayoutSelected = viewModel::setLayout,
        onLoadNextPage = viewModel::loadNextPage,
        onRetry = viewModel::retry
    )
}

@Composable
private fun LibraryBooksContent(
    state: LibraryBooksState,
    onSearch: (String) -> Unit,
    onOrderingSelected: (LibraryBooksOrdering) -> Unit,
    onLayoutSelected: (LibraryBooksLayout) -> Unit,
    onLoadNextPage: () -> Unit,
    onRetry: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        LibraryBooksControls(
            state,
            onSearch,
            onOrderingSelected,
            onLayoutSelected,
            Modifier.padding(top = 14.dp, bottom = 12.dp)
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        LibraryBooksResults(state, onLoadNextPage, onRetry, Modifier.weight(1f))
    }
}

private fun LibraryBooksViewModel.selectOrdering(ordering: LibraryBooksOrdering) {
    when (ordering) {
        is LibraryBooksOrdering.Browse -> changeBrowseOrdering(ordering.value)

        is LibraryBooksOrdering.BroadSearch ->
            changeBroadSearchOrdering(ordering.value)
    }
}
