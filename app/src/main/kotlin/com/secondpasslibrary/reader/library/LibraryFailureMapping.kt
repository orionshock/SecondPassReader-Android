package com.secondpasslibrary.reader.library

import com.secondpasslibrary.client.SplClientException

internal fun Throwable.toLibraryFailure(): LibraryFailure = when (this) {
    is SplClientException.ServerUnreachable -> LibraryFailure.UNREACHABLE

    is SplClientException.AuthenticationRejected -> LibraryFailure.AUTHENTICATION_REJECTED

    is SplClientException.ProtocolInvalid,
    is SplClientException.NotSecondPassServer -> LibraryFailure.PROTOCOL_INVALID

    else -> LibraryFailure.OTHER
}
