package com.secondpasslibrary.client

import com.secondpasslibrary.client.internal.MarginaliaAnnotationWire
import com.secondpasslibrary.client.internal.MarginaliaHighlightBodyWire
import com.secondpasslibrary.client.internal.ReadingProgressInputWire
import com.secondpasslibrary.client.internal.ReadingSessionBootstrapWire
import com.secondpasslibrary.client.internal.ReadingSessionFinalizationWire
import com.secondpasslibrary.client.internal.ReadingSessionMetadataInputWire
import com.secondpasslibrary.client.internal.ReadingSessionSummaryPageWire

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

private fun MarginaliaAnnotationWire.toModel(): MarginaliaAnnotation {
    val annotationId = id.required(ANNOTATION_CONTEXT)
    val annotationClientId = clientId.required(ANNOTATION_CONTEXT)
    val annotationLocation = MarginaliaAnnotationLocation(
        cfi = location?.cfi.required(ANNOTATION_CONTEXT),
        locationLabel = location?.locationLabel ?: invalidProtocol(ANNOTATION_CONTEXT)
    )
    val annotationCreatedAt = createdAt.required(ANNOTATION_CONTEXT)
    val annotationUpdatedAt = updatedAt.required(ANNOTATION_CONTEXT)
    return when (kind) {
        "bookmark" -> MarginaliaAnnotation.Bookmark(
            annotationId,
            annotationClientId,
            annotationLocation,
            annotationCreatedAt,
            annotationUpdatedAt
        )

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

private fun MarginaliaHighlightBodyWire.toModel() = MarginaliaHighlightBody(
    text = text ?: invalidProtocol(ANNOTATION_CONTEXT),
    prefix = prefix ?: invalidProtocol(ANNOTATION_CONTEXT),
    suffix = suffix ?: invalidProtocol(ANNOTATION_CONTEXT),
    color = color.toHighlightColor(),
    note = note ?: invalidProtocol(ANNOTATION_CONTEXT)
)

private fun String?.toHighlightColor(): MarginaliaHighlightColor = when (this) {
    "yellow" -> MarginaliaHighlightColor.YELLOW
    "green" -> MarginaliaHighlightColor.GREEN
    "blue" -> MarginaliaHighlightColor.BLUE
    "pink" -> MarginaliaHighlightColor.PINK
    "purple" -> MarginaliaHighlightColor.PURPLE
    "orange" -> MarginaliaHighlightColor.ORANGE
    else -> invalidProtocol(ANNOTATION_CONTEXT)
}
