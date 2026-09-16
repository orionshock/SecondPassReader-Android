package com.secondpasslibrary.reader.shelves.management

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.secondpasslibrary.client.ShelfVisibility

@Composable
internal fun CreatePersonalShelfDialog(
    state: CreatePersonalShelfState,
    onNameChanged: (String) -> Unit,
    onDescriptionChanged: (String) -> Unit,
    onVisibilityChanged: (ShelfVisibility) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!state.submitting) onDismiss() },
        title = { Text("Create Shelf") },
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
                state.failure?.takeUnless { it == CreateShelfFailure.FIELD_VALIDATION }?.let {
                    Text(it.message(), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(onClick = onSubmit, enabled = !state.submitting) {
                Text(if (state.submitting) "Creating…" else "Create")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, enabled = !state.submitting) { Text("Cancel") }
        }
    )
}

private fun CreateShelfFieldError.message(): String = when (this) {
    CreateShelfFieldError.REQUIRED -> "Name is required."
    CreateShelfFieldError.TOO_LONG -> "Name must be 255 characters or fewer."
    CreateShelfFieldError.SERVER_REJECTED -> "Second Pass Library rejected this value."
}

private fun CreateShelfFailure.message(): String = when (this) {
    CreateShelfFailure.UNREACHABLE ->
        "Couldn’t reach the Library. Check your connection and retry."

    CreateShelfFailure.AUTHENTICATION_REJECTED ->
        "Your connection is no longer authorized. Repair it in Settings."

    CreateShelfFailure.REJECTED -> "Second Pass Library rejected this Shelf. Check its details."

    CreateShelfFailure.FIELD_VALIDATION -> "Check the highlighted fields."

    CreateShelfFailure.OTHER -> "Couldn’t create the Shelf. Retry."
}
