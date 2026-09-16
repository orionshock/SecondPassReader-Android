package com.secondpasslibrary.reader.shelves.collection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
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
import com.secondpasslibrary.client.ShelfOrdering
import com.secondpasslibrary.reader.design.icons.AppIcon
import com.secondpasslibrary.reader.design.icons.AppIconGraphic
import com.secondpasslibrary.reader.shelves.ShelvesCollection
import com.secondpasslibrary.reader.shelves.label
import com.secondpasslibrary.reader.shelves.shelfOrderingOptions

@Composable
internal fun ShelvesRootControls(
    selected: ShelvesCollection,
    state: ShelfCollectionState,
    onCollectionSelected: (ShelvesCollection) -> Unit,
    onOrderingSelected: (ShelfOrdering) -> Unit,
    onCreateShelf: () -> Unit,
    createShelfAvailable: Boolean,
    modifier: Modifier
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        if (maxWidth >= 900.dp) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CollectionSelectors(selected, onCollectionSelected)
                Box(Modifier.weight(1f))
                CollectionActions(
                    selected,
                    state.ordering,
                    onOrderingSelected,
                    onCreateShelf,
                    createShelfAvailable
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CollectionSelectors(selected, onCollectionSelected)
                }
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CollectionActions(
                        selected,
                        state.ordering,
                        onOrderingSelected,
                        onCreateShelf,
                        createShelfAvailable
                    )
                }
            }
        }
    }
}

@Composable
private fun CollectionSelectors(
    selected: ShelvesCollection,
    onCollectionSelected: (ShelvesCollection) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CollectionChip("My Shelves", AppIcon.Shelf, selected == ShelvesCollection.PERSONAL) {
            onCollectionSelected(ShelvesCollection.PERSONAL)
        }
        CollectionChip(
            "Shared by Others",
            AppIcon.SharedShelf,
            selected == ShelvesCollection.SHARED
        ) { onCollectionSelected(ShelvesCollection.SHARED) }
        CollectionChip(
            "Group Shelves",
            AppIcon.GroupShelf,
            selected == ShelvesCollection.GROUP
        ) { onCollectionSelected(ShelvesCollection.GROUP) }
    }
}

@Composable
private fun CollectionActions(
    selected: ShelvesCollection,
    ordering: ShelfOrdering,
    onOrderingSelected: (ShelfOrdering) -> Unit,
    onCreateShelf: () -> Unit,
    createShelfAvailable: Boolean
) {
    if (selected == ShelvesCollection.PERSONAL) {
        OutlinedButton(onClick = onCreateShelf, enabled = createShelfAvailable) {
            AppIconGraphic(AppIcon.Add, null, Modifier.size(18.dp))
            Text("Create shelf", Modifier.padding(start = 6.dp))
        }
    }
    ShelfOrderingMenu(ordering, onOrderingSelected)
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
