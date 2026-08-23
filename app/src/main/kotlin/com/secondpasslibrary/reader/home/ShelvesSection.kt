package com.secondpasslibrary.reader.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.secondpasslibrary.client.ShelfSummary
import com.secondpasslibrary.reader.design.icons.AppIcon
import com.secondpasslibrary.reader.design.icons.AppIconGraphic

@Composable
internal fun ShelvesSection(
    state: HomeProjectionState<ShelfSummary>,
    onRetry: () -> Unit,
    onOpenShelves: () -> Unit,
    onShelfSelected: (HomeNavigationIntent.OpenShelfDetail) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val content = state.content
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            HomeSectionTitle(
                title = "Shelves",
                refreshing = content != null && state.refresh == HomeProjectionRefresh.Refreshing
            )
            TextButton(onClick = onOpenShelves) { Text("Open shelves") }
        }
        if (content == null) {
            when (val refresh = state.refresh) {
                is HomeProjectionRefresh.Failed ->
                    HomeSectionError(HomeErrorPresenter.message(refresh.reason), onRetry)

                HomeProjectionRefresh.Current,
                HomeProjectionRefresh.Idle,
                HomeProjectionRefresh.Refreshing -> HomeSectionLoading("shelves")
            }
            return@Column
        }
        HomeSectionCachedFailure(state.refresh, onRetry)
        if (content.items.isEmpty()) {
            HomeSectionEmpty(AppIcon.Shelf, "No shelves to show yet.")
        } else {
            ShelfGrid(content.items.map(HomePresenter::shelf), onShelfSelected)
        }
    }
}

@Composable
private fun ShelfGrid(
    shelves: List<ShelfCardModel>,
    onShelfSelected: (HomeNavigationIntent.OpenShelfDetail) -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = when {
            maxWidth >= 1_000.dp -> 3
            maxWidth >= 620.dp -> 2
            else -> 1
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            shelves.chunked(columns).forEach { rowShelves ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowShelves.forEach { shelf ->
                        ShelfCard(
                            shelf,
                            {
                                onShelfSelected(
                                    HomeNavigationIntent.OpenShelfDetail(shelf.id, shelf.origin)
                                )
                            },
                            Modifier.weight(1f)
                        )
                    }
                    repeat(columns - rowShelves.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun ShelfCard(model: ShelfCardModel, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(154.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    model.name,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppIconGraphic(
                        model.ownerIcon,
                        model.ownerIconDescription,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        model.ownerLabel,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Text(
                    model.itemCountLabel,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            ShelfPreviewStack(model)
        }
    }
}

@Composable
private fun ShelfPreviewStack(model: ShelfCardModel) {
    val previews = model.previewBooks.orEmpty().take(3)
    Box(
        modifier = Modifier.width(140.dp).height(124.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (previews.isEmpty()) {
            HomeBookCover(
                BookCoverPresentation.Missing,
                model.name,
                Modifier.align(Alignment.CenterEnd).size(width = 76.dp, height = 114.dp)
            )
        } else {
            previews.forEachIndexed { index, book ->
                HomeBookCover(
                    cover = book.cover,
                    title = book.title,
                    modifier =
                        Modifier
                            .offset(x = (index * 30).dp)
                            .size(width = 76.dp, height = 114.dp)
                            .clip(MaterialTheme.shapes.small)
                            .zIndex(index.toFloat())
                )
            }
        }
    }
}
