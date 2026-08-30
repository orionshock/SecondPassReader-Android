package com.secondpasslibrary.reader.reader.session

import com.secondpasslibrary.client.SplClientException
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.reader.persistence.LocalReaderAccountKey
import com.secondpasslibrary.reader.reader.persistence.LocalReaderStateStore
import com.secondpasslibrary.reader.reader.persistence.ReaderSessionBindingStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

internal sealed interface ReaderSessionReconciliationResult {
    data class Resolved(val session: ReaderSessionContext) : ReaderSessionReconciliationResult

    data class Failed(
        val reason: ReaderSessionReconciliationFailure,
        val refreshedSession: ReaderSessionContext? = null
    ) : ReaderSessionReconciliationResult
}

internal enum class ReaderSessionReconciliationFailure {
    AUTHENTICATION_REQUIRED,
    UNAVAILABLE
}

internal fun interface ReaderSessionReconciliation {
    suspend fun reconcile(
        profile: ConnectionProfile,
        account: LocalReaderAccountKey,
        bookId: String,
        localSession: ReaderSessionContext
    ): ReaderSessionReconciliationResult
}

/** Reconciles cached Reader Session identity with current server authority without delivering work. */
@Singleton
internal class ReaderSessionReconciler @Inject constructor(
    private val coordinator: ReaderSessionCoordinator,
    private val localStore: LocalReaderStateStore,
    private val bindingStore: ReaderSessionBindingStore
) : ReaderSessionReconciliation {
    // All authority/transport failures become retryable app state.
    @Suppress("TooGenericExceptionCaught")
    override suspend fun reconcile(
        profile: ConnectionProfile,
        account: LocalReaderAccountKey,
        bookId: String,
        localSession: ReaderSessionContext
    ): ReaderSessionReconciliationResult {
        var refreshedSession: ReaderSessionContext? = null
        return try {
            if (localSession.identityKind == ReaderSessionIdentityKind.PROVISIONAL ||
                localSession.serverSessionId == null
            ) {
                val authoritative = resolveWritable(profile, bookId)
                ReaderSessionReconciliationResult.Resolved(
                    bindingStore.bindProvisional(
                        account,
                        bookId,
                        localSession.sessionId,
                        authoritative
                    )
                )
            } else {
                val exact = coordinator.resolve(
                    profile,
                    ReaderSessionRequest(bookId, localSession.serverSessionId)
                )
                refreshedSession = bindingStore.refreshConfirmed(
                    account,
                    bookId,
                    localSession.sessionId,
                    exact
                )
                if (exact.status == ReaderSessionStatus.ACTIVE) {
                    ReaderSessionReconciliationResult.Resolved(refreshedSession)
                } else {
                    val writable = resolveWritable(profile, bookId)
                    ReaderSessionReconciliationResult.Resolved(
                        localStore.retainServerSession(account, bookId, writable)
                    )
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            ReaderSessionReconciliationResult.Failed(
                if (failure is SplClientException.AuthenticationRejected) {
                    ReaderSessionReconciliationFailure.AUTHENTICATION_REQUIRED
                } else {
                    ReaderSessionReconciliationFailure.UNAVAILABLE
                },
                refreshedSession
            )
        }
    }

    private suspend fun resolveWritable(
        profile: ConnectionProfile,
        bookId: String
    ): ReaderSessionContext {
        var unavailable: ReaderSessionUnavailableException? = null
        repeat(MAX_WRITABLE_RESOLUTION_ATTEMPTS) {
            try {
                return coordinator.resolve(profile, ReaderSessionRequest(bookId))
            } catch (failure: ReaderSessionUnavailableException) {
                unavailable = failure
            }
        }
        throw requireNotNull(unavailable)
    }

    private companion object {
        const val MAX_WRITABLE_RESOLUTION_ATTEMPTS = 2
    }
}
