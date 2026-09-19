package com.secondpasslibrary.reader.design.book

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
internal fun BookOfflineActionDialogs(
    pendingRemoval: Boolean,
    error: Boolean,
    onDismissRemoval: () -> Unit,
    onConfirmRemoval: () -> Unit,
    onDismissError: () -> Unit
) {
    if (pendingRemoval) {
        AlertDialog(
            onDismissRequest = onDismissRemoval,
            title = { Text("Remove download?") },
            text = {
                Text("Remove this Book from this device? Your reading progress and notes are kept.")
            },
            confirmButton = {
                TextButton(onClick = onConfirmRemoval) {
                    Text("Remove download", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = onDismissRemoval) { Text("Cancel") } }
        )
    }
    if (error) {
        AlertDialog(
            onDismissRequest = onDismissError,
            title = { Text("Download unavailable") },
            text = {
                Text("Couldn’t update this download. Try again when the Library is available.")
            },
            confirmButton = { TextButton(onClick = onDismissError) { Text("OK") } }
        )
    }
}
