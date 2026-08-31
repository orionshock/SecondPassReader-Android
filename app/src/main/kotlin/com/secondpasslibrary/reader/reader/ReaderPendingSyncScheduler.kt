package com.secondpasslibrary.reader.reader

/** Neutral port for ensuring durable Reader work has a future sync opportunity. */
internal interface ReaderPendingSyncScheduler {
    suspend fun ensureEnqueued(account: LocalReaderAccountKey)

    fun cancel(account: LocalReaderAccountKey)
}
