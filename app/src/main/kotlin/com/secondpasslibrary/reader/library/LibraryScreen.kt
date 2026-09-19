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
import com.secondpasslibrary.reader.app.storage.BookOfflineActionsState
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
    onBookAction: (BookCardAction) -> Unit,
    offlineActions: BookOfflineActionsState = BookOfflineActionsState()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler(
        enabled =
            state.result is LibraryResultState.AuthorBooks ||
                state.result is LibraryResultState.SeriesBooks,
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
        offlineActions = offlineActions,
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
    offlineActions: BookOfflineActionsState,
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
            onBookAction,
            offlineActions
        )
    }
}

@Composable
@Suppress(
    "LongMethod", // Exhaustive rendering keeps all five Library result surfaces visible here.
    "LongParameterList" // The rendering seam receives typed parent-owned intents.
)
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
    onBookAction: (BookCardAction) -> Unit,
    offlineActions: BookOfflineActionsState
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
        when (val result = state.result) {
            is LibraryResultState.Books ->
                LibraryBooksResults(
                    result.state,
                    onLoadNextPage,
                    onRetry,
                    onBookSelected,
                    onBookAction,
                    Modifier.weight(1f),
                    offlineActions.availableBookIds,
                    offlineActions.busyBookIds
                )

            is LibraryResultState.AuthorIndex ->
                LibraryAuthorsResults(
                    result.state,
                    onAuthorSelected,
                    onLoadNextPage,
                    onRetry,
                    onRetryAuthorDetail,
                    Modifier.weight(1f)
                )

            is LibraryResultState.AuthorBooks ->
                FilterableBooksResults(
                    result.books,
                    header = {
                        SelectedAuthorSeriesHeader(
                            result.author.toAuthorDetailPresentation(),
                            onRetryAuthorDetail
                        )
                    },
                    onLoadNextPage,
                    onRetry,
                    onBookSelected,
                    onBookAction,
                    Modifier.weight(1f),
                    offlineActions
                )

            is LibraryResultState.SeriesIndex ->
                LibrarySeriesResults(
                    result.state,
                    onSeriesSelected,
                    onLoadNextPage,
                    onRetry,
                    onRetrySeriesDetail,
                    Modifier.weight(1f)
                )

            is LibraryResultState.SeriesBooks ->
                FilterableBooksResults(
                    result.books,
                    header = {
                        SelectedAuthorSeriesHeader(
                            result.series.toSeriesDetailPresentation(),
                            onRetrySeriesDetail
                        )
                    },
                    onLoadNextPage,
                    onRetry,
                    onBookSelected,
                    onBookAction,
                    Modifier.weight(1f),
                    offlineActions
                )
        }
    }
}

@Composable
private fun FilterableBooksResults(
    books: com.secondpasslibrary.reader.library.books.LibraryBooksState,
    header: @Composable () -> Unit,
    onLoadNextPage: () -> Unit,
    onRetry: () -> Unit,
    onBookSelected: (String) -> Unit,
    onBookAction: (BookCardAction) -> Unit,
    modifier: Modifier,
    offlineActions: BookOfflineActionsState
) {
    Column(modifier) {
        header()
        LibraryBooksResults(
            books,
            onLoadNextPage,
            onRetry,
            onBookSelected,
            onBookAction,
            Modifier.weight(1f),
            offlineActions.availableBookIds,
            offlineActions.busyBookIds
        )
    }
}

private fun LibraryViewModel.selectOrdering(ordering: LibraryBooksOrdering) {
    when (ordering) {
        is LibraryBooksOrdering.Browse -> changeBrowseOrdering(ordering.value)
        is LibraryBooksOrdering.BroadSearch -> changeBroadSearchOrdering(ordering.value)
    }
}
