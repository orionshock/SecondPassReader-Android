package com.secondpasslibrary.reader.reader.readium.viewport

import com.secondpasslibrary.reader.reader.navigation.ReaderInteractionZone
import com.secondpasslibrary.reader.reader.navigation.ReaderPublicationAction
import com.secondpasslibrary.reader.reader.navigation.ReaderPublicationInteractionPolicy
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.input.DragEvent
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.KeyEvent
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.preferences.ReadingProgression
import org.readium.r2.navigator.util.DirectionalNavigationAdapter
import org.readium.r2.shared.ExperimentalReadiumApi

/** Arbitrates only Readium input left unhandled by selection, links, and decorations. */
@OptIn(ExperimentalReadiumApi::class)
internal class ReadiumPublicationInputAdapter(private val onBodyTap: () -> Unit) : InputListener {
    private var navigator: EpubNavigatorFragment? = null
    private var keyboardNavigation: DirectionalNavigationAdapter? = null
    private var interactionSuppressed = false
    private var draggingEdge: ReaderInteractionZone? = null

    fun bind(next: EpubNavigatorFragment) {
        if (navigator === next) return
        navigator?.let(::unbind)
        navigator = next
        // Readium keeps its reading-direction-aware keyboard behavior, without a second tap owner.
        keyboardNavigation = DirectionalNavigationAdapter(next, tapEdges = emptySet())
        next.addInputListener(this)
    }

    fun unbind(current: EpubNavigatorFragment) {
        if (navigator !== current) return
        current.removeInputListener(this)
        keyboardNavigation = null
        navigator = null
        draggingEdge = null
    }

    fun setInteractionSuppressed(suppressed: Boolean) {
        interactionSuppressed = suppressed
        if (suppressed) draggingEdge = null
    }

    override fun onTap(event: TapEvent): Boolean {
        val bound = navigator
        return if (bound == null || interactionSuppressed) {
            false
        } else {
            val action = ReaderPublicationInteractionPolicy.action(
                ReaderPublicationInteractionPolicy.tapZone(
                    event.point.x,
                    bound.publicationView.width.toFloat()
                ),
                bound.overflow.value.readingProgression == ReadingProgression.RTL
            )
            if (action == ReaderPublicationAction.TOGGLE_CHROME) {
                onBodyTap()
            } else if (!bound.overflow.value.scroll) {
                navigate(bound, action)
            }
            // A bounded edge tap is consumed even at the beginning or end of the Book.
            true
        }
    }

    override fun onDrag(event: DragEvent): Boolean {
        val bound = navigator
        return if (bound == null || interactionSuppressed || bound.overflow.value.scroll) {
            draggingEdge = null
            false
        } else {
            when (event.type) {
                DragEvent.Type.Start -> {
                    draggingEdge = ReaderPublicationInteractionPolicy.swipeZone(
                        event.start.x,
                        bound.publicationView.width.toFloat(),
                        bound.publicationView.resources.displayMetrics.density
                    ).takeIf {
                        ReaderPublicationInteractionPolicy.startsEdgeSwipe(
                            it,
                            event.offset.x,
                            event.offset.y
                        )
                    }
                    draggingEdge != null
                }

                DragEvent.Type.Move -> draggingEdge != null

                DragEvent.Type.End -> {
                    val edge = draggingEdge
                    draggingEdge = null
                    if (edge != null && ReaderPublicationInteractionPolicy.completesEdgeSwipe(
                            event.offset.x,
                            event.offset.y,
                            bound.publicationView.resources.displayMetrics.density
                        )
                    ) {
                        navigate(
                            bound,
                            ReaderPublicationInteractionPolicy.action(
                                edge,
                                bound.overflow.value.readingProgression == ReadingProgression.RTL
                            )
                        )
                    }
                    edge != null
                }
            }
        }
    }

    override fun onKey(event: KeyEvent): Boolean = keyboardNavigation?.onKey(event) ?: false

    private fun navigate(bound: EpubNavigatorFragment, action: ReaderPublicationAction) {
        when (action) {
            ReaderPublicationAction.PREVIOUS_PAGE -> bound.goBackward(animated = false)
            ReaderPublicationAction.NEXT_PAGE -> bound.goForward(animated = false)
            ReaderPublicationAction.TOGGLE_CHROME -> Unit
        }
    }
}
