package com.secondpasslibrary.reader.shelves

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
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.unit.dp
import com.secondpasslibrary.client.ShelfItemOrdering

@Composable
internal fun ShelfDetailHeader(
    state: ShelfDetailResourceState,
    onRetry: () -> Unit,
    modifier: Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        when {
            state.loading ->
                Box(Modifier.fillMaxWidth().height(112.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(28.dp))
                }

            state.failure != null ->
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(state.failure.message(), color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = onRetry) { Text("Retry details") }
                }

            state.shelf != null -> ShelfDetailMetadata(state.shelf.toCardPresentation(), state)
        }
    }
}

@Composable
private fun ShelfDetailMetadata(model: ShelfCardPresentation, state: ShelfDetailResourceState) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(model.name, style = MaterialTheme.typography.titleLarge)
        state.shelf?.description?.trim()?.takeIf(String::isNotEmpty)?.let { description ->
            Text(description, style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            "${model.ownerLabel} / ${model.visibilityLabel} / ${model.itemCountLabel}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium
        )
        if (model.canEdit) {
            Text(
                "Owned by you",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
internal fun ShelfItemControls(
    state: ShelfItemsState,
    onOrderingSelected: (ShelfItemOrdering) -> Unit,
    onLayoutSelected: (ShelfBooksLayout) -> Unit,
    modifier: Modifier
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "${state.totalCount} ${if (state.totalCount == 1) "book" else "books"}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium
        )
        Box(Modifier.weight(1f))
        ShelfItemOrderingMenu(state.ordering, onOrderingSelected)
        FilterChip(
            selected = state.layout == ShelfBooksLayout.LIST,
            onClick = { onLayoutSelected(ShelfBooksLayout.LIST) },
            label = { Text("List") }
        )
        FilterChip(
            selected = state.layout == ShelfBooksLayout.GRID,
            onClick = { onLayoutSelected(ShelfBooksLayout.GRID) },
            label = { Text("Grid") }
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
