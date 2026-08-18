package com.secondpasslibrary.reader.bookdetail

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.secondpasslibrary.reader.connection.ConnectionProfile

@Composable
internal fun BookDetailStateHost(
    profile: ConnectionProfile,
    bookId: String,
    onBack: () -> Unit,
    onNavigation: (BookDetailNavigationIntent) -> Unit,
    onAuthenticationRejected: () -> Unit,
    viewModel: BookDetailViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)
    LaunchedEffect(profile.apiBaseUrl, profile.clientSessionId, bookId) {
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
        onBack = onBack,
        onRetry = viewModel::retry,
        onAuthorSelected = { onNavigation(BookDetailNavigationIntent.Author(it)) },
        onSeriesSelected = { onNavigation(BookDetailNavigationIntent.Series(it)) },
        onTagSelected = { id, slug -> onNavigation(BookDetailNavigationIntent.Tag(id, slug)) }
    )
}
