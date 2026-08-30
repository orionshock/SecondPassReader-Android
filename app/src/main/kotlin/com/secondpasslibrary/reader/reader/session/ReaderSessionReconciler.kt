package com.secondpasslibrary.reader.reader.session

import com.secondpasslibrary.client.SplClientException
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotationsLoader
import com.secondpasslibrary.reader.reader.persistence.LocalReaderAccountKey
import com.secondpasslibrary.reader.reader.persistence.ReaderClosedSessionContinuationStore
import com.secondpasslibrary.reader.reader.persistence.ReaderSessionBindingStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

internal sealed interface ReaderSessionReconciliationResult {
    data class Resolved(
        val session: ReaderSessionContext,
        val forwardedEditCount: Int = 0,
        val droppedDeleteCount: Int = 0
    ) : ReaderSessionReconciliationResult

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
    private val bindingStore: ReaderSessionBindingStore,
    private val continuationStore: ReaderClosedSessionContinuationStore,
    private val annotationsLoader: ReaderAnnotationsLoader
) : ReaderSessionReconciliation {
    // All authority/transport failures become retryable app state.
    @Suppress("TooGenericExceptionCaught")
    override suspend fun reconcile(
        profile: ConnectionProfile,
        account: LocalReaderAccountKey,
        bookId: String,
        localSession: ReaderSessionContext
    ): ReaderSessionReconciliationResult = try {
        if (localSession.identityKind == ReaderSessionIdentityKind.PROVISIONAL ||
            localSession.serverSessionId == null
        ) {
            reconcileProvisional(profile, account, bookId, localSession)
        } else {
            reconcileConfirmed(profile, account, bookId, localSession)
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Exception) {
        failure.toReconciliationFailure()
    }

    private suspend fun reconcileProvisional(
        profile: ConnectionProfile,
        account: LocalReaderAccountKey,
        bookId: String,
        localSession: ReaderSessionContext
    ): ReaderSessionReconciliationResult {
        val authoritative = resolveWritable(profile, bookId)
        return ReaderSessionReconciliationResult.Resolved(
            bindingStore.bindProvisional(
                account,
                bookId,
                localSession.sessionId,
                authoritative
            )
        )
    }

    private suspend fun reconcileConfirmed(
        profile: ConnectionProfile,
        account: LocalReaderAccountKey,
        bookId: String,
        localSession: ReaderSessionContext
    ): ReaderSessionReconciliationResult {
        val exact = coordinator.resolve(
            profile,
            ReaderSessionRequest(bookId, localSession.serverSessionId)
        )
        if (exact.status == ReaderSessionStatus.ACTIVE) {
            return ReaderSessionReconciliationResult.Resolved(
                bindingStore.refreshConfirmed(
                    account,
                    bookId,
                    localSession.sessionId,
                    exact
                )
            )
        }
        return continueClosed(profile, account, bookId, localSession.sessionId, exact)
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun continueClosed(
        profile: ConnectionProfile,
        account: LocalReaderAccountKey,
        bookId: String,
        localSessionId: String,
        exact: ReaderSessionContext
    ): ReaderSessionReconciliationResult {
        val historical = exact.copy(sessionId = localSessionId)
        val annotations = annotationsLoader.load(profile, requireNotNull(exact.serverSessionId))
        val continuation = continuationStore.continueFrom(
            account,
            bookId,
            historical,
            annotations
        )
        val provisional = continuation.session ?: return ReaderSessionReconciliationResult.Resolved(
            historical,
            continuation.forwardedEditCount,
            continuation.droppedDeleteCount
        )
        return try {
            val writable = resolveWritable(profile, bookId)
            ReaderSessionReconciliationResult.Resolved(
                bindingStore.bindProvisional(account, bookId, provisional.sessionId, writable),
                continuation.forwardedEditCount,
                continuation.droppedDeleteCount
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            failure.toReconciliationFailure(historical)
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

private fun Exception.toReconciliationFailure(refreshedSession: ReaderSessionContext? = null) =
    ReaderSessionReconciliationResult.Failed(
        if (this is SplClientException.AuthenticationRejected) {
            ReaderSessionReconciliationFailure.AUTHENTICATION_REQUIRED
        } else {
            ReaderSessionReconciliationFailure.UNAVAILABLE
        },
        refreshedSession
    )
