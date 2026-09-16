package com.secondpasslibrary.reader.shelves.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.secondpasslibrary.client.Shelf
import com.secondpasslibrary.client.ShelfItemOrdering
import com.secondpasslibrary.reader.design.components.BinarySegmentedIconToggle
import com.secondpasslibrary.reader.design.components.SegmentedIconOption
import com.secondpasslibrary.reader.design.icons.AppIcon
import com.secondpasslibrary.reader.design.icons.AppIconGraphic
import com.secondpasslibrary.reader.design.richtext.ServerRichText
import com.secondpasslibrary.reader.shelves.ShelfCardPresentation
import com.secondpasslibrary.reader.shelves.label
import com.secondpasslibrary.reader.shelves.message
import com.secondpasslibrary.reader.shelves.ownerContextLabel
import com.secondpasslibrary.reader.shelves.shelfItemOrderingOptions
import com.secondpasslibrary.reader.shelves.toCardPresentation

@Composable
internal fun ShelfDetailHeader(
    state: ShelfDetailResourceState,
    items: ShelfItemsState,
    onRetry: () -> Unit,
    canManage: Boolean,
    onManageContents: () -> Unit,
    onOrderingSelected: (ShelfItemOrdering) -> Unit,
    onLayoutSelected: (ShelfBooksLayout) -> Unit,
    modifier: Modifier
) {
    when {
        state.loading ->
            ShelfDetailFeedbackSurface(modifier) {
                Box(Modifier.fillMaxWidth().height(112.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(28.dp))
                }
            }

        state.failure != null ->
            ShelfDetailFeedbackSurface(modifier) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(state.failure.message(), color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = onRetry) { Text("Retry") }
                }
            }

        state.shelf != null -> ShelfDetailOverview(
            state.shelf,
            items,
            canManage,
            onManageContents,
            onOrderingSelected,
            onLayoutSelected,
            modifier
        )
    }
}

@Composable
private fun ShelfDetailFeedbackSurface(modifier: Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        content = content
    )
}

@Composable
private fun ShelfDetailOverview(
    shelf: Shelf,
    items: ShelfItemsState,
    canManage: Boolean,
    onManageContents: () -> Unit,
    onOrderingSelected: (ShelfItemOrdering) -> Unit,
    onLayoutSelected: (ShelfBooksLayout) -> Unit,
    modifier: Modifier
) {
    val model = shelf.toCardPresentation()
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ShelfContextDescription(model, shelf.description, Modifier.weight(1f))
        if (canManage) {
            OutlinedButton(onClick = onManageContents) {
                AppIconGraphic(AppIcon.SortPositional, null, Modifier.size(18.dp))
                Text("Edit Shelf contents", Modifier.padding(start = 6.dp))
            }
        }
        ShelfItemOrderingMenu(items.ordering, onOrderingSelected)
        BinarySegmentedIconToggle(
            items.layout,
            SegmentedIconOption(ShelfBooksLayout.LIST, AppIcon.ListLayout, "List layout"),
            SegmentedIconOption(ShelfBooksLayout.GRID, AppIcon.GridLayout, "Grid layout"),
            onLayoutSelected
        )
    }
}

@Composable
private fun ShelfContextDescription(
    model: ShelfCardPresentation,
    rawDescription: String?,
    modifier: Modifier
) {
    val icon = when (model.ownerKind) {
        com.secondpasslibrary.reader.shelves.ShelfOwnerKind.PERSONAL -> AppIcon.User
        com.secondpasslibrary.reader.shelves.ShelfOwnerKind.SHARED_USER -> AppIcon.SharedShelf
        com.secondpasslibrary.reader.shelves.ShelfOwnerKind.GROUP -> AppIcon.GroupShelf
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIconGraphic(icon, null, Modifier.size(18.dp))
            Text(
                model.ownerContextLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        ServerRichText(
            value = rawDescription,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            collapsedMaxLines = 3,
            expandOverflow = true
        )
    }
}

@Composable
private fun ShelfItemOrderingMenu(
    selected: ShelfItemOrdering,
    onSelected: (ShelfItemOrdering) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) { Text(selected.label()) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            shelfItemOrderingOptions.forEach { option ->
                DropdownMenuItem(
                    modifier = Modifier.semantics { this.selected = option.value == selected },
                    text = { Text(option.label) },
                    onClick = {
                        expanded = false
                        onSelected(option.value)
                    }
                )
            }
        }
    }
}
