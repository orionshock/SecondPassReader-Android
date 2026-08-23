package com.secondpasslibrary.reader.shelves.collection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = "Open shelf ${model.name}"
        }
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val nameStyle = MaterialTheme.typography.titleLarge
            val contextStyle = MaterialTheme.typography.bodyMedium
            val countStyle = MaterialTheme.typography.labelMedium
            val requiredTextWidth =
                requiredShelfTextWidth(model, nameStyle, contextStyle, countStyle)
            val previewCount = model.previewBooks.size
            val capacity =
                separatedPreviewCapacity(
                    maxWidth,
                    requiredTextWidth + SHELF_ROW_CHROME_WIDTH,
                    SHELF_COVER_WIDTH,
                    SHELF_COVER_SPACING,
                    previewCount
                )
            Row(
                modifier = Modifier.fillMaxWidth().height(
                    SHELF_ROW_HEIGHT
                ).padding(SHELF_ROW_PADDING),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        model.name,
                        fontWeight = FontWeight.SemiBold,
                        style = nameStyle,
                        maxLines = 1,
                        softWrap = false
                    )
                    ShelfOwnerLine(model)
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
            measure(model.ownerContextLabel, contextStyle) + OWNER_ICON_AND_SPACING.roundToPx(),
            measure(model.itemCountLabel, countStyle)
        ).toDp()
    }
}

@Composable
private fun ShelfOwnerLine(model: ShelfCardPresentation) {
    val icon = when (model.ownerKind) {
        ShelfOwnerKind.PERSONAL -> AppIcon.User
        ShelfOwnerKind.SHARED_USER -> AppIcon.SharedShelf
        ShelfOwnerKind.GROUP -> AppIcon.GroupShelf
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIconGraphic(icon, null, Modifier.size(16.dp))
        Text(
            model.ownerContextLabel,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun ShelfPreviewRow(model: ShelfCardPresentation, capacity: Int) {
    val previews = model.previewBooks.take(capacity)
    Row(
        modifier = Modifier.clearAndSetSemantics { },
        horizontalArrangement = Arrangement.spacedBy(SHELF_COVER_SPACING)
    ) {
        previews.forEach { book ->
            PublicBookCover(
                book.cover,
                book.title,
                Modifier.size(width = SHELF_COVER_WIDTH, height = SHELF_COVER_HEIGHT)
            )
        }
    }
}

private val ShelfCardPresentation.previewBooks
    get() = (previews as? ShelfPreviewPresentation.Books)?.books.orEmpty()

private val SHELF_ROW_HEIGHT = 142.dp
private val SHELF_ROW_PADDING = 14.dp
private val SHELF_CONTENT_SPACING = 14.dp
private val OWNER_ICON_AND_SPACING = 22.dp
private val SHELF_ROW_CHROME_WIDTH = SHELF_ROW_PADDING * 2 + SHELF_CONTENT_SPACING
private val SHELF_COVER_WIDTH = 64.dp
private val SHELF_COVER_HEIGHT = 112.dp
private val SHELF_COVER_SPACING = 8.dp
