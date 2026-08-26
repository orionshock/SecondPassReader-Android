package com.secondpasslibrary.reader.reader.annotations

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.secondpasslibrary.reader.design.icons.AppIcon
import com.secondpasslibrary.reader.design.icons.AppIconGraphic
import com.secondpasslibrary.reader.reader.ReaderChromeColors

@Composable
internal fun ReaderSelectionToolbar(
    selection: ReaderSelection,
    state: ReaderAnnotationCreateState,
    colors: ReaderChromeColors,
    onCreate: (ReaderAnnotationColor) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedColor by remember(selection.cfi.value) {
        mutableStateOf(ReaderAnnotationColor.YELLOW)
    }
    val submit = {
        if (state.failure == null) onCreate(selectedColor) else onRetry()
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = colors.panelBackground.copy(alpha = 0.96f),
        contentColor = colors.content,
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ReaderAnnotationColor.entries.forEach { color ->
                ReaderColorButton(
                    color = color,
                    selected = color == selectedColor,
                    enabled = !state.submitting,
                    outline = colors.content,
                    onClick = { selectedColor = color }
                )
            }
            TextButton(
                enabled = !state.submitting,
                onClick = submit
            ) {
                Text(
                    when {
                        state.submitting -> "Saving…"
                        state.failure != null -> "Retry"
                        else -> "Highlight"
                    }
                )
            }
            IconButton(onClick = onDismiss) {
                AppIconGraphic(AppIcon.Close, "Dismiss highlight toolbar")
            }
        }
    }
}

@Composable
private fun ReaderColorButton(
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
