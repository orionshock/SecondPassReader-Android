package com.secondpasslibrary.reader.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.secondpasslibrary.client.LibraryAuthor
import com.secondpasslibrary.client.LibrarySeries
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

@Composable
internal fun LibraryAuthorsResults(
    state: LibraryAuthorsState,
    onSelect: (String) -> Unit,
    onLoadNextPage: () -> Unit,
    onRetry: () -> Unit,
    onRetryDetail: () -> Unit,
    modifier: Modifier = Modifier
) {
    LibraryEntityResults(
        state,
        "authors",
        LibraryAuthor::toLibraryEntityPresentation,
        { it.toAuthorDetailPresentation() },
        onSelect,
        onLoadNextPage,
        onRetry,
        onRetryDetail,
        modifier
    )
}

@Composable
internal fun LibrarySeriesResults(
    state: LibrarySeriesState,
    onSelect: (String) -> Unit,
    onLoadNextPage: () -> Unit,
    onRetry: () -> Unit,
    onRetryDetail: () -> Unit,
    modifier: Modifier = Modifier
) {
    LibraryEntityResults(
        state,
        "series",
        LibrarySeries::toLibraryEntityPresentation,
        { it.toSeriesDetailPresentation() },
        onSelect,
        onLoadNextPage,
        onRetry,
        onRetryDetail,
        modifier
    )
}

@Composable
private fun <T, O> LibraryEntityResults(
    state: LibraryEntityState<T, O>,
    axisLabel: String,
    present: (T) -> LibraryEntityCardPresentation,
    presentDetail: (LibraryEntityDetailState<T>) -> LibrarySelectedEntityPresentation,
    onSelect: (String) -> Unit,
    onLoadNextPage: () -> Unit,
    onRetry: () -> Unit,
    onRetryDetail: () -> Unit,
    modifier: Modifier
) {
    when {
        state.items.isEmpty() && state.initialLoading -> LoadingLibrary(modifier)

        state.items.isEmpty() && state.error != null ->
            EntityFailure(state.error.failure, axisLabel, onRetry, modifier)

        state.items.isEmpty() && state.currentPage > 0 && state.selected == null ->
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No $axisLabel found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

        else -> {
            val listState = rememberLazyListState()
            LaunchedEffect(listState, state.items.size, state.hasNext) {
                snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
                    .map { shouldRequestNextPage(it, state.items.size) && state.hasNext }
                    .distinctUntilChanged()
                    .filter { it }
                    .collect { onLoadNextPage() }
            }
            Column(modifier) {
                state.selected?.let { selected ->
                    Box(Modifier.padding(top = 12.dp)) {
                        SelectedEntityHeader(presentDetail(selected), onRetryDetail)
                    }
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (state.initialLoading) {
                        item {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
                    } else if (state.error?.phase == LibraryEntityLoadPhase.INITIAL) {
                        item {
                            InlineEntityFailure(state.error.failure, axisLabel, onRetry)
                        }
                    }
                    items(state.items, key = { present(it).id }) { item ->
                        LibraryEntityCard(present(item), onSelect)
                    }
                    item {
                        EntityNextPageFooter(state, onRetry)
                    }
                }
            }
        }
    }
}

@Composable
private fun <T, O> EntityNextPageFooter(state: LibraryEntityState<T, O>, onRetry: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.Center) {
        when {
            state.nextPageLoading -> CircularProgressIndicator(
                Modifier.size(24.dp),
                strokeWidth = 2.dp
            )

            state.error?.phase == LibraryEntityLoadPhase.NEXT_PAGE ->
                OutlinedButton(onClick = onRetry) { Text("Could not load more - Retry") }
        }
    }
}

@Composable
private fun EntityFailure(
    failure: LibraryFailure,
    subject: String,
    onRetry: () -> Unit,
    modifier: Modifier
) {
    Column(
        modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(entityFailureMessage(failure, subject), color = MaterialTheme.colorScheme.error)
        OutlinedButton(onClick = onRetry, modifier = Modifier.padding(top = 10.dp)) {
            Text("Retry")
        }
    }
}

@Composable
private fun InlineEntityFailure(failure: LibraryFailure, subject: String, onRetry: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(entityFailureMessage(failure, subject), color = MaterialTheme.colorScheme.error)
        OutlinedButton(onClick = onRetry) { Text("Retry") }
    }
}

internal fun entityFailureMessage(failure: LibraryFailure, subject: String): String =
    when (failure) {
        LibraryFailure.UNREACHABLE -> "Library is currently unreachable."
        LibraryFailure.AUTHENTICATION_REJECTED -> "Library authentication was rejected."
        LibraryFailure.PROTOCOL_INVALID -> "The library returned invalid $subject data."
        LibraryFailure.OTHER -> "Library $subject could not be loaded."
    }
