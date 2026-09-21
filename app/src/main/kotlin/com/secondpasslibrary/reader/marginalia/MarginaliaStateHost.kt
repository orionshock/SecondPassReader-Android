package com.secondpasslibrary.reader.marginalia

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.connection.authenticatedSessionIdentity

@Composable
internal fun MarginaliaStateHost(
    profile: ConnectionProfile,
    initialContext: MarginaliaHistoryContext = MarginaliaHistoryContext.Global,
    detailEntry: ReadingSessionDetailEntry? = null,
    onOpenDrawer: () -> Unit,
    onBackFromHistory: (() -> Unit)? = null,
    onBackFromDetail: (() -> Unit)? = null,
    onNavigation: (MarginaliaExternalNavigationIntent) -> Unit,
    onAuthenticationRejected: () -> Unit,
    viewModel: MarginaliaViewModel = viewModel()
) {
    val connectionIdentity = profile.authenticatedSessionIdentity
    LaunchedEffect(connectionIdentity, initialContext, detailEntry) {
        viewModel.initialize(profile, initialContext, detailEntry)
    }
    LaunchedEffect(viewModel, onAuthenticationRejected) {
        viewModel.connectionEvents.collect { event ->
            when (event) {
                MarginaliaConnectionEvent.AuthenticationRejected -> onAuthenticationRejected()
            }
        }
    }
    LaunchedEffect(viewModel, onNavigation) {
        viewModel.navigation.collect(onNavigation)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    MarginaliaScreen(
        state,
        viewModel::accept,
        onOpenDrawer,
        onBackFromHistory,
        onBackFromDetail
    )
}
