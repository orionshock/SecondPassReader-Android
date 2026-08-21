package com.secondpasslibrary.reader.shelves.management

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.secondpasslibrary.client.ShelfVisibility

@Composable
internal fun EditPersonalShelfDialog(
    state: EditPersonalShelfState,
    onNameChanged: (String) -> Unit,
    onDescriptionChanged: (String) -> Unit,
    onVisibilityChanged: (ShelfVisibility) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!state.submitting) onDismiss() },
        title = { Text("Edit shelf") },
        text = {
            Column {
                ShelfMetadataFields(
                    name = state.name,
                    description = state.description,
                    visibility = state.visibility,
                    enabled = !state.submitting,
                    nameError = state.nameError?.message(),
                    descriptionError = state.descriptionError?.message(),
                    visibilityError = state.visibilityError?.message(),
                    onNameChanged = onNameChanged,
                    onDescriptionChanged = onDescriptionChanged,
                    onVisibilityChanged = onVisibilityChanged
                )
                state.failure?.let {
                    Text(
                        it.message(),
                        modifier = Modifier.padding(top = 10.dp),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onSubmit,
                enabled = state.changed && !state.submitting
            ) {
                Text(if (state.submitting) "Saving…" else "Save")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, enabled = !state.submitting) { Text("Cancel") }
        }
    )
}

@Composable
internal fun DeletePersonalShelfDialog(
    state: DeletePersonalShelfState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!state.deleting) onDismiss() },
        title = { Text("Delete ${state.shelfName}?") },
        text = {
            Column {
                Text(
                    "This removes the shelf and its membership records. " +
                        "It does not delete books, EPUB files or covers, reading sessions, or marginalia."
                )
                state.failure?.let {
                    Text(
                        it.message(),
                        modifier = Modifier.padding(top = 12.dp),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !state.deleting,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
            ) {
                Text(if (state.deleting) "Deleting…" else "Delete shelf")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, enabled = !state.deleting) { Text("Cancel") }
        }
    )
}

private fun ShelfMetadataFieldError.message(): String = when (this) {
    ShelfMetadataFieldError.REQUIRED -> "Name is required."
    ShelfMetadataFieldError.TOO_LONG -> "Name must be 255 characters or fewer."
    ShelfMetadataFieldError.SERVER_REJECTED -> "The library rejected this value."
}

private fun ShelfManagementFailure.message(): String = when (this) {
    ShelfManagementFailure.UNREACHABLE -> "The library is currently unreachable."
    ShelfManagementFailure.AUTHENTICATION_REJECTED -> "Library authentication was rejected."
    ShelfManagementFailure.VALIDATION -> "Check the highlighted fields."
    ShelfManagementFailure.NOT_AUTHORIZED -> "This shelf cannot be changed by this client."
    ShelfManagementFailure.NOT_FOUND -> "The shelf was not found. It may already be deleted."
    ShelfManagementFailure.REJECTED -> "The library rejected this change."
    ShelfManagementFailure.OTHER -> "The shelf could not be changed."
}
