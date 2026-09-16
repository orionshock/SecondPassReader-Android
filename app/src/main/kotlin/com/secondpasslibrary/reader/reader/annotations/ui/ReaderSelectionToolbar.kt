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
import com.secondpasslibrary.reader.reader.appearance.ReaderPalette
import com.secondpasslibrary.reader.reader.cfi.EpubSelectionBounds
import kotlin.math.roundToInt

/** Compact Reader actions positioned from renderer-neutral viewport selection bounds. */
@Composable
internal fun ReaderSelectionToolbar(
    selection: ReaderSelection,
    state: ReaderAnnotationMutationState,
    palette: ReaderPalette,
    onQuickHighlight: (ReaderAnnotationColor) -> Unit,
    onNoteRequested: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (state.pendingCreate?.selection?.cfi != selection.cfi) return
    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        var toolbarSize by remember { mutableStateOf(Size.Zero) }
        val toolbarOffset = selectionToolbarOffset(
            selection = selection,
            toolbarSize = toolbarSize,
            viewportWidth = constraints.maxWidth,
            viewportHeight = constraints.maxHeight,
            edgePadding = with(density) { 8.dp.toPx() },
            // Selection bounds exclude Android's draggable handles. Keep enough clearance for them
            // without depending on private ActionMode or handle geometry.
            selectionSpacing = with(density) { 40.dp.toPx() },
            fallbackTop = with(density) { 64.dp.toPx() }
        )
        Surface(
            modifier = Modifier
                .offset { toolbarOffset }
                .onSizeChanged { toolbarSize = Size(it.width.toFloat(), it.height.toFloat()) },
            shape = RoundedCornerShape(14.dp),
            color = palette.floatingSurface.copy(alpha = 0.97f),
            contentColor = palette.primaryForeground,
            shadowElevation = 5.dp,
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(1.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ReaderAnnotationColor.entries.forEach { color ->
                    ReaderColorButton(
                        color = color,
                        selected = false,
                        enabled = !state.submitting,
                        outline = palette.primaryForeground,
                        onClick = { onQuickHighlight(color) }
                    )
                }
                IconButton(
                    modifier = Modifier.size(48.dp),
                    enabled = !state.submitting,
                    onClick = onNoteRequested
                ) {
                    AppIconGraphic(AppIcon.HighlightWithNote, "Add note")
                }
                IconButton(modifier = Modifier.size(48.dp), onClick = onDismiss) {
                    AppIconGraphic(AppIcon.Close, "Close highlight tools")
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
    val desired = selection.bounds?.let {
        desiredToolbarPosition(
            bounds = it,
            width = width,
            height = height,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
            edgePadding = edgePadding,
            selectionSpacing = selectionSpacing
        )
    } ?: ((viewportWidth - width) / 2f to fallbackTop)
    val maxX = (viewportWidth - width - edgePadding).coerceAtLeast(edgePadding)
    val maxY = (viewportHeight - height - edgePadding).coerceAtLeast(edgePadding)
    return IntOffset(
        desired.first.coerceIn(edgePadding, maxX).roundToInt(),
        desired.second.coerceIn(edgePadding, maxY).roundToInt()
    )
}

private fun desiredToolbarPosition(
    bounds: EpubSelectionBounds,
    width: Float,
    height: Float,
    viewportWidth: Int,
    viewportHeight: Int,
    edgePadding: Float,
    selectionSpacing: Float
): Pair<Float, Float> {
    val centeredX = (bounds.left + bounds.right - width) / 2f
    val below = bounds.bottom + selectionSpacing
    val above = bounds.top - height - selectionSpacing
    val besideY = (bounds.top + bounds.bottom - height) / 2f
    val right = bounds.right + selectionSpacing
    val left = bounds.left - width - selectionSpacing
    return when {
        below + height <= viewportHeight - edgePadding -> centeredX to below
        right + width <= viewportWidth - edgePadding -> right to besideY
        left >= edgePadding -> left to besideY
        above >= edgePadding -> centeredX to above
        else -> centeredX to below
    }
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
        modifier = Modifier.size(48.dp).semantics {
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
