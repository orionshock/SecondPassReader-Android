package com.secondpasslibrary.reader.app

import com.secondpasslibrary.client.AuthenticatedContext
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.connection.ConnectionUiState

internal sealed interface AppSessionState {
    data object Resolving : AppSessionState

    data class ConnectionRequired(val connection: ConnectionUiState) : AppSessionState

    data class AccountShell(
        val profile: ConnectionProfile,
        val profileId: String,
        val authority: AppSessionAuthority,
        val availability: AppAvailability = authority.toAvailability(),
        val retainedContext: AuthenticatedContext? = null
    ) : AppSessionState
}

internal sealed interface AppSessionAuthority {
    data object Restoring : AppSessionAuthority

    data class TransientFailure(val message: String) : AppSessionAuthority

    data class AuthenticationRequired(val message: String) : AppSessionAuthority

    data class Healing(val connection: ConnectionUiState) : AppSessionAuthority

    data class Verified(val context: AuthenticatedContext) : AppSessionAuthority
}

internal val AppSessionState.AccountShell.authenticatedFeatureContext: AuthenticatedContext?
    get() = (authority as? AppSessionAuthority.Verified)?.context ?: retainedContext

private fun AppSessionAuthority.toAvailability(): AppAvailability = when (this) {
    AppSessionAuthority.Restoring,
    is AppSessionAuthority.Healing -> AppAvailability.Syncing

    is AppSessionAuthority.Verified -> AppAvailability.Online

    is AppSessionAuthority.TransientFailure ->
        AppAvailability.Offline(AppAvailabilityReason.UNREACHABLE)

    is AppSessionAuthority.AuthenticationRequired ->
        AppAvailability.Offline(AppAvailabilityReason.AUTHENTICATION_REQUIRED)
}
