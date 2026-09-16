package com.secondpasslibrary.reader.shelves.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.secondpasslibrary.client.ShelfEditorItem
import com.secondpasslibrary.reader.design.icons.AppIcon
import com.secondpasslibrary.reader.design.icons.AppIconGraphic
import com.secondpasslibrary.reader.shelves.ShelvesFailure
import com.secondpasslibrary.reader.shelves.ShelvesLoading
import com.secondpasslibrary.reader.shelves.ShelvesNextPageFooter
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

@Composable
internal fun ShelfContentsEditorContent(
    state: ShelfContentsEditorState,
    onLoadNextPage: () -> Unit,
    onRetry: () -> Unit,
    onMoveUp: (String) -> Unit,
    onMoveDown: (String) -> Unit,
    onMoveToPosition: (String) -> Unit,
    onRemove: (String) -> Unit,
    onDismissFailure: () -> Unit,
    onEditDetails: () -> Unit,
    onDeleteShelf: () -> Unit,
    modifier: Modifier = Modifier
) {
    when {
        state.entries.isEmpty() && state.initialLoading -> ShelvesLoading(modifier)

        state.entries.isEmpty() && state.loadError != null ->
            ShelvesFailure(state.loadError, onRetry, modifier)

        else -> EditorLoadedContent(
            state,
            onLoadNextPage,
            onRetry,
            onMoveUp,
            onMoveDown,
            onMoveToPosition,
            onRemove,
            onDismissFailure,
            onEditDetails,
            onDeleteShelf,
            modifier
        )
    }
}

@Composable
private fun EditorLoadedContent(
    state: ShelfContentsEditorState,
    onLoadNextPage: () -> Unit,
    onRetry: () -> Unit,
    onMoveUp: (String) -> Unit,
    onMoveDown: (String) -> Unit,
    onMoveToPosition: (String) -> Unit,
    onRemove: (String) -> Unit,
    onDismissFailure: () -> Unit,
    onEditDetails: () -> Unit,
    onDeleteShelf: () -> Unit,
    modifier: Modifier
) {
    val listState = rememberLazyListState()
    LaunchedEffect(listState, state.entries.size, state.hasNext) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .map { shouldRequestEditorNextPage(it, state.entries.size) && state.hasNext }
            .distinctUntilChanged()
            .filter { it }
            .collect { onLoadNextPage() }
    }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { EditorSummary(state, onDismissFailure, onEditDetails, onDeleteShelf) }
        if (state.entries.isEmpty() && state.currentPage > 0) {
            item {
                Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                    Text("This Shelf is empty.")
                }
            }
        }
        items(state.entries, key = { it.id }) { entry ->
            val enabled = !state.mutation.inProgress
            when (entry) {
                is ShelfEditorItem.Available ->
                    AvailableShelfEditorRow(
                        entry,
                        state.directPositionAvailable,
                        enabled,
                        { onMoveUp(entry.id) },
                        { onMoveDown(entry.id) },
                        { onMoveToPosition(entry.id) },
                        { onRemove(entry.id) }
                    )

                is ShelfEditorItem.Unavailable ->
                    UnavailableShelfEditorRow(entry, enabled) { onRemove(entry.id) }
            }
        }
        item { ShelvesNextPageFooter(state.nextPageLoading, state.loadError, onRetry) }
    }
}

@Composable
private fun EditorSummary(
    state: ShelfContentsEditorState,
    onDismissFailure: () -> Unit,
    onEditDetails: () -> Unit,
    onDeleteShelf: () -> Unit
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                shelfEditorCountsLabel(state.visibleItemCount, state.unavailableItemCount),
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = onEditDetails) {
                AppIconGraphic(AppIcon.Edit, null)
                Text("Edit Shelf details", Modifier.padding(start = 6.dp))
            }
            OutlinedButton(
                onClick = onDeleteShelf,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                AppIconGraphic(AppIcon.Delete, null)
                Text("Delete Shelf", Modifier.padding(start = 6.dp))
            }
        }
        if (state.unavailableItemCount > 0) {
            Text(
                "Some Shelf items aren’t available to this account. Available Books can move " +
                    "up or down, but not to a numbered position.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
        state.mutation.failure?.let { failure ->
            Text(failure.message(), color = MaterialTheme.colorScheme.error)
            OutlinedButton(onClick = onDismissFailure) { Text("Dismiss") }
        }
    }
}
