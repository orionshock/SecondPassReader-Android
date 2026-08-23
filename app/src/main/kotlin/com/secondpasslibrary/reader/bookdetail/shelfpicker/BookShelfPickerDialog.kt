package com.secondpasslibrary.reader.bookdetail.shelfpicker

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.secondpasslibrary.reader.design.icons.AppIcon
import com.secondpasslibrary.reader.design.icons.AppIconGraphic

@Composable
internal fun BookShelfPickerDialog(
    state: BookShelfPickerState,
    onRetry: () -> Unit,
    onAdd: (String) -> Unit,
    onManageShelves: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to shelf") },
        text = {
            Column(
                Modifier.widthIn(min = 420.dp, max = 620.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                BookShelfPickerBody(state, onRetry, onAdd)
                TextButton(onClick = onManageShelves, modifier = Modifier.align(Alignment.End)) {
                    AppIconGraphic(AppIcon.Shelf, null, Modifier.size(18.dp))
                    Text("Manage Shelves", Modifier.padding(start = 6.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun BookShelfPickerBody(
    state: BookShelfPickerState,
    onRetry: () -> Unit,
    onAdd: (String) -> Unit
) {
    when {
        state.loading ->
            Box(
                Modifier.fillMaxWidth().heightIn(min = 180.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }

        state.failure != null ->
            Column(
                Modifier.fillMaxWidth().padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(state.failure.message(), color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = onRetry, modifier = Modifier.padding(top = 10.dp)) {
                    Text("Retry")
                }
            }

        state.loaded && state.targets.isEmpty() ->
            Text(
                "You do not have an editable personal shelf yet.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 20.dp)
            )

        else ->
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.targets, key = { it.shelfId }) { target ->
                    BookShelfTargetRow(target) { onAdd(target.shelfId) }
                }
            }
    }
}

@Composable
private fun BookShelfTargetRow(target: BookShelfTarget, onAdd: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(target.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    target.metadataLabel(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium
                )
                target.failure?.let {
                    Text(it.message(), color = MaterialTheme.colorScheme.error)
                }
            }
            when {
                target.added -> {
                    AppIconGraphic(AppIcon.Confirm, null, Modifier.size(18.dp))
                    Text("Added", color = MaterialTheme.colorScheme.primary)
                }

                target.adding -> CircularProgressIndicator(Modifier.size(24.dp))

                else -> OutlinedButton(onClick = onAdd) {
                    Text(if (target.failure == null) "Add" else "Retry")
                }
            }
        }
    }
}
