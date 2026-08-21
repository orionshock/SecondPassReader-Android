package com.secondpasslibrary.reader.marginalia

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.connection.authenticatedConnectionIdentity

@Composable
internal fun MarginaliaStateHost(
    profile: ConnectionProfile,
    initialContext: MarginaliaHistoryContext = MarginaliaHistoryContext.Global,
    onOpenDrawer: () -> Unit,
    onBackFromHistory: (() -> Unit)? = null,
    onAuthenticationRejected: () -> Unit,
    viewModel: MarginaliaViewModel = viewModel()
) {
    val connectionIdentity = profile.authenticatedConnectionIdentity
    LaunchedEffect(connectionIdentity, initialContext) {
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
