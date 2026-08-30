package com.secondpasslibrary.reader.app

/** App interpretation of whether authenticated server work is currently usable. */
internal sealed interface AppAvailability {
    data object Syncing : AppAvailability

    data object Online : AppAvailability

    data class Offline(val reason: AppAvailabilityReason) : AppAvailability
}

internal enum class AppAvailabilityReason {
    UNREACHABLE,
    AUTHENTICATION_REQUIRED
}
