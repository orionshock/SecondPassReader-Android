package com.secondpasslibrary.reader.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Global Library Search", style = MaterialTheme.typography.titleLarge)
        Text(
            "Search books, authors, series, publishers, and tags across this library.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Title, author, series, publisher, or tag") },
                leadingIcon = { AppIconGraphic(AppIcon.Search, null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submit() })
            )
            Button(onClick = submit) {
                AppIconGraphic(AppIcon.Search, null)
                Text("Search", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}
