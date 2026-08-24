package com.secondpasslibrary.reader.reader.readium

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
import org.readium.r2.shared.publication.Locator

/** Converts Readium's public current-locator state into settled, renderer-neutral movement events. */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
internal class ReadiumViewportMovements(
    private val settleDelayMillis: Long = MOVEMENT_SETTLE_DELAY_MILLIS
) : ReaderViewportMovements,
    AutoCloseable {
    private val tracker = SettledViewportMovementTracker<Locator>(settleDelayMillis)

    override fun settled(): Flow<ReaderViewportMovement> = tracker.settled()

    fun bind(next: EpubNavigatorFragment) {
        tracker.bind(next.currentLocator)
    }

    fun unbind(current: EpubNavigatorFragment) {
        tracker.unbind(current.currentLocator)
    }

    override fun close() {
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
    private val location = MutableStateFlow<StateFlow<T>?>(null)
    private val sequences = AtomicLong()

    fun settled(): Flow<ReaderViewportMovement> = location.flatMapLatest { current ->
        current
            // A newly attached navigator's current location is its baseline, not a movement.
            ?.drop(1)
            ?.debounce(settleDelayMillis)
            ?.map { ReaderViewportMovement(sequences.incrementAndGet()) }
            ?: emptyFlow()
    }

    fun bind(next: StateFlow<T>) {
        location.value = next
    }

    fun unbind(current: StateFlow<T>) {
        location.compareAndSet(current, null)
    }

    override fun close() {
        location.value = null
    }
}
