package com.secondpasslibrary.reader.shelves.collection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.secondpasslibrary.client.ShelfPreviewBook
import com.secondpasslibrary.reader.design.book.PublicBookCover
import com.secondpasslibrary.reader.design.book.separatedPreviewCapacity
import com.secondpasslibrary.reader.design.icons.AppIcon
import com.secondpasslibrary.reader.design.icons.AppIconGraphic
import com.secondpasslibrary.reader.shelves.ShelfCardPresentation
import com.secondpasslibrary.reader.shelves.ShelfOwnerKind
import com.secondpasslibrary.reader.shelves.ShelfPreviewPresentation
import com.secondpasslibrary.reader.shelves.ownerContextLabel

@Composable
internal fun ShelfCard(model: ShelfCardPresentation, onClick: () -> Unit) {
    OutlinedCard(
        onClick = onClick,
        modifier =
            Modifier
                .testTag(model.cardTestTag)
                .semantics(mergeDescendants = true) {
                    contentDescription = "Open shelf ${model.name}"
                }
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val nameStyle = MaterialTheme.typography.titleMedium
            val contextStyle = MaterialTheme.typography.bodySmall
            val countStyle = MaterialTheme.typography.labelMedium
            val requiredTextWidth =
                requiredShelfTextWidth(model, nameStyle, contextStyle, countStyle)
                    .coerceIn(SHELF_TEXT_MIN_WIDTH, SHELF_TEXT_MAX_WIDTH)
            val previewCount = model.previewBooks.size
            val previewSlotCount =
                previewCount + if (model.itemCount > previewCount && previewCount > 0) 1 else 0
            val capacity =
                separatedPreviewCapacity(
                    maxWidth,
                    requiredTextWidth + SHELF_ROW_CHROME_WIDTH,
                    SHELF_COVER_WIDTH,
                    SHELF_COVER_SPACING,
                    previewSlotCount
                )
            Row(
                modifier = Modifier.fillMaxWidth().height(SHELF_ROW_HEIGHT)
                    .padding(SHELF_ROW_PADDING),
                horizontalArrangement = Arrangement.spacedBy(SHELF_CONTENT_SPACING),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        model.name,
                        fontWeight = FontWeight.SemiBold,
                        style = nameStyle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    ShelfContextLine(model)
                    Text(
                        model.itemCountLabel,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = countStyle
                    )
                }
                ShelfPreviewRow(model, capacity)
            }
        }
    }
}

@Composable
private fun requiredShelfTextWidth(
    model: ShelfCardPresentation,
    nameStyle: TextStyle,
    contextStyle: TextStyle,
    countStyle: TextStyle
): Dp {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    fun measure(text: String, style: TextStyle) = textMeasurer.measure(
        AnnotatedString(text),
        style,
        maxLines = 1,
        softWrap = false
    ).size.width
    return with(density) {
        maxOf(
            measure(model.name, nameStyle),
            model.ownerContextLabel?.let { label ->
                measure(label, contextStyle) + OWNER_ICON_AND_SPACING.roundToPx()
            } ?: 0,
            measure(model.itemCountLabel, countStyle)
        ).toDp()
    }
}

@Composable
private fun ShelfContextLine(model: ShelfCardPresentation) {
    val label = model.ownerContextLabel ?: return
    val icon = when (model.ownerKind) {
        ShelfOwnerKind.PERSONAL -> AppIcon.Shelf
        ShelfOwnerKind.SHARED_USER -> AppIcon.SharedShelf
        ShelfOwnerKind.GROUP -> AppIcon.GroupShelf
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIconGraphic(icon, null, Modifier.size(16.dp))
        Text(
            label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun ShelfPreviewRow(model: ShelfCardPresentation, capacity: Int) {
    val slots = previewSlots(model.previewBooks, model.itemCount, capacity)
    Row(
        horizontalArrangement = Arrangement.spacedBy(SHELF_COVER_SPACING)
    ) {
        slots.books.forEach { book ->
            PublicBookCover(
                book.cover,
                book.title,
                Modifier.size(width = SHELF_COVER_WIDTH, height = SHELF_COVER_HEIGHT)
                    .testTag(book.previewTestTag),
                contentDescription = null
            )
        }
        slots.overflowCount?.let { overflow -> ShelfPreviewOverflow(overflow) }
    }
}

@Composable
private fun ShelfPreviewOverflow(count: Int) {
    Surface(
        modifier = Modifier.size(width = SHELF_COVER_WIDTH, height = SHELF_COVER_HEIGHT),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "+$count",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

internal data class PreviewSlots(val books: List<ShelfPreviewBook>, val overflowCount: Int?)

internal fun previewSlots(books: List<ShelfPreviewBook>, total: Int, capacity: Int): PreviewSlots {
    if (capacity <= 0 || books.isEmpty()) return PreviewSlots(emptyList(), null)
    val showOverflow = capacity >= 2 && total > capacity
    val visibleCount = if (showOverflow) capacity - 1 else capacity
    val visibleBooks = books.take(visibleCount)
    val overflow = (total - visibleBooks.size).takeIf { showOverflow && it > 0 }
    return PreviewSlots(visibleBooks, overflow)
}

private val ShelfCardPresentation.previewBooks
    get() = (previews as? ShelfPreviewPresentation.Books)?.books.orEmpty()

internal val ShelfCardPresentation.cardTestTag: String
    get() = "shelf-card-$id"

internal val ShelfPreviewBook.previewTestTag: String
    get() = "shelf-preview-$id"

private val SHELF_ROW_HEIGHT = 112.dp
private val SHELF_ROW_PADDING = 12.dp
private val SHELF_CONTENT_SPACING = 12.dp
private val OWNER_ICON_AND_SPACING = 22.dp
private val SHELF_ROW_CHROME_WIDTH = SHELF_ROW_PADDING * 2 + SHELF_CONTENT_SPACING
private val SHELF_TEXT_MIN_WIDTH = 144.dp
private val SHELF_TEXT_MAX_WIDTH = 300.dp
private val SHELF_COVER_WIDTH = 52.dp
private val SHELF_COVER_HEIGHT = 82.dp
private val SHELF_COVER_SPACING = 7.dp
