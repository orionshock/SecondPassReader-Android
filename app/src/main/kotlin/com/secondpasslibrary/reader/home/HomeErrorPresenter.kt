package com.secondpasslibrary.reader.home

internal object HomeErrorPresenter {
    fun message(failure: HomeProjectionFailure): String = when (failure) {
        HomeProjectionFailure.AuthenticationRejected ->
            "The stored credential was rejected. Reconnect this device from Settings."

        HomeProjectionFailure.Unreachable ->
            "The library could not be reached."

        HomeProjectionFailure.ProtocolInvalid ->
            "The library returned data this client could not understand."

        HomeProjectionFailure.Other -> "This section could not be loaded."
    }
}
