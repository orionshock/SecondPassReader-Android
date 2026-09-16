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
    MarginaliaFailure.UNREACHABLE ->
        "Couldn’t reach the Library. Check your connection and retry."

    MarginaliaFailure.AUTHENTICATION_REJECTED ->
        "Your connection is no longer authorized. Repair it in Settings."

    MarginaliaFailure.PROTOCOL_INVALID ->
        "Couldn’t read the Library response. Retry or repair the connection in Settings."

    MarginaliaFailure.OTHER -> "Couldn’t load Reading Sessions. Retry."
}
