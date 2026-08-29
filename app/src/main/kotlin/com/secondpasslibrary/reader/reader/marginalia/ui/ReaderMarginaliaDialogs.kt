package com.secondpasslibrary.reader.reader.marginalia.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.secondpasslibrary.reader.reader.appearance.ReaderPalette
import com.secondpasslibrary.reader.reader.session.MAX_SESSION_NAME_LENGTH
import com.secondpasslibrary.reader.reader.session.ReaderSessionMetadataState

@Composable
internal fun ReaderSessionMetadataDialog(
    state: ReaderSessionMetadataState,
    palette: ReaderPalette,
    onNameChanged: (String) -> Unit,
    onNotesChanged: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!state.editorOpen) return
    BackHandler(onBack = onDismiss)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit reading session") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ReaderMetadataField(
                    value = state.draftName,
                    onValueChanged = onNameChanged,
                    label = "Session name",
                    palette = palette,
                    enabled = !state.saving,
                    singleLine = true,
                    supportingText = if (state.nameTooLong) {
                        "Name must be $MAX_SESSION_NAME_LENGTH characters or fewer."
                    } else {
                        null
                    }
                )
                ReaderMetadataField(
                    value = state.draftNotes,
                    onValueChanged = onNotesChanged,
                    label = "Session note",
                    palette = palette,
                    enabled = !state.saving,
                    singleLine = false
                )
                if (state.failure) {
                    Text(
                        "Session could not be saved. Try again.",
                        color = palette.secondaryForeground
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = state.dirty && !state.saving,
                onClick = onSave,
                colors = ButtonDefaults.textButtonColors(contentColor = palette.primaryForeground)
            ) { Text(if (state.failure) "Retry" else "Save") }
        },
        dismissButton = {
            TextButton(
                enabled = !state.saving,
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = palette.primaryForeground)
            ) { Text("Cancel") }
        },
        containerColor = palette.panelSurface,
        textContentColor = palette.primaryForeground,
        titleContentColor = palette.primaryForeground
    )
}

@Composable
private fun ReaderMetadataField(
    value: String,
    onValueChanged: (String) -> Unit,
    label: String,
    palette: ReaderPalette,
    enabled: Boolean,
    singleLine: Boolean,
    supportingText: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChanged,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        label = { Text(label) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        maxLines = if (singleLine) 1 else 6,
        isError = supportingText != null,
        supportingText = supportingText?.let { { Text(it) } },
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = palette.primaryForeground,
            unfocusedTextColor = palette.primaryForeground,
            focusedLabelColor = palette.secondaryForeground,
            unfocusedLabelColor = palette.secondaryForeground,
            cursorColor = palette.primaryForeground,
            focusedBorderColor = palette.primaryForeground,
            unfocusedBorderColor = palette.border
        )
    )
}
