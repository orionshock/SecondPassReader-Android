package com.secondpasslibrary.client.internal.marginalia

import com.secondpasslibrary.client.ClosedReadingSessionPage
import com.secondpasslibrary.client.MAX_ANNOTATION_CLIENT_ID_LENGTH
import com.secondpasslibrary.client.MAX_CFI_LENGTH
import com.secondpasslibrary.client.MAX_HIGHLIGHT_CONTEXT_LENGTH
import com.secondpasslibrary.client.MAX_HIGHLIGHT_NOTE_LENGTH
import com.secondpasslibrary.client.MAX_HIGHLIGHT_TEXT_LENGTH
import com.secondpasslibrary.client.MAX_LOCATION_LABEL_LENGTH
import com.secondpasslibrary.client.MarginaliaAnnotation
import com.secondpasslibrary.client.MarginaliaAnnotationLocation
import com.secondpasslibrary.client.MarginaliaHighlightBody
import com.secondpasslibrary.client.MarginaliaHighlightColor
import com.secondpasslibrary.client.ReadingSessionBootstrap
import com.secondpasslibrary.client.ReadingSessionDetail
import com.secondpasslibrary.client.ReadingSessionFinalization
import com.secondpasslibrary.client.ReadingSessionMetadataInput
import com.secondpasslibrary.client.internal.transport.boundedNullable
import com.secondpasslibrary.client.internal.transport.boundedOpaque
import com.secondpasslibrary.client.internal.transport.invalidProtocol
import com.secondpasslibrary.client.internal.transport.required

private const val BOOTSTRAP_CONTEXT = "Reading Session bootstrap"
private const val ANNOTATION_CONTEXT = "marginalia annotation"

internal fun ReadingSessionBootstrapWire.toModel(): ReadingSessionBootstrap =
    ReadingSessionBootstrap(
        created = created ?: invalidProtocol(BOOTSTRAP_CONTEXT),
        book = context?.book?.toBookModel() ?: invalidProtocol(BOOTSTRAP_CONTEXT),
        activeSession = session?.let {
            ReadingSessionDetail(it.toSummary(), it.progress?.toModel())
        },
        annotations = annotations?.map(MarginaliaAnnotationWire::toModel)
            ?: invalidProtocol(BOOTSTRAP_CONTEXT),
        closedSessions = closedSessions?.toModel() ?: invalidProtocol(BOOTSTRAP_CONTEXT)
    )

internal fun ReadingSessionMetadataInput.toWire() = ReadingSessionMetadataInputWire(name, notes)

internal fun ReadingSessionFinalization.toWire() = ReadingSessionFinalizationWire(
    name = name,
    notes = notes,
    progress = progress?.let { input ->
        ReadingProgressInputWire(
            cfi = input.cfi,
            locationLabel = input.locationLabel
        )
    }
)

private fun ReadingSessionSummaryPageWire.toModel() = ClosedReadingSessionPage(
    totalCount = count.nonNegative(BOOTSTRAP_CONTEXT),
    results = results?.map { it.toSummary() } ?: invalidProtocol(BOOTSTRAP_CONTEXT),
    hasNext = next != null,
    hasPrevious = previous != null
)

internal fun MarginaliaAnnotationWire.toModel(): MarginaliaAnnotation {
    val annotationId = id.required(ANNOTATION_CONTEXT)
    val annotationClientId = clientId.boundedOpaque(
        MAX_ANNOTATION_CLIENT_ID_LENGTH,
        ANNOTATION_CONTEXT
    )
    val annotationLocation = MarginaliaAnnotationLocation(
        cfi = location?.cfi.boundedOpaque(MAX_CFI_LENGTH, ANNOTATION_CONTEXT),
        locationLabel = location?.locationLabel.boundedNullable(
            MAX_LOCATION_LABEL_LENGTH,
            ANNOTATION_CONTEXT
        )
    )
    val annotationCreatedAt = createdAt.required(ANNOTATION_CONTEXT)
    val annotationUpdatedAt = updatedAt.required(ANNOTATION_CONTEXT)
    return when (kind) {
        "bookmark" -> {
            if (body != null) invalidProtocol(ANNOTATION_CONTEXT)
            MarginaliaAnnotation.Bookmark(
                annotationId,
                annotationClientId,
                annotationLocation,
                annotationCreatedAt,
                annotationUpdatedAt
            )
        }

        "highlight" -> MarginaliaAnnotation.Highlight(
            annotationId,
            annotationClientId,
            annotationLocation,
            annotationCreatedAt,
            annotationUpdatedAt,
            body?.toModel() ?: invalidProtocol(ANNOTATION_CONTEXT)
        )

        else -> invalidProtocol(ANNOTATION_CONTEXT)
    }
}

private fun MarginaliaHighlightBodyWire.toModel(): MarginaliaHighlightBody {
    val bodyText = text.boundedOpaque(MAX_HIGHLIGHT_TEXT_LENGTH, ANNOTATION_CONTEXT)
    return MarginaliaHighlightBody(
        text = bodyText,
        prefix = prefix.boundedNullable(MAX_HIGHLIGHT_CONTEXT_LENGTH, ANNOTATION_CONTEXT),
        suffix = suffix.boundedNullable(MAX_HIGHLIGHT_CONTEXT_LENGTH, ANNOTATION_CONTEXT),
        color = color.toHighlightColor(),
        note = note.boundedNullable(MAX_HIGHLIGHT_NOTE_LENGTH, ANNOTATION_CONTEXT)
    )
}

private fun String?.toHighlightColor(): MarginaliaHighlightColor = when (this) {
    "yellow" -> MarginaliaHighlightColor.YELLOW
    "green" -> MarginaliaHighlightColor.GREEN
    "blue" -> MarginaliaHighlightColor.BLUE
    "pink" -> MarginaliaHighlightColor.PINK
    "purple" -> MarginaliaHighlightColor.PURPLE
    "orange" -> MarginaliaHighlightColor.ORANGE
    else -> invalidProtocol(ANNOTATION_CONTEXT)
}
