package com.secondpasslibrary.reader.reader.persistence

import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotation
import com.secondpasslibrary.reader.reader.session.ReaderSessionContext
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

internal data class ReaderClosedSessionContinuation(
    val session: ReaderSessionContext?,
    val forwardedEditCount: Int,
    val droppedDeleteCount: Int
)

internal interface ReaderClosedSessionContinuationStore {
    suspend fun continueFrom(
        account: LocalReaderAccountKey,
        bookId: String,
        closedSession: ReaderSessionContext,
        authoritativeAnnotations: List<ReaderAnnotation>
    ): ReaderClosedSessionContinuation
}

internal class RoomReaderClosedSessionContinuationStore @Inject constructor(
    private val dao: LocalReaderDao
) : ReaderClosedSessionContinuationStore {
    override suspend fun continueFrom(
        account: LocalReaderAccountKey,
        bookId: String,
        closedSession: ReaderSessionContext,
        authoritativeAnnotations: List<ReaderAnnotation>
    ): ReaderClosedSessionContinuation {
        require(closedSession.serverSessionId != null)
        val now = Instant.now().toEpochMilli()
        val current = requireNotNull(dao.session(account.value, closedSession.sessionId))
        require(current.bookId == bookId)
        val candidate = LocalReaderSessionEntity(
            accountKey = account.value,
            localSessionId = UUID.randomUUID().toString(),
            bookId = bookId,
            serverSessionId = null,
            identityKind = "PROVISIONAL",
            serverStatus = null,
            activeProvisionalBookId = bookId,
            sessionName = null,
            sessionNotes = "",
            startedAt = null,
            closedAt = null,
            lastActivityAt = null,
            serverAnnotationCount = null,
            createdAtEpochMillis = now,
            lastUsedAtEpochMillis = now
        )
        val result = dao.continueClosedSession(
            current.copy(
                serverSessionId = closedSession.serverSessionId,
                identityKind = "SERVER_CONFIRMED",
                serverStatus = closedSession.status.name,
                activeProvisionalBookId = null,
                sessionName = closedSession.sessionName,
                sessionNotes = closedSession.sessionNotes,
                startedAt = closedSession.startedAt,
                closedAt = closedSession.closedAt,
                lastActivityAt = closedSession.lastActivityAt,
                serverAnnotationCount = authoritativeAnnotations.size,
                lastUsedAtEpochMillis = now
            ),
            closedSession.savedProgressCfi,
            authoritativeAnnotations.map { it.toEntity(account, closedSession.sessionId) },
            candidate,
            now
        )
        return ReaderClosedSessionContinuation(
            result.continuation?.let { entity ->
                entity.toContext(dao.progress(account.value, entity.localSessionId)?.cfi)
            },
            result.forwardedEditCount,
            result.droppedDeleteCount
        )
    }
}

internal data class ReaderClosedSessionContinuationResult(
    val continuation: LocalReaderSessionEntity?,
    val forwardedEditCount: Int,
    val droppedDeleteCount: Int
)

internal object ReaderContinuationIdentity {
    fun forwardedEdit(
        sourceLocalSessionId: String,
        continuationLocalSessionId: String,
        sourceClientId: String
    ): String = UUID.nameUUIDFromBytes(
        (
            "reader-continuation-edit\u0000$sourceLocalSessionId\u0000" +
                "$continuationLocalSessionId\u0000$sourceClientId"
            ).toByteArray(StandardCharsets.UTF_8)
    ).toString()
}
