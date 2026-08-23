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
        val authority: AppSessionAuthority
    ) : AppSessionState
}

internal sealed interface AppSessionAuthority {
    data object Restoring : AppSessionAuthority

    data class TransientFailure(val message: String) : AppSessionAuthority

    data class Verified(val context: AuthenticatedContext) : AppSessionAuthority
}

internal val AppSessionState.AccountShell.authenticatedFeatureContext: AuthenticatedContext?
    get() = (authority as? AppSessionAuthority.Verified)?.context
