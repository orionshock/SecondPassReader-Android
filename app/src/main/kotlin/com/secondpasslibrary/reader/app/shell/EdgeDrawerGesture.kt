package com.secondpasslibrary.reader.app.shell

import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs

internal val DRAWER_GESTURE_EDGE_WIDTH = 24.dp
// Android caps each edge's exclusion length at 200dp; request one deterministic upper segment.
internal val DRAWER_GESTURE_EDGE_HEIGHT = 200.dp
private val DRAWER_OPEN_THRESHOLD = 64.dp
private const val HORIZONTAL_DOMINANCE_RATIO = 1.25f

/** Opens the existing drawer after a deliberate rightward drag originating at the left edge. */
internal fun Modifier.edgeDrawerGesture(
    enabled: Boolean,
    edgeWidth: Float,
    edgeHeight: Float,
    onOpenDrawer: () -> Unit
): Modifier {
    if (!enabled) return this
    return systemGestureExclusion { coordinates ->
        Rect(
            left = 0f,
            top = 0f,
            right = edgeWidth.coerceAtMost(coordinates.size.width.toFloat()),
            bottom = edgeHeight.coerceAtMost(coordinates.size.height.toFloat())
        )
    }.pointerInput(enabled) {
        val recognizer =
            EdgeDrawerDragRecognizer(
                edgeWidth = edgeWidth,
                edgeHeight = edgeHeight,
                openThreshold = DRAWER_OPEN_THRESHOLD.toPx(),
                touchSlop = viewConfiguration.touchSlop
            )
        awaitPointerEventScope {
            while (true) {
                if (awaitEdgeDrawerGesture(recognizer)) onOpenDrawer()
            }
        }
    }
}

private suspend fun AwaitPointerEventScope.awaitEdgeDrawerGesture(
    recognizer: EdgeDrawerDragRecognizer
): Boolean {
    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
    if (!recognizer.startsAtEdge(down.position)) return false
    var recognized = false
    var cancelled = false
    while (!recognized && !cancelled) {
        val event = awaitPointerEvent(pass = PointerEventPass.Initial)
        val change = event.changes.firstOrNull { it.id == down.id }
        if (change == null || !change.pressed) {
            cancelled = true
        } else {
            when (recognizer.classify(change.position - down.position)) {
                DragRecognition.CONTINUE -> Unit

                DragRecognition.CANCEL -> cancelled = true

                DragRecognition.OPEN -> {
                    change.consume()
                    recognized = true
                }
            }
        }
    }
    return recognized
}

private class EdgeDrawerDragRecognizer(
    private val edgeWidth: Float,
    private val edgeHeight: Float,
    private val openThreshold: Float,
    private val touchSlop: Float
) {
    fun startsAtEdge(position: Offset): Boolean =
        position.x <= edgeWidth && position.y <= edgeHeight

    fun classify(drag: Offset): DragRecognition = when {
        isVerticalGesture(drag) -> DragRecognition.CANCEL
        isDrawerOpeningGesture(drag) -> DragRecognition.OPEN
        else -> DragRecognition.CONTINUE
    }

    private fun isVerticalGesture(drag: Offset): Boolean =
        abs(drag.y) >= touchSlop && abs(drag.y) > abs(drag.x)

    private fun isDrawerOpeningGesture(drag: Offset): Boolean =
        drag.x >= openThreshold && drag.x > abs(drag.y) * HORIZONTAL_DOMINANCE_RATIO
}

private enum class DragRecognition {
    CONTINUE,
    CANCEL,
    OPEN
}
