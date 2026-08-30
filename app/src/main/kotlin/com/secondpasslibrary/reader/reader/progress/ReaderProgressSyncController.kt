package com.secondpasslibrary.reader.reader.progress

import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.reader.cfi.EpubCfi
import com.secondpasslibrary.reader.reader.session.ReaderSessionStatus
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

internal data class ReaderProgressSyncState(
    val sessionId: String,
    val latestCapturedVersion: Long,
    val latestSyncedVersion: Long,
    val inFlightVersion: Long?,
    val dirty: Boolean,
    val lastFailure: ReaderProgressSyncFailure?
)

internal enum class ReaderProgressFlushResult {
    FLUSHED,
    CLEAN,
    NOT_WRITABLE,
    AUTHORITY_UNAVAILABLE,
    FAILED,
    OWNERSHIP_CHANGED,
    TIMED_OUT,
    DISPOSED
}

/** Coalesces captured progress and serializes authoritative Session progress replacement. */
@Suppress("TooManyFunctions") // Explicit event handlers keep one serialized sync state machine.
internal class ReaderProgressSyncController(
    private val scope: CoroutineScope,
    private val writer: ReaderProgressWriter,
    private val onAuthenticationRejected: () -> Unit = {},
    private val onProgressConfirmed: suspend (String, EpubCfi) -> Unit = { _, _ -> }
) : AutoCloseable {
    private val events = Channel<SyncEvent>(Channel.UNLIMITED)
    private val bindingIds = AtomicLong()
    private val authorityAllowed = AtomicBoolean()
    private val mutableState = MutableStateFlow<ReaderProgressSyncState?>(null)
    val state = mutableState.asStateFlow()
    private val actorJob = scope.launch(start = CoroutineStart.UNDISPATCHED) { runActor() }
    private var progressJob: Job? = null
    private var timerJob: Job? = null
    private var writeJob: Job? = null
    private val flushWaiters = mutableListOf<CompletableDeferred<ReaderProgressFlushResult>>()

    fun start(profile: ConnectionProfile, progress: StateFlow<ReaderProgressState?>) {
        progressJob?.cancel()
        val bindingId = bindingIds.incrementAndGet()
        events.trySend(SyncEvent.Bind(bindingId, profile))
        progressJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            progress.collect { events.send(SyncEvent.Candidate(bindingId, it)) }
        }
    }

    fun setAuthorityAvailable(available: Boolean) {
        authorityAllowed.set(available)
        events.trySend(SyncEvent.AuthorityChanged(available))
    }

    suspend fun flushLatest(): ReaderProgressFlushResult {
        val waiter = CompletableDeferred<ReaderProgressFlushResult>()
        if (events.trySend(SyncEvent.Flush(waiter)).isFailure) {
            return ReaderProgressFlushResult.DISPOSED
        }
        val result = withTimeoutOrNull(PROGRESS_FLUSH_TIMEOUT_MILLIS) { waiter.await() }
        if (result == null) waiter.cancel()
        return result ?: ReaderProgressFlushResult.TIMED_OUT
    }

    fun reset() {
        progressJob?.cancel()
        progressJob = null
        events.trySend(SyncEvent.Reset(bindingIds.incrementAndGet()))
    }

    override fun close() {
        progressJob?.cancel()
        timerJob?.cancel()
        writeJob?.cancel()
        actorJob.cancel()
        events.close()
        mutableState.value = null
    }

    private suspend fun runActor() {
        var model = SyncModel()
        for (event in events) {
            model = when (event) {
                is SyncEvent.Bind -> bind(model, event)
                is SyncEvent.Reset -> reset(model, event.bindingId)
                is SyncEvent.AuthorityChanged -> authorityChanged(model, event.available)
                is SyncEvent.Candidate -> acceptCandidate(model, event)
                is SyncEvent.Flush -> flush(model, event.waiter)
                is SyncEvent.TimerElapsed -> timerElapsed(model, event)
                is SyncEvent.WriteCompleted -> writeCompleted(model, event)
            }
            settleFlushWaiters(model, event)
            publish(model)
        }
    }

    private fun bind(current: SyncModel, event: SyncEvent.Bind): SyncModel {
        cancelPendingWork()
        return SyncModel(
            bindingId = event.bindingId,
            profile = event.profile,
            authorityAvailable = current.authorityAvailable
        )
    }

    private fun reset(current: SyncModel, bindingId: Long): SyncModel {
        cancelPendingWork()
        return SyncModel(bindingId = bindingId, authorityAvailable = current.authorityAvailable)
    }

    private fun authorityChanged(current: SyncModel, available: Boolean): SyncModel {
        val updated = current.copy(authorityAvailable = available)
        return if (available) pump(updated) else updated
    }

    private fun flush(
        current: SyncModel,
        waiter: CompletableDeferred<ReaderProgressFlushResult>
    ): SyncModel {
        flushWaiters.removeAll { !it.isActive }
        val immediate = current.flushBlocker()
        if (immediate != null) {
            waiter.complete(immediate)
            return current
        }
        flushWaiters += waiter
        timerJob?.cancel()
        timerJob = null
        return pump(current.copy(eligibleVersion = current.latestVersion))
    }

    private fun acceptCandidate(current: SyncModel, event: SyncEvent.Candidate): SyncModel {
        val candidate = event.progress?.takeIf { event.bindingId == current.bindingId }
            ?: return current
        val base = if (current.sessionId != null && current.sessionId != candidate.sessionId) {
            completeFlushWaiters(ReaderProgressFlushResult.OWNERSHIP_CHANGED)
            cancelPendingWork()
            current.copy(
                sessionId = candidate.sessionId,
                latestCfi = null,
                latestVersion = 0,
                syncedVersion = 0,
                eligibleVersion = null,
                inFlightVersion = null,
                sessionActive = candidate.sessionStatus == ReaderSessionStatus.ACTIVE,
                lastFailure = null
            )
        } else {
            current.copy(sessionActive = candidate.sessionStatus == ReaderSessionStatus.ACTIVE)
        }
        val cfi = candidate.latestCandidate
        return when {
            candidate.sessionStatus != ReaderSessionStatus.ACTIVE || !candidate.captureEnabled -> {
                timerJob?.cancel()
                timerJob = null
                base.withSession(candidate.sessionId)
            }

            cfi == null -> base.withSession(candidate.sessionId)

            candidate.candidateVersion <= base.latestVersion -> base

            else -> base.copy(
                sessionId = candidate.sessionId,
                latestCfi = cfi,
                latestVersion = candidate.candidateVersion,
                eligibleVersion = null,
                lastFailure = null
            ).also(::scheduleTimer)
        }
    }

    private fun timerElapsed(current: SyncModel, event: SyncEvent.TimerElapsed): SyncModel {
        if (event.bindingId != current.bindingId || event.version != current.latestVersion) {
            return current
        }
        val updated = current.copy(eligibleVersion = event.version)
        return pump(updated)
    }

    private suspend fun writeCompleted(
        current: SyncModel,
        event: SyncEvent.WriteCompleted
    ): SyncModel {
        if (event.bindingId != current.bindingId || event.version != current.inFlightVersion) {
            return current
        }
        writeJob = null
        val updated = when (val outcome = event.outcome) {
            ReaderProgressWriteOutcome.Success -> {
                runCatching { onProgressConfirmed(event.sessionId, event.cfi) }
                current.copy(
                    syncedVersion = maxOf(current.syncedVersion, event.version),
                    inFlightVersion = null,
                    lastFailure = null
                )
            }

            is ReaderProgressWriteOutcome.Failure -> current.copy(
                inFlightVersion = null,
                lastFailure = outcome.reason
            ).also {
                if (outcome.reason == ReaderProgressSyncFailure.AUTHENTICATION_REQUIRED) {
                    onAuthenticationRejected()
                }
            }
        }
        val flushHasNewerCandidate = flushWaiters.any { it.isActive } &&
            updated.latestVersion > event.version
        return if (event.outcome is ReaderProgressWriteOutcome.Success || flushHasNewerCandidate) {
            pump(updated.copy(eligibleVersion = updated.latestVersion))
        } else {
            updated
        }
    }

    private fun scheduleTimer(model: SyncModel) {
        timerJob?.cancel()
        timerJob = scope.launch {
            delay(PROGRESS_SYNC_WINDOW_MILLIS)
            events.send(SyncEvent.TimerElapsed(model.bindingId, model.latestVersion))
        }
    }

    private fun pump(model: SyncModel): SyncModel {
        val submission = model.submission(
            authorityAllowed = authorityAllowed.get(),
            writeActive = writeJob?.isActive == true
        ) ?: return model
        val version = submission.version
        val started = model.copy(inFlightVersion = version)
        writeJob = scope.launch {
            val outcome = writer.replace(submission.profile, submission.sessionId, submission.cfi)
            events.send(
                SyncEvent.WriteCompleted(
                    model.bindingId,
                    version,
                    submission.sessionId,
                    submission.cfi,
                    outcome
                )
            )
        }
        return started
    }

    private fun publish(model: SyncModel) {
        mutableState.value = model.toState()
    }

    private fun settleFlushWaiters(model: SyncModel, event: SyncEvent) {
        flushWaiters.removeAll { !it.isActive }
        val result = when {
            event is SyncEvent.Bind || event is SyncEvent.Reset ->
                ReaderProgressFlushResult.OWNERSHIP_CHANGED

            !model.authorityAvailable || !authorityAllowed.get() ->
                ReaderProgressFlushResult.AUTHORITY_UNAVAILABLE

            !model.sessionActive -> ReaderProgressFlushResult.NOT_WRITABLE

            model.latestVersion <= model.syncedVersion && model.inFlightVersion == null ->
                ReaderProgressFlushResult.FLUSHED

            event is SyncEvent.WriteCompleted &&
                event.outcome is ReaderProgressWriteOutcome.Failure &&
                model.inFlightVersion == null -> ReaderProgressFlushResult.FAILED

            else -> null
        }
        if (result != null) {
            completeFlushWaiters(result)
        }
    }

    private fun completeFlushWaiters(result: ReaderProgressFlushResult) {
        flushWaiters.forEach { it.complete(result) }
        flushWaiters.clear()
    }

    private fun cancelPendingWork() {
        timerJob?.cancel()
        timerJob = null
        writeJob?.cancel()
        writeJob = null
    }

    private data class SyncModel(
        val bindingId: Long = 0,
        val profile: ConnectionProfile? = null,
        val authorityAvailable: Boolean = false,
        val sessionId: String? = null,
        val latestCfi: EpubCfi? = null,
        val latestVersion: Long = 0,
        val syncedVersion: Long = 0,
        val eligibleVersion: Long? = null,
        val inFlightVersion: Long? = null,
        val sessionActive: Boolean = false,
        val lastFailure: ReaderProgressSyncFailure? = null
    ) {
        fun withSession(nextSessionId: String): SyncModel = if (sessionId == null) {
            copy(sessionId = nextSessionId)
        } else {
            this
        }

        fun toState(): ReaderProgressSyncState? = sessionId?.let {
            ReaderProgressSyncState(
                sessionId = it,
                latestCapturedVersion = latestVersion,
                latestSyncedVersion = syncedVersion,
                inFlightVersion = inFlightVersion,
                dirty = latestVersion > syncedVersion,
                lastFailure = lastFailure
            )
        }

        fun submission(authorityAllowed: Boolean, writeActive: Boolean): ProgressSubmission? {
            val canReachServer = authorityAvailable && authorityAllowed && sessionActive &&
                !writeActive
            val hasEligibleProgress = eligibleVersion == latestVersion &&
                latestVersion > syncedVersion
            return if (canReachServer && hasEligibleProgress) {
                profile?.let { profile ->
                    sessionId?.let { sessionId ->
                        latestCfi?.let { cfi ->
                            ProgressSubmission(profile, sessionId, cfi, latestVersion)
                        }
                    }
                }
            } else {
                null
            }
        }

        fun flushBlocker(): ReaderProgressFlushResult? = when {
            sessionId == null -> ReaderProgressFlushResult.CLEAN
            !sessionActive -> ReaderProgressFlushResult.NOT_WRITABLE
            latestVersion <= syncedVersion -> ReaderProgressFlushResult.CLEAN
            !authorityAvailable -> ReaderProgressFlushResult.AUTHORITY_UNAVAILABLE
            else -> null
        }
    }

    private data class ProgressSubmission(
        val profile: ConnectionProfile,
        val sessionId: String,
        val cfi: EpubCfi,
        val version: Long
    )

    private sealed interface SyncEvent {
        data class Bind(val bindingId: Long, val profile: ConnectionProfile) : SyncEvent
        data class Reset(val bindingId: Long) : SyncEvent
        data class AuthorityChanged(val available: Boolean) : SyncEvent
        data class Candidate(val bindingId: Long, val progress: ReaderProgressState?) : SyncEvent
        data class Flush(val waiter: CompletableDeferred<ReaderProgressFlushResult>) : SyncEvent
        data class TimerElapsed(val bindingId: Long, val version: Long) : SyncEvent
        data class WriteCompleted(
            val bindingId: Long,
            val version: Long,
            val sessionId: String,
            val cfi: EpubCfi,
            val outcome: ReaderProgressWriteOutcome
        ) : SyncEvent
    }

    private companion object {
        const val PROGRESS_SYNC_WINDOW_MILLIS = 3_000L
        const val PROGRESS_FLUSH_TIMEOUT_MILLIS = 1_500L
    }
}
