package com.secondpasslibrary.reader.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.secondpasslibrary.reader.app.AppAvailability
import com.secondpasslibrary.reader.app.storage.BookOfflineActionsViewModel
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.connection.authenticatedSessionIdentity
import com.secondpasslibrary.reader.design.book.BookCardAction
import com.secondpasslibrary.reader.design.book.BookOfflineActionDialogs
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
    viewModel: LibraryViewModel = viewModel(),
    offlineActions: BookOfflineActionsViewModel = viewModel()
) {
    val libraryState by viewModel.state.collectAsStateWithLifecycle()
    val offlineState by offlineActions.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { offlineActions.refresh() }
    val connectionIdentity = profile.authenticatedSessionIdentity
    LaunchedEffect(connectionIdentity, profileId, availability) {
        offlineActions.initialize(profile, profileId, availability)
    }
    LaunchedEffect(libraryState.result.booksStateOrNull()?.books) {
        offlineActions.observeBooks(
            libraryState.result.booksStateOrNull()?.books.orEmpty().associate { it.id to it.cover }
        )
    }
    LaunchedEffect(offlineActions, availability) {
        if (availability is AppAvailability.Offline) {
            offlineActions.changes.collect { viewModel.refresh() }
        }
    }
    LaunchedEffect(offlineActions, onAuthenticationRejected) {
        offlineActions.authenticationRejected.collect { onAuthenticationRejected() }
    }
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
    LibraryScreen(viewModel, onOpenDrawer, onBookSelected, { action ->
        when (action) {
            is BookCardAction.MakeAvailableOffline -> offlineActions.makeAvailable(action.bookId)
            is BookCardAction.RemoveDownload -> offlineActions.requestRemoval(action.bookId)
            else -> onBookAction(action)
        }
    }, offlineState)
    BookOfflineActionDialogs(
        pendingRemoval = offlineState.pendingRemoval != null,
        error = offlineState.error,
        onDismissRemoval = offlineActions::dismissRemoval,
        onConfirmRemoval = offlineActions::confirmRemoval,
        onDismissError = offlineActions::dismissError
    )
}
