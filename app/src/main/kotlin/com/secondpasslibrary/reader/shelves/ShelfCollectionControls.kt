package com.secondpasslibrary.reader.shelves

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.secondpasslibrary.client.ShelfOrdering
import com.secondpasslibrary.reader.design.icons.AppIcon
import com.secondpasslibrary.reader.design.icons.AppIconGraphic

@Composable
internal fun ShelvesRootControls(
    selected: ShelvesCollection,
    state: ShelfCollectionState,
    onCollectionSelected: (ShelvesCollection) -> Unit,
    onOrderingSelected: (ShelfOrdering) -> Unit,
    modifier: Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CollectionChip("My Shelves", AppIcon.Shelf, selected == ShelvesCollection.PERSONAL) {
            onCollectionSelected(ShelvesCollection.PERSONAL)
        }
        CollectionChip(
            "Shared Shelves",
            AppIcon.SharedShelf,
            selected == ShelvesCollection.SHARED
        ) { onCollectionSelected(ShelvesCollection.SHARED) }
        Box(Modifier.weight(1f))
        Text(
            "${state.totalCount} ${if (state.totalCount == 1) "shelf" else "shelves"}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium
        )
        ShelfOrderingMenu(state.ordering, onOrderingSelected)
    }
}

@Composable
private fun CollectionChip(label: String, icon: AppIcon, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { AppIconGraphic(icon, null, Modifier.size(18.dp)) }
    )
}

@Composable
private fun ShelfOrderingMenu(selected: ShelfOrdering, onSelected: (ShelfOrdering) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) { Text(selected.label()) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            shelfOrderingOptions.forEach { option ->
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
