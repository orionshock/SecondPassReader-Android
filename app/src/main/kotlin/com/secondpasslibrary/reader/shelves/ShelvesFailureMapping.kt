package com.secondpasslibrary.reader.shelves

import com.secondpasslibrary.client.SplClientException

internal fun Throwable.toShelvesFailure(): ShelvesFailure = when (this) {
    is SplClientException.ServerUnreachable -> ShelvesFailure.UNREACHABLE

    is SplClientException.AuthenticationRejected -> ShelvesFailure.AUTHENTICATION_REJECTED

    is SplClientException.ProtocolInvalid,
    is SplClientException.NotSecondPassServer -> ShelvesFailure.PROTOCOL_INVALID

    else -> ShelvesFailure.OTHER
}
