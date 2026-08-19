package com.secondpasslibrary.reader.shelves

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
        title = { Text("Create shelf") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = onNameChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Name") },
                    supportingText = state.nameError?.let { { Text(it.message()) } },
                    isError = state.nameError != null,
                    singleLine = true,
                    enabled = !state.submitting
                )
                OutlinedTextField(
                    value = state.description,
                    onValueChange = onDescriptionChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Description (optional)") },
                    supportingText = state.descriptionError?.let { { Text(it.message()) } },
                    isError = state.descriptionError != null,
                    minLines = 2,
                    maxLines = 4,
                    enabled = !state.submitting
                )
                VisibilityChoice(
                    "Private",
                    "Visible only to you",
                    ShelfVisibility.PRIVATE,
                    state,
                    onVisibilityChanged
                )
                VisibilityChoice(
                    "Listed",
                    "May be visible to readers who can see its books",
                    ShelfVisibility.LISTED,
                    state,
                    onVisibilityChanged
                )
                state.visibilityError?.let {
                    Text(it.message(), color = MaterialTheme.colorScheme.error)
                }
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

@Composable
private fun VisibilityChoice(
    label: String,
    explanation: String,
    value: ShelfVisibility,
    state: CreatePersonalShelfState,
    onSelected: (ShelfVisibility) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = state.visibility == value,
            onClick = { onSelected(value) },
            enabled = !state.submitting
        )
        Column(Modifier.padding(start = 6.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                explanation,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

private fun CreateShelfFieldError.message(): String = when (this) {
    CreateShelfFieldError.REQUIRED -> "Name is required."
    CreateShelfFieldError.TOO_LONG -> "Name must be 255 characters or fewer."
    CreateShelfFieldError.SERVER_REJECTED -> "The library rejected this value."
}

private fun CreateShelfFailure.message(): String = when (this) {
    CreateShelfFailure.UNREACHABLE -> "The library is currently unreachable."
    CreateShelfFailure.AUTHENTICATION_REJECTED -> "Library authentication was rejected."
    CreateShelfFailure.REJECTED -> "The library rejected this shelf."
    CreateShelfFailure.FIELD_VALIDATION -> "Check the highlighted fields."
    CreateShelfFailure.OTHER -> "The shelf could not be created."
}
