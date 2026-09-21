package com.secondpasslibrary.reader.app

import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
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
@Suppress("LongMethod") // Root composition wires the existing shell and connection owner.
fun SecondPassApp(
    modifier: Modifier = Modifier,
    connectionViewModel: ConnectionViewModel = viewModel(),
    appSessionViewModel: AppSessionViewModel = viewModel()
) {
    val requestLanDiscoveryAccess =
        rememberLanDiscoveryPermission(connectionViewModel::setLanDiscoveryEnabled)
    val connectionState by connectionViewModel.state.collectAsStateWithLifecycle()
    val localAccount by connectionViewModel.localAccountContext.collectAsStateWithLifecycle()
    val lifecycleActionState by
        connectionViewModel.lifecycleActionState.collectAsStateWithLifecycle()
    val appSessionState by appSessionViewModel.state.collectAsStateWithLifecycle()
    val syncOutcomeNotice by appSessionViewModel.syncOutcomeNotice.collectAsStateWithLifecycle()
    LaunchedEffect(connectionState, localAccount) {
        appSessionViewModel.updateConnection(connectionState, localAccount)
    }
    ObserveAppConnectionEvents(appSessionViewModel, connectionViewModel)
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        connectionViewModel.pairingForegrounded()
        connectionViewModel.retryUnreachableOnForeground()
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
                    connectionViewModel.screenActions
                )

            is AppSessionState.ConnectionRequired ->
                ConnectionScreen(
                    currentState.connection,
                    connectionViewModel.screenActions,
                    requestLanDiscoveryAccess
                )

            is AppSessionState.AccountShell ->
                key(currentState.profile.serverId, currentState.profileId) {
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
                        onCheckConnection = connectionViewModel::checkConnectionNow,
                        onWorkOffline = connectionViewModel::workOffline,
                        onReconnect = connectionViewModel::reconnect,
                        syncOutcomeNotice = syncOutcomeNotice,
                        onSyncOutcomeNoticeAcknowledged =
                            appSessionViewModel::acknowledgeSyncOutcomeNotice
                    )
                }
        }
    }
}

@Composable
private fun ObserveAppConnectionEvents(
    appSessionViewModel: AppSessionViewModel,
    connectionViewModel: ConnectionViewModel
) {
    LaunchedEffect(appSessionViewModel, connectionViewModel) {
        appSessionViewModel.connectionEvents.collect { event ->
            when (event) {
                AppConnectionEvent.AuthenticationRejected ->
                    connectionViewModel.onAuthenticatedRequestRejected()
            }
        }
    }
}

@Composable
private fun rememberLanDiscoveryPermission(onChanged: (Boolean) -> Unit): (() -> Unit)? {
    val context = LocalContext.current
    fun currentAccess() = hasLocalNetworkAccess(
        ContextCompat.checkSelfPermission(context, ACCESS_LOCAL_NETWORK_PERMISSION)
    )
    var granted by remember { mutableStateOf(currentAccess()) }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { result ->
            granted = result
        }
    LaunchedEffect(granted) { onChanged(granted) }
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        granted = currentAccess()
        onChanged(granted)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { onChanged(false) }
    return if (Build.VERSION.SDK_INT >= ANDROID_17_API_LEVEL && !granted) {
        { launcher.launch(ACCESS_LOCAL_NETWORK_PERMISSION) }
    } else {
        null
    }
}

private fun hasLocalNetworkAccess(permissionResult: Int): Boolean =
    Build.VERSION.SDK_INT < ANDROID_17_API_LEVEL ||
        permissionResult == PackageManager.PERMISSION_GRANTED

private const val ANDROID_17_API_LEVEL = 37
private const val ACCESS_LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"
