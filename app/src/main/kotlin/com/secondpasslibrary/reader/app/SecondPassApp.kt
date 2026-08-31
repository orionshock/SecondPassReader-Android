package com.secondpasslibrary.reader.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.secondpasslibrary.reader.app.shell.AccountAppShell
import com.secondpasslibrary.reader.connection.ConnectionScreen
import com.secondpasslibrary.reader.connection.ConnectionUiState
import com.secondpasslibrary.reader.connection.ConnectionViewModel
import com.secondpasslibrary.reader.home.HomeRefreshAvailability

@Composable
fun SecondPassApp(
    modifier: Modifier = Modifier,
    connectionViewModel: ConnectionViewModel = viewModel(),
    appSessionViewModel: AppSessionViewModel = viewModel()
) {
    val connectionState by connectionViewModel.state.collectAsStateWithLifecycle()
    val localAccount by connectionViewModel.localAccountContext.collectAsStateWithLifecycle()
    val lifecycleActionState by
        connectionViewModel.lifecycleActionState.collectAsStateWithLifecycle()
    val appSessionState by appSessionViewModel.state.collectAsStateWithLifecycle()
    val syncOutcomeNotice by appSessionViewModel.syncOutcomeNotice.collectAsStateWithLifecycle()
    LaunchedEffect(connectionState, localAccount) {
        appSessionViewModel.updateConnection(connectionState, localAccount)
    }
    LaunchedEffect(appSessionViewModel, connectionViewModel) {
        appSessionViewModel.connectionEvents.collect { event ->
            when (event) {
                AppConnectionEvent.AuthenticationRejected ->
                    connectionViewModel.onAuthenticatedRequestRejected()
            }
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { connectionViewModel.pairingForegrounded() }
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        when (val currentState = appSessionState) {
            AppSessionState.Resolving ->
                ConnectionScreen(
                    ConnectionUiState.Restoring,
                    connectionViewModel.screenActions
                )

            is AppSessionState.ConnectionRequired ->
                ConnectionScreen(
                    currentState.connection,
                    connectionViewModel.screenActions
                )

            is AppSessionState.AccountShell ->
                key(currentState.profile.serverOrigin, currentState.profileId) {
                    AccountAppShell(
                        session = currentState,
                        connectionActions = connectionViewModel.screenActions,
                        lifecycleActionState = lifecycleActionState,
                        lifecycleActions = connectionViewModel.lifecycleActions,
                        onAuthenticationRejected =
                            connectionViewModel.onAuthenticatedRequestRejected,
                        onHomeRefreshAvailabilityChanged =
                            { availability ->
                                appSessionViewModel.updateHomeRefreshAvailability(availability)
                                if (availability == HomeRefreshAvailability.UNREACHABLE) {
                                    connectionViewModel.onAuthenticatedRequestUnreachable()
                                }
                            },
                        syncOutcomeNotice = syncOutcomeNotice,
                        onSyncOutcomeNoticeAcknowledged =
                            appSessionViewModel::acknowledgeSyncOutcomeNotice
                    )
                }
        }
    }
}
