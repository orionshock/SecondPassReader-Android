package com.secondpasslibrary.reader.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.secondpasslibrary.client.AuthenticatedContext
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.design.icons.AppIcon
import com.secondpasslibrary.reader.design.icons.AppIconGraphic
import kotlinx.coroutines.flow.collectLatest

@Composable
internal fun HomeScreen(
    profile: ConnectionProfile,
    profileId: String,
    verifiedContext: AuthenticatedContext?,
    onNavigation: (HomeNavigationIntent) -> Unit,
    onAuthenticationRejected: () -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val accountScope = HomeAccountScope(profile.serverOrigin, profileId)
    val currentOnNavigation by rememberUpdatedState(onNavigation)
    val currentOnAuthenticationRejected by rememberUpdatedState(onAuthenticationRejected)
    LaunchedEffect(accountScope, verifiedContext) {
        viewModel.initializeCached(accountScope)
        if (verifiedContext != null) {
            viewModel.provideVerifiedAuthority(profile, profileId)
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.navigation.collectLatest { currentOnNavigation(it) }
    }
    LaunchedEffect(viewModel) {
        viewModel.connectionEvents.collectLatest { event ->
            when (event) {
                HomeConnectionEvent.AuthenticationRejected -> currentOnAuthenticationRejected()
            }
        }
    }
    HomeContent(
        state = state,
        onShowClosedChanged = viewModel::setShowClosedSessions,
        onRetryRecentReading = viewModel::retryRecentReading,
        onRetryShelves = viewModel::retryShelves,
        onSearch = viewModel::searchLibrary,
        onReadingHistoryAction = viewModel::navigate,
        onViewAllSessions = viewModel::viewAllSessions,
        onOpenShelves = viewModel::openShelves
    )
}

@Composable
private fun HomeContent(
    state: HomeUiState,
    onShowClosedChanged: (Boolean) -> Unit,
    onRetryRecentReading: () -> Unit,
    onRetryShelves: () -> Unit,
    onSearch: (String) -> Unit,
    onReadingHistoryAction: (HomeNavigationIntent) -> Unit,
    onViewAllSessions: () -> Unit,
    onOpenShelves: () -> Unit
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        GlobalLibrarySearch(onSearch, Modifier.padding(horizontal = 24.dp))
        ReadingHistorySection(
            state = state.recentReading,
            showClosed = state.showClosedSessions,
            onShowClosedChanged = onShowClosedChanged,
            onRetry = onRetryRecentReading,
            onContextAction = onReadingHistoryAction,
            onViewAll = onViewAllSessions
        )
        ShelvesSection(
            state = state.shelves,
            onRetry = onRetryShelves,
            onOpenShelves = onOpenShelves,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
    }
}

@Composable
private fun GlobalLibrarySearch(onSearch: (String) -> Unit, modifier: Modifier = Modifier) {
    var query by rememberSaveable { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    val submit = {
        keyboard?.hide()
        onSearch(query)
    }
    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        modifier =
            modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Global library search" },
        placeholder = { Text("Search books, authors, series, publishers, or tags") },
        leadingIcon = { AppIconGraphic(AppIcon.Search, null) },
        trailingIcon = {
            IconButton(onClick = submit) {
                AppIconGraphic(AppIcon.Search, "Search library")
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { submit() }),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)
    )
}
