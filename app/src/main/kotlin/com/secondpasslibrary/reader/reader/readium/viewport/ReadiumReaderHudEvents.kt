package com.secondpasslibrary.reader.reader.readium.viewport

import com.secondpasslibrary.reader.reader.domain.ReaderHudEvents
import com.secondpasslibrary.reader.reader.domain.ReaderReadingStatus
import com.secondpasslibrary.reader.reader.domain.ReaderReadingStatusScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator

/** Adapts Readium's live resource pagination and publication taps to Reader HUD events. */
@OptIn(ExperimentalReadiumApi::class)
internal class ReadiumReaderHudEvents(private val onPageChanged: () -> Unit = {}) :
    ReaderHudEvents,
    InputListener,
    AutoCloseable {
    private val pagination = ReadiumSectionPaginationTracker()
    private val taps = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val paginationChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private var navigator: EpubNavigatorFragment? = null

    override val readingStatus = pagination.status

    override fun publicationTaps(): Flow<Unit> = taps

    fun paginationListener(): EpubNavigatorFragment.PaginationListener {
        val generation = pagination.newGeneration()
        return object : EpubNavigatorFragment.PaginationListener {
            override fun onPageChanged(pageIndex: Int, totalPages: Int, locator: Locator) {
                if (pagination.publish(generation, pageIndex, totalPages)) {
                    onPageChanged()
                    paginationChanges.tryEmit(Unit)
                }
            }

            override fun onPageLoaded() {
                if (pagination.isCurrent(generation)) paginationChanges.tryEmit(Unit)
            }
        }
    }

    override fun onTap(event: TapEvent): Boolean {
        taps.tryEmit(Unit)
        return false
    }

    fun paginationChanges(): Flow<Unit> = paginationChanges

    fun bind(next: EpubNavigatorFragment) {
        if (navigator === next) return
        navigator?.let(::unbind)
        navigator = next
        next.addInputListener(this)
        paginationChanges.tryEmit(Unit)
    }

    fun unbind(current: EpubNavigatorFragment) {
        if (navigator !== current) return
        current.removeInputListener(this)
        navigator = null
        pagination.invalidate()
    }

    override fun close() {
        navigator?.let(::unbind)
        navigator = null
        pagination.invalidate()
    }
}

internal class ReadiumSectionPaginationTracker {
    private val mutableStatus = MutableStateFlow<ReaderReadingStatus?>(null)
    private var generation = 0L
    val status = mutableStatus.asStateFlow()

    fun newGeneration(): Long {
        generation += 1
        mutableStatus.value = null
        return generation
    }

    fun publish(sourceGeneration: Long, pageIndex: Int, totalPages: Int): Boolean {
        if (!isCurrent(sourceGeneration)) return false
        mutableStatus.value = readerSectionStatus(pageIndex, totalPages)
        return true
    }

    fun isCurrent(sourceGeneration: Long): Boolean = sourceGeneration == generation

    fun invalidate() {
        generation += 1
        mutableStatus.value = null
    }
}

internal fun readerSectionStatus(pageIndex: Int, totalPages: Int): ReaderReadingStatus? {
    if (pageIndex < 0 || totalPages <= 0 || pageIndex >= totalPages) return null
    return ReaderReadingStatus(
        pagesRemaining = totalPages - pageIndex - 1,
        scope = ReaderReadingStatusScope.SECTION
    )
}
