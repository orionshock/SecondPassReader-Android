package com.secondpasslibrary.reader.library.chrome

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.secondpasslibrary.client.AuthorOrdering
import com.secondpasslibrary.client.LibraryCatalogTag
import com.secondpasslibrary.client.LibraryScope
import com.secondpasslibrary.client.SeriesOrdering
import com.secondpasslibrary.reader.design.components.BinarySegmentedIconToggle
import com.secondpasslibrary.reader.design.components.InlineSearchField
import com.secondpasslibrary.reader.design.components.SegmentedIconOption
import com.secondpasslibrary.reader.design.icons.AppIcon
import com.secondpasslibrary.reader.design.icons.AppIconGraphic
import com.secondpasslibrary.reader.library.LibraryAxis
import com.secondpasslibrary.reader.library.LibraryResultState
import com.secondpasslibrary.reader.library.LibraryState
import com.secondpasslibrary.reader.library.axis.authorOrderingOptions
import com.secondpasslibrary.reader.library.axis.libraryLabel
import com.secondpasslibrary.reader.library.axis.seriesOrderingOptions
import com.secondpasslibrary.reader.library.books.LibraryBooksFilter
import com.secondpasslibrary.reader.library.books.LibraryBooksLayout
import com.secondpasslibrary.reader.library.books.LibraryBooksMode
import com.secondpasslibrary.reader.library.books.LibraryBooksOrdering
import com.secondpasslibrary.reader.library.books.label
import com.secondpasslibrary.reader.library.books.libraryOrderingOptions
import com.secondpasslibrary.reader.library.booksStateOrNull
import com.secondpasslibrary.reader.library.isBookResults

@Composable
internal fun LibraryControls(
    state: LibraryState,
    onSearch: (String) -> Unit,
    onBookOrderingSelected: (LibraryBooksOrdering) -> Unit,
    onAuthorOrderingSelected: (AuthorOrdering) -> Unit,
    onSeriesOrderingSelected: (SeriesOrdering) -> Unit,
    onLayoutSelected: (LibraryBooksLayout) -> Unit,
    onScopeSelected: (LibraryScope) -> Unit,
    onAxisSelected: (LibraryAxis) -> Unit,
    onRetryGroups: () -> Unit,
    onTagSelected: (LibraryCatalogTag?) -> Unit,
    onRetryTags: () -> Unit,
    modifier: Modifier = Modifier
) {
    val committedQuery = state.committedQuery()
    val bookResults = state.result.booksStateOrNull()
    var query by rememberSaveable(state.axis, committedQuery) { mutableStateOf(committedQuery) }
    var tagSheetOpen by rememberSaveable { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SearchRow(state, query, { query = it }) { onSearch(query) }
        if (bookResults?.offlineDownloadedOnly == true) {
            LayoutChoices(bookResults.layout, onLayoutSelected)
        } else {
            LibrarySelectorRow(
                state,
                onScopeSelected,
                onAxisSelected,
                onRetryGroups,
                tagControl = { LibraryTagFilterButton(state.selectedTag) { tagSheetOpen = true } },
                {
                    OrderingMenu(
                        state,
                        onBookOrderingSelected,
                        onAuthorOrderingSelected,
                        onSeriesOrderingSelected
                    )
                },
                { bookResults?.let { LayoutChoices(it.layout, onLayoutSelected) } }
            )
        }
    }
    if (tagSheetOpen) {
        CatalogTagFilterSheet(
            state = state,
            onDismiss = { tagSheetOpen = false },
            onTagSelected = { tag ->
                onTagSelected(tag)
                tagSheetOpen = false
            },
            onRetry = onRetryTags
        )
    }
}

@Composable
private fun SearchRow(
    state: LibraryState,
    query: String,
    onQueryChanged: (String) -> Unit,
    onSearch: () -> Unit
) {
    InlineSearchField(
        query = query,
        placeholder = state.searchPlaceholder(),
        contentDescription = "Search ${state.axis.label.lowercase()}",
        onQueryChanged = onQueryChanged,
        onSubmit = onSearch
    )
}

@Composable
private fun OrderingMenu(
    state: LibraryState,
    onBookSelected: (LibraryBooksOrdering) -> Unit,
    onAuthorSelected: (AuthorOrdering) -> Unit,
    onSeriesSelected: (SeriesOrdering) -> Unit
) {
    var expanded by rememberSaveable(state.axis) { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            AppIconGraphic(AppIcon.SortAlphabetical, null)
            Text(state.orderingLabel(), Modifier.padding(start = 6.dp))
            AppIconGraphic(AppIcon.Expand, null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            when (val result = state.result) {
                is LibraryResultState.Books ->
                    libraryOrderingOptions(
                        result.state.mode,
                        result.state.filter
                    ).forEach { option ->
                        OrderingItem(option.label, option.ordering == result.state.ordering) {
                            onBookSelected(option.ordering)
                            expanded = false
                        }
                    }

                is LibraryResultState.AuthorBooks ->
                    libraryOrderingOptions(
                        result.books.mode,
                        result.books.filter
                    ).forEach { option ->
                        OrderingItem(option.label, option.ordering == result.books.ordering) {
                            onBookSelected(option.ordering)
                            expanded = false
                        }
                    }

                is LibraryResultState.SeriesBooks ->
                    libraryOrderingOptions(
                        result.books.mode,
                        result.books.filter
                    ).forEach { option ->
                        OrderingItem(option.label, option.ordering == result.books.ordering) {
                            onBookSelected(option.ordering)
                            expanded = false
                        }
                    }

                is LibraryResultState.AuthorIndex ->
                    authorOrderingOptions().forEach { option ->
                        OrderingItem(option.label, option.ordering == result.state.ordering) {
                            onAuthorSelected(option.ordering)
                            expanded = false
                        }
                    }

                is LibraryResultState.SeriesIndex ->
                    seriesOrderingOptions().forEach { option ->
                        OrderingItem(option.label, option.ordering == result.state.ordering) {
                            onSeriesSelected(option.ordering)
                            expanded = false
                        }
                    }
            }
        }
    }
}

@Composable
private fun OrderingItem(label: String, selected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        modifier = Modifier.semantics { this.selected = selected },
        text = { Text(label) },
        onClick = onClick,
        leadingIcon = { if (selected) AppIconGraphic(AppIcon.Confirm, null) }
    )
}

@Composable
private fun LayoutChoices(selected: LibraryBooksLayout, onSelected: (LibraryBooksLayout) -> Unit) {
    BinarySegmentedIconToggle(
        selected,
        SegmentedIconOption(LibraryBooksLayout.LIST, AppIcon.ListLayout, "List layout"),
        SegmentedIconOption(LibraryBooksLayout.GRID, AppIcon.GridLayout, "Grid layout"),
        onSelected
    )
}

internal val LibraryAxis.label: String
    get() = name.lowercase().replaceFirstChar(Char::uppercase)

internal fun LibraryState.committedQuery(): String = when (val current = result) {
    is LibraryResultState.Books -> current.state.committedQuery
    is LibraryResultState.AuthorIndex -> current.state.committedQuery
    is LibraryResultState.AuthorBooks -> current.books.committedQuery
    is LibraryResultState.SeriesIndex -> current.state.committedQuery
    is LibraryResultState.SeriesBooks -> current.books.committedQuery
}

internal fun LibraryState.resultCount(): Int? = when (val current = result) {
    is LibraryResultState.Books -> current.state.totalCount.takeIf { current.state.currentPage > 0 }

    is LibraryResultState.AuthorIndex -> current.state.totalCount.takeIf {
        current.state.currentPage >
            0
    }

    is LibraryResultState.AuthorBooks -> current.books.totalCount.takeIf {
        current.books.currentPage >
            0
    }

    is LibraryResultState.SeriesIndex -> current.state.totalCount.takeIf {
        current.state.currentPage >
            0
    }

    is LibraryResultState.SeriesBooks -> current.books.totalCount.takeIf {
        current.books.currentPage >
            0
    }
}

internal fun LibraryState.orderingLabel(): String = when (val current = result) {
    is LibraryResultState.Books -> current.state.ordering.label()
    is LibraryResultState.AuthorIndex -> current.state.ordering.libraryLabel()
    is LibraryResultState.AuthorBooks -> current.books.ordering.label()
    is LibraryResultState.SeriesIndex -> current.state.ordering.libraryLabel()
    is LibraryResultState.SeriesBooks -> current.books.ordering.label()
}

internal fun LibraryState.countLabel(count: Int) = when (result) {
    is LibraryResultState.Books,
    is LibraryResultState.AuthorBooks,
    is LibraryResultState.SeriesBooks -> if (count == 1) "book" else "books"

    is LibraryResultState.AuthorIndex -> if (count == 1) "author" else "authors"

    is LibraryResultState.SeriesIndex -> "series"
}

internal fun LibraryState.searchPlaceholder() = when (val current = result) {
    is LibraryResultState.AuthorBooks -> "Search books by this author"

    is LibraryResultState.SeriesBooks -> "Search books in this series"

    is LibraryResultState.Books ->
        if (current.state.mode == LibraryBooksMode.BROAD_SEARCH) {
            "Title, author, series, publisher, or tag"
        } else {
            "Search book titles"
        }

    is LibraryResultState.AuthorIndex -> "Search authors"

    is LibraryResultState.SeriesIndex -> "Search series"
}
