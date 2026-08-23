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
import com.secondpasslibrary.reader.library.LibraryResultKind
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
    var query by rememberSaveable(state.axis, committedQuery) { mutableStateOf(committedQuery) }
    var tagSheetOpen by rememberSaveable { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SearchRow(state, query, { query = it }) { onSearch(query) }
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
            { LayoutChoices(state.books.layout, onLayoutSelected) }
        )
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
            when {
                state.resultKind == LibraryResultKind.BOOKS ->
                    libraryOrderingOptions(state.books.mode, state.books.filter).forEach { option ->
                        OrderingItem(option.label, option.ordering == state.books.ordering) {
                            onBookSelected(option.ordering)
                            expanded = false
                        }
                    }

                state.axis == LibraryAxis.AUTHORS ->
                    authorOrderingOptions().forEach { option ->
                        OrderingItem(option.label, option.ordering == state.authors.ordering) {
                            onAuthorSelected(option.ordering)
                            expanded = false
                        }
                    }

                else ->
                    seriesOrderingOptions().forEach { option ->
                        OrderingItem(option.label, option.ordering == state.series.ordering) {
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

internal fun LibraryState.committedQuery(): String = when {
    resultKind == LibraryResultKind.BOOKS -> books.committedQuery
    axis == LibraryAxis.AUTHORS -> authors.committedQuery
    else -> series.committedQuery
}

internal fun LibraryState.resultCount(): Int? = when {
    resultKind == LibraryResultKind.BOOKS -> books.totalCount.takeIf { books.currentPage > 0 }
    axis == LibraryAxis.AUTHORS -> authors.totalCount.takeIf { authors.currentPage > 0 }
    else -> series.totalCount.takeIf { series.currentPage > 0 }
}

internal fun LibraryState.orderingLabel(): String = when {
    resultKind == LibraryResultKind.BOOKS -> books.ordering.label()
    axis == LibraryAxis.AUTHORS -> authors.ordering.libraryLabel()
    else -> series.ordering.libraryLabel()
}

internal fun LibraryState.countLabel(count: Int) = when {
    resultKind == LibraryResultKind.BOOKS -> if (count == 1) "book" else "books"
    axis == LibraryAxis.AUTHORS -> if (count == 1) "author" else "authors"
    else -> "series"
}

internal fun LibraryState.searchPlaceholder() = when {
    books.filter is LibraryBooksFilter.Author -> "Search books by this author"

    books.filter is LibraryBooksFilter.Series -> "Search books in this series"

    axis == LibraryAxis.BOOKS ->
        if (books.mode == LibraryBooksMode.BROAD_SEARCH) {
            "Title, author, series, publisher, or tag"
        } else {
            "Search book titles"
        }

    axis == LibraryAxis.AUTHORS -> "Search authors"

    else -> "Search series"
}
