package com.secondpasslibrary.reader.reader.progress

import com.secondpasslibrary.reader.reader.LocalReaderAccountKey
import com.secondpasslibrary.reader.reader.persistence.LocalReaderStateStore
import com.secondpasslibrary.reader.reader.persistence.LocalReaderWriteProvenance
import com.secondpasslibrary.reader.reader.session.ReaderSessionContext
import com.secondpasslibrary.reader.reader.session.ReaderSessionStatus
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

internal enum class ReaderProgressFlushResult {
    PERSISTED,
    CLEAN,
    NOT_WRITABLE,
    FAILED,
    TIMED_OUT,
    OWNERSHIP_CHANGED,
    DISPOSED
}

/** Durably records Reader progress and debounces foreground outbox sync requests. */
internal class ReaderProgressPersistenceController(
    private val scope: CoroutineScope,
    private val store: LocalReaderStateStore,
    private val onSyncRequested: () -> Unit
) : AutoCloseable {
    @Volatile private var owner: Owner? = null
    private val generation = AtomicLong()

    @Volatile private var progressJob: Job? = null

    @Volatile private var timerJob: Job? = null
    private val disposed = AtomicBoolean()
    private val persistenceMutex = Mutex()

    fun start(
        account: LocalReaderAccountKey,
        session: ReaderSessionContext,
        progress: StateFlow<ReaderProgressState?>
    ) {
        reset()
        val selected = Owner(account, session, progress)
        owner = selected
        val activeGeneration = generation.get()
        progressJob = scope.launch {
            progress.collect { candidate ->
                persistCandidate(selected, activeGeneration, candidate)
            }
        }
    }

    suspend fun flushLatestLocal(): ReaderProgressFlushResult =
        withTimeoutOrNull(LOCAL_FLUSH_TIMEOUT_MILLIS) {
            val selected = owner
            when {
                disposed.get() -> ReaderProgressFlushResult.DISPOSED
                selected == null -> ReaderProgressFlushResult.CLEAN
                else -> flush(selected, generation.get())
            }
        } ?: ReaderProgressFlushResult.TIMED_OUT

    private suspend fun flush(selected: Owner, activeGeneration: Long): ReaderProgressFlushResult =
        try {
            persistenceMutex.withLock {
                val candidate = selected.progress.value
                val cfi = candidate?.latestCandidate
                when {
                    candidate == null || cfi == null -> ReaderProgressFlushResult.CLEAN

                    candidate.sessionStatus != ReaderSessionStatus.ACTIVE ->
                        ReaderProgressFlushResult.NOT_WRITABLE

                    candidate.candidateVersion <= selected.persistedVersion.get() ->
                        ReaderProgressFlushResult.CLEAN

                    else -> {
                        store.writeProgress(
                            selected.account,
                            selected.session.sessionId,
                            cfi.value,
                            LocalReaderWriteProvenance.LOCAL_PENDING,
                            candidate.latestLocationLabel
                        )
                        if (owner !== selected || generation.get() != activeGeneration) {
                            ReaderProgressFlushResult.OWNERSHIP_CHANGED
                        } else {
                            selected.persistedVersion.set(candidate.candidateVersion)
                            ReaderProgressFlushResult.PERSISTED
                        }
                    }
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            ReaderProgressFlushResult.FAILED
        }

    fun reset() {
        generation.incrementAndGet()
        progressJob?.cancel()
        progressJob = null
        timerJob?.cancel()
        timerJob = null
        owner = null
    }

    override fun close() {
        if (!disposed.compareAndSet(false, true)) return
        reset()
    }

    private suspend fun persistCandidate(
        selected: Owner,
        activeGeneration: Long,
        candidate: ReaderProgressState?
    ) {
        if (candidate != null && candidate.sessionId == selected.session.sessionId) {
            persistenceMutex.withLock {
                val stale = owner !== selected || generation.get() != activeGeneration
                val writable = candidate.sessionStatus == ReaderSessionStatus.ACTIVE &&
                    candidate.captureEnabled
                val cfi = candidate.latestCandidate
                when {
                    stale -> Unit

                    !writable -> {
                        timerJob?.cancel()
                        timerJob = null
                    }

                    cfi == null || candidate.candidateVersion <= selected.persistedVersion.get() ->
                        Unit

                    else -> {
                        store.writeProgress(
                            selected.account,
                            selected.session.sessionId,
                            cfi.value,
                            LocalReaderWriteProvenance.LOCAL_PENDING,
                            candidate.latestLocationLabel
                        )
                        val stillCurrent = owner === selected &&
                            generation.get() == activeGeneration
                        if (stillCurrent) {
                            selected.persistedVersion.set(candidate.candidateVersion)
                            scheduleSync(selected, activeGeneration, candidate.candidateVersion)
                        }
                    }
                }
            }
        }
    }

    private fun scheduleSync(selected: Owner, activeGeneration: Long, version: Long) {
        timerJob?.cancel()
        timerJob = scope.launch {
            delay(PROGRESS_SYNC_WINDOW_MILLIS)
            if (owner === selected && generation.get() == activeGeneration &&
                selected.persistedVersion.get() == version
            ) {
                onSyncRequested()
            }
        }
    }

    private data class Owner(
        val account: LocalReaderAccountKey,
        val session: ReaderSessionContext,
        val progress: StateFlow<ReaderProgressState?>,
        val persistedVersion: AtomicLong = AtomicLong()
    )

    private companion object {
        const val LOCAL_FLUSH_TIMEOUT_MILLIS = 750L
        const val PROGRESS_SYNC_WINDOW_MILLIS = 3_000L
    }
}
