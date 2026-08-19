package com.secondpasslibrary.reader.shelves

import com.secondpasslibrary.client.ShelfMutationRejection
import com.secondpasslibrary.client.SplClientException

internal fun Throwable.toShelfContentsMutationFailure(): ShelfContentsMutationFailure =
    when (this) {
        is SplClientException.ServerUnreachable -> ShelfContentsMutationFailure.UNREACHABLE

        is SplClientException.AuthenticationRejected ->
            ShelfContentsMutationFailure.AUTHENTICATION_REJECTED

        is SplClientException.ShelfMutationRejected -> reason.toContentsFailure()

        else -> ShelfContentsMutationFailure.OTHER
    }

private fun ShelfMutationRejection.toContentsFailure(): ShelfContentsMutationFailure = when (this) {
    ShelfMutationRejection.DIRECT_POSITION_UNAVAILABLE ->
        ShelfContentsMutationFailure.DIRECT_POSITION_UNAVAILABLE

    ShelfMutationRejection.VALIDATION,
    ShelfMutationRejection.INVALID_MOVE,
    ShelfMutationRejection.DUPLICATE_BOOK -> ShelfContentsMutationFailure.VALIDATION

    ShelfMutationRejection.NOT_AUTHORIZED -> ShelfContentsMutationFailure.NOT_AUTHORIZED

    ShelfMutationRejection.RESOURCE_NOT_FOUND -> ShelfContentsMutationFailure.NOT_FOUND
}
