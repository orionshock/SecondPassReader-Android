package com.secondpasslibrary.reader.home

import java.time.Instant

internal data class HomeProjectionContent<T>(val items: List<T>, val fetchedAt: Instant)

internal data class HomeProjectionState<T>(
    val content: HomeProjectionContent<T>?,
    val refresh: HomeProjectionRefresh
)

internal sealed interface HomeProjectionRefresh {
    data object Idle : HomeProjectionRefresh

    data object Refreshing : HomeProjectionRefresh

    data object Current : HomeProjectionRefresh

    data class Failed(val reason: HomeProjectionFailure) : HomeProjectionRefresh
}

internal enum class HomeProjectionFailure {
    Unreachable,
    AuthenticationRejected,
    ProtocolInvalid,
    Other
}
