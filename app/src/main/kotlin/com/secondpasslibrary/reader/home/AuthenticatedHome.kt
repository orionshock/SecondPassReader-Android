package com.secondpasslibrary.reader.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.secondpasslibrary.reader.connection.ConnectionProfile
import kotlinx.coroutines.flow.collectLatest

@Composable
fun AuthenticatedHome(
    profile: ConnectionProfile,
    onNavigation: (HomeNavigationIntent) -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnNavigation by rememberUpdatedState(onNavigation)
    LaunchedEffect(profile.apiBaseUrl, profile.clientSessionId) {
        viewModel.initialize(profile)
    }
    LaunchedEffect(viewModel) {
        viewModel.navigation.collectLatest { currentOnNavigation(it) }
    }
    HomeContent(
        state = state,
        onShowClosedChanged = viewModel::setShowClosedSessions,
        onRetryRecentReading = viewModel::retryRecentReading,
        onRetryShelves = viewModel::retryShelves,
        onSearch = viewModel::searchLibrary
    )
}

@Composable
private fun HomeContent(
    state: HomeUiState,
    onShowClosedChanged: (Boolean) -> Unit,
    onRetryRecentReading: () -> Unit,
    onRetryShelves: () -> Unit,
    onSearch: (String) -> Unit
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text("Second Pass Reader", style = MaterialTheme.typography.headlineMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.weight(1f),
                label = { Text("Search library") },
                singleLine = true
            )
            Button(onClick = { onSearch(searchQuery) }) { Text("Search") }
        }
        HomeSectionStatus(
            title = "Recent reading",
            state = state.recentReading,
            onRetry = onRetryRecentReading,
            controls = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = state.showClosedSessions,
                        onCheckedChange = onShowClosedChanged
                    )
                    Text("Show closed sessions")
                }
            }
        )
        HomeSectionStatus("Shelves", state.shelves, onRetryShelves)
    }
}

@Composable
private fun HomeSectionStatus(
    title: String,
    state: HomeSectionState<*>,
    onRetry: () -> Unit,
    controls: @Composable () -> Unit = {}
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        controls()
        when (state) {
            HomeSectionState.Loading -> Text("Loading...")

            HomeSectionState.Empty -> Text("Nothing to show yet.")

            is HomeSectionState.Loaded -> Text("${state.items.size} items loaded.")

            is HomeSectionState.Error -> {
                Text(state.message, color = MaterialTheme.colorScheme.error)
                Button(onClick = onRetry) { Text("Retry") }
            }
        }
    }
}
