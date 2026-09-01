package com.secondpasslibrary.reader.reader.readium.annotations

import com.secondpasslibrary.reader.reader.annotations.selection.ReaderSelectionEvents
import com.secondpasslibrary.reader.reader.readium.cfi.ReadiumCfiNavigatorBinding
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.withContext
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.input.DragEvent
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.shared.ExperimentalReadiumApi

/** Combines real DOM selection events with Readium input completion as a bounded fallback. */
@OptIn(ExperimentalReadiumApi::class, FlowPreview::class)
internal class ReadiumSelectionEvents(cfiBinding: ReadiumCfiNavigatorBinding) :
    ReaderSelectionEvents,
    InputListener,
    AutoCloseable {
    private val signals = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val documentObserver = ReadiumSelectionDocumentObserver(cfiBinding) {
        signals.tryEmit(Unit)
    }
    private var navigator: EpubNavigatorFragment? = null

    override fun changes(): Flow<Unit> = signals.debounce(SELECTION_SETTLE_DELAY)

    override fun onTap(event: TapEvent): Boolean {
        signals.tryEmit(Unit)
        return false
    }

    override fun onDrag(event: DragEvent): Boolean {
        if (event.type == DragEvent.Type.End) signals.tryEmit(Unit)
        return false
    }

    fun bind(next: EpubNavigatorFragment) {
        if (navigator === next) return
        navigator?.let(::unbind)
        navigator = next
        next.addInputListener(this)
        documentObserver.bind(next)
    }

    fun unbind(current: EpubNavigatorFragment) {
        if (navigator !== current) return
        documentObserver.unbind(current)
        current.removeInputListener(this)
        navigator = null
    }

    fun javascriptInterface(): Any = documentObserver.javascriptInterface()

    fun documentLoaded() = documentObserver.documentLoaded()

    override suspend fun clear() {
        val current = navigator ?: return
        withContext(Dispatchers.Main.immediate) { current.clearSelection() }
    }

    override fun close() {
        navigator?.let(::unbind)
        navigator = null
        documentObserver.close()
    }

    private companion object {
        val SELECTION_SETTLE_DELAY = 150.milliseconds
    }
}
