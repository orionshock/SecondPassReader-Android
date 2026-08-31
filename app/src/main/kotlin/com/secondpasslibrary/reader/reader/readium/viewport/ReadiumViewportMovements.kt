package com.secondpasslibrary.reader.reader.readium.viewport

import com.secondpasslibrary.reader.reader.domain.ReaderViewportMovement
import com.secondpasslibrary.reader.reader.domain.ReaderViewportMovements
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import org.readium.r2.navigator.epub.EpubNavigatorFragment

/** Converts Readium's public current-locator state into settled, renderer-neutral movement events. */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
internal class ReadiumViewportMovements(
    private val settleDelayMillis: Long = MOVEMENT_SETTLE_DELAY_MILLIS
) : ReaderViewportMovements,
    AutoCloseable {
    private val pageChanges = MutableStateFlow(0L)
    private val tracker = SettledViewportMovementTracker<Long>(settleDelayMillis)
    private var navigator: EpubNavigatorFragment? = null

    override fun settled(): Flow<ReaderViewportMovement> = tracker.settled()

    fun bind(next: EpubNavigatorFragment) {
        if (navigator === next) return
        navigator = next
        tracker.bind(pageChanges)
    }

    fun unbind(current: EpubNavigatorFragment) {
        if (navigator !== current) return
        navigator = null
        tracker.unbind(pageChanges)
    }

    /** Records the supported Readium pagination callback as a meaningful viewport movement. */
    fun pageChanged() {
        if (navigator != null) pageChanges.value += 1
    }

    suspend fun suppressSettledMovement(block: suspend () -> Unit) {
        tracker.suppress()
        try {
            block()
        } finally {
            tracker.resumeWithCurrentAsBaseline()
        }
    }

    override fun close() {
        navigator = null
        tracker.close()
    }

    private companion object {
        const val MOVEMENT_SETTLE_DELAY_MILLIS = 250L
    }
}

/** Testable state-flow adapter; every new navigator's current value is an attachment baseline. */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
internal class SettledViewportMovementTracker<T : Any>(private val settleDelayMillis: Long) :
    AutoCloseable {
    private val location = MutableStateFlow<Attachment<T>?>(null)
    private val sequences = AtomicLong()
    private val attachmentSequences = AtomicLong()

    fun settled(): Flow<ReaderViewportMovement> = location.flatMapLatest { attachment ->
        attachment?.source
            ?.takeIf { attachment.reporting }
            // A newly attached navigator's current location is its baseline, not a movement.
            ?.drop(1)
            ?.debounce(settleDelayMillis)
            ?.map { ReaderViewportMovement(sequences.incrementAndGet()) }
            ?: emptyFlow()
    }

    fun bind(next: StateFlow<T>) {
        location.value = next.attachment(reporting = true)
    }

    fun unbind(current: StateFlow<T>) {
        location.value?.takeIf { it.source === current }?.let { location.compareAndSet(it, null) }
    }

    fun suppress() {
        location.value?.let { current ->
            location.value = current.source.attachment(reporting = false)
        }
    }

    fun resumeWithCurrentAsBaseline() {
        location.value?.let { current ->
            location.value = current.source.attachment(reporting = true)
        }
    }

    override fun close() {
        location.value = null
    }

    private fun StateFlow<T>.attachment(reporting: Boolean) = Attachment(
        source = this,
        generation = attachmentSequences.incrementAndGet(),
        reporting = reporting
    )

    private data class Attachment<T>(
        val source: StateFlow<T>,
        val generation: Long,
        val reporting: Boolean
    )
}
