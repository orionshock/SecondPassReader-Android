package com.secondpasslibrary.reader.reader.persistence

import com.secondpasslibrary.reader.reader.LocalReaderAccountKey
import com.secondpasslibrary.reader.reader.ReaderPendingSyncScheduler
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotation
import com.secondpasslibrary.reader.reader.annotations.mutation.ReaderAnnotationMutationRequest
import com.secondpasslibrary.reader.reader.session.ReaderSessionContext
import com.secondpasslibrary.reader.reader.session.ReaderSessionIdentityKind
import com.secondpasslibrary.reader.reader.session.ReaderSessionStatus
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

internal enum class LocalReaderWriteProvenance { SERVER_CONFIRMED, LOCAL_PENDING }

internal data class LocalReaderBookSummary(val sessionCount: Int, val pendingChangeCount: Int)

internal interface LocalReaderStateStore {
    suspend fun selectOfflineSession(
        account: LocalReaderAccountKey,
        bookId: String
    ): ReaderSessionContext

    suspend fun retainServerSession(
        account: LocalReaderAccountKey,
        bookId: String,
        session: ReaderSessionContext
    ): ReaderSessionContext

    suspend fun writeProgress(
        account: LocalReaderAccountKey,
        localSessionId: String,
        cfi: String,
        provenance: LocalReaderWriteProvenance,
        locationLabel: String? = null
    )

    suspend fun acknowledgeProgress(
        account: LocalReaderAccountKey,
        localSessionId: String,
        cfi: String
    )

    suspend fun readAnnotations(
        account: LocalReaderAccountKey,
        localSessionId: String
    ): List<ReaderAnnotation>

    suspend fun applyAnnotationMutation(
        account: LocalReaderAccountKey,
        localSessionId: String,
        request: ReaderAnnotationMutationRequest
    ): List<ReaderAnnotation>

    suspend fun replaceAuthoritativeAnnotations(
        account: LocalReaderAccountKey,
        localSessionId: String,
        annotations: List<ReaderAnnotation>,
        acknowledgedMutation: ReaderAnnotationMutationRequest? = null
    )

    suspend fun purgeAccount(account: LocalReaderAccountKey)

    suspend fun bookSummary(
        account: LocalReaderAccountKey,
        bookId: String
    ): LocalReaderBookSummary = error("Book summary is not implemented by this Reader store.")

    suspend fun bookSessionIds(account: LocalReaderAccountKey, bookId: String): List<String> =
        error("Book sessions are not implemented by this Reader store.")

    suspend fun purgeBook(account: LocalReaderAccountKey, bookId: String): Unit =
        error("Book cleanup is not implemented by this Reader store.")
}

@Singleton
@Suppress("TooManyFunctions") // One Room owner keeps Reader state and Book-scoped cleanup together.
internal class RoomLocalReaderStateStore @Inject constructor(
    private val dao: LocalReaderDao,
    private val syncScheduler: ReaderPendingSyncScheduler,
    private val sessionBindingStore: ReaderSessionBindingStore
) : LocalReaderStateStore {
    constructor(dao: LocalReaderDao) : this(
        dao,
        NoOpReaderPendingSyncScheduler,
        RoomReaderSessionBindingStore(dao)
    )

    constructor(dao: LocalReaderDao, syncScheduler: ReaderPendingSyncScheduler) : this(
        dao,
        syncScheduler,
        RoomReaderSessionBindingStore(dao)
    )

    override suspend fun selectOfflineSession(
        account: LocalReaderAccountKey,
        bookId: String
    ): ReaderSessionContext {
        val now = Instant.now().toEpochMilli()
        val selected = dao.activeServerSession(account.value, bookId)
            ?: dao.activeProvisionalSession(account.value, bookId)
            ?: createProvisional(account, bookId, now)
        if (selected.identityKind == ReaderSessionIdentityKind.PROVISIONAL.name) {
            dao.upsertOutbox(selected.toEstablishmentOutbox(now))
            schedule(account)
        }
        dao.touchSession(account.value, selected.localSessionId, now)
        return selected.toContext(dao.progress(account.value, selected.localSessionId)?.cfi)
    }

    override suspend fun retainServerSession(
        account: LocalReaderAccountKey,
        bookId: String,
        session: ReaderSessionContext
    ): ReaderSessionContext {
        require(session.identityKind == ReaderSessionIdentityKind.SERVER_CONFIRMED)
        val serverSessionId = requireNotNull(session.serverSessionId)
        val retained = dao.sessionByServerId(account.value, serverSessionId)
        return if (retained != null) {
            require(retained.bookId == bookId) {
                "Server Reader Session belongs to another Book."
            }
            sessionBindingStore.refreshConfirmed(
                account,
                bookId,
                retained.localSessionId,
                session
            )
        } else {
            retainNewServerSession(account, bookId, session)
        }
    }

    private suspend fun retainNewServerSession(
        account: LocalReaderAccountKey,
        bookId: String,
        session: ReaderSessionContext
    ): ReaderSessionContext {
        val now = Instant.now().toEpochMilli()
        val existingProgress = dao.progress(account.value, session.sessionId)
        dao.upsertSession(session.toServerEntity(account, bookId, now))
        if (existingProgress?.provenance == LocalReaderWriteProvenance.LOCAL_PENDING.name) {
            return session.copy(savedProgressCfi = existingProgress.cfi)
        }
        val serverCfi = session.savedProgressCfi
        if (serverCfi == null) {
            dao.deleteProgress(account.value, session.sessionId)
        } else {
            writeProgress(
                account,
                session.sessionId,
                serverCfi,
                LocalReaderWriteProvenance.SERVER_CONFIRMED
            )
        }
        return session
    }

    override suspend fun writeProgress(
        account: LocalReaderAccountKey,
        localSessionId: String,
        cfi: String,
        provenance: LocalReaderWriteProvenance,
        locationLabel: String?
    ) {
        val session = dao.session(account.value, localSessionId) ?: return
        if (session.serverStatus == ReaderSessionStatus.CLOSED.name &&
            provenance != LocalReaderWriteProvenance.SERVER_CONFIRMED
        ) {
            return
        }
        val now = Instant.now().toEpochMilli()
        val progress = LocalReaderProgressEntity(
            account.value,
            localSessionId,
            cfi,
            now,
            provenance.name
        )
        dao.writeProgress(
            progress,
            if (provenance == LocalReaderWriteProvenance.LOCAL_PENDING) {
                progress.toOutbox(session.bookId, locationLabel)
            } else {
                null
            }
        )
        if (provenance == LocalReaderWriteProvenance.LOCAL_PENDING) schedule(account)
    }

    override suspend fun acknowledgeProgress(
        account: LocalReaderAccountKey,
        localSessionId: String,
        cfi: String
    ) {
        val locationLabel = dao.pendingReaderIntents(account.value, localSessionId)
            .singleOrNull { it.operationKind == ReaderOutboxOperation.PROGRESS }
            ?.locationLabel
        dao.acknowledgeProgress(
            account.value,
            localSessionId,
            cfi,
            locationLabel,
            Instant.now().toEpochMilli()
        )
    }

    override suspend fun readAnnotations(
        account: LocalReaderAccountKey,
        localSessionId: String
    ): List<ReaderAnnotation> = dao.visibleAnnotations(account.value, localSessionId).map {
        it.toReaderAnnotation()
    }

    override suspend fun applyAnnotationMutation(
        account: LocalReaderAccountKey,
        localSessionId: String,
        request: ReaderAnnotationMutationRequest
    ): List<ReaderAnnotation> {
        val session = dao.session(account.value, localSessionId)
        if (session == null || session.serverStatus == ReaderSessionStatus.CLOSED.name) {
            return readAnnotations(account, localSessionId)
        }
        val now = Instant.now().toEpochMilli()
        when (request) {
            is ReaderAnnotationMutationRequest.UpsertHighlight -> {
                val annotation = request.toEntity(account, localSessionId)
                dao.writeAnnotationUpsert(
                    annotation,
                    annotation.toUpsertOutbox(session.bookId, now)
                )
            }

            is ReaderAnnotationMutationRequest.UpsertBookmark -> {
                val annotation = request.toEntity(account, localSessionId)
                dao.writeAnnotationUpsert(
                    annotation,
                    annotation.toUpsertOutbox(session.bookId, now)
                )
            }

            is ReaderAnnotationMutationRequest.Delete -> {
                dao.writeAnnotationDelete(
                    account.value,
                    localSessionId,
                    request.clientId,
                    request.localSnapshot?.toEntity(account, localSessionId),
                    annotationDeleteOutbox(
                        account,
                        session.bookId,
                        localSessionId,
                        request.clientId,
                        now
                    )
                )
            }
        }
        schedule(account)
        return readAnnotations(account, localSessionId)
    }

    override suspend fun replaceAuthoritativeAnnotations(
        account: LocalReaderAccountKey,
        localSessionId: String,
        annotations: List<ReaderAnnotation>,
        acknowledgedMutation: ReaderAnnotationMutationRequest?
    ) {
        val session = dao.session(account.value, localSessionId) ?: return
        dao.mergeAuthoritativeAnnotations(
            account.value,
            localSessionId,
            acknowledgedMutation?.let {
                listOf(it.toOutboxIntent(session.bookId, localSessionId))
            }.orEmpty(),
            annotations.map { it.toEntity(account, localSessionId) }
        )
    }

    override suspend fun purgeAccount(account: LocalReaderAccountKey) {
        dao.purgeAccount(account.value)
    }

    override suspend fun bookSummary(account: LocalReaderAccountKey, bookId: String) =
        LocalReaderBookSummary(
            dao.sessionCountForBook(account.value, bookId),
            dao.pendingCountForBook(account.value, bookId)
        )

    override suspend fun bookSessionIds(
        account: LocalReaderAccountKey,
        bookId: String
    ): List<String> = dao.sessionIdsForBook(account.value, bookId)

    override suspend fun purgeBook(account: LocalReaderAccountKey, bookId: String) {
        dao.purgeBook(account.value, bookId)
    }

    private suspend fun schedule(account: LocalReaderAccountKey) {
        try {
            syncScheduler.ensureEnqueued(account)
        } catch (cancellation: kotlinx.coroutines.CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            // Local state remains durable; foreground reconnect can schedule another wakeup.
        }
    }

    private suspend fun createProvisional(
        account: LocalReaderAccountKey,
        bookId: String,
        now: Long
    ): LocalReaderSessionEntity {
        val candidate = LocalReaderSessionEntity(
            accountKey = account.value,
            localSessionId = UUID.randomUUID().toString(),
            bookId = bookId,
            serverSessionId = null,
            identityKind = ReaderSessionIdentityKind.PROVISIONAL.name,
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
        return dao.ensureProvisionalSession(candidate, candidate.toEstablishmentOutbox(now))
    }
}

private fun ReaderAnnotationMutationRequest.toOutboxIntent(
    bookId: String,
    localSessionId: String
): ReaderOutboxIntent.Annotation = when (this) {
    is ReaderAnnotationMutationRequest.UpsertHighlight -> ReaderOutboxIntent.Annotation(
        ReaderOutboxIdentity.annotation(localSessionId, clientId),
        bookId,
        localSessionId,
        copy(sessionId = localSessionId)
    )

    is ReaderAnnotationMutationRequest.UpsertBookmark -> ReaderOutboxIntent.Annotation(
        ReaderOutboxIdentity.annotation(localSessionId, clientId),
        bookId,
        localSessionId,
        copy(sessionId = localSessionId)
    )

    is ReaderAnnotationMutationRequest.Delete -> ReaderOutboxIntent.Annotation(
        ReaderOutboxIdentity.annotation(localSessionId, clientId),
        bookId,
        localSessionId,
        copy(sessionId = localSessionId, localSnapshot = null)
    )
}

private data object NoOpReaderPendingSyncScheduler : ReaderPendingSyncScheduler {
    override suspend fun ensureEnqueued(account: LocalReaderAccountKey) = Unit

    override fun cancel(account: LocalReaderAccountKey) = Unit
}
