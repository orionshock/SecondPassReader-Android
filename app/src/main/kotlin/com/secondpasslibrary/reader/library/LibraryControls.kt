package com.secondpasslibrary.reader.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.secondpasslibrary.client.AuthorOrdering
import com.secondpasslibrary.client.SeriesOrdering
import com.secondpasslibrary.reader.design.icons.AppIcon
import com.secondpasslibrary.reader.design.icons.AppIconGraphic

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
    modifier: Modifier = Modifier
) {
    val committedQuery = state.committedQuery()
    var query by rememberSaveable(state.axis, committedQuery) { mutableStateOf(committedQuery) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Header(state)
        SearchRow(state.axis, state.books.mode, query, { query = it }) { onSearch(query) }
        LibrarySelectorRow(
            state,
            onScopeSelected,
            onAxisSelected,
            onRetryGroups,
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
}

@Composable
private fun Header(state: LibraryState) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(state.axis.label, style = MaterialTheme.typography.titleLarge)
        if (state.axis == LibraryAxis.BOOKS && state.books.mode == LibraryBooksMode.BROAD_SEARCH) {
            Text(
                "Global results",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelMedium
            )
        }
        state.resultCount()?.let { count ->
            Text(
                "$count ${state.axis.countLabel(count)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
private fun SearchRow(
    axis: LibraryAxis,
    booksMode: LibraryBooksMode,
    query: String,
    onQueryChanged: (String) -> Unit,
    onSearch: () -> Unit
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val submit = {
        keyboard?.hide()
        onSearch()
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChanged,
            modifier = Modifier.weight(1f),
            placeholder = { Text(axis.searchPlaceholder(booksMode)) },
            leadingIcon = { AppIconGraphic(AppIcon.Search, null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { submit() })
        )
        Button(onClick = submit) {
            AppIconGraphic(AppIcon.Search, null)
            Text("Search", Modifier.padding(start = 6.dp))
        }
    }
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
            when (state.axis) {
                LibraryAxis.BOOKS ->
                    libraryOrderingOptions(state.books.mode).forEach { option ->
                        OrderingItem(option.label, option.ordering == state.books.ordering) {
                            onBookSelected(option.ordering)
                            expanded = false
                        }
                    }

                LibraryAxis.AUTHORS ->
                    authorOrderingOptions().forEach { option ->
                        OrderingItem(option.label, option.ordering == state.authors.ordering) {
                            onAuthorSelected(option.ordering)
                            expanded = false
                        }
                    }

                LibraryAxis.SERIES ->
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
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = selected == LibraryBooksLayout.LIST,
            onClick = { onSelected(LibraryBooksLayout.LIST) },
            label = { Text("List") }
        )
        FilterChip(
            selected = selected == LibraryBooksLayout.GRID,
            onClick = { onSelected(LibraryBooksLayout.GRID) },
            label = { Text("Grid") }
        )
    }
}

internal val LibraryAxis.label: String
    get() = name.lowercase().replaceFirstChar(Char::uppercase)

internal fun LibraryState.committedQuery(): String = when (axis) {
    LibraryAxis.BOOKS -> books.committedQuery
    LibraryAxis.AUTHORS -> authors.committedQuery
    LibraryAxis.SERIES -> series.committedQuery
}

internal fun LibraryState.resultCount(): Int? = when (axis) {
    LibraryAxis.BOOKS -> books.totalCount.takeIf { books.currentPage > 0 }
    LibraryAxis.AUTHORS -> authors.totalCount.takeIf { authors.currentPage > 0 }
    LibraryAxis.SERIES -> series.totalCount.takeIf { series.currentPage > 0 }
}

internal fun LibraryState.orderingLabel(): String = when (axis) {
    LibraryAxis.BOOKS -> books.ordering.label()
    LibraryAxis.AUTHORS -> authors.ordering.libraryLabel()
    LibraryAxis.SERIES -> series.ordering.libraryLabel()
}

private fun LibraryAxis.countLabel(count: Int) = when (this) {
    LibraryAxis.BOOKS -> if (count == 1) "book" else "books"
    LibraryAxis.AUTHORS -> if (count == 1) "author" else "authors"
    LibraryAxis.SERIES -> "series"
}

private fun LibraryAxis.searchPlaceholder(booksMode: LibraryBooksMode) = when (this) {
    LibraryAxis.BOOKS ->
        if (booksMode == LibraryBooksMode.BROAD_SEARCH) {
            "Title, author, series, publisher, or tag"
        } else {
            "Search book titles"
        }

    LibraryAxis.AUTHORS -> "Search authors"

    LibraryAxis.SERIES -> "Search series"
}
