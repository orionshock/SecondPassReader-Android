package com.secondpasslibrary.reader.reader.persistence

import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotation
import com.secondpasslibrary.reader.reader.annotations.mutation.ReaderAnnotationMutationRequest
import com.secondpasslibrary.reader.reader.session.ReaderSessionContext
import com.secondpasslibrary.reader.reader.session.ReaderSessionIdentityKind
import com.secondpasslibrary.reader.reader.session.ReaderSessionStatus
import java.security.MessageDigest
import java.time.Instant
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@JvmInline
internal value class LocalReaderAccountKey private constructor(val value: String) {
    companion object {
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
        confirmedClientId: String? = null
    )

    suspend fun purgeAccount(account: LocalReaderAccountKey)
}

@Singleton
internal class RoomLocalReaderStateStore @Inject constructor(private val dao: LocalReaderDao) :
    LocalReaderStateStore {
    override suspend fun selectOfflineSession(
        account: LocalReaderAccountKey,
        bookId: String
    ): ReaderSessionContext {
        val now = Instant.now().toEpochMilli()
        val selected = dao.activeServerSession(account.value, bookId)
            ?: dao.activeProvisionalSession(account.value, bookId)
            ?: createProvisional(account, bookId, now)
        dao.touchSession(account.value, selected.localSessionId, now)
        return selected.toContext(dao.progress(account.value, selected.localSessionId)?.cfi)
    }

    override suspend fun retainServerSession(
        account: LocalReaderAccountKey,
        bookId: String,
        session: ReaderSessionContext
    ): ReaderSessionContext {
        require(session.identityKind == ReaderSessionIdentityKind.SERVER_CONFIRMED)
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
        dao.upsertProgress(
            LocalReaderProgressEntity(
                account.value,
                localSessionId,
                cfi,
                Instant.now().toEpochMilli(),
                provenance.name
            )
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
        when (request) {
            is ReaderAnnotationMutationRequest.UpsertHighlight ->
                dao.upsertAnnotation(request.toEntity(account, localSessionId))

            is ReaderAnnotationMutationRequest.UpsertBookmark ->
                dao.upsertAnnotation(request.toEntity(account, localSessionId))

            is ReaderAnnotationMutationRequest.Delete -> {
                val existing = dao.annotation(account.value, localSessionId, request.clientId)
                    ?: request.localSnapshot?.toEntity(account, localSessionId)
                if (existing != null) {
                    dao.upsertAnnotation(
                        existing.copy(syncState = LocalAnnotationSync.LOCAL_DELETED)
                    )
                }
            }
        }
        return readAnnotations(account, localSessionId)
    }

    override suspend fun replaceAuthoritativeAnnotations(
        account: LocalReaderAccountKey,
        localSessionId: String,
        annotations: List<ReaderAnnotation>,
        confirmedClientId: String?
    ) {
        val pending = dao.pendingAnnotations(account.value, localSessionId)
            .filterNot { it.clientId == confirmedClientId }
        dao.replaceAnnotations(
            account.value,
            localSessionId,
            annotations.map { it.toEntity(account, localSessionId) } +
                pending
        )
    }

    override suspend fun purgeAccount(account: LocalReaderAccountKey) {
        dao.purgeAccount(account.value)
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
        dao.insertSessionIfAbsent(candidate)
        return dao.activeProvisionalSession(account.value, bookId) ?: candidate
    }
}
