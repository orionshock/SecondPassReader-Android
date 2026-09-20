package com.secondpasslibrary.reader.reader.navigation

import kotlin.math.abs
import kotlin.math.min

internal enum class ReaderInteractionZone { LEFT, BODY, RIGHT }

internal enum class ReaderPublicationAction { PREVIOUS_PAGE, NEXT_PAGE, TOGGLE_CHROME }

/** Classifies unhandled publication input without intercepting the WebView's touch surface. */
internal object ReaderPublicationInteractionPolicy {
    private const val TAP_EDGE_FRACTION = 0.30
    private const val SWIPE_EDGE_WIDTH_DP = 56f
    private const val MAX_SWIPE_EDGE_FRACTION = 0.18f
    private const val SWIPE_DISTANCE_DP = 24f
    private const val HORIZONTAL_INTENT_RATIO = 1.5f

    fun tapZone(x: Float, viewportWidth: Float): ReaderInteractionZone {
        if (viewportWidth <= 0f) return ReaderInteractionZone.BODY
        val position = x.toDouble()
        val width = viewportWidth.toDouble()
        return when {
            position < width * TAP_EDGE_FRACTION -> ReaderInteractionZone.LEFT
            position > width * (1.0 - TAP_EDGE_FRACTION) -> ReaderInteractionZone.RIGHT
            else -> ReaderInteractionZone.BODY
        }
    }

    // Swipes stay narrower than taps so text selection and drawer drags keep more of the page.
    fun swipeZone(x: Float, viewportWidth: Float, density: Float): ReaderInteractionZone {
        if (viewportWidth <= 0f) return ReaderInteractionZone.BODY
        val edgeWidth = min(SWIPE_EDGE_WIDTH_DP * density, viewportWidth * MAX_SWIPE_EDGE_FRACTION)
        return when {
            x < edgeWidth -> ReaderInteractionZone.LEFT
            x >= viewportWidth - edgeWidth -> ReaderInteractionZone.RIGHT
            else -> ReaderInteractionZone.BODY
        }
    }

    fun action(zone: ReaderInteractionZone, rightToLeft: Boolean): ReaderPublicationAction =
        when (zone) {
            ReaderInteractionZone.LEFT -> if (rightToLeft) {
                ReaderPublicationAction.NEXT_PAGE
            } else {
                ReaderPublicationAction.PREVIOUS_PAGE
            }

            ReaderInteractionZone.RIGHT -> if (rightToLeft) {
                ReaderPublicationAction.PREVIOUS_PAGE
            } else {
                ReaderPublicationAction.NEXT_PAGE
            }

            ReaderInteractionZone.BODY -> ReaderPublicationAction.TOGGLE_CHROME
        }

    fun startsEdgeSwipe(zone: ReaderInteractionZone, dx: Float, dy: Float): Boolean =
        horizontal(dx, dy) && when (zone) {
            ReaderInteractionZone.LEFT -> dx > 0f
            ReaderInteractionZone.RIGHT -> dx < 0f
            ReaderInteractionZone.BODY -> false
        }

    fun completesEdgeSwipe(dx: Float, dy: Float, density: Float): Boolean =
        abs(dx) >= SWIPE_DISTANCE_DP * density && horizontal(dx, dy)

    private fun horizontal(dx: Float, dy: Float): Boolean =
        abs(dx) > abs(dy) * HORIZONTAL_INTENT_RATIO
}
