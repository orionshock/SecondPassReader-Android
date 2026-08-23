package com.secondpasslibrary.reader.bookdetail

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.secondpasslibrary.reader.bookdetail.shelfpicker.BookShelfPickerDialog
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.connection.authenticatedConnectionIdentity

@Composable
internal fun BookDetailStateHost(
    profile: ConnectionProfile,
    bookId: String,
    appBarContext: String,
    onBack: () -> Unit,
    onNavigation: (BookDetailNavigationIntent) -> Unit,
    onAuthenticationRejected: () -> Unit,
    viewModel: BookDetailViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val shelfPickerState by viewModel.shelfPickerState.collectAsStateWithLifecycle()
    val connectionIdentity = profile.authenticatedConnectionIdentity
    BackHandler(onBack = onBack)
    LaunchedEffect(connectionIdentity, bookId) {
        viewModel.initialize(profile, bookId)
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
        onReadingSessions = {
            onNavigation(BookDetailNavigationIntent.ReadingSessions(bookId))
        },
        onAddToShelf = viewModel::openShelfPicker
    )
    if (shelfPickerState.open) {
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
