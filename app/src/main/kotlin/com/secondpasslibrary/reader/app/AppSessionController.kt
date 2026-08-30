package com.secondpasslibrary.reader.app

import com.secondpasslibrary.reader.connection.ConnectionUiState
import com.secondpasslibrary.reader.connection.LocalAccountContext
import com.secondpasslibrary.reader.home.HomeAccountScope
import com.secondpasslibrary.reader.home.HomeProjectionRepository
import com.secondpasslibrary.reader.home.HomeRefreshAvailability
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal class AppSessionController(
    private val homeRepository: HomeProjectionRepository,
    private val scope: CoroutineScope
) {
    private val mutableState = MutableStateFlow<AppSessionState>(AppSessionState.Resolving)
    val state: StateFlow<AppSessionState> = mutableState.asStateFlow()

    private var connectionState: ConnectionUiState = ConnectionUiState.Restoring
    private var localAccount: LocalAccountContext? = null
    private var evaluatedAccount: LocalAccountContext? = null
    private var cachedHomeEligible: Boolean? = null
    private var eligibilityLoad: Job? = null
    private var lastVerifiedContext: com.secondpasslibrary.client.AuthenticatedContext? = null
    private var lastVerifiedAccount: Pair<String, String>? = null

    fun updateConnection(connection: ConnectionUiState, localAccount: LocalAccountContext?) {
        connectionState = connection
        this.localAccount = localAccount
        when (connection) {
            is ConnectionUiState.Linked -> publishVerifiedShell(connection)

            ConnectionUiState.Restoring,
            is ConnectionUiState.RestoreProblem,
            is ConnectionUiState.AuthenticationRequired,
            is ConnectionUiState.VerifyingServer,
            is ConnectionUiState.ServerConfirmed,
            is ConnectionUiState.StartingPairing,
            is ConnectionUiState.WaitingForApproval,
            is ConnectionUiState.CompletingPairing,
            is ConnectionUiState.PersistenceRecovery,
            is ConnectionUiState.StoredCredentialProblem,
            is ConnectionUiState.TerminalPairingProblem -> resolveCachedShell(localAccount)

            else -> publishConnectionRequired(connection)
        }
    }

    fun updateHomeRefreshAvailability(availability: HomeRefreshAvailability) {
        val shell = mutableState.value as? AppSessionState.AccountShell ?: return
        if (shell.authority !is AppSessionAuthority.Verified) return
        val appAvailability =
            when (availability) {
                HomeRefreshAvailability.REFRESHING -> AppAvailability.Syncing

                HomeRefreshAvailability.REACHABLE -> AppAvailability.Online

                HomeRefreshAvailability.UNREACHABLE ->
                    AppAvailability.Offline(AppAvailabilityReason.UNREACHABLE)
            }
        if (shell.availability != appAvailability) {
            mutableState.value = shell.copy(availability = appAvailability)
        }
    }

    private fun publishVerifiedShell(linked: ConnectionUiState.Linked) {
        eligibilityLoad?.cancel()
        lastVerifiedContext = linked.context
        lastVerifiedAccount = linked.profile.serverOrigin to linked.context.currentUser.profileId
        mutableState.value =
            AppSessionState.AccountShell(
                profile = linked.profile,
                profileId = linked.context.currentUser.profileId,
                authority = AppSessionAuthority.Verified(linked.context)
            )
    }

    private fun resolveCachedShell(account: LocalAccountContext?) {
        if (account == null) {
            mutableState.value =
                if (connectionState == ConnectionUiState.Restoring) {
                    AppSessionState.Resolving
                } else {
                    AppSessionState.ConnectionRequired(connectionState)
                }
            return
        }
        if (account == evaluatedAccount && cachedHomeEligible != null) {
            publishCachedShell(account, checkNotNull(cachedHomeEligible))
            return
        }

        eligibilityLoad?.cancel()
        evaluatedAccount = account
        cachedHomeEligible = null
        mutableState.value = AppSessionState.Resolving
        eligibilityLoad = scope.launch {
            val eligible =
                try {
                    homeRepository.hasCachedProjection(
                        HomeAccountScope(
                            account.profile.serverOrigin,
                            account.persistedAccount.profileId
                        )
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    false
                }
            if (account != localAccount ||
                connectionState is ConnectionUiState.Linked
            ) {
                return@launch
            }
            cachedHomeEligible = eligible
            publishCachedShell(account, eligible)
        }
    }

    private fun publishCachedShell(account: LocalAccountContext, eligible: Boolean) {
        val currentConnection = connectionState
        if (!eligible) {
            mutableState.value = AppSessionState.ConnectionRequired(currentConnection)
            return
        }
        val authority =
            when (currentConnection) {
                ConnectionUiState.Restoring -> AppSessionAuthority.Restoring

                is ConnectionUiState.RestoreProblem ->
                    AppSessionAuthority.TransientFailure(currentConnection.message)

                is ConnectionUiState.AuthenticationRequired ->
                    AppSessionAuthority.AuthenticationRequired(currentConnection.message)

                is ConnectionUiState.VerifyingServer,
                is ConnectionUiState.ServerConfirmed,
                is ConnectionUiState.StartingPairing,
                is ConnectionUiState.WaitingForApproval,
                is ConnectionUiState.CompletingPairing,
                is ConnectionUiState.PersistenceRecovery,
                is ConnectionUiState.StoredCredentialProblem,
                is ConnectionUiState.TerminalPairingProblem ->
                    AppSessionAuthority.Healing(currentConnection)

                else -> return publishConnectionRequired(currentConnection)
            }
        mutableState.value =
            AppSessionState.AccountShell(
                profile = account.profile,
                profileId = account.persistedAccount.profileId,
                authority = authority,
                retainedContext = lastVerifiedContext.takeIf {
                    lastVerifiedAccount ==
                        account.profile.serverOrigin to account.persistedAccount.profileId
                }
            )
    }

    private fun publishConnectionRequired(connection: ConnectionUiState) {
        eligibilityLoad?.cancel()
        lastVerifiedContext = null
        lastVerifiedAccount = null
        mutableState.value = AppSessionState.ConnectionRequired(connection)
    }
}
