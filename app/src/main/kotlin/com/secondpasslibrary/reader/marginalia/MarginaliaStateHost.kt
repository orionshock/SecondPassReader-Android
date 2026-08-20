package com.secondpasslibrary.reader.marginalia

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.secondpasslibrary.reader.connection.ConnectionProfile

@Composable
internal fun MarginaliaStateHost(
    profile: ConnectionProfile,
    initialContext: MarginaliaHistoryContext = MarginaliaHistoryContext.Global,
    onOpenDrawer: () -> Unit,
    onBackFromHistory: (() -> Unit)? = null,
    onAuthenticationRejected: () -> Unit,
    viewModel: MarginaliaViewModel = viewModel()
) {
    LaunchedEffect(profile.apiBaseUrl, profile.clientSessionId, initialContext) {
        viewModel.initialize(profile, initialContext)
    }
    LaunchedEffect(viewModel, onAuthenticationRejected) {
        viewModel.connectionEvents.collect { event ->
            when (event) {
                MarginaliaConnectionEvent.AuthenticationRejected -> onAuthenticationRejected()
            }
        }
    }
    MarginaliaScreen(viewModel, onOpenDrawer, onBackFromHistory)
}
