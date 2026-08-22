package com.secondpasslibrary.reader.marginalia

import com.secondpasslibrary.client.SplClientException

internal fun Throwable.toMarginaliaFailure(): MarginaliaFailure = when (this) {
    is SplClientException.ServerUnreachable -> MarginaliaFailure.UNREACHABLE

    is SplClientException.AuthenticationRejected -> MarginaliaFailure.AUTHENTICATION_REJECTED

    is SplClientException.ProtocolInvalid,
    is SplClientException.NotSecondPassServer -> MarginaliaFailure.PROTOCOL_INVALID

    else -> MarginaliaFailure.OTHER
}

internal fun MarginaliaFailure.userMessage(): String = when (this) {
    MarginaliaFailure.UNREACHABLE -> "The library is currently unreachable."
    MarginaliaFailure.AUTHENTICATION_REJECTED -> "Library authentication was rejected."
    MarginaliaFailure.PROTOCOL_INVALID -> "The library returned an invalid response."
    MarginaliaFailure.OTHER -> "Reading sessions could not be loaded."
}
