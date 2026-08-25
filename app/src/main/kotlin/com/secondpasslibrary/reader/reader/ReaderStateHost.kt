package com.secondpasslibrary.reader.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.secondpasslibrary.reader.connection.ConnectionProfile

@Composable
internal fun ReaderStateHost(
    profile: ConnectionProfile,
    profileId: String,
    bookId: String,
    existingSessionId: String?,
    onBack: () -> Unit,
    onAuthenticationRejected: () -> Unit,
    viewModel: ReaderViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    DisposableEffect(viewModel) {
        viewModel.setAuthorityAvailable(true)
        onDispose { viewModel.setAuthorityAvailable(false) }
    }
    LaunchedEffect(profile, profileId, bookId, existingSessionId) {
        viewModel.initialize(profile, profileId, bookId, existingSessionId)
    }
    LaunchedEffect(viewModel, onAuthenticationRejected) {
        viewModel.connectionEvents.collect { event ->
            when (event) {
                ReaderConnectionEvent.AuthenticationRejected -> onAuthenticationRejected()
            }
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        viewModel.flushForBackground()
    }
    ReaderScreen(
        state = state,
        onBack = { viewModel.flushThenExit(onBack) },
        onRetry = viewModel::retry,
        onAppearanceChanged = viewModel::updateAppearance
    )
}
