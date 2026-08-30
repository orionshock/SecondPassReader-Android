package com.secondpasslibrary.reader.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.secondpasslibrary.reader.design.book.menuLabel
import com.secondpasslibrary.reader.design.components.AnchoredOverflowMenu

private const val COVER_SCRIM_START = 0.3f

@Composable
internal fun ReadingHistoryCard(
    model: ReadingHistoryCardModel,
    onPrimaryAction: (OpenReaderIntent) -> Unit,
    onContextAction: (HomeNavigationIntent) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box {
        ReadingHistoryCardSurface(
            model,
            onClick = { model.primaryIntent?.let(onPrimaryAction) },
            onLongClick = { menuExpanded = true }
        )
        AnchoredOverflowMenu(
            items = model.contextActions,
            expanded = menuExpanded,
            onExpandedChange = { menuExpanded = it },
            label = HomeNavigationIntent::menuLabel,
            onSelected = onContextAction,
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            contentDescription = "Reading session actions"
        )
    }
}

@Composable
private fun ReadingHistoryCardSurface(
    model: ReadingHistoryCardModel,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Surface(
        modifier =
            Modifier
                .width(172.dp)
                .height(258.dp)
                .combinedClickable(
                    onClickLabel = model.primaryIntent?.let { "Read ${model.title}" },
                    onClick = onClick,
                    onLongClickLabel = "Reading session actions",
                    onLongClick = onLongClick
                ),
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
            ReadingHistoryCardText(model, Modifier.align(Alignment.BottomStart).padding(12.dp))
        }
    }
}

@Composable
private fun ReadingHistoryCardText(model: ReadingHistoryCardModel, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
        model.availabilityLabel?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall
            )
        }
        ReadingStatus(model)
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

private val HomeNavigationIntent.menuLabel: String
    get() = when (this) {
        is HomeNavigationIntent.BookAction -> action.menuLabel

        is HomeNavigationIntent.OpenReadingSessionDetail -> when (action) {
            ReadingSessionDetailAction.VIEW -> "Reading Session details"
            ReadingSessionDetailAction.EDIT -> "Edit Reading Session"
            ReadingSessionDetailAction.CLOSE -> "Close Reading Session"
        }

        else -> error("Unsupported Reading History context action.")
    }
