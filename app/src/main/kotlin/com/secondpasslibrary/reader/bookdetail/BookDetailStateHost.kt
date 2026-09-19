package com.secondpasslibrary.reader.bookdetail

import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.secondpasslibrary.reader.app.AppAvailability
import com.secondpasslibrary.reader.bookdetail.shelfpicker.BookShelfPickerDialog
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.connection.authenticatedConnectionIdentity

@Composable
@Suppress("LongMethod") // Selection, navigation, shelf picker, and bounded removal share one host.
internal fun BookDetailStateHost(
    profile: ConnectionProfile,
    profileId: String,
    availability: AppAvailability,
    serverMutationsAvailable: Boolean,
    bookId: String,
    appBarContext: String,
    onBack: () -> Unit,
    onNavigation: (BookDetailNavigationIntent) -> Unit,
    onAuthenticationRejected: () -> Unit,
    viewModel: BookDetailViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val shelfPickerState by viewModel.shelfPickerState.collectAsStateWithLifecycle()
    val offlineReadable by viewModel.offlineReadable.collectAsStateWithLifecycle()
    val offlineAction by viewModel.offlineAction.collectAsStateWithLifecycle()
    var confirmRemoval by remember(bookId) { mutableStateOf(false) }
    val connectionIdentity = profile.authenticatedConnectionIdentity
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshOfflineAvailability() }
    BackHandler(onBack = onBack)
    LaunchedEffect(connectionIdentity, profileId, availability, bookId) {
        viewModel.initialize(profile, profileId, availability, bookId)
    }
    LaunchedEffect(viewModel, onAuthenticationRejected) {
        viewModel.connectionEvents.collect { event ->
            when (event) {
                BookDetailConnectionEvent.AuthenticationRejected -> onAuthenticationRejected()
            }
        }
    }
    BookDetailScreen(
        state = state,
        appBarContext = appBarContext,
        onBack = onBack,
        onRetry = viewModel::retry,
        onAuthorSelected = { onNavigation(BookDetailNavigationIntent.Author(it)) },
        onSeriesSelected = { onNavigation(BookDetailNavigationIntent.Series(it)) },
        onTagSelected = { id, slug -> onNavigation(BookDetailNavigationIntent.Tag(id, slug)) },
        onReadBook = {
            onNavigation(BookDetailNavigationIntent.ReadBook(bookId, state.detail?.title))
        },
        onReadingSessions = {
            onNavigation(BookDetailNavigationIntent.ReadingSessions(bookId))
        },
        onAddToShelf = viewModel::openShelfPicker,
        readAvailable = offlineReadable,
        serverActionsAvailable = serverMutationsAvailable,
        offlineAction = offlineAction,
        onMakeAvailable = viewModel::makeAvailable,
        onRemoveDownload = { confirmRemoval = true }
    )
    if (confirmRemoval) {
        BookDownloadRemovalDialog(
            onDismiss = { confirmRemoval = false },
            onConfirm = {
                confirmRemoval = false
                viewModel.removeDownload()
            }
        )
    }
    if (shelfPickerState.open && serverMutationsAvailable) {
        BookShelfPickerDialog(
            state = shelfPickerState,
            onRetry = viewModel::retryShelfPicker,
            onAdd = viewModel::addToShelf,
            onManageShelves = {
                viewModel.dismissShelfPicker()
                onNavigation(BookDetailNavigationIntent.ManageShelves)
            },
            onDismiss = viewModel::dismissShelfPicker
        )
    }
}

@Composable
private fun BookDownloadRemovalDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remove download?") },
        text = {
            Text("Remove this Book from this device? Your reading progress and notes are kept.")
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Remove download") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
