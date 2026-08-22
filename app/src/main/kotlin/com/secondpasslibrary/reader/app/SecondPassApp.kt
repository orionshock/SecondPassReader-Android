package com.secondpasslibrary.reader.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.secondpasslibrary.reader.app.shell.AuthenticatedAppShell
import com.secondpasslibrary.reader.connection.ConnectionScreen
import com.secondpasslibrary.reader.connection.ConnectionScreenActions
import com.secondpasslibrary.reader.connection.ConnectionUiState
import com.secondpasslibrary.reader.connection.ConnectionViewModel

@Composable
fun SecondPassApp(
    modifier: Modifier = Modifier,
    connectionViewModel: ConnectionViewModel = viewModel()
) {
    val state by connectionViewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        connectionViewModel.pairingForegrounded()
    }
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        when (val currentState = state) {
            is ConnectionUiState.Linked ->
                AuthenticatedAppShell(
                    currentState.profile,
                    currentState.context,
                    connectionViewModel.onAuthenticatedRequestRejected
                )

            else ->
                ConnectionScreen(
                    currentState,
                    ConnectionScreenActions(
                        updateServerUrl = connectionViewModel::updateServerUrl,
                        verifyServer = connectionViewModel::verifyServer,
                        updateClientName = connectionViewModel::updateClientName,
                        beginPairing = connectionViewModel::beginPairing,
                        abandonPairing = connectionViewModel::abandonPairing,
                        retryProfilePersistence = connectionViewModel::retryProfilePersistence,
                        retryStoredVerification = connectionViewModel::retryStoredVerification,
                        retryRestore = connectionViewModel::retryRestore,
                        forgetLocalConnection = connectionViewModel::forgetLocalConnection
                    )
                )
        }
    }
}
