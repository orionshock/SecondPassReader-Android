package com.secondpasslibrary.reader.marginalia.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.secondpasslibrary.reader.marginalia.detail.close.ReadingSessionCloseState
import com.secondpasslibrary.reader.marginalia.detail.metadata.ReadingSessionMetadataEditState

@Composable
internal fun ReadingSessionMetadataEditDialog(
    state: ReadingSessionMetadataEditState,
    actions: ReadingSessionDetailActions
) {
    SessionMetadataDialog(
        title = "Edit Reading Session",
        name = state.name,
        notes = state.notes,
        fieldsEnabled = !state.saving,
        dismissEnabled = !state.saving,
        nameError = state.nameError,
        failure = state.failure,
        supporting = null,
        onNameChanged = actions.editNameChanged,
        onNotesChanged = actions.editNotesChanged,
        confirm = {
            Button(onClick = actions.saveEdit, enabled = state.dirty && !state.saving) {
                Text(if (state.saving) "Saving…" else "Save")
            }
        },
        onDismiss = actions.cancelEdit
    )
}

@Composable
internal fun ReadingSessionCloseDialog(
    state: ReadingSessionCloseState,
    actions: ReadingSessionDetailActions
) {
    val supporting = when {
        state.exactRetryRequired ->
            "The Reading Session may already be closed. Retry with the same name and notes."

        state.unnamedWarning ->
            "A closed Reading Session can’t be renamed. You can close it without a name."

        else -> null
    }
    SessionMetadataDialog(
        title = "Close Reading Session?",
        name = state.name,
        notes = state.notes,
        fieldsEnabled = !state.closing && !state.exactRetryRequired,
        dismissEnabled = !state.closing,
        nameError = state.nameError,
        failure = state.failure,
        supporting = supporting,
        onNameChanged = actions.closeNameChanged,
        onNotesChanged = actions.closeNotesChanged,
        confirm = {
            Button(
                onClick = actions.confirmClose,
                enabled = !state.closing,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Text(closeButtonLabel(state))
            }
        },
        onDismiss = actions.cancelClose
    )
}

@Composable
private fun SessionMetadataDialog(
    title: String,
    name: String,
    notes: String,
    fieldsEnabled: Boolean,
    dismissEnabled: Boolean,
    nameError: ReadingSessionNameError?,
    failure: ReadingSessionMutationFailure?,
    supporting: String?,
    onNameChanged: (String) -> Unit,
    onNotesChanged: (String) -> Unit,
    confirm: @Composable () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (dismissEnabled) onDismiss() },
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChanged,
                    modifier =
                        Modifier.fillMaxWidth().then(
                            nameError?.let { fieldError ->
                                Modifier.semantics { error(fieldError.message()) }
                            } ?: Modifier
                        ),
                    enabled = fieldsEnabled,
                    label = { Text("Name") },
                    singleLine = true,
                    isError = nameError != null,
                    supportingText = nameError?.let { { Text(it.message()) } }
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = onNotesChanged,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    enabled = fieldsEnabled,
                    label = { Text("Notes") },
                    minLines = 3,
                    maxLines = 7
                )
                supporting?.let {
                    Text(
                        it,
                        Modifier.padding(top = 10.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                failure?.let {
                    Text(
                        it.message(),
                        Modifier.padding(top = 10.dp),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = confirm,
        dismissButton = {
            OutlinedButton(onClick = onDismiss, enabled = dismissEnabled) { Text("Cancel") }
        }
    )
}

internal fun closeButtonLabel(state: ReadingSessionCloseState): String = when {
    state.closing -> "Closing…"
    state.exactRetryRequired -> "Retry close"
    else -> "Close Reading Session"
}

private fun ReadingSessionNameError.message(): String = when (this) {
    ReadingSessionNameError.TOO_LONG -> "Name must be 255 characters or fewer."
    ReadingSessionNameError.SERVER_REJECTED -> "Second Pass Library rejected this name."
}

internal fun ReadingSessionMutationFailure.message(): String = when (this) {
    ReadingSessionMutationFailure.UNREACHABLE ->
        "Couldn’t reach the Library. Check your connection and retry."

    ReadingSessionMutationFailure.AUTHENTICATION_REJECTED ->
        "Your connection is no longer authorized. Repair it in Settings."

    ReadingSessionMutationFailure.SESSION_CLOSED -> "This Reading Session is already closed."

    ReadingSessionMutationFailure.NOT_AUTHORIZED -> "You can’t change this Reading Session."

    ReadingSessionMutationFailure.NOT_FOUND -> "This Reading Session is no longer available."

    ReadingSessionMutationFailure.VALIDATION ->
        "Second Pass Library rejected these values. Check them and retry."

    ReadingSessionMutationFailure.PROTOCOL_INVALID ->
        "Couldn’t read the Library response. Retry or repair the connection in Settings."

    ReadingSessionMutationFailure.OTHER -> "Couldn’t change the Reading Session. Retry."
}
