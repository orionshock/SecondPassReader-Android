package com.secondpasslibrary.reader.shelves

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.secondpasslibrary.reader.design.book.PublicBookCover
import com.secondpasslibrary.reader.design.icons.AppIcon
import com.secondpasslibrary.reader.design.icons.AppIconGraphic

@Composable
internal fun ShelfCard(model: ShelfCardPresentation, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick) {
        Row(
            modifier = Modifier.height(142.dp).padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    model.name,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                ShelfOwnerLine(model)
                Text(
                    "${model.visibilityLabel} / ${model.itemCountLabel}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            ShelfPreviewStack(model)
        }
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
            model.ownerLabel,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun ShelfPreviewStack(model: ShelfCardPresentation) {
    val previews = (model.previews as? ShelfPreviewPresentation.Books)?.books.orEmpty()
    Box(Modifier.width(134.dp).height(116.dp), contentAlignment = Alignment.CenterStart) {
        if (previews.isEmpty()) {
            AppIconGraphic(
                AppIcon.Shelf,
                "No preview covers for ${model.name}",
                Modifier.align(Alignment.Center).size(44.dp),
                MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            previews.forEachIndexed { index, book ->
                PublicBookCover(
                    book.cover,
                    book.title,
                    Modifier
                        .offset(x = (index * 28).dp)
                        .size(width = 72.dp, height = 108.dp)
                        .clip(MaterialTheme.shapes.small)
                        .zIndex(index.toFloat())
                )
            }
        }
    }
}
