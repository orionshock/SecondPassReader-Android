package com.secondpasslibrary.reader.reader.annotations.mutation

import com.secondpasslibrary.client.MAX_ANNOTATION_BATCH_SIZE
import com.secondpasslibrary.client.MarginaliaAnnotationDraft
import com.secondpasslibrary.client.MarginaliaAnnotationLocationInput
import com.secondpasslibrary.client.MarginaliaAnnotationOperation
import com.secondpasslibrary.client.MarginaliaHighlightBodyInput
import com.secondpasslibrary.client.MarginaliaHighlightColor
import com.secondpasslibrary.reader.connection.AuthenticatedClientProvider
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotation
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotationColor
import com.secondpasslibrary.reader.reader.annotations.toReaderAnnotation
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal sealed interface ReaderAnnotationMutationRequest {
    val sessionId: String
    val clientId: String

    data class UpsertHighlight(
        override val sessionId: String,
        override val clientId: String,
        val cfi: String,
        val locationLabel: String?,
        val text: String,
        val prefix: String?,
        val suffix: String?,
        val color: ReaderAnnotationColor,
        val note: String
    ) : ReaderAnnotationMutationRequest {
        init {
            validateIdentityAndAnchor(sessionId, clientId, cfi)
            require(text.isNotBlank()) { "Highlight text must not be blank." }
        }
    }

    data class UpsertBookmark(
        override val sessionId: String,
        override val clientId: String,
        val cfi: String,
        val locationLabel: String
    ) : ReaderAnnotationMutationRequest {
        init {
            validateIdentityAndAnchor(sessionId, clientId, cfi)
        }
    }

    data class Delete(
        override val sessionId: String,
        override val clientId: String,
        val localSnapshot: ReaderAnnotation? = null
    ) : ReaderAnnotationMutationRequest {
        init {
            require(sessionId.isNotBlank()) { "Reader Session ID must not be blank." }
            validateClientId(clientId)
        }
    }
}

private fun validateIdentityAndAnchor(sessionId: String, clientId: String, cfi: String) {
    require(sessionId.isNotBlank()) { "Reader Session ID must not be blank." }
    validateClientId(clientId)
    require(cfi.isNotBlank()) { "Annotation CFI must not be blank." }
}

internal fun interface ReaderAnnotationBatchWriter {
    suspend fun synchronize(
        profile: ConnectionProfile,
        serverSessionId: String,
        requests: List<ReaderAnnotationMutationRequest>
    ): List<ReaderAnnotation>
}

@Singleton
internal class SplReaderAnnotationWriter @Inject constructor(
    private val clientProvider: AuthenticatedClientProvider
) : ReaderAnnotationBatchWriter {
    private val deliveryMutex = Mutex()

    override suspend fun synchronize(
        profile: ConnectionProfile,
        serverSessionId: String,
        requests: List<ReaderAnnotationMutationRequest>
    ): List<ReaderAnnotation> = deliveryMutex.withLock {
        require(requests.size in 1..MAX_ANNOTATION_BATCH_SIZE)
        require(requests.all { it.sessionId == serverSessionId })
        val operations = requests.map(ReaderAnnotationMutationRequest::toOperation)
        clientProvider.forProfile(profile)
            .marginalia.sessions.synchronizeAnnotations(serverSessionId, operations)
            .map { it.toReaderAnnotation() }
    }
}

internal fun ReaderAnnotationMutationRequest.toOperation(): MarginaliaAnnotationOperation =
    when (this) {
        is ReaderAnnotationMutationRequest.UpsertHighlight -> toOperation()
        is ReaderAnnotationMutationRequest.UpsertBookmark -> toOperation()
        is ReaderAnnotationMutationRequest.Delete -> MarginaliaAnnotationOperation.Delete(clientId)
    }

internal fun ReaderAnnotationMutationRequest.forSession(
    sessionId: String
): ReaderAnnotationMutationRequest = when (this) {
    is ReaderAnnotationMutationRequest.UpsertHighlight -> copy(sessionId = sessionId)
    is ReaderAnnotationMutationRequest.UpsertBookmark -> copy(sessionId = sessionId)
    is ReaderAnnotationMutationRequest.Delete -> copy(sessionId = sessionId, localSnapshot = null)
}

private fun ReaderAnnotationMutationRequest.UpsertHighlight.toOperation() =
    MarginaliaAnnotationOperation.Upsert(
        MarginaliaAnnotationDraft.Highlight(
            clientId = clientId,
            location = MarginaliaAnnotationLocationInput(cfi, locationLabel),
            body = MarginaliaHighlightBodyInput(text, prefix, suffix, color.toSdkColor(), note)
        )
    )

private fun ReaderAnnotationMutationRequest.UpsertBookmark.toOperation() =
    MarginaliaAnnotationOperation.Upsert(
        MarginaliaAnnotationDraft.Bookmark(
            clientId = clientId,
            location = MarginaliaAnnotationLocationInput(cfi, locationLabel)
        )
    )

private fun ReaderAnnotationColor.toSdkColor(): MarginaliaHighlightColor = when (this) {
    ReaderAnnotationColor.YELLOW -> MarginaliaHighlightColor.YELLOW
    ReaderAnnotationColor.GREEN -> MarginaliaHighlightColor.GREEN
    ReaderAnnotationColor.BLUE -> MarginaliaHighlightColor.BLUE
    ReaderAnnotationColor.PINK -> MarginaliaHighlightColor.PINK
    ReaderAnnotationColor.PURPLE -> MarginaliaHighlightColor.PURPLE
    ReaderAnnotationColor.ORANGE -> MarginaliaHighlightColor.ORANGE
}
