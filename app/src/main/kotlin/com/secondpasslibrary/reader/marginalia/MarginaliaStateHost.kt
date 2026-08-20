package com.secondpasslibrary.reader.marginalia

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.secondpasslibrary.reader.connection.ConnectionProfile

@Composable
internal fun MarginaliaStateHost(
    profile: ConnectionProfile,
    onOpenDrawer: () -> Unit,
    onAuthenticationRejected: () -> Unit,
    viewModel: MarginaliaViewModel = viewModel()
) {
    LaunchedEffect(profile.apiBaseUrl, profile.clientSessionId) { viewModel.initialize(profile) }
    LaunchedEffect(viewModel, onAuthenticationRejected) {
        viewModel.connectionEvents.collect { event ->
            when (event) {
                MarginaliaConnectionEvent.AuthenticationRejected -> onAuthenticationRejected()
            }
        }
    }
    MarginaliaScreen(viewModel, onOpenDrawer)
}
