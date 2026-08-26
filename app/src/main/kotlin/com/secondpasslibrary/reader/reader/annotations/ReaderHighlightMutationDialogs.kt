package com.secondpasslibrary.reader.reader.annotations

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.secondpasslibrary.reader.reader.ReaderChromeColors

@Composable
internal fun ReaderHighlightMutationDialogs(
    state: ReaderAnnotationMutationState,
    colors: ReaderChromeColors,
    onEditColorChanged: (ReaderAnnotationColor) -> Unit,
    onEditNoteChanged: (String) -> Unit,
    onSaveEdit: () -> Unit,
    onConfirmDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    state.editing?.let { draft ->
        ReaderHighlightEditDialog(
            draft,
            state,
            colors,
            onEditColorChanged,
            onEditNoteChanged,
            onSaveEdit,
            onDismiss
        )
    }
    state.deleting?.let {
        ReaderHighlightDeleteDialog(state, onConfirmDelete, onDismiss)
    }
}

@Composable
private fun ReaderHighlightEditDialog(
    draft: ReaderHighlightEditDraft,
    state: ReaderAnnotationMutationState,
    colors: ReaderChromeColors,
    onColorChanged: (ReaderAnnotationColor) -> Unit,
    onNoteChanged: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler(onBack = onDismiss)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit highlight") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    draft.annotation.quote,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    fontStyle = FontStyle.Italic
                )
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    ReaderAnnotationColor.entries.forEach { color ->
                        ReaderColorButton(
                            color,
                            color == draft.color,
                            !state.submitting,
                            colors.content
                        ) { onColorChanged(color) }
                    }
                }
                ReaderHighlightNoteField(draft.note, state.submitting, onNoteChanged)
                state.failure?.let {
                    Text("Highlight could not be saved. Try again.")
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !state.submitting, onClick = onSave) {
                Text(if (state.failure == null) "Save" else "Retry")
            }
        },
        dismissButton = {
            TextButton(enabled = !state.submitting, onClick = onDismiss) { Text("Cancel") }
        },
        containerColor = colors.panelBackground,
        textContentColor = colors.content,
        titleContentColor = colors.content
    )
}

@Composable
private fun ReaderHighlightDeleteDialog(
    state: ReaderAnnotationMutationState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler(onBack = onDismiss)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete this highlight?") },
        text = {
            state.failure?.let {
                Text("Highlight could not be deleted. Try again.", Modifier.padding(top = 4.dp))
            }
        },
        confirmButton = {
            TextButton(enabled = !state.submitting, onClick = onConfirm) {
                Text(if (state.failure == null) "Delete" else "Retry")
            }
        },
        dismissButton = {
            TextButton(enabled = !state.submitting, onClick = onDismiss) { Text("Cancel") }
        }
    )
}
