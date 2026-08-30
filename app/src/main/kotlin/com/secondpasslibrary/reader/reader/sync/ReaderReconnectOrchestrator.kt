package com.secondpasslibrary.reader.reader.sync

import com.secondpasslibrary.reader.app.AppAvailability
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.reader.persistence.LocalReaderAccountKey
import com.secondpasslibrary.reader.reader.persistence.ReaderOutboxStore
import com.secondpasslibrary.reader.reader.persistence.ReaderPendingOutboxSession
import com.secondpasslibrary.reader.reader.session.ReaderSessionContext
import com.secondpasslibrary.reader.reader.session.ReaderSessionReconciliation
import com.secondpasslibrary.reader.reader.session.ReaderSessionReconciliationFailure
import com.secondpasslibrary.reader.reader.session.ReaderSessionReconciliationResult
import com.secondpasslibrary.reader.reader.session.ReaderSessionStatus
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class ReaderReconnectReport(
    val hadPendingWork: Boolean,
    val remainingPendingWork: Boolean,
    val authenticationRequired: Boolean = false,
    val transientFailure: Boolean = false
)

internal fun interface ReaderReconnectOperation {
    suspend fun reconnect(
        profile: ConnectionProfile,
        account: LocalReaderAccountKey
    ): ReaderReconnectReport
}

/** The account-scoped reconcile-then-drain operation shared by foreground and WorkManager. */
@Singleton
internal class ReaderReconnectOrchestrator @Inject constructor(
    private val reconciliation: ReaderSessionReconciliation,
    private val outbox: ReaderOutboxStore,
    private val synchronizer: ReaderOutboxSynchronizer
) : ReaderReconnectOperation {
    private val mutex = Mutex()

    override suspend fun reconnect(
        profile: ConnectionProfile,
        account: LocalReaderAccountKey
    ): ReaderReconnectReport = mutex.withLock {
        val hadPending = outbox.hasPendingWork(account)
        var authenticationRequired = false
        var transientFailure = false
        if (hadPending) {
            for (pending in outbox.pendingSessions(account)) {
                when (reconcile(profile, account, pending)) {
                    ReconcileOutcome.AUTHENTICATION_REQUIRED -> {
                        authenticationRequired = true
                        break
                    }

                    ReconcileOutcome.TRANSIENT_FAILURE -> transientFailure = true

                    ReconcileOutcome.SETTLED -> Unit
                }
            }
        }
        ReaderReconnectReport(
            hadPending,
            outbox.hasPendingWork(account),
            authenticationRequired,
            transientFailure
        )
    }

    private suspend fun reconcile(
        profile: ConnectionProfile,
        account: LocalReaderAccountKey,
        pending: ReaderPendingOutboxSession
    ): ReconcileOutcome = when (
        val result = reconciliation.reconcile(
            profile,
            account,
            pending.bookId,
            pending.session
        )
    ) {
        is ReaderSessionReconciliationResult.Failed -> when (result.reason) {
            ReaderSessionReconciliationFailure.AUTHENTICATION_REQUIRED ->
                ReconcileOutcome.AUTHENTICATION_REQUIRED

            ReaderSessionReconciliationFailure.UNAVAILABLE -> ReconcileOutcome.TRANSIENT_FAILURE
        }

        is ReaderSessionReconciliationResult.Resolved ->
            drainResolved(profile, account, result.session)
    }

    private suspend fun drainResolved(
        profile: ConnectionProfile,
        account: LocalReaderAccountKey,
        resolved: ReaderSessionContext
    ): ReconcileOutcome {
        val eligible = resolved.status == ReaderSessionStatus.ACTIVE &&
            resolved.serverSessionId != null
        if (!eligible) return ReconcileOutcome.SETTLED
        val report = synchronizer.syncBoundSession(profile, account, resolved.sessionId)
        return when {
            report.authenticationRequired -> ReconcileOutcome.AUTHENTICATION_REQUIRED

            report.unavailable || report.reconciliationSessionIds.isNotEmpty() ->
                ReconcileOutcome.TRANSIENT_FAILURE

            else -> ReconcileOutcome.SETTLED
        }
    }

    private enum class ReconcileOutcome {
        SETTLED,
        TRANSIENT_FAILURE,
        AUTHENTICATION_REQUIRED
    }
}

/** Foreground availability/generation adapter around the durable reconnect operation. */
internal class ReaderReconnectController(
    private val orchestrator: ReaderReconnectOrchestrator,
    private val scope: CoroutineScope,
    private val onRunCompleted: () -> Unit = {},
    private val onAuthenticationRequired: () -> Unit
) {
    private var owner: Owner? = null
    private var availability: AppAvailability? = null
    private var generation = 0L
    private var job: Job? = null
    private var rerunRequested = false

    fun update(
        profile: ConnectionProfile?,
        profileId: String?,
        nextAvailability: AppAvailability?
    ) {
        val nextOwner = if (profile != null && profileId != null) {
            Owner(profile, LocalReaderAccountKey.from(profile.serverOrigin, profileId))
        } else {
            null
        }
        val recovered = nextAvailability is AppAvailability.Online &&
            availability !is AppAvailability.Online
        val accountChanged = nextOwner != owner
        if (accountChanged || nextAvailability !is AppAvailability.Online) cancelRun()
        owner = nextOwner
        availability = nextAvailability
        if (nextAvailability is AppAvailability.Online && (recovered || accountChanged)) {
            start(nextOwner ?: return)
        }
    }

    fun clear() {
        cancelRun()
        owner = null
        availability = null
    }

    /** Requests an immediate foreground reconcile-and-drain for newly committed durable work. */
    fun requestSync() {
        val selected = owner ?: return
        if (availability !is AppAvailability.Online) return
        if (job?.isActive == true) {
            rerunRequested = true
        } else {
            start(selected)
        }
    }

    private fun start(selected: Owner) {
        if (job?.isActive == true) return
        val runGeneration = ++generation
        job = scope.launch {
            do {
                rerunRequested = false
                val report = orchestrator.reconnect(selected.profile, selected.account)
                if (isCurrent(selected, runGeneration)) onRunCompleted()
                if (isCurrent(selected, runGeneration) && report.authenticationRequired) {
                    onAuthenticationRequired()
                    return@launch
                }
            } while (rerunRequested && isCurrent(selected, runGeneration))
        }
    }

    private fun cancelRun() {
        generation += 1
        rerunRequested = false
        job?.cancel()
        job = null
    }

    private fun isCurrent(selected: Owner, runGeneration: Long) =
        owner == selected && availability is AppAvailability.Online && generation == runGeneration

    private data class Owner(val profile: ConnectionProfile, val account: LocalReaderAccountKey)
}
