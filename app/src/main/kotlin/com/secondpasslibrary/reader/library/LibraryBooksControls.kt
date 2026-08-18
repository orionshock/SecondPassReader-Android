package com.secondpasslibrary.reader.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import com.secondpasslibrary.reader.design.icons.AppIcon
import com.secondpasslibrary.reader.design.icons.AppIconGraphic

@Composable
internal fun LibraryBooksControls(
    state: LibraryBooksState,
    onSearch: (String) -> Unit,
    onOrderingSelected: (LibraryBooksOrdering) -> Unit,
    onLayoutSelected: (LibraryBooksLayout) -> Unit,
    onScopeSelected: (LibraryScope) -> Unit,
    onAxisSelected: (LibraryAxis) -> Unit,
    onRetryGroups: () -> Unit,
    modifier: Modifier = Modifier
) {
    var query by rememberSaveable(state.mode, state.committedQuery) {
        mutableStateOf(state.committedQuery)
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(state.axis.label, style = MaterialTheme.typography.titleLarge)
            Text(
                if (state.mode ==
                    LibraryBooksMode.BROAD_SEARCH
                ) {
                    "Global results"
                } else {
                    "Library axis"
                },
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelMedium
            )
            if (state.axis == LibraryAxis.BOOKS && state.currentPage > 0) {
                Text(
                    "${state.totalCount} ${if (state.totalCount == 1) "book" else "books"}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
        SearchRow(
            state.mode,
            query,
            state.axis == LibraryAxis.BOOKS,
            { query = it }
        ) { onSearch(query) }
        LibrarySelectorRow(
            state,
            onScopeSelected,
            onAxisSelected,
            onRetryGroups,
            { OrderingMenu(state, onOrderingSelected) },
            { LayoutChoices(state.layout, onLayoutSelected) }
        )
    }
}

@Composable
private fun SearchRow(
    mode: LibraryBooksMode,
    query: String,
    enabled: Boolean,
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
            enabled = enabled,
            modifier = Modifier.weight(1f),
            placeholder = {
                Text(
                    if (mode == LibraryBooksMode.BROAD_SEARCH) {
                        "Title, author, series, publisher, or tag"
                    } else {
                        "Search book titles"
                    }
                )
            },
            leadingIcon = { AppIconGraphic(AppIcon.Search, null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { submit() })
        )
        Button(onClick = submit, enabled = enabled) {
            AppIconGraphic(AppIcon.Search, null)
            Text("Search", Modifier.padding(start = 6.dp))
        }
    }
}

private val LibraryAxis.label: String
    get() = when (this) {
        LibraryAxis.BOOKS -> "Books"
        LibraryAxis.AUTHORS -> "Authors"
        LibraryAxis.SERIES -> "Series"
    }

@Composable
private fun OrderingMenu(
    state: LibraryBooksState,
    onOrderingSelected: (LibraryBooksOrdering) -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            AppIconGraphic(AppIcon.SortAlphabetical, null)
            Text(state.ordering.label(), Modifier.padding(start = 6.dp))
            AppIconGraphic(AppIcon.Expand, null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            libraryOrderingOptions(state.mode).forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        expanded = false
                        onOrderingSelected(option.ordering)
                    },
                    leadingIcon = {
                        if (option.ordering == state.ordering) {
                            AppIconGraphic(AppIcon.Confirm, null)
                        }
                    }
                )
            }
        }
    }
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
