package com.secondpasslibrary.reader.shelves.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
internal fun ShelfPositionDialog(
    state: ShelfPositionDialogState,
    maximum: Int,
    submitting: Boolean,
    onValueChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text("Move Book") },
        text = {
            Column {
                Text(state.title, Modifier.padding(bottom = 12.dp))
                OutlinedTextField(
                    value = state.value,
                    onValueChange = onValueChanged,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !submitting,
                    label = { Text("Position (1–$maximum)") },
                    isError = state.invalid,
                    supportingText = {
                        if (state.invalid) Text("Enter a position from 1 to $maximum.")
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onSubmit, enabled = !submitting) { Text("Move") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !submitting) { Text("Cancel") }
        }
    )
}

@Composable
internal fun RemoveShelfItemDialog(
    state: ShelfRemovalDialogState,
    submitting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text("Remove from Shelf?") },
        text = {
            Text(
                state.label?.let { "Remove “$it” from this Shelf? The Book is not deleted." }
                    ?: "Remove this unavailable item from the Shelf? No Book is deleted."
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !submitting,
                colors =
                    ButtonDefaults.textButtonColors(
                        contentColor = androidx.compose.material3.MaterialTheme.colorScheme.error
                    )
            ) { Text("Remove from Shelf") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !submitting) { Text("Cancel") }
        }
    )
}
