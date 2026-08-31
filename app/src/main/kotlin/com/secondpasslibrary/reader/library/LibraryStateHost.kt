package com.secondpasslibrary.reader.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.secondpasslibrary.reader.app.AppAvailability
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.connection.authenticatedConnectionIdentity
import com.secondpasslibrary.reader.design.book.BookCardAction
import com.secondpasslibrary.reader.library.books.LibraryBooksEntry

@Composable
internal fun LibraryStateHost(
    profile: ConnectionProfile,
    profileId: String,
    availability: AppAvailability,
    entry: LibraryBooksEntry,
    advancedGroupsEnabled: Boolean,
    onAuthenticationRejected: () -> Unit,
    onOpenDrawer: () -> Unit,
    onBookSelected: (String) -> Unit,
    onBookAction: (BookCardAction) -> Unit,
    externalNavigation: LibraryExternalNavigation? = null,
    viewModel: LibraryViewModel = viewModel()
) {
    val connectionIdentity = profile.authenticatedConnectionIdentity
    LaunchedEffect(
        connectionIdentity,
        profileId,
        availability,
        entry,
        advancedGroupsEnabled,
        externalNavigation
    ) {
        if (availability is AppAvailability.Offline) {
            viewModel.initializeOffline(profile, profileId, entry)
        } else {
            viewModel.initialize(profile, entry, advancedGroupsEnabled)
            externalNavigation?.let(viewModel::navigateTo)
        }
    }
    LaunchedEffect(viewModel, onAuthenticationRejected) {
        viewModel.connectionEvents.collect { event ->
            when (event) {
                LibraryConnectionEvent.AuthenticationRejected -> onAuthenticationRejected()
            }
        }
    }
    LibraryScreen(viewModel, onOpenDrawer, onBookSelected, onBookAction)
}
