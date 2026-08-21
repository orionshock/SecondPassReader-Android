package com.secondpasslibrary.reader.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.connection.authenticatedConnectionIdentity

@Composable
internal fun LibraryStateHost(
    profile: ConnectionProfile,
    entry: LibraryBooksEntry,
    advancedGroupsEnabled: Boolean,
    onAuthenticationRejected: () -> Unit,
    onBookSelected: (String) -> Unit,
    externalNavigation: LibraryExternalNavigation? = null,
    viewModel: LibraryViewModel = viewModel()
) {
    val connectionIdentity = profile.authenticatedConnectionIdentity
    LaunchedEffect(
        connectionIdentity,
        entry,
        advancedGroupsEnabled,
        externalNavigation
    ) {
        viewModel.initialize(profile, entry, advancedGroupsEnabled)
        externalNavigation?.let(viewModel::navigateTo)
    }
    LaunchedEffect(viewModel, onAuthenticationRejected) {
        viewModel.connectionEvents.collect { event ->
            when (event) {
                LibraryConnectionEvent.AuthenticationRejected -> onAuthenticationRejected()
            }
        }
    }
    LibraryScreen(viewModel, onBookSelected)
}
