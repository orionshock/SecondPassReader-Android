package com.secondpasslibrary.reader.reader.session

import com.secondpasslibrary.reader.app.AppAvailability
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.reader.persistence.LocalReaderAccountKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Owns reconnect generation and stale-result safety for one live Reader Session. */
internal class ReaderSessionReconciliationController(
    private val reconciler: ReaderSessionReconciliation,
    private val scope: CoroutineScope,
    private val onResolved: (expectedLocalSessionId: String, ReaderSessionContext) -> Unit,
    private val onAuthenticationRejected: () -> Unit
) {
    private var owner: Owner? = null
    private var availability: AppAvailability? = null
    private var onlineGeneration = 0L
    private var attemptedGeneration = -1L
    private var job: Job? = null

    fun select(
        profile: ConnectionProfile,
        profileId: String,
        bookId: String,
        session: ReaderSessionContext,
        localOnly: Boolean
    ) {
        val next = Owner(
            profile,
            LocalReaderAccountKey.from(profile.serverOrigin, profileId),
            bookId,
            session,
            localOnly
        )
        if (owner != next) {
            job?.cancel()
            owner = next
            attemptedGeneration = -1L
        }
        startIfEligible()
    }

    fun setAvailability(next: AppAvailability) {
        if (next is AppAvailability.Online && availability !is AppAvailability.Online) {
            onlineGeneration += 1
        }
        availability = next
        startIfEligible()
    }

    fun requestAuthorityRefresh(localSessionId: String) {
        val selected = owner?.takeIf { it.session.sessionId == localSessionId } ?: return
        attemptedGeneration = -1L
        startIfEligible(force = selected)
    }

    fun clear() {
        job?.cancel()
        job = null
        owner = null
        attemptedGeneration = -1L
    }

    private fun startIfEligible(force: Owner? = null) {
        val selected = force ?: owner?.takeIf {
            it.localOnly && availability is AppAvailability.Online
        } ?: return
        val eligible = availability is AppAvailability.Online &&
            attemptedGeneration != onlineGeneration && job?.isActive != true
        if (!eligible) return
        attemptedGeneration = onlineGeneration
        job = scope.launch {
            val result = reconciler.reconcile(
                selected.profile,
                selected.account,
                selected.bookId,
                selected.session
            )
            if (owner != selected) return@launch
            when (result) {
                is ReaderSessionReconciliationResult.Resolved ->
                    onResolved(selected.session.sessionId, result.session)

                is ReaderSessionReconciliationResult.Failed -> {
                    result.refreshedSession?.let {
                        onResolved(selected.session.sessionId, it)
                    }
                    if (result.reason ==
                        ReaderSessionReconciliationFailure.AUTHENTICATION_REQUIRED
                    ) {
                        onAuthenticationRejected()
                    }
                }
            }
        }
    }

    private data class Owner(
        val profile: ConnectionProfile,
        val account: LocalReaderAccountKey,
        val bookId: String,
        val session: ReaderSessionContext,
        val localOnly: Boolean
    )
}
