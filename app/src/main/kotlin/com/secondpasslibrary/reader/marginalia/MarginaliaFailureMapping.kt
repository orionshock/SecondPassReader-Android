package com.secondpasslibrary.reader.marginalia

import com.secondpasslibrary.client.SplClientException

internal fun Throwable.toMarginaliaFailure(): MarginaliaFailure = when (this) {
    is SplClientException.ServerUnreachable -> MarginaliaFailure.UNREACHABLE

    is SplClientException.AuthenticationRejected -> MarginaliaFailure.AUTHENTICATION_REJECTED

    is SplClientException.ProtocolInvalid,
    is SplClientException.NotSecondPassServer -> MarginaliaFailure.PROTOCOL_INVALID

    else -> MarginaliaFailure.OTHER
}
