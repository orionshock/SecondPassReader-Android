package com.secondpasslibrary.reader.connection

import com.secondpasslibrary.client.BearerCredential
import com.secondpasslibrary.reader.app.storage.AccountLocalScope
import com.secondpasslibrary.reader.connection.storage.PersistedAccountContextStore
import com.secondpasslibrary.reader.coroutines.runSuspendCatching
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

interface ConnectionProfileStore {
    suspend fun read(): ConnectionProfile?

    suspend fun write(profile: ConnectionProfile)

    suspend fun clear()
}

data class StoredCredential(
    val credential: BearerCredential,
    val recoveryProfile: ConnectionProfile?
)

interface BearerCredentialStore {
    suspend fun read(): StoredCredential?

    suspend fun write(credential: BearerCredential, recoveryProfile: ConnectionProfile)

    suspend fun markProfileCommitted()

    suspend fun clear()
}

class CredentialStorageException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

class ConnectionProfileStorageException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

internal class ConnectionPersistenceClearException(cause: Throwable) :
    Exception("Durable connection could not be cleared.", cause)

internal data class DurableConnection(
    val profile: ConnectionProfile,
    val credential: BearerCredential
)

internal sealed interface DurableConnectionRestore {
    data object None : DurableConnectionRestore

    data class Committed(val connection: DurableConnection) : DurableConnectionRestore

    data class RecoveryRequired(val profile: ConnectionProfile) : DurableConnectionRestore
}

internal sealed interface DurableConnectionCommit {
    data object Committed : DurableConnectionCommit

    data object SecureCredentialFailed : DurableConnectionCommit

    data class RecoveryRequired(val profile: ConnectionProfile) : DurableConnectionCommit
}

/**
 * Owns the atomic illusion over separate secure-credential and profile stores.
 *
 * Commit writes the credential with a recovery profile, writes the normal profile, then removes
 * the recovery profile from the credential envelope. Restore repairs an interrupted profile write
 * from that envelope. Clear always attempts profile, credential, and account-descriptor removal so
 * any surviving half-state can converge on a later retry.
 */
@Singleton
@Suppress("TooManyFunctions", "ReturnCount") // One durable transaction boundary.
internal class ConnectionPersistence @Inject constructor(
    private val profileStore: ConnectionProfileStore,
    private val credentialStore: BearerCredentialStore,
    private val accountContextStore: PersistedAccountContextStore,
    private val routesStore: KnownServerRoutesStore
) {
    suspend fun commit(
        profile: ConnectionProfile,
        credential: BearerCredential
    ): DurableConnectionCommit {
        try {
            credentialStore.write(credential, profile)
            currentCoroutineContext().ensureActive()
        } catch (_: CredentialStorageException) {
            currentCoroutineContext().ensureActive()
            return DurableConnectionCommit.SecureCredentialFailed
        }
        return if (writeProfileAndCommitMarker(profile)) {
            DurableConnectionCommit.Committed
        } else {
            DurableConnectionCommit.RecoveryRequired(profile)
        }
    }

    suspend fun restore(
        onProfileAvailable: suspend (ConnectionProfile, PersistedAccountContext?) -> Unit
    ): DurableConnectionRestore {
        var profile = profileStore.read()
        if (profile != null) {
            onProfileAvailable(profile, matchingAccount(profile))
        }
        val stored = credentialStore.read()
        if (profile == null && stored?.recoveryProfile != null) {
            profile = stored.recoveryProfile
            try {
                profileStore.write(profile)
                currentCoroutineContext().ensureActive()
            } catch (_: ConnectionProfileStorageException) {
                currentCoroutineContext().ensureActive()
                return DurableConnectionRestore.RecoveryRequired(profile)
            }
            onProfileAvailable(profile, matchingAccount(profile))
        }
        if (profile == null || stored == null) {
            if (profile != null || stored != null) clear()
            return DurableConnectionRestore.None
        }
        attempt { credentialStore.markProfileCommitted() }
        return DurableConnectionRestore.Committed(DurableConnection(profile, stored.credential))
    }

    suspend fun retryInterruptedCommit(profile: ConnectionProfile): DurableConnectionRestore {
        val stored = credentialStore.read() ?: return DurableConnectionRestore.None
        return if (writeProfileAndCommitMarker(profile)) {
            DurableConnectionRestore.Committed(DurableConnection(profile, stored.credential))
        } else {
            DurableConnectionRestore.RecoveryRequired(profile)
        }
    }

    suspend fun readCredential(): BearerCredential? = credentialStore.read()?.credential

    suspend fun requiresAccountIdentityReset(): Boolean =
        accountContextStore.requiresAccountIdentityReset()

    suspend fun markAccountIdentityReset() = accountContextStore.markAccountIdentityReset()

    suspend fun routesFor(profile: ConnectionProfile): KnownServerRoutes =
        routesStore.read(profile.serverId) ?: KnownServerRoutes.initial(profile)

    suspend fun saveRoutes(routes: KnownServerRoutes) = routesStore.write(routes)

    suspend fun readAccountContext(): PersistedAccountContext? = accountContextStore.read()

    suspend fun readLocalAccountScope(): AccountLocalScope? {
        val profile = profileStore.read() ?: return null
        return accountContextStore.read()
            ?.takeIf { it.matches(profile) }
            ?.let { AccountLocalScope.from(profile.serverId, it.profileId) }
    }

    suspend fun writeAccountContext(context: PersistedAccountContext) {
        accountContextStore.write(context)
    }

    suspend fun clearAccountContext() {
        accountContextStore.clear()
    }

    suspend fun clear() {
        val profileResult = attempt { profileStore.clear() }
        val credentialResult = attempt { credentialStore.clear() }
        val accountContextResult = attempt { accountContextStore.clear() }
        val routesResult = attempt { routesStore.clear() }
        listOf(credentialResult, profileResult, accountContextResult, routesResult)
            .firstNotNullOfOrNull { it.exceptionOrNull() }
            ?.let { throw ConnectionPersistenceClearException(it) }
    }

    private suspend fun writeProfileAndCommitMarker(profile: ConnectionProfile): Boolean {
        try {
            profileStore.write(profile)
            currentCoroutineContext().ensureActive()
        } catch (_: ConnectionProfileStorageException) {
            currentCoroutineContext().ensureActive()
            return false
        }
        attempt { credentialStore.markProfileCommitted() }
        return true
    }

    private suspend fun matchingAccount(profile: ConnectionProfile): PersistedAccountContext? =
        attempt { accountContextStore.read() }
            .getOrNull()
            ?.takeIf { it.matches(profile) }

    private suspend fun <T> attempt(block: suspend () -> T): Result<T> =
        runSuspendCatching { block() }.also { currentCoroutineContext().ensureActive() }
}
