package com.secondpasslibrary.reader.reader.persistence

import com.secondpasslibrary.reader.reader.LocalReaderAccountKey
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotation
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotationColor
import com.secondpasslibrary.reader.reader.annotations.mutation.ReaderAnnotationMutationRequest
import com.secondpasslibrary.reader.reader.session.ReaderSessionContext
import com.secondpasslibrary.reader.reader.session.ReaderSessionIdentityKind
import com.secondpasslibrary.reader.reader.session.ReaderSessionStatus
import java.time.Instant

internal fun LocalReaderSessionEntity.toContext(savedCfi: String?): ReaderSessionContext {
    LocalReaderPersistedStateValidator.session(this)
    return ReaderSessionContext(
        sessionId = localSessionId,
        serverSessionId = serverSessionId,
        identityKind = ReaderSessionIdentityKind.valueOf(identityKind),
        status = if (identityKind == ReaderSessionIdentityKind.PROVISIONAL.name) {
            ReaderSessionStatus.ACTIVE
        } else {
            ReaderSessionStatus.valueOf(requireNotNull(serverStatus))
        },
        savedProgressCfi = savedCfi,
        sessionName = sessionName,
        startedAt = startedAt,
        closedAt = closedAt,
        lastActivityAt = lastActivityAt,
        annotationCount = serverAnnotationCount,
        sessionNotes = sessionNotes
    )
}

internal fun ReaderSessionContext.toServerEntity(
    account: LocalReaderAccountKey,
    bookId: String,
    now: Long
) = LocalReaderSessionEntity(
    accountKey = account.value,
    localSessionId = sessionId,
    bookId = bookId,
    serverSessionId = requireNotNull(serverSessionId),
    identityKind = ReaderSessionIdentityKind.SERVER_CONFIRMED.name,
    serverStatus = status.name,
    activeProvisionalBookId = null,
    sessionName = sessionName,
    sessionNotes = sessionNotes,
    startedAt = startedAt,
    closedAt = closedAt,
    lastActivityAt = lastActivityAt,
    serverAnnotationCount = annotationCount,
    createdAtEpochMillis = now,
    lastUsedAtEpochMillis = now
)

internal fun ReaderAnnotation.toEntity(account: LocalReaderAccountKey, localSessionId: String) =
    when (this) {
        is ReaderAnnotation.Bookmark -> LocalReaderAnnotationEntity(
            account.value, localSessionId, id.takeUnless { it.startsWith("local:") }, clientId,
            LocalAnnotationKind.BOOKMARK, cfi, locationLabel, null, null, null, null, null,
            updatedAt, LocalAnnotationSync.SERVER_CONFIRMED
        )

        is ReaderAnnotation.Highlight -> LocalReaderAnnotationEntity(
            account.value, localSessionId, id.takeUnless { it.startsWith("local:") }, clientId,
            LocalAnnotationKind.HIGHLIGHT, cfi, locationLabel, quote, prefix, suffix, note,
            color.name, updatedAt, LocalAnnotationSync.SERVER_CONFIRMED
        )
    }

internal fun ReaderAnnotationMutationRequest.UpsertHighlight.toEntity(
    account: LocalReaderAccountKey,
    localSessionId: String
) = LocalReaderAnnotationEntity(
    account.value, localSessionId, null, clientId, LocalAnnotationKind.HIGHLIGHT, cfi,
    locationLabel, text, prefix, suffix, note, color.name, Instant.now().toString(),
    LocalAnnotationSync.LOCAL_PENDING
)

internal fun ReaderAnnotationMutationRequest.UpsertBookmark.toEntity(
    account: LocalReaderAccountKey,
    localSessionId: String
) = LocalReaderAnnotationEntity(
    account.value, localSessionId, null, clientId, LocalAnnotationKind.BOOKMARK, cfi,
    locationLabel, null, null, null, null, null, Instant.now().toString(),
    LocalAnnotationSync.LOCAL_PENDING
)

internal fun LocalReaderAnnotationEntity.toReaderAnnotation(): ReaderAnnotation {
    LocalReaderPersistedStateValidator.annotation(this)
    return when (kind) {
        LocalAnnotationKind.BOOKMARK -> ReaderAnnotation.Bookmark(
            serverAnnotationId ?: "local:$clientId",
            clientId,
            cfi,
            locationLabel,
            updatedAt
        )

        else -> ReaderAnnotation.Highlight(
            serverAnnotationId ?: "local:$clientId", clientId, cfi, locationLabel, updatedAt,
            requireNotNull(quote), prefix, suffix, note,
            ReaderAnnotationColor.valueOf(requireNotNull(color))
        )
    }
}

internal object LocalAnnotationKind {
    const val BOOKMARK = "BOOKMARK"
    const val HIGHLIGHT = "HIGHLIGHT"
}

internal object LocalAnnotationSync {
    const val SERVER_CONFIRMED = "SERVER_CONFIRMED"
    const val LOCAL_PENDING = "LOCAL_PENDING"
    const val LOCAL_DELETED = "LOCAL_DELETED"
}
