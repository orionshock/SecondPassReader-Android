package com.secondpasslibrary.reader.reader.progress

import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.reader.cfi.EpubCfi
import com.secondpasslibrary.reader.reader.session.ReaderSessionStatus
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class ReaderProgressSyncState(
    val sessionId: String,
    val latestCapturedVersion: Long,
    val latestSyncedVersion: Long,
    val inFlightVersion: Long?,
    val dirty: Boolean,
    val lastFailure: ReaderProgressSyncFailure?
)

/** Coalesces captured progress and serializes authoritative Session progress replacement. */
@Suppress("TooManyFunctions") // Explicit event handlers keep one serialized sync state machine.
internal class ReaderProgressSyncController(
    private val scope: CoroutineScope,
    private val writer: ReaderProgressWriter,
    private val onAuthenticationRejected: () -> Unit = {}
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
                is SyncEvent.TimerElapsed -> timerElapsed(model, event)
                is SyncEvent.WriteCompleted -> writeCompleted(model, event)
            }
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

    private fun acceptCandidate(current: SyncModel, event: SyncEvent.Candidate): SyncModel {
        val candidate = event.progress?.takeIf { event.bindingId == current.bindingId }
            ?: return current
        val base = if (current.sessionId != null && current.sessionId != candidate.sessionId) {
            cancelPendingWork()
            current.copy(
                sessionId = candidate.sessionId,
                latestCfi = null,
                latestVersion = 0,
                syncedVersion = 0,
                eligibleVersion = null,
                inFlightVersion = null,
                lastFailure = null
            )
        } else {
            current
        }
        val cfi = candidate.latestCandidate
        return when {
            candidate.sessionStatus != ReaderSessionStatus.ACTIVE || !candidate.captureEnabled ->
                base.withSession(candidate.sessionId)

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

    private fun writeCompleted(current: SyncModel, event: SyncEvent.WriteCompleted): SyncModel {
        if (event.bindingId != current.bindingId || event.version != current.inFlightVersion) {
            return current
        }
        writeJob = null
        val updated = when (val outcome = event.outcome) {
            ReaderProgressWriteOutcome.Success -> current.copy(
                syncedVersion = maxOf(current.syncedVersion, event.version),
                inFlightVersion = null,
                lastFailure = null
            )

            is ReaderProgressWriteOutcome.Failure -> current.copy(
                inFlightVersion = null,
                lastFailure = outcome.reason
            ).also {
                if (outcome.reason == ReaderProgressSyncFailure.AUTHENTICATION_REQUIRED) {
                    onAuthenticationRejected()
                }
            }
        }
        return if (event.outcome is ReaderProgressWriteOutcome.Success) pump(updated) else updated
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
            events.send(SyncEvent.WriteCompleted(model.bindingId, version, outcome))
        }
        return started
    }

    private fun publish(model: SyncModel) {
        mutableState.value = model.toState()
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
            val canReachServer = authorityAvailable && authorityAllowed && !writeActive
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
        data class TimerElapsed(val bindingId: Long, val version: Long) : SyncEvent
        data class WriteCompleted(
            val bindingId: Long,
            val version: Long,
            val outcome: ReaderProgressWriteOutcome
        ) : SyncEvent
    }

    private companion object {
        const val PROGRESS_SYNC_WINDOW_MILLIS = 3_000L
    }
}
