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
import com.secondpasslibrary.client.LibraryCatalogTag
import com.secondpasslibrary.client.LibraryScope

@Composable
internal fun LibraryScreen(viewModel: LibraryViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LibraryContent(
        state = state,
        onSearch = viewModel::commitSearch,
        onBookOrderingSelected = { ordering -> viewModel.selectOrdering(ordering) },
        onAuthorOrderingSelected = viewModel::changeAuthorOrdering,
        onSeriesOrderingSelected = viewModel::changeSeriesOrdering,
        onLayoutSelected = viewModel::setLayout,
        onScopeSelected = viewModel::selectScope,
        onAxisSelected = viewModel::selectAxis,
        onRetryGroups = viewModel::retryGroups,
        onTagSelected = viewModel::selectTag,
        onRetryTags = viewModel::retryTags,
        onLoadNextPage = viewModel::loadNextPage,
        onRetry = viewModel::retry,
        onAuthorSelected = viewModel::selectAuthor,
        onSeriesSelected = viewModel::selectSeries,
        onClearSelectedEntity = viewModel::clearSelectedEntity,
        onRetryAuthorDetail = viewModel::retryAuthorDetail,
        onRetrySeriesDetail = viewModel::retrySeriesDetail
    )
}

@Composable
private fun LibraryContent(
    state: LibraryState,
    onSearch: (String) -> Unit,
    onBookOrderingSelected: (LibraryBooksOrdering) -> Unit,
    onAuthorOrderingSelected: (com.secondpasslibrary.client.AuthorOrdering) -> Unit,
    onSeriesOrderingSelected: (com.secondpasslibrary.client.SeriesOrdering) -> Unit,
    onLayoutSelected: (LibraryBooksLayout) -> Unit,
    onScopeSelected: (LibraryScope) -> Unit,
    onAxisSelected: (LibraryAxis) -> Unit,
    onRetryGroups: () -> Unit,
    onTagSelected: (LibraryCatalogTag?) -> Unit,
    onRetryTags: () -> Unit,
    onLoadNextPage: () -> Unit,
    onRetry: () -> Unit,
    onAuthorSelected: (String) -> Unit,
    onSeriesSelected: (String) -> Unit,
    onClearSelectedEntity: () -> Unit,
    onRetryAuthorDetail: () -> Unit,
    onRetrySeriesDetail: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        LibraryControls(
            state,
            onSearch,
            onBookOrderingSelected,
            onAuthorOrderingSelected,
            onSeriesOrderingSelected,
            onLayoutSelected,
            onScopeSelected,
            onAxisSelected,
            onRetryGroups,
            onTagSelected,
            onRetryTags,
            Modifier.padding(top = 14.dp, bottom = 12.dp)
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        when {
            state.resultKind == LibraryResultKind.BOOKS ->
                FilterableBooksResults(
                    state,
                    onLoadNextPage,
                    onRetry,
                    onRetryAuthorDetail,
                    onRetrySeriesDetail,
                    onClearSelectedEntity,
                    Modifier.weight(1f)
                )

            state.axis == LibraryAxis.AUTHORS ->
                LibraryAuthorsResults(
                    state.authors,
                    onAuthorSelected,
                    onLoadNextPage,
                    onRetry,
                    onRetryAuthorDetail,
                    Modifier.weight(1f)
                )

            else ->
                LibrarySeriesResults(
                    state.series,
                    onSeriesSelected,
                    onLoadNextPage,
                    onRetry,
                    onRetrySeriesDetail,
                    Modifier.weight(1f)
                )
        }
    }
}

@Composable
private fun FilterableBooksResults(
    state: LibraryState,
    onLoadNextPage: () -> Unit,
    onRetry: () -> Unit,
    onRetryAuthorDetail: () -> Unit,
    onRetrySeriesDetail: () -> Unit,
    onClearSelectedEntity: () -> Unit,
    modifier: Modifier
) {
    Column(modifier) {
        when (state.axis) {
            LibraryAxis.BOOKS -> Unit

            LibraryAxis.AUTHORS ->
                state.authors.selected?.let { selected ->
                    SelectedEntityHeader(
                        selected.toAuthorDetailPresentation(),
                        onRetryAuthorDetail,
                        "Back to authors",
                        onClearSelectedEntity
                    )
                }

            LibraryAxis.SERIES ->
                state.series.selected?.let { selected ->
                    SelectedEntityHeader(
                        selected.toSeriesDetailPresentation(),
                        onRetrySeriesDetail,
                        "Back to series",
                        onClearSelectedEntity
                    )
                }
        }
        LibraryBooksResults(state.books, onLoadNextPage, onRetry, Modifier.weight(1f))
    }
}

private fun LibraryViewModel.selectOrdering(ordering: LibraryBooksOrdering) {
    when (ordering) {
        is LibraryBooksOrdering.Browse -> changeBrowseOrdering(ordering.value)
        is LibraryBooksOrdering.BroadSearch -> changeBroadSearchOrdering(ordering.value)
    }
}
