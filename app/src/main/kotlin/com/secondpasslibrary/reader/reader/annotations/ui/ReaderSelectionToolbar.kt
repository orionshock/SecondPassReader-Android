package com.secondpasslibrary.reader.reader.annotations.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.secondpasslibrary.client.MAX_HIGHLIGHT_NOTE_LENGTH
import com.secondpasslibrary.reader.design.icons.AppIcon
import com.secondpasslibrary.reader.design.icons.AppIconGraphic
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotationColor
import com.secondpasslibrary.reader.reader.annotations.mutation.ReaderAnnotationMutationState
import com.secondpasslibrary.reader.reader.annotations.selection.ReaderSelection
import com.secondpasslibrary.reader.reader.ui.ReaderChromeColors

@Composable
internal fun ReaderSelectionToolbar(
    selection: ReaderSelection,
    state: ReaderAnnotationMutationState,
    colors: ReaderChromeColors,
    onColorChanged: (ReaderAnnotationColor) -> Unit,
    onNoteChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pending = state.pendingCreate?.takeIf { it.selection.cfi == selection.cfi } ?: return
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val dismiss = {
        focusManager.clearFocus()
        keyboard?.hide()
        onDismiss()
    }
    val submit = {
        focusManager.clearFocus()
        keyboard?.hide()
        onSubmit()
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = colors.panelBackground.copy(alpha = 0.96f),
        contentColor = colors.content,
        tonalElevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ReaderHighlightNoteField(pending.note, state.submitting, onNoteChanged)
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ReaderAnnotationColor.entries.forEach { color ->
                    ReaderColorButton(
                        color = color,
                        selected = color == pending.color,
                        enabled = !state.submitting,
                        outline = colors.content,
                        onClick = { onColorChanged(color) }
                    )
                }
                TextButton(enabled = !state.submitting, onClick = submit) {
                    Text(
                        when {
                            state.submitting -> "Saving…"
                            state.failure != null -> "Retry"
                            else -> "Highlight"
                        }
                    )
                }
                IconButton(onClick = dismiss) {
                    AppIconGraphic(AppIcon.Close, "Dismiss highlight toolbar")
                }
            }
        }
    }
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
internal fun ReaderColorButton(
    color: ReaderAnnotationColor,
    selected: Boolean,
    enabled: Boolean,
    outline: Color,
    onClick: () -> Unit
) {
    IconButton(
        modifier = Modifier.size(40.dp).semantics {
            contentDescription = "${color.displayName} highlight"
            this.selected = selected
        },
        enabled = enabled,
        onClick = onClick
    ) {
        androidx.compose.foundation.layout.Box(
            Modifier
                .size(24.dp)
                .background(Color(color.displayArgb), CircleShape)
                .then(
                    if (selected) {
                        Modifier.border(2.dp, outline, CircleShape)
                    } else {
                        Modifier
                    }
                )
        )
    }
}

private val ReaderAnnotationColor.displayName: String
    get() = name.lowercase().replaceFirstChar(Char::uppercase)
