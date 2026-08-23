package com.secondpasslibrary.reader.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.secondpasslibrary.reader.app.shell.AccountAppShell
import com.secondpasslibrary.reader.connection.ConnectionScreen
import com.secondpasslibrary.reader.connection.ConnectionScreenActions
import com.secondpasslibrary.reader.connection.ConnectionUiState
import com.secondpasslibrary.reader.connection.ConnectionViewModel

@Composable
fun SecondPassApp(
    modifier: Modifier = Modifier,
    connectionViewModel: ConnectionViewModel = viewModel(),
    appSessionViewModel: AppSessionViewModel = viewModel()
) {
    val connectionState by connectionViewModel.state.collectAsStateWithLifecycle()
    val localAccount by connectionViewModel.localAccountContext.collectAsStateWithLifecycle()
    val appSessionState by appSessionViewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(connectionState, localAccount) {
        appSessionViewModel.updateConnection(connectionState, localAccount)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        connectionViewModel.pairingForegrounded()
    }
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        when (val currentState = appSessionState) {
            AppSessionState.Resolving ->
                ConnectionScreen(
                    ConnectionUiState.Restoring,
                    connectionViewModel.actions()
                )

            is AppSessionState.ConnectionRequired ->
                ConnectionScreen(
                    currentState.connection,
                    connectionViewModel.actions()
                )

            is AppSessionState.AccountShell ->
                AccountAppShell(
                    session = currentState,
                    onRetryConnection = connectionViewModel::retryRestore,
                    onAuthenticationRejected =
                        connectionViewModel.onAuthenticatedRequestRejected
                )
        }
    }
}

private fun ConnectionViewModel.actions() = ConnectionScreenActions(
    updateServerUrl = ::updateServerUrl,
    verifyServer = ::verifyServer,
    updateClientName = ::updateClientName,
    beginPairing = ::beginPairing,
    abandonPairing = ::abandonPairing,
    retryProfilePersistence = ::retryProfilePersistence,
    retryStoredVerification = ::retryStoredVerification,
    retryRestore = ::retryRestore,
    forgetLocalConnection = ::forgetLocalConnection
)
