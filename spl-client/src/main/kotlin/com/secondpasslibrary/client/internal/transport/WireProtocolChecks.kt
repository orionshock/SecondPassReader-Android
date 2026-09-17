package com.secondpasslibrary.client.internal.transport

import com.secondpasslibrary.client.SplClientException
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode

private const val MIN_CLIENT_NAME_LENGTH = 1
private const val MAX_CLIENT_NAME_LENGTH = 200
private const val MIN_CLIENT_TYPE_LENGTH = 1
private const val MAX_CLIENT_TYPE_LENGTH = 64
private const val HTTP_SUCCESS_START = 200
private const val HTTP_SUCCESS_END = 299

internal fun validateClientIdentity(clientName: String, clientType: String): Pair<String, String> {
    val name = clientName.trim()
    val type = clientType.trim()
    if (name.length !in MIN_CLIENT_NAME_LENGTH..MAX_CLIENT_NAME_LENGTH ||
        type.length !in MIN_CLIENT_TYPE_LENGTH..MAX_CLIENT_TYPE_LENGTH
    ) {
        throw SplClientException.PairingValidationRejected()
    }
    return name to type
}

internal fun requireDiscoverySuccess(response: HttpResponse) {
    if (!response.status.isSuccess()) throw SplClientException.NotSecondPassServer()
}

internal fun requireDiscoveryBearer(tokenType: String) {
    if (!tokenType.equals("Bearer", ignoreCase = true)) {
        throw SplClientException.NotSecondPassServer()
    }
}

internal fun requirePairingCreateSuccess(response: HttpResponse) {
    if (response.status.isSuccess()) return
    throw when (response.status) {
        HttpStatusCode.BadRequest, HttpStatusCode.UnprocessableEntity ->
            SplClientException.PairingValidationRejected()

        HttpStatusCode.TooManyRequests -> SplClientException.PairingThrottled()

        else -> SplClientException.ProtocolInvalid("pairing creation")
    }
}

internal fun requirePairingStatusSuccess(response: HttpResponse) {
    if (response.status.isSuccess()) return
    throw if (response.status == HttpStatusCode.TooManyRequests) {
        SplClientException.PairingThrottled()
    } else {
        SplClientException.ProtocolInvalid("pairing status")
    }
}

internal fun requireConsumeSuccess(response: HttpResponse) {
    if (response.status.isSuccess()) return
    throw if (response.status == HttpStatusCode.TooManyRequests) {
        SplClientException.PairingThrottled()
    } else {
        SplClientException.AmbiguousConsumeFailure()
    }
}

internal fun requireAuthenticatedSuccess(response: HttpResponse) {
    if (response.status.isSuccess()) return
    throw if (response.status == HttpStatusCode.Unauthorized) {
        SplClientException.AuthenticationRejected()
    } else {
        SplClientException.AuthenticatedRequestFailed()
    }
}

internal fun requireClientSessionRevocationSuccess(response: HttpResponse): Unit =
    throw when (response.status) {
        HttpStatusCode.NoContent -> return

        HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden ->
            SplClientException.AuthenticationRejected()

        HttpStatusCode.NotFound -> SplClientException.ClientSessionNotFound()

        else -> SplClientException.ClientSessionRevocationFailed()
    }

internal inline fun <T> discoveryValue(block: () -> T): T = try {
    block()
} catch (_: SplClientException.ProtocolInvalid) {
    throw SplClientException.NotSecondPassServer()
}

internal fun invalidResponse(discovery: Boolean, context: String): Nothing = throw if (discovery) {
    SplClientException.NotSecondPassServer()
} else {
    SplClientException.ProtocolInvalid(context)
}

private fun HttpStatusCode.isSuccess(): Boolean = value in HTTP_SUCCESS_START..HTTP_SUCCESS_END
