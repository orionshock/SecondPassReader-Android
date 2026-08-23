package com.secondpasslibrary.client.internal.marginalia

import com.secondpasslibrary.client.MarginaliaAnnotationDraft
import com.secondpasslibrary.client.MarginaliaAnnotationLocationInput
import com.secondpasslibrary.client.MarginaliaAnnotationOperation
import com.secondpasslibrary.client.MarginaliaHighlightColor
import com.secondpasslibrary.client.ReadingProgressInput

internal fun List<MarginaliaAnnotationOperation>.toWire() = MarginaliaAnnotationBatchWire(
    map(MarginaliaAnnotationOperation::toWire)
)

internal fun ReadingProgressInput.toWire() = ReadingProgressInputWire(cfi, locationLabel)

private fun MarginaliaAnnotationOperation.toWire(): MarginaliaAnnotationOperationWire =
    when (this) {
        is MarginaliaAnnotationOperation.Delete -> MarginaliaAnnotationOperationWire(
            action = "delete",
            clientId = clientId
        )

        is MarginaliaAnnotationOperation.Upsert -> MarginaliaAnnotationOperationWire(
            action = "upsert",
            annotation = annotation.toWire()
        )
    }

private fun MarginaliaAnnotationDraft.toWire(): MarginaliaAnnotationDraftWire = when (this) {
    is MarginaliaAnnotationDraft.Bookmark -> MarginaliaAnnotationDraftWire(
        clientId = clientId,
        kind = "bookmark",
        location = location.toWire()
    )

    is MarginaliaAnnotationDraft.Highlight -> MarginaliaAnnotationDraftWire(
        clientId = clientId,
        kind = "highlight",
        location = location.toWire(),
        body = MarginaliaHighlightBodyWire(
            text = body.text,
            prefix = body.prefix,
            suffix = body.suffix,
            color = body.color.token,
            note = body.note
        )
    )
}

private fun MarginaliaAnnotationLocationInput.toWire() = MarginaliaAnnotationLocationWire(
    cfi = cfi,
    locationLabel = locationLabel
)

private val MarginaliaHighlightColor.token: String
    get() = when (this) {
        MarginaliaHighlightColor.YELLOW -> "yellow"
        MarginaliaHighlightColor.GREEN -> "green"
        MarginaliaHighlightColor.BLUE -> "blue"
        MarginaliaHighlightColor.PINK -> "pink"
        MarginaliaHighlightColor.PURPLE -> "purple"
        MarginaliaHighlightColor.ORANGE -> "orange"
    }
