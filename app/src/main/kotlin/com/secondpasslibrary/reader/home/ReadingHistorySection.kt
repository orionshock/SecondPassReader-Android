package com.secondpasslibrary.reader.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.secondpasslibrary.client.RecentReadingItem
import com.secondpasslibrary.reader.design.icons.AppIcon

private const val COVER_SCRIM_START = 0.3f

@Composable
internal fun ReadingHistorySection(
    state: HomeProjectionState<RecentReadingItem>,
    showClosed: Boolean,
    onShowClosedChanged: (Boolean) -> Unit,
    onRetry: () -> Unit,
    onViewAll: () -> Unit
) {
    val emptyMessage =
        if (showClosed) "No reading sessions yet." else "No active reading sessions yet."
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ReadingHistoryHeader(showClosed, onShowClosedChanged, onViewAll)
        val content = state.content
        if (content == null) {
            when (val refresh = state.refresh) {
                is HomeProjectionRefresh.Failed ->
                    Box(Modifier.padding(horizontal = 24.dp)) {
                        HomeSectionError(HomeErrorPresenter.message(refresh.reason), onRetry)
                    }

                HomeProjectionRefresh.Current,
                HomeProjectionRefresh.Idle,
                HomeProjectionRefresh.Refreshing ->
                    Box(Modifier.padding(horizontal = 24.dp)) {
                        HomeSectionLoading("reading history")
                    }
            }
            return@Column
        }
        HomeSectionRefreshFeedback(
            state.refresh,
            onRetry,
            Modifier.padding(horizontal = 24.dp)
        )
        if (content.items.isEmpty()) {
            Box(Modifier.padding(horizontal = 24.dp)) {
                HomeSectionEmpty(
                    AppIcon.Series,
                    emptyMessage
                )
            }
        } else {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 24.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(content.items, key = { it.sessionId }) { item ->
                    ReadingHistoryCard(HomePresenter.readingHistory(item))
                }
            }
        }
    }
}

@Composable
private fun ReadingHistoryHeader(
    showClosed: Boolean,
    onShowClosedChanged: (Boolean) -> Unit,
    onViewAll: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Reading History", style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = onViewAll) { Text("View all") }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Switch(checked = showClosed, onCheckedChange = onShowClosedChanged)
            Text("Show closed sessions", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ReadingHistoryCard(model: ReadingHistoryCardModel) {
    Surface(
        modifier = Modifier.width(172.dp).height(258.dp),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Box(Modifier.fillMaxSize()) {
            HomeBookCover(model.cover, model.title, Modifier.fillMaxSize())
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            COVER_SCRIM_START to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.94f)
                        )
                    )
            )
            Column(
                modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    model.title,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium
                )
                model.sessionName?.let {
                    Text(
                        it,
                        color = Color.White.copy(alpha = 0.78f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                model.locationLabel?.let {
                    Text(
                        it,
                        color = Color.White.copy(alpha = 0.72f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                ReadingStatus(model)
            }
        }
    }
}

@Composable
private fun ReadingStatus(model: ReadingHistoryCardModel) {
    val color = when (model.statusIndicator) {
        ReadingStatusIndicator.Active -> MaterialTheme.colorScheme.tertiary
        ReadingStatusIndicator.Closed -> MaterialTheme.colorScheme.outline
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Text(
            model.statusLabel,
            color = Color.White.copy(alpha = 0.88f),
            style = MaterialTheme.typography.labelSmall
        )
    }
}
