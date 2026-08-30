package com.secondpasslibrary.reader.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.secondpasslibrary.client.AuthenticatedContext
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.design.components.InlineSearchField
import kotlinx.coroutines.flow.collectLatest

@Composable
internal fun HomeScreen(
    profile: ConnectionProfile,
    profileId: String,
    verifiedContext: AuthenticatedContext?,
    onNavigation: (HomeNavigationIntent) -> Unit,
    onAuthenticationRejected: () -> Unit,
    onRefreshAvailabilityChanged: (HomeRefreshAvailability) -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val accountScope = HomeAccountScope(profile.serverOrigin, profileId)
    val currentOnNavigation by rememberUpdatedState(onNavigation)
    val currentOnAuthenticationRejected by rememberUpdatedState(onAuthenticationRejected)
    val currentOnRefreshAvailabilityChanged by
        rememberUpdatedState(onRefreshAvailabilityChanged)
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
    LaunchedEffect(viewModel) {
        viewModel.refreshAvailability.collectLatest(currentOnRefreshAvailabilityChanged)
    }
    HomeContent(
        state = state,
        onShowClosedChanged = viewModel::setShowClosedSessions,
        onRetryRecentReading = viewModel::retryRecentReading,
        onRetryShelves = viewModel::retryShelves,
        onSearch = viewModel::searchLibrary,
        onReadingHistoryAction = viewModel::navigate,
        onViewAllSessions = viewModel::viewAllSessions,
        onOpenShelves = viewModel::openShelves,
        onShelfSelected = viewModel::navigate
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
    onOpenShelves: () -> Unit,
    onShelfSelected: (HomeNavigationIntent.OpenShelfDetail) -> Unit
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
            onPrimaryAction = { onReadingHistoryAction(it) },
            onContextAction = onReadingHistoryAction,
            onViewAll = onViewAllSessions
        )
        ShelvesSection(
            state = state.shelves,
            onRetry = onRetryShelves,
            onOpenShelves = onOpenShelves,
            onShelfSelected = onShelfSelected,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
    }
}

@Composable
private fun GlobalLibrarySearch(onSearch: (String) -> Unit, modifier: Modifier = Modifier) {
    var query by rememberSaveable { mutableStateOf("") }
    InlineSearchField(
        query = query,
        placeholder = "Search books, authors, series, publishers, or tags",
        contentDescription = "Global library search",
        onQueryChanged = { query = it },
        onSubmit = { onSearch(query) },
        modifier = modifier.fillMaxWidth()
    )
}
