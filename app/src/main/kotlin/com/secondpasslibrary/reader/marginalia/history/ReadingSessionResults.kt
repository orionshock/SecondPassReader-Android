package com.secondpasslibrary.reader.marginalia.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.secondpasslibrary.reader.design.book.PublicBookCover
import com.secondpasslibrary.reader.marginalia.MarginaliaFailure
import com.secondpasslibrary.reader.marginalia.userMessage
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

@Composable
internal fun ReadingSessionResults(
    state: ReadingSessionsState,
    listState: LazyListState,
    onLoadNextPage: () -> Unit,
    onRetry: () -> Unit,
    onSessionSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    when {
        state.sessions.isEmpty() && state.initialLoading -> LoadingSessions(modifier)

        state.sessions.isEmpty() && state.error != null ->
            SessionLoadFailure(state.error.failure, onRetry, modifier)

        state.sessions.isEmpty() && state.currentPage > 0 ->
            EmptySessions(state.emptyMessage(), modifier)

        else -> SessionList(state, listState, onLoadNextPage, onRetry, onSessionSelected, modifier)
    }
}

@Composable
private fun SessionList(
    state: ReadingSessionsState,
    listState: LazyListState,
    onLoadNextPage: () -> Unit,
    onRetry: () -> Unit,
    onSessionSelected: (String) -> Unit,
    modifier: Modifier
) {
    NextSessionPageEffect(listState, state, onLoadNextPage)
    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(state.sessions, key = { it.session.id }) { item ->
            ReadingSessionRow(item.toRowPresentation()) { onSessionSelected(item.session.id) }
        }
        item { SessionNextPageFooter(state, onRetry) }
    }
}

@Composable
private fun ReadingSessionRow(model: ReadingSessionRowPresentation, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PublicBookCover(
                reference = model.cover,
                title = model.bookTitle,
                modifier = Modifier.width(56.dp).height(80.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    model.bookTitle,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium
                )
                model.sessionName?.let { name ->
                    Text(
                        name,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                SessionMetadata(model)
            }
        }
    }
}

@Composable
private fun SessionMetadata(model: ReadingSessionRowPresentation) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(8.dp)
                .background(
                    if (model.active) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                    CircleShape
                )
        )
        Text(model.statusLabel, style = MaterialTheme.typography.labelMedium)
        Text("•", color = MaterialTheme.colorScheme.outline)
        Text(
            model.annotationCountLabel,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium
        )
        Text("•", color = MaterialTheme.colorScheme.outline)
        Text(
            model.lastActivityLabel,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun NextSessionPageEffect(
    listState: LazyListState,
    state: ReadingSessionsState,
    onLoadNextPage: () -> Unit
) {
    LaunchedEffect(listState, state.sessions.size, state.hasNext) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .map { shouldRequestMoreSessions(it, state.sessions.size) && state.hasNext }
            .distinctUntilChanged()
            .filter { it }
            .collect { onLoadNextPage() }
    }
}

@Composable
private fun SessionNextPageFooter(state: ReadingSessionsState, onRetry: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(58.dp), contentAlignment = Alignment.Center) {
        when {
            state.nextPageLoading -> CircularProgressIndicator(
                Modifier.size(24.dp),
                strokeWidth = 2.dp
            )

            state.error?.phase == MarginaliaLoadPhase.NEXT_PAGE ->
                OutlinedButton(onClick = onRetry) { Text("Could not load more — Retry") }
        }
    }
}

@Composable
private fun LoadingSessions(modifier: Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptySessions(message: String, modifier: Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SessionLoadFailure(
    failure: MarginaliaFailure,
    onRetry: () -> Unit,
    modifier: Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(failure.userMessage(), color = MaterialTheme.colorScheme.error)
        OutlinedButton(onClick = onRetry, modifier = Modifier.padding(top = 10.dp)) {
            Text("Retry")
        }
    }
}
