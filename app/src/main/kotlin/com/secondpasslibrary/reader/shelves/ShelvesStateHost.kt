package com.secondpasslibrary.reader.shelves

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.connection.authenticatedConnectionIdentity

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
    val connectionIdentity = profile.authenticatedConnectionIdentity
    LaunchedEffect(connectionIdentity, initialDetail) {
        viewModel.initialize(profile)
        initialDetail?.let(viewModel::openShelf)
    }
    LaunchedEffect(serverMutationsAvailable) {
        if (!serverMutationsAvailable) viewModel.leaveMutationSurfaces()
    }
    LaunchedEffect(viewModel, onAuthenticationRejected) {
        viewModel.connectionEvents.collect { event ->
            when (event) {
                ShelvesConnectionEvent.AuthenticationRejected -> onAuthenticationRejected()
            }
        }
    }
    ShelvesScreen(
        viewModel,
        serverMutationsAvailable,
        onOpenDrawer,
        onBookSelected,
        onExitInitialDetail
    )
}
