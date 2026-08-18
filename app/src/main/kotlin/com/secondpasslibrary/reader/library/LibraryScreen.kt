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
internal fun LibraryScreen(viewModel: LibraryViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LibraryContent(
        state = state,
        onSearch = viewModel::commitSearch,
        onOrderingSelected = { ordering -> viewModel.selectOrdering(ordering) },
        onLayoutSelected = viewModel::setLayout,
        onScopeSelected = viewModel::selectScope,
        onAxisSelected = viewModel::selectAxis,
        onRetryGroups = viewModel::retryGroups,
        onLoadNextPage = viewModel::loadNextPage,
        onRetry = viewModel::retry
    )
}

@Composable
private fun LibraryContent(
    state: LibraryState,
    onSearch: (String) -> Unit,
    onOrderingSelected: (LibraryBooksOrdering) -> Unit,
    onLayoutSelected: (LibraryBooksLayout) -> Unit,
    onScopeSelected: (LibraryScope) -> Unit,
    onAxisSelected: (LibraryAxis) -> Unit,
    onRetryGroups: () -> Unit,
    onLoadNextPage: () -> Unit,
    onRetry: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        LibraryBooksControls(
            state,
            onSearch,
            onOrderingSelected,
            onLayoutSelected,
            onScopeSelected,
            onAxisSelected,
            onRetryGroups,
            Modifier.padding(top = 14.dp, bottom = 12.dp)
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        if (state.axis == LibraryAxis.BOOKS) {
            LibraryBooksResults(state.books, onLoadNextPage, onRetry, Modifier.weight(1f))
        } else {
            LibraryAxisPlaceholder(state.axis, Modifier.weight(1f))
        }
    }
}

@Composable
private fun LibraryAxisPlaceholder(axis: LibraryAxis, modifier: Modifier) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        androidx.compose.material3.Text(
            "${if (axis == LibraryAxis.AUTHORS) "Authors" else "Series"} browsing is coming next.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun LibraryViewModel.selectOrdering(ordering: LibraryBooksOrdering) {
    when (ordering) {
        is LibraryBooksOrdering.Browse -> changeBrowseOrdering(ordering.value)
        is LibraryBooksOrdering.BroadSearch -> changeBroadSearchOrdering(ordering.value)
    }
}
