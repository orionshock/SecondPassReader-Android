package com.secondpasslibrary.reader.bookdetail

import com.secondpasslibrary.client.ShelfMutationRejection
import com.secondpasslibrary.client.SplClientException

internal fun Throwable.toBookShelfPickerFailure(): BookShelfPickerFailure = when (this) {
    is SplClientException.ServerUnreachable -> BookShelfPickerFailure.UNREACHABLE

    is SplClientException.AuthenticationRejected ->
        BookShelfPickerFailure.AUTHENTICATION_REJECTED

    is SplClientException.ProtocolInvalid,
    is SplClientException.NotSecondPassServer -> BookShelfPickerFailure.PROTOCOL_INVALID

    is SplClientException.ShelfMutationRejected -> reason.toBookShelfPickerFailure()

    else -> BookShelfPickerFailure.OTHER
}

internal fun Throwable.isAmbiguousShelfAddFailure(): Boolean =
    this is SplClientException.ServerUnreachable ||
        this is SplClientException.AuthenticatedRequestFailed ||
        this is SplClientException.ProtocolInvalid

private fun ShelfMutationRejection.toBookShelfPickerFailure(): BookShelfPickerFailure =
    when (this) {
        ShelfMutationRejection.NOT_AUTHORIZED -> BookShelfPickerFailure.NOT_AUTHORIZED

        ShelfMutationRejection.RESOURCE_NOT_FOUND -> BookShelfPickerFailure.NOT_FOUND

        ShelfMutationRejection.VALIDATION,
        ShelfMutationRejection.INVALID_MOVE,
        ShelfMutationRejection.DIRECT_POSITION_UNAVAILABLE,
        ShelfMutationRejection.DUPLICATE_BOOK -> BookShelfPickerFailure.VALIDATION
    }
