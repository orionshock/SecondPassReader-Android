package com.secondpasslibrary.reader.marginalia.books

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.secondpasslibrary.client.MarginaliaBookSummary
import com.secondpasslibrary.reader.design.book.PublicBookCover
import com.secondpasslibrary.reader.design.components.InlineSearchField
import com.secondpasslibrary.reader.marginalia.formatSessionTimestamp
import com.secondpasslibrary.reader.marginalia.userMessage
import java.time.ZoneId
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

@Composable
internal fun MarginaliaBooksContent(
    state: MarginaliaBooksState,
    listState: LazyListState,
    onSearch: (String) -> Unit,
    onLoadNextPage: () -> Unit,
    onRetry: () -> Unit,
    onBookSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var query by rememberSaveable { mutableStateOf(state.committedQuery) }
    LaunchedEffect(state.committedQuery) { query = state.committedQuery }
    Column(modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        InlineSearchField(
            query = query,
            placeholder = "Search books with marginalia",
            contentDescription = "Search books with marginalia",
            onQueryChanged = { query = it },
            onSubmit = { onSearch(query) },
            modifier = Modifier.padding(top = 12.dp, bottom = 10.dp)
        )
        MarginaliaBookResults(
            state,
            listState,
            onLoadNextPage,
            onRetry,
            onBookSelected,
            Modifier.weight(1f)
        )
    }
}

@Composable
private fun MarginaliaBookResults(
    state: MarginaliaBooksState,
    listState: LazyListState,
    onLoadNextPage: () -> Unit,
    onRetry: () -> Unit,
    onBookSelected: (String) -> Unit,
    modifier: Modifier
) {
    when {
        state.books.isEmpty() && state.initialLoading ->
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

        state.books.isEmpty() && state.error != null ->
            Column(
                modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(state.error.failure.userMessage(), color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = onRetry, modifier = Modifier.padding(top = 10.dp)) {
                    Text("Retry")
                }
            }

        state.books.isEmpty() && state.currentPage > 0 ->
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (state.committedQuery.isBlank()) {
                        "No books with marginalia."
                    } else {
                        "No books match this search."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

        else -> MarginaliaBookList(
            state,
            listState,
            onLoadNextPage,
            onRetry,
            onBookSelected,
            modifier
        )
    }
}

@Composable
private fun MarginaliaBookList(
    state: MarginaliaBooksState,
    listState: LazyListState,
    onLoadNextPage: () -> Unit,
    onRetry: () -> Unit,
    onBookSelected: (String) -> Unit,
    modifier: Modifier
) {
    LaunchedEffect(listState, state.books.size, state.hasNext) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .distinctUntilChanged()
            .filter { it >= state.books.lastIndex - BOOK_PAGING_THRESHOLD && state.hasNext }
            .collect { onLoadNextPage() }
    }
    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(state.books, key = MarginaliaBookSummary::id) { book ->
            MarginaliaBookRow(book) { onBookSelected(book.id) }
        }
        item {
            Box(Modifier.fillMaxWidth().height(58.dp), contentAlignment = Alignment.Center) {
                when {
                    state.nextPageLoading -> CircularProgressIndicator()
                    state.error != null -> OutlinedButton(onClick = onRetry) { Text("Retry") }
                }
            }
        }
    }
}

@Composable
private fun MarginaliaBookRow(book: MarginaliaBookSummary, onClick: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PublicBookCover(book.cover, book.title, Modifier.width(56.dp).height(80.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    book.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val byline = buildList {
                    book.authors.takeIf { it.isNotEmpty() }?.let {
                        add(it.joinToString { author -> author.name })
                    }
                    book.series?.name?.let(::add)
                }.joinToString(" · ")
                if (byline.isNotBlank()) {
                    Text(
                        byline,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(book.sessionSummaryLabel(), style = MaterialTheme.typography.labelMedium)
                book.lastActivityAt?.let {
                    Text(
                        "Last activity ${formatSessionTimestamp(
                            it,
                            ZoneId.systemDefault(),
                            locale
                        )}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

private const val BOOK_PAGING_THRESHOLD = 4

internal fun MarginaliaBookSummary.sessionSummaryLabel(): String {
    val sessions = if (sessionCount == 1) "1 session" else "$sessionCount sessions"
    val active = when (activeSessionCount) {
        0 -> "no active session"
        1 -> "1 active"
        else -> "$activeSessionCount active"
    }
    return "$sessions · $active"
}
