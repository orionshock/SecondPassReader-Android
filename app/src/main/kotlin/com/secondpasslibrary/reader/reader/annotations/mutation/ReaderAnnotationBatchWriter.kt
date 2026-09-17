package com.secondpasslibrary.reader.reader.annotations.mutation

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

    data class UpsertHighlight(
        override val sessionId: String,
        val clientId: String,
        val cfi: String,
        val locationLabel: String?,
        val text: String,
        val prefix: String?,
        val suffix: String?,
        val color: ReaderAnnotationColor,
        val note: String
    ) : ReaderAnnotationMutationRequest

    data class UpsertBookmark(
        override val sessionId: String,
        val clientId: String,
        val cfi: String,
        val locationLabel: String
    ) : ReaderAnnotationMutationRequest

    data class Delete(
        override val sessionId: String,
        val clientId: String,
        val localSnapshot: ReaderAnnotation? = null
    ) : ReaderAnnotationMutationRequest
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
        val operations = requests.map { request ->
            when (request) {
                is ReaderAnnotationMutationRequest.UpsertHighlight -> request.toOperation()

                is ReaderAnnotationMutationRequest.UpsertBookmark -> request.toOperation()

                is ReaderAnnotationMutationRequest.Delete ->
                    MarginaliaAnnotationOperation.Delete(request.clientId)
            }
        }
        clientProvider.forProfile(profile)
            .marginalia.sessions.synchronizeAnnotations(serverSessionId, operations)
            .map { it.toReaderAnnotation() }
    }

    private companion object {
        const val MAX_ANNOTATION_BATCH_SIZE = 100
    }
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
