package com.secondpasslibrary.reader.shelves

import com.secondpasslibrary.client.ShelfMutationRejection
import com.secondpasslibrary.client.SplClientException

internal fun Throwable.toShelfManagementFailure(): ShelfManagementFailure = when (this) {
    is SplClientException.ServerUnreachable -> ShelfManagementFailure.UNREACHABLE
    is SplClientException.AuthenticationRejected -> ShelfManagementFailure.AUTHENTICATION_REJECTED
    is SplClientException.ShelfMutationRejected -> reason.toManagementFailure()
    else -> ShelfManagementFailure.OTHER
}

private fun ShelfMutationRejection.toManagementFailure(): ShelfManagementFailure = when (this) {
    ShelfMutationRejection.VALIDATION -> ShelfManagementFailure.VALIDATION

    ShelfMutationRejection.NOT_AUTHORIZED -> ShelfManagementFailure.NOT_AUTHORIZED

    ShelfMutationRejection.RESOURCE_NOT_FOUND -> ShelfManagementFailure.NOT_FOUND

    ShelfMutationRejection.DUPLICATE_BOOK,
    ShelfMutationRejection.INVALID_MOVE,
    ShelfMutationRejection.DIRECT_POSITION_UNAVAILABLE -> ShelfManagementFailure.REJECTED
}
