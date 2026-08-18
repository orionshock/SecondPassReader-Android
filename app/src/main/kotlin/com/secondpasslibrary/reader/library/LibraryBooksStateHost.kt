package com.secondpasslibrary.reader.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.secondpasslibrary.reader.connection.ConnectionProfile

@Composable
internal fun LibraryBooksStateHost(
    profile: ConnectionProfile,
    entry: LibraryBooksEntry,
    advancedGroupsEnabled: Boolean,
    onAuthenticationRejected: () -> Unit,
    viewModel: LibraryBooksViewModel = viewModel()
) {
    LaunchedEffect(profile.apiBaseUrl, profile.clientSessionId, entry, advancedGroupsEnabled) {
        when (entry) {
            LibraryBooksEntry.Browse ->
                viewModel.initializeBrowse(profile, advancedGroupsEnabled)

            is LibraryBooksEntry.BroadSearch ->
                viewModel.initializeBroadSearch(profile, entry.query, advancedGroupsEnabled)
        }
    }
    LaunchedEffect(viewModel, onAuthenticationRejected) {
        viewModel.connectionEvents.collect { event ->
            when (event) {
                LibraryBooksConnectionEvent.AuthenticationRejected -> onAuthenticationRejected()
            }
        }
    }
    LibraryBooksScreen(viewModel)
}
