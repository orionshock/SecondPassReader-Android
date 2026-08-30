package com.secondpasslibrary.reader.reader.session

import com.secondpasslibrary.client.AuthenticatedMarginaliaClient
import com.secondpasslibrary.client.ReadingSessionDetail
import com.secondpasslibrary.client.ReadingSessionStatus as SplReadingSessionStatus
import com.secondpasslibrary.client.SplClientException
import com.secondpasslibrary.reader.connection.AuthenticatedClientProvider
import com.secondpasslibrary.reader.connection.ConnectionProfile
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

internal data class ReaderSessionRequest(
    val bookId: String,
    val existingSessionId: String? = null
) {
    init {
        require(bookId.isNotBlank()) { "Reader Session Book ID must not be blank." }
        require(existingSessionId == null || existingSessionId.isNotBlank()) {
            "Existing Reading Session ID must not be blank."
        }
    }
}

internal data class ReaderSessionContext(
    val sessionId: String,
    val status: ReaderSessionStatus,
    val savedProgressCfi: String?,
    val progressFailure: ReaderProgressLoadFailure? = null,
    val sessionName: String? = null,
    val startedAt: String? = null,
    val closedAt: String? = null,
    val lastActivityAt: String? = null,
    val annotationCount: Int? = null,
    val sessionNotes: String = "",
    val serverSessionId: String? = sessionId,
    val identityKind: ReaderSessionIdentityKind = ReaderSessionIdentityKind.SERVER_CONFIRMED
)

internal enum class ReaderSessionIdentityKind {
    SERVER_CONFIRMED,
    PROVISIONAL
}

internal enum class ReaderSessionStatus {
    ACTIVE,
    CLOSED
}

internal enum class ReaderProgressLoadFailure {
    AUTHENTICATION_REQUIRED,
    UNAVAILABLE
}

internal fun interface ReaderSessionCoordinator {
    suspend fun resolve(
        profile: ConnectionProfile,
        request: ReaderSessionRequest
    ): ReaderSessionContext
}

internal class ReaderSessionIdentityMismatchException :
    Exception(
        "Reading Session does not belong to the requested Book."
    )

internal class ReaderSessionUnavailableException :
    Exception(
        "The server did not provide the required Reading Session."
    )

internal class SplReaderSessionCoordinator @Inject constructor(
    private val clientProvider: AuthenticatedClientProvider
) : ReaderSessionCoordinator {
    override suspend fun resolve(
        profile: ConnectionProfile,
        request: ReaderSessionRequest
    ): ReaderSessionContext {
        val marginalia = clientProvider.forProfile(profile).marginalia
        val session = request.existingSessionId?.let { sessionId ->
            val result = marginalia.sessions.get(sessionId)
            result.session.verifyBook(result.book.id, request.bookId)
        } ?: resolveActiveSession(marginalia, request.bookId)
        val progress = loadProgress(marginalia, session.summary.id)
        return ReaderSessionContext(
            sessionId = session.summary.id,
            serverSessionId = session.summary.id,
            identityKind = ReaderSessionIdentityKind.SERVER_CONFIRMED,
            status = session.summary.status.toReaderStatus(),
            savedProgressCfi = progress.cfi,
            progressFailure = progress.failure,
            sessionName = session.summary.name,
            sessionNotes = session.summary.notes,
            startedAt = session.summary.startedAt,
            closedAt = session.summary.closedAt,
            lastActivityAt = session.summary.lastActivityAt,
            annotationCount = session.summary.annotationCount
        )
    }

    private suspend fun resolveActiveSession(
        marginalia: AuthenticatedMarginaliaClient,
        bookId: String
    ): ReadingSessionDetail {
        val lookup = marginalia.books.getActiveSession(bookId)
        requireBookMatch(lookup.book.id, bookId)
        lookup.activeSession?.let { active ->
            return active.requireActive().verifyBook(lookup.book.id, bookId)
        }
        val opened = marginalia.books.openSession(bookId)
        requireBookMatch(opened.book.id, bookId)
        return opened.activeSession
            ?.requireActive()
            ?.verifyBook(opened.book.id, bookId)
            ?: throw ReaderSessionUnavailableException()
    }

    private suspend fun loadProgress(
        marginalia: AuthenticatedMarginaliaClient,
        sessionId: String
    ): ProgressResult = try {
        ProgressResult(marginalia.sessions.getProgress(sessionId)?.cfi)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: SplClientException.AuthenticationRejected) {
        ProgressResult(null, ReaderProgressLoadFailure.AUTHENTICATION_REQUIRED)
    } catch (_: Exception) {
        ProgressResult(null, ReaderProgressLoadFailure.UNAVAILABLE)
    }
}

private data class ProgressResult(val cfi: String?, val failure: ReaderProgressLoadFailure? = null)

private fun ReadingSessionDetail.verifyBook(
    actualBookId: String,
    expectedBookId: String
): ReadingSessionDetail {
    requireBookMatch(actualBookId, expectedBookId)
    return this
}

private fun requireBookMatch(actualBookId: String, expectedBookId: String) {
    if (actualBookId != expectedBookId) throw ReaderSessionIdentityMismatchException()
}

private fun ReadingSessionDetail.requireActive(): ReadingSessionDetail {
    if (summary.status != SplReadingSessionStatus.ACTIVE) {
        throw ReaderSessionUnavailableException()
    }
    return this
}

private fun SplReadingSessionStatus.toReaderStatus(): ReaderSessionStatus = when (this) {
    SplReadingSessionStatus.ACTIVE -> ReaderSessionStatus.ACTIVE
    SplReadingSessionStatus.CLOSED -> ReaderSessionStatus.CLOSED
}
