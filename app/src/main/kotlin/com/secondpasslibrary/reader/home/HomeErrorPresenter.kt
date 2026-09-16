package com.secondpasslibrary.reader.home

internal object HomeErrorPresenter {
    fun message(failure: HomeProjectionFailure): String = when (failure) {
        HomeProjectionFailure.AuthenticationRejected ->
            "Your connection is no longer authorized. Repair it in Settings."

        HomeProjectionFailure.Unreachable ->
            "Couldn’t reach the Library. Check your connection and retry."

        HomeProjectionFailure.ProtocolInvalid ->
            "Couldn’t read the Library response. Retry or repair the connection in Settings."

        HomeProjectionFailure.Other -> "Couldn’t load this section. Retry."
    }
}
