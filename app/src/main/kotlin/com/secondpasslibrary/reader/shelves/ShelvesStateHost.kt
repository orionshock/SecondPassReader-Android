package com.secondpasslibrary.reader.shelves

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.secondpasslibrary.reader.connection.ConnectionProfile

@Composable
internal fun ShelvesStateHost(
    profile: ConnectionProfile,
    onOpenDrawer: () -> Unit,
    onBookSelected: (ShelfBookNavigationRequest) -> Unit,
    onAuthenticationRejected: () -> Unit,
    viewModel: ShelvesViewModel = viewModel()
) {
    LaunchedEffect(profile.apiBaseUrl, profile.clientSessionId) { viewModel.initialize(profile) }
    LaunchedEffect(viewModel, onAuthenticationRejected) {
        viewModel.connectionEvents.collect { event ->
            when (event) {
                ShelvesConnectionEvent.AuthenticationRejected -> onAuthenticationRejected()
            }
        }
    }
    ShelvesScreen(viewModel, onOpenDrawer, onBookSelected)
}
