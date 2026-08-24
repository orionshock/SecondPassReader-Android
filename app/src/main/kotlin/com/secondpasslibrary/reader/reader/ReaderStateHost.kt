package com.secondpasslibrary.reader.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.secondpasslibrary.reader.connection.ConnectionProfile

@Composable
internal fun ReaderStateHost(
    profile: ConnectionProfile,
    profileId: String,
    bookId: String,
    onBack: () -> Unit,
    onAuthenticationRejected: () -> Unit,
    viewModel: ReaderViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(profile, profileId, bookId) {
        viewModel.initialize(profile, profileId, bookId)
    }
    LaunchedEffect(viewModel, onAuthenticationRejected) {
        viewModel.connectionEvents.collect { event ->
            when (event) {
                ReaderConnectionEvent.AuthenticationRejected -> onAuthenticationRejected()
            }
        }
    }
    ReaderScreen(state, onBack, viewModel::retry)
}
