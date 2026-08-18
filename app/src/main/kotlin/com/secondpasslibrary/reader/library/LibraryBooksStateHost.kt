package com.secondpasslibrary.reader.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.secondpasslibrary.reader.connection.ConnectionProfile

@Composable
internal fun LibraryBooksStateHost(
    profile: ConnectionProfile,
    entry: LibraryBooksEntry,
    onAuthenticationRejected: () -> Unit,
    viewModel: LibraryBooksViewModel = viewModel(),
    content: @Composable () -> Unit
) {
    LaunchedEffect(profile.apiBaseUrl, profile.clientSessionId, entry) {
        when (entry) {
            LibraryBooksEntry.Browse -> viewModel.initializeBrowse(profile)

            is LibraryBooksEntry.BroadSearch ->
                viewModel.initializeBroadSearch(profile, entry.query)
        }
    }
    LaunchedEffect(viewModel, onAuthenticationRejected) {
        viewModel.connectionEvents.collect { event ->
            when (event) {
                LibraryBooksConnectionEvent.AuthenticationRejected -> onAuthenticationRejected()
            }
        }
    }
    content()
}
