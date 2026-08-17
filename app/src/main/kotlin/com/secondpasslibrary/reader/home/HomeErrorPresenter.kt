package com.secondpasslibrary.reader.home

import com.secondpasslibrary.client.SplClientException

internal object HomeErrorPresenter {
    fun message(failure: Throwable): String = when (failure) {
        is SplClientException.AuthenticationRejected ->
            "The stored credential was rejected. Reconnect this device from Settings."

        is SplClientException.ServerUnreachable ->
            "The library could not be reached."

        is SplClientException.AuthenticatedRequestFailed ->
            "The library rejected this request."

        is SplClientException.ProtocolInvalid ->
            "The library returned data this client could not understand."

        else -> "This section could not be loaded."
    }
}
