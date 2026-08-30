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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Account-scoped foreground reconnect flow: server authority first, durable intent second. */
internal class ReaderReconnectOrchestrator(
    private val reconciliation: ReaderSessionReconciliation,
    private val outbox: ReaderOutboxStore,
    private val synchronizer: ReaderOutboxSynchronizer,
    private val scope: CoroutineScope,
    private val onAuthenticationRequired: () -> Unit
) {
    private var owner: Owner? = null
    private var availability: AppAvailability? = null
    private var runGeneration = 0L
    private var job: Job? = null

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
        if (accountChanged || nextAvailability !is AppAvailability.Online) {
            cancelRun()
        }
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

    private fun start(selected: Owner) {
        if (job?.isActive == true) return
        val generation = ++runGeneration
        job = scope.launch { reconnect(selected, generation) }
    }

    private suspend fun reconnect(selected: Owner, generation: Long) {
        if (outbox.hasPendingWork(selected.account)) {
            val pending = outbox.pendingSessions(selected.account).iterator()
            var keepRunning = true
            while (keepRunning && pending.hasNext()) {
                keepRunning = isCurrent(selected, generation) &&
                    reconcilePending(selected, generation, pending.next())
            }
        }
    }

    private suspend fun reconcilePending(
        selected: Owner,
        generation: Long,
        pending: ReaderPendingOutboxSession
    ): Boolean {
        val result = reconciliation.reconcile(
            selected.profile,
            selected.account,
            pending.bookId,
            pending.session
        )
        return when (result) {
            is ReaderSessionReconciliationResult.Failed ->
                handleReconciliationFailure(selected, generation, result)

            is ReaderSessionReconciliationResult.Resolved ->
                drainResolved(selected, generation, pending.session.sessionId, result.session)
        }
    }

    private fun handleReconciliationFailure(
        selected: Owner,
        generation: Long,
        result: ReaderSessionReconciliationResult.Failed
    ): Boolean {
        if (result.reason != ReaderSessionReconciliationFailure.AUTHENTICATION_REQUIRED) {
            return true
        }
        if (isCurrent(selected, generation)) onAuthenticationRequired()
        return false
    }

    private suspend fun drainResolved(
        selected: Owner,
        generation: Long,
        pendingLocalSessionId: String,
        resolved: ReaderSessionContext
    ): Boolean {
        val eligible = resolved.sessionId == pendingLocalSessionId &&
            resolved.status == ReaderSessionStatus.ACTIVE &&
            resolved.serverSessionId != null
        if (!eligible) return true
        val report = synchronizer.syncBoundSession(
            selected.profile,
            selected.account,
            resolved.sessionId
        )
        val current = isCurrent(selected, generation)
        if (current && report.authenticationRequired) onAuthenticationRequired()
        return current && !report.authenticationRequired
    }

    private fun cancelRun() {
        runGeneration += 1
        job?.cancel()
        job = null
    }

    private fun isCurrent(selected: Owner, generation: Long) =
        owner == selected && availability is AppAvailability.Online && runGeneration == generation

    private data class Owner(val profile: ConnectionProfile, val account: LocalReaderAccountKey)
}
