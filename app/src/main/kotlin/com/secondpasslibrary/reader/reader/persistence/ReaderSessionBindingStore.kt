package com.secondpasslibrary.reader.reader.persistence

import com.secondpasslibrary.reader.reader.session.ReaderSessionContext
import com.secondpasslibrary.reader.reader.session.ReaderSessionIdentityKind
import java.time.Instant
import javax.inject.Inject

internal interface ReaderSessionBindingStore {
    suspend fun bindProvisional(
        account: LocalReaderAccountKey,
        bookId: String,
        localSessionId: String,
        authoritative: ReaderSessionContext
    ): ReaderSessionContext

    suspend fun refreshConfirmed(
        account: LocalReaderAccountKey,
        bookId: String,
        localSessionId: String,
        authoritative: ReaderSessionContext
    ): ReaderSessionContext
}

internal class RoomReaderSessionBindingStore @Inject constructor(private val dao: LocalReaderDao) :
    ReaderSessionBindingStore {
    override suspend fun bindProvisional(
        account: LocalReaderAccountKey,
        bookId: String,
        localSessionId: String,
        authoritative: ReaderSessionContext
    ): ReaderSessionContext = bind(
        account,
        bookId,
        localSessionId,
        authoritative,
        requireProvisional = true,
        acknowledgeEstablishment = true
    )

    override suspend fun refreshConfirmed(
        account: LocalReaderAccountKey,
        bookId: String,
        localSessionId: String,
        authoritative: ReaderSessionContext
    ): ReaderSessionContext = bind(
        account,
        bookId,
        localSessionId,
        authoritative,
        requireProvisional = false,
        acknowledgeEstablishment = false
    )

    private suspend fun bind(
        account: LocalReaderAccountKey,
        bookId: String,
        localSessionId: String,
        authoritative: ReaderSessionContext,
        requireProvisional: Boolean,
        acknowledgeEstablishment: Boolean
    ): ReaderSessionContext {
        require(authoritative.identityKind == ReaderSessionIdentityKind.SERVER_CONFIRMED)
        val current = requireNotNull(dao.session(account.value, localSessionId))
        require(current.bookId == bookId) { "Local Reader Session belongs to another Book." }
        if (requireProvisional) {
            val mayBind = current.identityKind == ReaderSessionIdentityKind.PROVISIONAL.name ||
                current.serverSessionId == authoritative.serverSessionId
            require(mayBind) { "Only a provisional Reader Session can establish a binding." }
        }
        val now = Instant.now().toEpochMilli()
        dao.bindAuthoritativeSession(
            current.withServerTruth(authoritative, now),
            authoritative.savedProgressCfi,
            acknowledgeEstablishment,
            now
        )
        val savedCfi = dao.progress(account.value, localSessionId)?.cfi
        return requireNotNull(dao.session(account.value, localSessionId)).toContext(savedCfi)
    }
}

private fun LocalReaderSessionEntity.withServerTruth(
    authoritative: ReaderSessionContext,
    now: Long
) = copy(
    serverSessionId = requireNotNull(authoritative.serverSessionId),
    identityKind = ReaderSessionIdentityKind.SERVER_CONFIRMED.name,
    serverStatus = authoritative.status.name,
    activeProvisionalBookId = null,
    sessionName = authoritative.sessionName,
    sessionNotes = authoritative.sessionNotes,
    startedAt = authoritative.startedAt,
    closedAt = authoritative.closedAt,
    lastActivityAt = authoritative.lastActivityAt,
    serverAnnotationCount = authoritative.annotationCount,
    lastUsedAtEpochMillis = now
)
