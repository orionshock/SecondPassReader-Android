package com.secondpasslibrary.reader.shelves

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.connection.authenticatedSessionIdentity

@Composable
internal fun ShelvesStateHost(
    profile: ConnectionProfile,
    serverMutationsAvailable: Boolean,
    onOpenDrawer: () -> Unit,
    onBookSelected: (ShelfBookNavigationRequest) -> Unit,
    onAuthenticationRejected: () -> Unit,
    initialDetail: ShelfDetailEntry? = null,
    onExitInitialDetail: (() -> Unit)? = null,
    viewModel: ShelvesViewModel = viewModel()
) {
    val connectionIdentity = profile.authenticatedSessionIdentity
    LaunchedEffect(connectionIdentity, initialDetail) {
        viewModel.initialize(profile)
        initialDetail?.let { viewModel.accept(ShelvesIntent.OpenShelf(it)) }
    }
    LaunchedEffect(serverMutationsAvailable) {
        if (!serverMutationsAvailable) viewModel.accept(ShelvesIntent.LeaveMutationSurfaces)
    }
    LaunchedEffect(viewModel, onAuthenticationRejected) {
        viewModel.connectionEvents.collect { event ->
            when (event) {
                ShelvesConnectionEvent.AuthenticationRejected -> onAuthenticationRejected()
            }
        }
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    ShelvesScreen(
        state,
        viewModel::accept,
        serverMutationsAvailable,
        onOpenDrawer,
        onBookSelected,
        onExitInitialDetail
    )
}
