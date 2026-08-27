package com.secondpasslibrary.reader.reader.annotations.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.secondpasslibrary.reader.design.icons.AppIcon
import com.secondpasslibrary.reader.design.icons.AppIconGraphic
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotationColor
import com.secondpasslibrary.reader.reader.annotations.mutation.ReaderAnnotationMutationState
import com.secondpasslibrary.reader.reader.annotations.selection.ReaderSelection
import com.secondpasslibrary.reader.reader.ui.ReaderChromeColors
import kotlin.math.roundToInt

/** Compact Reader actions positioned from renderer-neutral viewport selection bounds. */
@Composable
internal fun ReaderSelectionToolbar(
    selection: ReaderSelection,
    state: ReaderAnnotationMutationState,
    colors: ReaderChromeColors,
    onColorChanged: (ReaderAnnotationColor) -> Unit,
    onQuickHighlight: () -> Unit,
    onNoteRequested: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pending = state.pendingCreate?.takeIf { it.selection.cfi == selection.cfi } ?: return
    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        var toolbarSize by remember { mutableStateOf(Size.Zero) }
        val toolbarOffset = selectionToolbarOffset(
            selection = selection,
            toolbarSize = toolbarSize,
            viewportWidth = constraints.maxWidth,
            viewportHeight = constraints.maxHeight,
            edgePadding = with(density) { 8.dp.toPx() },
            selectionSpacing = with(density) { 8.dp.toPx() },
            fallbackTop = with(density) { 64.dp.toPx() }
        )
        Surface(
            modifier = Modifier
                .offset { toolbarOffset }
                .onSizeChanged { toolbarSize = Size(it.width.toFloat(), it.height.toFloat()) },
            shape = RoundedCornerShape(18.dp),
            color = colors.panelBackground.copy(alpha = 0.97f),
            contentColor = colors.content,
            shadowElevation = 8.dp,
            tonalElevation = 3.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(1.dp),
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
                TextButton(enabled = !state.submitting, onClick = onQuickHighlight) {
                    Text(
                        when {
                            state.submitting -> "Saving…"
                            state.failure != null -> "Retry"
                            else -> "Highlight"
                        }
                    )
                }
                TextButton(enabled = !state.submitting, onClick = onNoteRequested) {
                    Text("Note")
                }
                IconButton(onClick = onDismiss) {
                    AppIconGraphic(AppIcon.Close, "Dismiss highlight toolbar")
                }
            }
        }
    }
}

internal fun selectionToolbarOffset(
    selection: ReaderSelection,
    toolbarSize: Size,
    viewportWidth: Int,
    viewportHeight: Int,
    edgePadding: Float,
    selectionSpacing: Float,
    fallbackTop: Float
): IntOffset {
    val width = toolbarSize.width
    val height = toolbarSize.height
    val bounds = selection.bounds
    val desiredX = bounds?.let { (it.left + it.right - width) / 2f }
        ?: (viewportWidth - width) / 2f
    val above = bounds?.let { it.top - height - selectionSpacing }
    val desiredY = when {
        above != null && above >= edgePadding -> above
        bounds != null -> bounds.bottom + selectionSpacing
        else -> fallbackTop
    }
    val maxX = (viewportWidth - width - edgePadding).coerceAtLeast(edgePadding)
    val maxY = (viewportHeight - height - edgePadding).coerceAtLeast(edgePadding)
    return IntOffset(
        desiredX.coerceIn(edgePadding, maxX).roundToInt(),
        desiredY.coerceIn(edgePadding, maxY).roundToInt()
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
                    if (selected) Modifier.border(2.dp, outline, CircleShape) else Modifier
                )
        )
    }
}

private val ReaderAnnotationColor.displayName: String
    get() = name.lowercase().replaceFirstChar(Char::uppercase)
