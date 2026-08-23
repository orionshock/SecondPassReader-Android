package com.secondpasslibrary.reader.marginalia.history

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.secondpasslibrary.reader.design.components.InlineSearchField
import com.secondpasslibrary.reader.marginalia.MarginaliaHistoryContext

@Composable
internal fun ReadingSessionsContent(
    state: ReadingSessionsState,
    listState: LazyListState,
    onSearch: (String) -> Unit,
    onLoadNextPage: () -> Unit,
    onRetry: () -> Unit,
    onSessionSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        MarginaliaHistoryControls(
            state,
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
    onSearch: (String) -> Unit,
    modifier: Modifier
) {
    var query by rememberSaveable { mutableStateOf(state.committedQuery) }
    LaunchedEffect(state.committedQuery) { query = state.committedQuery }
    InlineSearchField(
        query = query,
        placeholder =
            if (state.context is MarginaliaHistoryContext.Book) {
                "Search names and notes"
            } else {
                "Search reading sessions"
            },
        contentDescription = "Search reading sessions",
        onQueryChanged = { query = it },
        onSubmit = { onSearch(query) },
        modifier = modifier
    )
}
