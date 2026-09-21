package com.secondpasslibrary.reader.shelves.collection

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.secondpasslibrary.reader.design.book.PublicBookCover
import com.secondpasslibrary.reader.design.icons.AppIcon
import com.secondpasslibrary.reader.design.icons.AppIconGraphic
import com.secondpasslibrary.reader.shelves.ShelvesCollection
import com.secondpasslibrary.reader.shelves.ShelvesDestination
import com.secondpasslibrary.reader.shelves.ShelvesEmpty
import com.secondpasslibrary.reader.shelves.ShelvesFailure
import com.secondpasslibrary.reader.shelves.ShelvesLoading
import com.secondpasslibrary.reader.shelves.ShelvesNextPageFooter
import com.secondpasslibrary.reader.shelves.ShelvesReplacementFeedback
import com.secondpasslibrary.reader.shelves.ShelvesState
import com.secondpasslibrary.reader.shelves.shouldRequestShelfNextPage
import com.secondpasslibrary.reader.shelves.toCardPresentation
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

@Composable
internal fun ShelvesRoot(
    state: ShelvesState,
    onCollectionSelected: (ShelvesCollection) -> Unit,
    onOrderingSelected: (com.secondpasslibrary.client.ShelfOrdering) -> Unit,
    onLoadNextPersonal: () -> Unit,
    onLoadNextShared: () -> Unit,
    onLoadNextGroup: () -> Unit,
    onRetryPersonal: () -> Unit,
    onRetryShared: () -> Unit,
    onRetryGroup: () -> Unit,
    onShelfSelected: (String) -> Unit,
    onCreateShelf: () -> Unit,
    createShelfAvailable: Boolean,
    scrollStates: ShelfCollectionScrollStates,
    modifier: Modifier = Modifier
) {
    val selected =
        (state.destination as? ShelvesDestination.Collection)?.collection
            ?: ShelvesCollection.PERSONAL
    val collectionState = when (selected) {
        ShelvesCollection.PERSONAL -> state.personal
        ShelvesCollection.SHARED -> state.shared
        ShelvesCollection.GROUP -> state.group
    }
    val loadNext = when (selected) {
        ShelvesCollection.PERSONAL -> onLoadNextPersonal
        ShelvesCollection.SHARED -> onLoadNextShared
        ShelvesCollection.GROUP -> onLoadNextGroup
    }
    val retry = when (selected) {
        ShelvesCollection.PERSONAL -> onRetryPersonal
        ShelvesCollection.SHARED -> onRetryShared
        ShelvesCollection.GROUP -> onRetryGroup
    }
    val listState = scrollStates[selected]

    Column(modifier.padding(horizontal = 20.dp)) {
        ShelvesRootControls(
            selected,
            collectionState,
            onCollectionSelected,
            onOrderingSelected,
            onCreateShelf,
            createShelfAvailable,
            Modifier.padding(top = 14.dp, bottom = 8.dp)
        )
        ShelvesReplacementFeedback(collectionState, retry)
        ShelfCollectionResults(
            selected,
            collectionState,
            listState,
            loadNext,
            retry,
            onShelfSelected,
            Modifier.weight(1f)
        )
    }
}

@Composable
private fun ShelfCollectionResults(
    collection: ShelvesCollection,
    state: ShelfCollectionState,
    listState: LazyListState,
    onLoadNextPage: () -> Unit,
    onRetry: () -> Unit,
    onShelfSelected: (String) -> Unit,
    modifier: Modifier
) {
    when {
        state.shelves.isEmpty() && !state.hasLoaded && state.error == null ->
            ShelvesLoading(modifier)

        state.shelves.isEmpty() && state.error != null -> ShelvesFailure(
            state.error,
            onRetry,
            modifier
        )

        state.shelves.isEmpty() && state.hasLoaded -> ShelvesEmpty(collection, modifier)

        else ->
            ShelfCardList(
                collection,
                state,
                listState,
                onLoadNextPage,
                onRetry,
                onShelfSelected,
                modifier
            )
    }
}

@Composable
private fun ShelfCardList(
    collection: ShelvesCollection,
    state: ShelfCollectionState,
    listState: LazyListState,
    onLoadNextPage: () -> Unit,
    onRetry: () -> Unit,
    onShelfSelected: (String) -> Unit,
    modifier: Modifier
) {
    LaunchedEffect(listState, state.shelves.size, state.hasNext) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .map { shouldRequestShelfNextPage(it, state.shelves.size) && state.hasNext }
            .distinctUntilChanged()
            .filter { it }
            .collect { onLoadNextPage() }
    }
    LazyColumn(
        modifier = modifier.testTag(collection.listTestTag),
        state = listState,
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(state.shelves, key = { it.id }) { shelf ->
            ShelfCard(shelf.toCardPresentation()) { onShelfSelected(shelf.id) }
        }
        item { ShelvesNextPageFooter(state.nextPageLoading, state.error, onRetry) }
    }
}

internal class ShelfCollectionScrollStates(
    private val personal: LazyListState,
    private val shared: LazyListState,
    private val group: LazyListState
) {
    operator fun get(collection: ShelvesCollection): LazyListState = when (collection) {
        ShelvesCollection.PERSONAL -> personal
        ShelvesCollection.SHARED -> shared
        ShelvesCollection.GROUP -> group
    }
}

@Composable
internal fun rememberShelfCollectionScrollStates() = ShelfCollectionScrollStates(
    personal = rememberLazyListState(),
    shared = rememberLazyListState(),
    group = rememberLazyListState()
)

internal val ShelvesCollection.listTestTag: String
    get() = "shelves-${name.lowercase()}-list"
