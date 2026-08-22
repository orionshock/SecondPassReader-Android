package com.secondpasslibrary.reader.marginalia.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.secondpasslibrary.reader.marginalia.MarginaliaHistoryContext

@Composable
internal fun ReadingSessionsContent(
    state: ReadingSessionsState,
    listState: LazyListState,
    onStatusSelected: (ReadingSessionStatusFilter) -> Unit,
    onSearch: (String) -> Unit,
    onLoadNextPage: () -> Unit,
    onRetry: () -> Unit,
    onSessionSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        MarginaliaHistoryControls(
            state,
            onStatusSelected,
            onSearch,
            Modifier.padding(top = 12.dp, bottom = 10.dp)
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        ReadingSessionResults(
            state,
            listState,
            onLoadNextPage,
            onRetry,
            onSessionSelected,
            Modifier.weight(1f)
        )
    }
}

@Composable
private fun MarginaliaHistoryControls(
    state: ReadingSessionsState,
    onStatusSelected: (ReadingSessionStatusFilter) -> Unit,
    onSearch: (String) -> Unit,
    modifier: Modifier
) {
    var query by rememberSaveable { mutableStateOf(state.committedQuery) }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(state.committedQuery) { query = state.committedQuery }
    val commit = {
        focusManager.clearFocus()
        onSearch(query)
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Search reading sessions") },
            placeholder = {
                Text(
                    if (state.context is MarginaliaHistoryContext.Book) {
                        "Search names and notes"
                    } else {
                        "Search sessions, books, authors, or series"
                    }
                )
            },
            trailingIcon = {
                androidx.compose.material3.TextButton(onClick = commit) { Text("Search") }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { commit() })
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ReadingSessionStatusFilter.entries.forEach { filter ->
                FilterChip(
                    selected = state.statusFilter == filter,
                    onClick = { onStatusSelected(filter) },
                    label = { Text(filter.presentationLabel) }
                )
            }
            if (state.totalCount > 0) {
                Text(
                    sessionCountLabel(state.totalCount),
                    modifier = Modifier.padding(start = 4.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}
