package com.secondpasslibrary.reader.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.secondpasslibrary.reader.connection.ConnectionProfile

@Composable
internal fun LibraryStateHost(
    profile: ConnectionProfile,
    entry: LibraryBooksEntry,
    advancedGroupsEnabled: Boolean,
    onAuthenticationRejected: () -> Unit,
    viewModel: LibraryViewModel = viewModel()
) {
    LaunchedEffect(profile.apiBaseUrl, profile.clientSessionId, entry, advancedGroupsEnabled) {
        viewModel.initialize(profile, entry, advancedGroupsEnabled)
    }
    LaunchedEffect(viewModel, onAuthenticationRejected) {
        viewModel.connectionEvents.collect { event ->
            when (event) {
                LibraryConnectionEvent.AuthenticationRejected -> onAuthenticationRejected()
            }
        }
    }
    LibraryScreen(viewModel)
}
