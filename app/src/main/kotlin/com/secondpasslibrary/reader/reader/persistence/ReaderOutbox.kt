package com.secondpasslibrary.reader.reader.persistence

import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotationColor

internal interface ReaderOutboxStore {
    suspend fun pendingSessions(account: LocalReaderAccountKey): List<ReaderPendingOutboxSession>

    suspend fun boundPendingSessions(account: LocalReaderAccountKey): List<ReaderBoundOutboxSession>

    suspend fun pendingSessionEstablishments(
        account: LocalReaderAccountKey
    ): List<ReaderOutboxIntent>

    suspend fun pendingReaderIntents(
        account: LocalReaderAccountKey,
        localSessionId: String
    ): List<ReaderOutboxIntent>

    suspend fun hasPendingWork(account: LocalReaderAccountKey): Boolean

    suspend fun acknowledgeIntent(account: LocalReaderAccountKey, outboxId: String)

    suspend fun acceptProgress(
        account: LocalReaderAccountKey,
        localSessionId: String,
        sent: ReaderOutboxIntent.Progress
    )

    suspend fun acceptAnnotationBatch(
        account: LocalReaderAccountKey,
        localSessionId: String,
        sent: List<ReaderOutboxIntent>,
        authoritative: List<com.secondpasslibrary.reader.reader.annotations.ReaderAnnotation>
    )
}

internal class RoomReaderOutboxStore @javax.inject.Inject constructor(
    private val dao: LocalReaderDao
) : ReaderOutboxStore {
    override suspend fun pendingSessions(
        account: LocalReaderAccountKey
    ): List<ReaderPendingOutboxSession> = dao.pendingOutboxSessions(account.value).map { session ->
        ReaderPendingOutboxSession(
            bookId = session.bookId,
            session = session.toContext(
                dao.progress(account.value, session.localSessionId)?.cfi
            )
        )
    }

    override suspend fun boundPendingSessions(
        account: LocalReaderAccountKey
    ): List<ReaderBoundOutboxSession> = dao.boundPendingSessions(account.value).map {
        ReaderBoundOutboxSession(
            it.localSessionId,
            requireNotNull(it.serverSessionId),
            it.bookId
        )
    }

    override suspend fun pendingSessionEstablishments(
        account: LocalReaderAccountKey
    ): List<ReaderOutboxIntent> = dao.pendingSessionEstablishments(account.value).map {
        it.toIntent()
    }

    override suspend fun pendingReaderIntents(
        account: LocalReaderAccountKey,
        localSessionId: String
    ): List<ReaderOutboxIntent> = dao.pendingReaderIntents(account.value, localSessionId).map {
        it.toIntent()
    }

    override suspend fun hasPendingWork(account: LocalReaderAccountKey): Boolean =
        dao.hasPendingWork(account.value)

    override suspend fun acknowledgeIntent(account: LocalReaderAccountKey, outboxId: String) {
        dao.deleteOutbox(account.value, outboxId)
    }

    override suspend fun acceptProgress(
        account: LocalReaderAccountKey,
        localSessionId: String,
        sent: ReaderOutboxIntent.Progress
    ) {
        dao.acknowledgeProgress(
            account.value,
            localSessionId,
            sent.cfi,
            java.time.Instant.now().toEpochMilli()
        )
    }

    override suspend fun acceptAnnotationBatch(
        account: LocalReaderAccountKey,
        localSessionId: String,
        sent: List<ReaderOutboxIntent>,
        authoritative: List<com.secondpasslibrary.reader.reader.annotations.ReaderAnnotation>
    ) {
        dao.acceptAnnotationDelivery(
            account.value,
            localSessionId,
            sent,
            authoritative.map { it.toEntity(account, localSessionId) }
        )
    }
}

internal data class ReaderBoundOutboxSession(
    val localSessionId: String,
    val serverSessionId: String,
    val bookId: String
)

internal data class ReaderPendingOutboxSession(
    val bookId: String,
    val session: com.secondpasslibrary.reader.reader.session.ReaderSessionContext
)

internal sealed interface ReaderOutboxIntent {
    val id: String
    val bookId: String
    val localSessionId: String

    data class EstablishSession(
        override val id: String,
        override val bookId: String,
        override val localSessionId: String
    ) : ReaderOutboxIntent

    data class Progress(
        override val id: String,
        override val bookId: String,
        override val localSessionId: String,
        val cfi: String
    ) : ReaderOutboxIntent

    data class AnnotationUpsert(
        override val id: String,
        override val bookId: String,
        override val localSessionId: String,
        val clientId: String,
        val kind: String,
        val cfi: String,
        val locationLabel: String?,
        val quote: String?,
        val prefix: String?,
        val suffix: String?,
        val note: String?,
        val color: ReaderAnnotationColor?
    ) : ReaderOutboxIntent

    data class AnnotationDelete(
        override val id: String,
        override val bookId: String,
        override val localSessionId: String,
        val clientId: String
    ) : ReaderOutboxIntent
}

internal object ReaderOutboxOperation {
    const val SESSION_ESTABLISHMENT = "SESSION_ESTABLISHMENT"
    const val PROGRESS = "PROGRESS"
    const val ANNOTATION_UPSERT = "ANNOTATION_UPSERT"
    const val ANNOTATION_DELETE = "ANNOTATION_DELETE"
}

internal object ReaderOutboxIdentity {
    fun session(localSessionId: String) = "session:$localSessionId"

    fun progress(localSessionId: String) = "progress:$localSessionId"

    fun annotation(localSessionId: String, clientId: String) =
        "annotation:$localSessionId:$clientId"
}

internal const val SESSION_ESTABLISHMENT_DELIVERY_ORDER = 0
internal const val READER_MUTATION_DELIVERY_ORDER = 1

internal fun LocalReaderOutboxEntity.toIntent(): ReaderOutboxIntent = when (operationKind) {
    ReaderOutboxOperation.SESSION_ESTABLISHMENT -> ReaderOutboxIntent.EstablishSession(
        outboxId,
        bookId,
        localSessionId
    )

    ReaderOutboxOperation.PROGRESS -> ReaderOutboxIntent.Progress(
        outboxId,
        bookId,
        localSessionId,
        requireNotNull(cfi)
    )

    ReaderOutboxOperation.ANNOTATION_UPSERT -> ReaderOutboxIntent.AnnotationUpsert(
        outboxId,
        bookId,
        localSessionId,
        requireNotNull(annotationClientId),
        requireNotNull(annotationKind),
        requireNotNull(cfi),
        locationLabel,
        quote,
        prefix,
        suffix,
        note,
        color?.let(ReaderAnnotationColor::valueOf)
    )

    ReaderOutboxOperation.ANNOTATION_DELETE -> ReaderOutboxIntent.AnnotationDelete(
        outboxId,
        bookId,
        localSessionId,
        requireNotNull(annotationClientId)
    )

    else -> error("Unknown Reader outbox operation: $operationKind")
}

internal fun LocalReaderSessionEntity.toEstablishmentOutbox(now: Long) = emptyOutbox(
    ReaderOutboxIdentity.session(localSessionId),
    ReaderOutboxOperation.SESSION_ESTABLISHMENT,
    SESSION_ESTABLISHMENT_DELIVERY_ORDER,
    now
)

internal fun LocalReaderProgressEntity.toOutbox(bookId: String) = LocalReaderOutboxEntity(
    accountKey,
    ReaderOutboxIdentity.progress(localSessionId),
    bookId,
    localSessionId,
    ReaderOutboxOperation.PROGRESS,
    null,
    READER_MUTATION_DELIVERY_ORDER,
    cfi,
    null,
    null,
    null,
    null,
    null,
    null,
    null,
    updatedAtEpochMillis
)

internal fun LocalReaderAnnotationEntity.toUpsertOutbox(bookId: String, now: Long) =
    LocalReaderOutboxEntity(
        accountKey,
        ReaderOutboxIdentity.annotation(localSessionId, clientId),
        bookId,
        localSessionId,
        ReaderOutboxOperation.ANNOTATION_UPSERT,
        clientId,
        READER_MUTATION_DELIVERY_ORDER,
        cfi,
        kind,
        locationLabel,
        quote,
        prefix,
        suffix,
        note,
        color,
        now
    )

internal fun annotationDeleteOutbox(
    account: LocalReaderAccountKey,
    bookId: String,
    localSessionId: String,
    clientId: String,
    now: Long
) = LocalReaderOutboxEntity(
    account.value,
    ReaderOutboxIdentity.annotation(localSessionId, clientId),
    bookId,
    localSessionId,
    ReaderOutboxOperation.ANNOTATION_DELETE,
    clientId,
    READER_MUTATION_DELIVERY_ORDER,
    null,
    null,
    null,
    null,
    null,
    null,
    null,
    null,
    now
)

private fun LocalReaderSessionEntity.emptyOutbox(
    id: String,
    operation: String,
    order: Int,
    now: Long
) = LocalReaderOutboxEntity(
    accountKey,
    id,
    bookId,
    localSessionId,
    operation,
    null,
    order,
    null,
    null,
    null,
    null,
    null,
    null,
    null,
    null,
    now
)
