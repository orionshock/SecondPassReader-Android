package com.secondpasslibrary.reader.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.secondpasslibrary.client.RecentReadingItem
import com.secondpasslibrary.reader.design.icons.AppIcon
import com.secondpasslibrary.reader.design.icons.AppIconGraphic
import kotlinx.coroutines.launch

@Composable
internal fun ReadingHistorySection(
    state: HomeProjectionState<RecentReadingItem>,
    showClosed: Boolean,
    onShowClosedChanged: (Boolean) -> Unit,
    onRetry: () -> Unit,
    onPrimaryAction: (OpenReaderIntent) -> Unit,
    onContextAction: (HomeNavigationIntent) -> Unit,
    onViewAll: () -> Unit
) {
    val emptyMessage =
        if (showClosed) "No reading sessions yet." else "No active reading sessions yet."
    val listState = rememberLazyListState()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val content = state.content
        ReadingHistoryHeader(
            showClosed = showClosed,
            refreshing = content != null && state.refresh == HomeProjectionRefresh.Refreshing,
            listState = listState,
            onShowClosedChanged = onShowClosedChanged,
            onViewAll = onViewAll
        )
        if (content == null) {
            ReadingHistoryWithoutContent(state.refresh, onRetry)
            return@Column
        }
        HomeSectionCachedFailure(
            state.refresh,
            onRetry,
            Modifier.padding(horizontal = 24.dp)
        )
        if (content.items.isEmpty()) {
            Box(Modifier.padding(horizontal = 24.dp)) {
                HomeSectionEmpty(AppIcon.Series, emptyMessage)
            }
        } else {
            LazyRow(
                state = listState,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(content.items, key = { it.sessionId }) { item ->
                    ReadingHistoryCard(
                        HomePresenter.readingHistory(item),
                        onPrimaryAction,
                        onContextAction
                    )
                }
            }
        }
    }
}

@Composable
private fun ReadingHistoryWithoutContent(refresh: HomeProjectionRefresh, onRetry: () -> Unit) {
    Box(Modifier.padding(horizontal = 24.dp)) {
        when (refresh) {
            is HomeProjectionRefresh.Failed ->
                HomeSectionError(HomeErrorPresenter.message(refresh.reason), onRetry)

            HomeProjectionRefresh.Current,
            HomeProjectionRefresh.Idle,
            HomeProjectionRefresh.Refreshing -> HomeSectionLoading("reading history")
        }
    }
}

@Composable
private fun ReadingHistoryHeader(
    showClosed: Boolean,
    refreshing: Boolean,
    listState: LazyListState,
    onShowClosedChanged: (Boolean) -> Unit,
    onViewAll: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val previous: () -> Unit = {
        scope.launch {
            listState.animateScrollToItem((listState.firstVisibleItemIndex - 1).coerceAtLeast(0))
        }
    }
    val next: () -> Unit = {
        scope.launch {
            val lastIndex = listState.layoutInfo.totalItemsCount - 1
            if (lastIndex >= 0) {
                listState.animateScrollToItem(
                    (listState.firstVisibleItemIndex + 1).coerceAtMost(lastIndex)
                )
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        if (maxWidth >= 700.dp) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HomeSectionTitle("Reading History", refreshing)
                Spacer(Modifier.weight(1f))
                ReadingHistoryControls(
                    showClosed,
                    listState,
                    onShowClosedChanged,
                    onViewAll,
                    previous,
                    next
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                HomeSectionTitle("Reading History", refreshing)
                ReadingHistoryControls(
                    showClosed,
                    listState,
                    onShowClosedChanged,
                    onViewAll,
                    previous,
                    next
                )
            }
        }
    }
}

@Composable
private fun ReadingHistoryControls(
    showClosed: Boolean,
    listState: LazyListState,
    onShowClosedChanged: (Boolean) -> Unit,
    onViewAll: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Switch(checked = showClosed, onCheckedChange = onShowClosedChanged)
        Text("Show closed", style = MaterialTheme.typography.bodyMedium)
        IconButton(onClick = onPrevious, enabled = listState.canScrollBackward) {
            AppIconGraphic(AppIcon.Previous, "Scroll reading history left")
        }
        IconButton(onClick = onNext, enabled = listState.canScrollForward) {
            AppIconGraphic(AppIcon.Next, "Scroll reading history right")
        }
        TextButton(onClick = onViewAll) { Text("View all") }
    }
}
