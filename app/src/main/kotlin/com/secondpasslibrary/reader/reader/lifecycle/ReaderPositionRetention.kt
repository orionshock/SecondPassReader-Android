package com.secondpasslibrary.reader.reader.lifecycle

import com.secondpasslibrary.reader.reader.cfi.EpubCfi

/** Owns the transient durable position used only while an open Reader engine is recreated. */
internal interface ReaderPositionRetention {
    /** Enables recreation retention after initial Session progress restoration has terminated. */
    fun completeStartupRestore(restoredPosition: EpubCfi?)

    /** Starts a best-effort capture while the current navigator is still live. */
    fun captureBeforeNavigatorLoss()

    /** Awaits only the bounded local capture needed before replacing the navigator. */
    suspend fun awaitPendingCapture(): EpubCfi?

    /** Retains a position already captured by the settled-movement owner without recapturing it. */
    fun retainPosition(position: EpubCfi)
}

internal data object EmptyReaderPositionRetention : ReaderPositionRetention {
    override fun completeStartupRestore(restoredPosition: EpubCfi?) = Unit

    override fun captureBeforeNavigatorLoss() = Unit

    override suspend fun awaitPendingCapture(): EpubCfi? = null

    override fun retainPosition(position: EpubCfi) = Unit
}
