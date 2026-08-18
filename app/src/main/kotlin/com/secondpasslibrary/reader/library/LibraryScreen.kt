package com.secondpasslibrary.reader.library

import androidx.activity.compose.BackHandler
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
    BackHandler(enabled = state.resultKind == LibraryResultKind.BOOK_DETAIL) {
        viewModel.clearBookDetail()
    }
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
        onRetrySeriesDetail = viewModel::retrySeriesDetail, onBookSelected = viewModel::selectBook,
        onBackFromBook = viewModel::clearBookDetail,
        onRetryBookDetail = viewModel::retryBookDetail,
        onBookAuthorSelected = viewModel::selectBookAuthor,
        onBookSeriesSelected = viewModel::selectBookSeries,
        onBookTagSelected = viewModel::selectBookTag
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
    onRetrySeriesDetail: () -> Unit,
    onBookSelected: (String) -> Unit,
    onBackFromBook: () -> Unit,
    onRetryBookDetail: () -> Unit,
    onBookAuthorSelected: (String) -> Unit,
    onBookSeriesSelected: (String) -> Unit,
    onBookTagSelected: (String, String) -> Unit
) {
    if (state.resultKind == LibraryResultKind.BOOK_DETAIL) {
        LibraryBookDetailScreen(
            state = state.bookDetail,
            onBack = onBackFromBook,
            onRetry = onRetryBookDetail,
            onAuthorSelected = onBookAuthorSelected,
            onSeriesSelected = onBookSeriesSelected,
            onTagSelected = onBookTagSelected,
            availableTagIds = state.tagSelector.tags.mapTo(hashSetOf()) { it.id }
        )
        return
    }
    LibraryBrowseContent(
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
        onLoadNextPage,
        onRetry,
        onAuthorSelected,
        onSeriesSelected,
        onClearSelectedEntity,
        onRetryAuthorDetail,
        onRetrySeriesDetail,
        onBookSelected
    )
}

@Composable
@Suppress("LongParameterList") // The rendering boundary receives typed parent-owned intents.
private fun LibraryBrowseContent(
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
    onRetrySeriesDetail: () -> Unit,
    onBookSelected: (String) -> Unit
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
                    onBookSelected,
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
    onBookSelected: (String) -> Unit,
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
        LibraryBooksResults(
            state.books,
            onLoadNextPage,
            onRetry,
            onBookSelected,
            Modifier.weight(1f)
        )
    }
}

private fun LibraryViewModel.selectOrdering(ordering: LibraryBooksOrdering) {
    when (ordering) {
        is LibraryBooksOrdering.Browse -> changeBrowseOrdering(ordering.value)
        is LibraryBooksOrdering.BroadSearch -> changeBroadSearchOrdering(ordering.value)
    }
}
