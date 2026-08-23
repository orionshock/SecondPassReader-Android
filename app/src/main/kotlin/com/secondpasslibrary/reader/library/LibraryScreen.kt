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
import com.secondpasslibrary.reader.design.book.BookCardAction
import com.secondpasslibrary.reader.design.components.ContextualAppBar
import com.secondpasslibrary.reader.library.axis.LibraryAuthorsResults
import com.secondpasslibrary.reader.library.axis.LibrarySeriesResults
import com.secondpasslibrary.reader.library.axis.SelectedAuthorSeriesHeader
import com.secondpasslibrary.reader.library.axis.toAuthorDetailPresentation
import com.secondpasslibrary.reader.library.axis.toSeriesDetailPresentation
import com.secondpasslibrary.reader.library.books.LibraryBooksLayout
import com.secondpasslibrary.reader.library.books.LibraryBooksOrdering
import com.secondpasslibrary.reader.library.books.LibraryBooksResults
import com.secondpasslibrary.reader.library.chrome.LibraryControls

@Composable
internal fun LibraryScreen(
    viewModel: LibraryViewModel,
    onOpenDrawer: () -> Unit,
    onBookSelected: (String) -> Unit,
    onBookAction: (BookCardAction) -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler(
        enabled = state.resultKind == LibraryResultKind.BOOKS && state.axis != LibraryAxis.BOOKS,
        onBack = viewModel::clearSelectedAuthorSeries
    )
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
        onRetryAuthorDetail = viewModel::retryAuthorDetail,
        onRetrySeriesDetail = viewModel::retrySeriesDetail,
        onBookSelected = onBookSelected,
        onBookAction = onBookAction,
        onOpenDrawer = onOpenDrawer
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
    onRetryAuthorDetail: () -> Unit,
    onRetrySeriesDetail: () -> Unit,
    onBookSelected: (String) -> Unit,
    onBookAction: (BookCardAction) -> Unit,
    onOpenDrawer: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        ContextualAppBar(state.appBarPresentation(), onNavigation = onOpenDrawer)
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
            onRetryAuthorDetail,
            onRetrySeriesDetail,
            onBookSelected,
            onBookAction
        )
    }
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
    onRetryAuthorDetail: () -> Unit,
    onRetrySeriesDetail: () -> Unit,
    onBookSelected: (String) -> Unit,
    onBookAction: (BookCardAction) -> Unit
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
                    onBookSelected,
                    onBookAction,
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
    onBookSelected: (String) -> Unit,
    onBookAction: (BookCardAction) -> Unit,
    modifier: Modifier
) {
    Column(modifier) {
        when (state.axis) {
            LibraryAxis.BOOKS -> Unit

            LibraryAxis.AUTHORS ->
                state.authors.selected?.let { selected ->
                    SelectedAuthorSeriesHeader(
                        selected.toAuthorDetailPresentation(),
                        onRetryAuthorDetail
                    )
                }

            LibraryAxis.SERIES ->
                state.series.selected?.let { selected ->
                    SelectedAuthorSeriesHeader(
                        selected.toSeriesDetailPresentation(),
                        onRetrySeriesDetail
                    )
                }
        }
        LibraryBooksResults(
            state.books,
            onLoadNextPage,
            onRetry,
            onBookSelected,
            onBookAction,
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
