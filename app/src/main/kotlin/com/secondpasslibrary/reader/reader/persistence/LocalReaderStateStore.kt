package com.secondpasslibrary.reader.reader.persistence

import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotation
import com.secondpasslibrary.reader.reader.annotations.mutation.ReaderAnnotationMutationRequest
import com.secondpasslibrary.reader.reader.session.ReaderSessionContext
import com.secondpasslibrary.reader.reader.session.ReaderSessionIdentityKind
import com.secondpasslibrary.reader.reader.session.ReaderSessionStatus
import com.secondpasslibrary.reader.reader.sync.ReaderSyncScheduler
import java.security.MessageDigest
import java.time.Instant
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@JvmInline
internal value class LocalReaderAccountKey private constructor(val value: String) {
    companion object {
        internal fun fromPersistedValue(value: String): LocalReaderAccountKey {
            require(value.length == ACCOUNT_KEY_HEX_LENGTH && value.all { it in HEX_DIGITS }) {
                "Invalid persisted Reader account scope."
            }
            return LocalReaderAccountKey(value)
        }

        fun from(serverOrigin: String, profileId: String): LocalReaderAccountKey {
            val origin = serverOrigin.trim().trimEnd('/').lowercase(Locale.ROOT)
            val account = profileId.trim()
            require(origin.isNotEmpty()) { "Server origin is required for Reader cache scope." }
            require(account.isNotEmpty()) { "Profile ID is required for Reader cache scope." }
            val digest = MessageDigest.getInstance("SHA-256")
                .digest("$origin\u0000$account".toByteArray(Charsets.UTF_8))
                .joinToString("") { byte -> "%02x".format(byte) }
            return LocalReaderAccountKey(digest)
        }

        private const val ACCOUNT_KEY_HEX_LENGTH = 64
        private const val HEX_DIGITS = "0123456789abcdef"
    }
}

internal enum class LocalReaderWriteProvenance { SERVER_CONFIRMED, LOCAL_PENDING }

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
        provenance: LocalReaderWriteProvenance
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
}

@Singleton
internal class RoomLocalReaderStateStore @Inject constructor(
    private val dao: LocalReaderDao,
    private val syncScheduler: ReaderSyncScheduler,
    private val sessionBindingStore: ReaderSessionBindingStore
) : LocalReaderStateStore {
    constructor(dao: LocalReaderDao) : this(
        dao,
        NoOpReaderSyncScheduler,
        RoomReaderSessionBindingStore(dao)
    )

    constructor(dao: LocalReaderDao, syncScheduler: ReaderSyncScheduler) : this(
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
        provenance: LocalReaderWriteProvenance
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
                progress.toOutbox(session.bookId)
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
        dao.acknowledgeProgress(
            account.value,
            localSessionId,
            cfi,
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
            annotationCount = null,
            createdAtEpochMillis = now,
            lastUsedAtEpochMillis = now
        )
        return dao.ensureProvisionalSession(candidate, candidate.toEstablishmentOutbox(now))
    }
}

private fun ReaderAnnotationMutationRequest.toOutboxIntent(
    bookId: String,
    localSessionId: String
): ReaderOutboxIntent = when (this) {
    is ReaderAnnotationMutationRequest.UpsertHighlight -> ReaderOutboxIntent.AnnotationUpsert(
        ReaderOutboxIdentity.annotation(localSessionId, clientId),
        bookId,
        localSessionId,
        clientId,
        LocalAnnotationKind.HIGHLIGHT,
        cfi,
        locationLabel,
        text,
        prefix,
        suffix,
        note,
        color
    )

    is ReaderAnnotationMutationRequest.UpsertBookmark -> ReaderOutboxIntent.AnnotationUpsert(
        ReaderOutboxIdentity.annotation(localSessionId, clientId),
        bookId,
        localSessionId,
        clientId,
        LocalAnnotationKind.BOOKMARK,
        cfi,
        locationLabel,
        null,
        null,
        null,
        null,
        null
    )

    is ReaderAnnotationMutationRequest.Delete -> ReaderOutboxIntent.AnnotationDelete(
        ReaderOutboxIdentity.annotation(localSessionId, clientId),
        bookId,
        localSessionId,
        clientId
    )
}

private data object NoOpReaderSyncScheduler : ReaderSyncScheduler {
    override suspend fun ensureEnqueued(account: LocalReaderAccountKey) = Unit

    override fun cancel(account: LocalReaderAccountKey) = Unit
}
