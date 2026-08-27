package com.secondpasslibrary.reader.reader.annotations.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.secondpasslibrary.client.MAX_HIGHLIGHT_NOTE_LENGTH
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotation
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotationColor
import com.secondpasslibrary.reader.reader.annotations.mutation.ReaderAnnotationMutationState
import com.secondpasslibrary.reader.reader.annotations.mutation.ReaderHighlightEditDraft
import com.secondpasslibrary.reader.reader.ui.ReaderChromeColors

@Composable
internal fun ReaderHighlightMutationDialogs(
    state: ReaderAnnotationMutationState,
    colors: ReaderChromeColors,
    onCreateColorChanged: (ReaderAnnotationColor) -> Unit,
    onCreateNoteChanged: (String) -> Unit,
    onSaveCreate: () -> Unit,
    onCancelCreateNote: () -> Unit,
    onEditColorChanged: (ReaderAnnotationColor) -> Unit,
    onEditNoteChanged: (String) -> Unit,
    onSaveEdit: () -> Unit,
    onConfirmDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    state.pendingCreate?.takeIf { state.createNoteEditorVisible }?.let { pending ->
        ReaderHighlightDraftDialog(
            title = "Add note",
            confirmLabel = "Create",
            quote = pending.selection.selectedText,
            color = pending.color,
            note = pending.note,
            state = state,
            colors = colors,
            onColorChanged = onCreateColorChanged,
            onNoteChanged = onCreateNoteChanged,
            onSave = onSaveCreate,
            onDismiss = onCancelCreateNote
        )
    }
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
    state.deleting?.let { annotation ->
        ReaderAnnotationDeleteDialog(annotation, state, onConfirmDelete, onDismiss)
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
    ReaderHighlightDraftDialog(
        title = "Edit highlight",
        confirmLabel = "Save",
        quote = draft.annotation.quote,
        color = draft.color,
        note = draft.note,
        state = state,
        colors = colors,
        onColorChanged = onColorChanged,
        onNoteChanged = onNoteChanged,
        onSave = onSave,
        onDismiss = onDismiss
    )
}

@Composable
private fun ReaderHighlightDraftDialog(
    title: String,
    confirmLabel: String,
    quote: String,
    color: ReaderAnnotationColor,
    note: String,
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
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    quote,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    fontStyle = FontStyle.Italic
                )
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    ReaderAnnotationColor.entries.forEach { option ->
                        ReaderColorButton(
                            option,
                            option == color,
                            !state.submitting,
                            colors.content
                        ) { onColorChanged(option) }
                    }
                }
                ReaderHighlightNoteField(note, state.submitting, onNoteChanged)
                state.failure?.let { Text("Highlight could not be saved. Try again.") }
            }
        },
        confirmButton = {
            TextButton(enabled = !state.submitting, onClick = onSave) {
                Text(if (state.failure == null) confirmLabel else "Retry")
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
internal fun ReaderHighlightNoteField(
    note: String,
    submitting: Boolean,
    onNoteChanged: (String) -> Unit
) {
    OutlinedTextField(
        value = note,
        onValueChange = { updated ->
            if (updated.length <= MAX_HIGHLIGHT_NOTE_LENGTH) onNoteChanged(updated)
        },
        modifier = Modifier.fillMaxWidth(),
        enabled = !submitting,
        label = { Text("Note (optional)") },
        minLines = 1,
        maxLines = 3
    )
}

@Composable
private fun ReaderAnnotationDeleteDialog(
    annotation: ReaderAnnotation,
    state: ReaderAnnotationMutationState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler(onBack = onDismiss)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when (annotation) {
                    is ReaderAnnotation.Bookmark -> "Delete this bookmark?"
                    is ReaderAnnotation.Highlight -> "Delete this highlight?"
                }
            )
        },
        text = {
            state.failure?.let {
                Text(
                    "Annotation could not be deleted. Try again.",
                    Modifier.padding(top = 4.dp)
                )
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
