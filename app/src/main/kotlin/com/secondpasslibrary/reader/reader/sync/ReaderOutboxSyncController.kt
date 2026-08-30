package com.secondpasslibrary.reader.reader.sync

import com.secondpasslibrary.reader.app.AppAvailability
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.reader.persistence.LocalReaderAccountKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Event-driven foreground owner; durable retry state remains in Room. */
internal class ReaderOutboxSyncController(
    private val synchronizer: ReaderOutboxSynchronizer,
    private val scope: CoroutineScope,
    private val onAuthenticationRequired: () -> Unit,
    private val onReconciliationRequired: (String) -> Unit
) {
    private var owner: Owner? = null
    private var availability: AppAvailability? = null
    private var onlineGeneration = 0L
    private var attemptedGeneration = -1L
    private var job: Job? = null

    fun select(
        profile: ConnectionProfile,
        profileId: String,
        localSessionId: String,
        bindingIdentity: String?
    ) {
        val next = Owner(
            profile,
            LocalReaderAccountKey.from(profile.serverOrigin, profileId),
            localSessionId,
            bindingIdentity
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

    fun clear() {
        job?.cancel()
        job = null
        owner = null
        attemptedGeneration = -1L
    }

    private fun startIfEligible() {
        val selected = owner?.takeIf { availability is AppAvailability.Online } ?: return
        if (attemptedGeneration == onlineGeneration || job?.isActive == true) return
        attemptedGeneration = onlineGeneration
        job = scope.launch {
            val report = synchronizer.syncBoundSession(
                selected.profile,
                selected.account,
                selected.localSessionId
            )
            if (owner != selected) return@launch
            if (report.authenticationRequired) onAuthenticationRequired()
            report.reconciliationSessionIds.forEach(onReconciliationRequired)
        }
    }

    private data class Owner(
        val profile: ConnectionProfile,
        val account: LocalReaderAccountKey,
        val localSessionId: String,
        val bindingIdentity: String?
    )
}
