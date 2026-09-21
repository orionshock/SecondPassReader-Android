package com.secondpasslibrary.reader.connection

import com.secondpasslibrary.client.AuthenticatedContext
import com.secondpasslibrary.client.DiscoveredServer
import com.secondpasslibrary.reader.connection.discovery.ConnectionLibrarySuggestion

sealed interface ConnectionUiState {
    data object Restoring : ConnectionUiState

    data class ServerEntry(
        val serverUrl: String = "",
        val message: String? = null,
        val suggestions: List<ConnectionLibrarySuggestion> = emptyList()
    ) : ConnectionUiState

    data class VerifyingServer(val serverUrl: String) : ConnectionUiState

    data class ServerConfirmed(val server: DiscoveredServer, val clientName: String) :
        ConnectionUiState

    data class StartingPairing(val server: DiscoveredServer, val clientName: String) :
        ConnectionUiState

    data class WaitingForApproval(
        val serverName: String,
        val clientName: String,
        val code: String,
        val authorizeUrl: String,
        val expiresAt: String,
        val statusText: String = "Waiting for approval"
    ) : ConnectionUiState

    data class CompletingPairing(val serverName: String, val code: String) : ConnectionUiState

    data class PersistenceRecovery(val profile: ConnectionProfile, val message: String) :
        ConnectionUiState

    data class StoredCredentialProblem(
        val profile: ConnectionProfile,
        val message: String,
        val retryable: Boolean
    ) : ConnectionUiState

    data class RestoreProblem(val profile: ConnectionProfile, val message: String) :
        ConnectionUiState

    data class WorkingOffline(val profile: ConnectionProfile) : ConnectionUiState

    data class CheckingConnection(val profile: ConnectionProfile) : ConnectionUiState

    data class AuthenticationRequired(val profile: ConnectionProfile, val message: String) :
        ConnectionUiState

    data class LocalStorageProblem(val message: String) : ConnectionUiState

    data class Linked(
        val profile: ConnectionProfile,
        val context: AuthenticatedContext,
        val reachability: ServerReachability = ServerReachability.REACHABLE
    ) : ConnectionUiState

    data class TerminalPairingProblem(val message: String) : ConnectionUiState
}
