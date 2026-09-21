package com.secondpasslibrary.reader.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.secondpasslibrary.client.AuthenticatedContext
import com.secondpasslibrary.reader.app.AppAvailability
import com.secondpasslibrary.reader.app.AppAvailabilityReason
import com.secondpasslibrary.reader.app.storage.BookOfflineActionsState
import com.secondpasslibrary.reader.app.storage.BookOfflineActionsViewModel
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.design.book.BookCardAction
import com.secondpasslibrary.reader.design.book.BookOfflineActionDialogs
import com.secondpasslibrary.reader.design.components.InlineSearchField
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Composable
@Suppress("LongMethod") // Home wires refresh and Book actions around existing sections.
internal fun HomeScreen(
    profile: ConnectionProfile,
    profileId: String,
    verifiedContext: AuthenticatedContext?,
    availability: AppAvailability,
    onNavigation: (HomeNavigationIntent) -> Unit,
    onAuthenticationRejected: () -> Unit,
    onRefreshAvailabilityChanged: (HomeRefreshAvailability) -> Unit,
    onCheckConnection: suspend () -> Boolean,
    viewModel: HomeViewModel = viewModel(),
    offlineActions: BookOfflineActionsViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val offlineActionState by offlineActions.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { offlineActions.refresh() }
    val accountScope = HomeAccountScope(profile.serverId, profileId)
    val currentOnNavigation by rememberUpdatedState(onNavigation)
    val currentOnAuthenticationRejected by rememberUpdatedState(onAuthenticationRejected)
    val currentOnRefreshAvailabilityChanged by
        rememberUpdatedState(onRefreshAvailabilityChanged)
    LaunchedEffect(accountScope, verifiedContext) {
        viewModel.initializeCached(accountScope)
        if (verifiedContext != null && availability !is AppAvailability.Offline) {
            viewModel.provideVerifiedAuthority(profile, profileId)
        }
    }
    LaunchedEffect(accountScope, availability) {
        offlineActions.initialize(profile, profileId, availability)
    }
    LaunchedEffect(state.recentReading.content?.items, state.shelves.content?.items) {
        val recent = state.recentReading.content?.items.orEmpty().associate {
            it.book.id to it.book.cover
        }
        val previews = state.shelves.content?.items.orEmpty()
            .flatMap { it.previewBooks.orEmpty() }
            .associate { it.id to it.cover }
        offlineActions.observeBooks(previews + recent)
    }
    LaunchedEffect(offlineActions) {
        offlineActions.changes.collect { viewModel.refreshLocalBookAvailability() }
    }
    LaunchedEffect(offlineActions, onAuthenticationRejected) {
        offlineActions.authenticationRejected.collect { onAuthenticationRejected() }
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
    LaunchedEffect(accountScope, availability) {
        viewModel.updateAppAvailability(availability)
        if (availability !is AppAvailability.Offline && verifiedContext != null) {
            viewModel.provideVerifiedAuthority(profile, profileId)
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.refreshAvailability.collectLatest(currentOnRefreshAvailabilityChanged)
    }
    HomeRefreshBox(onRefresh = {
        val online = when (availability) {
            AppAvailability.Online, AppAvailability.Syncing -> true

            is AppAvailability.Offline ->
                availability.reason != AppAvailabilityReason.USER_CHOICE && onCheckConnection()
        }
        viewModel.refreshAll(profile.takeIf { online }, profileId.takeIf { online })
    }) {
        HomeContent(
            state = state,
            onShowClosedChanged = viewModel::setShowClosedSessions,
            onRetryRecentReading = viewModel::retryRecentReading,
            onRetryShelves = viewModel::retryShelves,
            onSearch = viewModel::searchLibrary,
            onReadingHistoryAction = { action ->
                when (val bookAction = (action as? HomeNavigationIntent.BookAction)?.action) {
                    is BookCardAction.MakeAvailableOffline ->
                        offlineActions.makeAvailable(bookAction.bookId)

                    is BookCardAction.RemoveDownload ->
                        offlineActions.requestRemoval(bookAction.bookId)

                    else -> viewModel.navigate(action)
                }
            },
            onViewAllSessions = viewModel::viewAllSessions,
            onOpenShelves = viewModel::openShelves,
            onShelfSelected = viewModel::navigate,
            offlineActions = offlineActionState
        )
    }
    BookOfflineActionDialogs(
        pendingRemoval = offlineActionState.pendingRemoval != null,
        error = offlineActionState.error,
        onDismissRemoval = offlineActions::dismissRemoval,
        onConfirmRemoval = offlineActions::confirmRemoval,
        onDismissError = offlineActions::dismissError
    )
}

@Composable
internal fun HomeRefreshBox(onRefresh: suspend () -> Unit, content: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    val currentOnRefresh by rememberUpdatedState(onRefresh)
    var refreshing by remember { mutableStateOf(false) }
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            if (!refreshing) {
                refreshing = true
                scope.launch {
                    try {
                        currentOnRefresh()
                    } finally {
                        refreshing = false
                    }
                }
            }
        },
        modifier = Modifier.fillMaxSize().testTag("home-refresh")
    ) {
        content()
    }
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
    onShelfSelected: (HomeNavigationIntent.OpenShelfDetail) -> Unit,
    offlineActions: BookOfflineActionsState
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
            offline = state.offline,
            locallyReadableBookIds = state.locallyReadableBookIds,
            showClosed = state.showClosedSessions,
            onShowClosedChanged = onShowClosedChanged,
            onRetry = onRetryRecentReading,
            onPrimaryAction = { onReadingHistoryAction(it) },
            onContextAction = onReadingHistoryAction,
            onViewAll = onViewAllSessions,
            availableBookIds = offlineActions.availableBookIds,
            busyBookIds = offlineActions.busyBookIds,
            localCovers = offlineActions.localCovers
        )
        ShelvesSection(
            state = state.shelves,
            onRetry = onRetryShelves,
            onOpenShelves = onOpenShelves,
            onShelfSelected = onShelfSelected,
            modifier = Modifier.padding(horizontal = 24.dp),
            localCovers = offlineActions.localCovers
        )
    }
}

@Composable
private fun GlobalLibrarySearch(onSearch: (String) -> Unit, modifier: Modifier = Modifier) {
    var query by rememberSaveable { mutableStateOf("") }
    InlineSearchField(
        query = query,
        placeholder = "Search books, authors, series, publishers, or tags",
        contentDescription = "Search Library",
        onQueryChanged = { query = it },
        onSubmit = { onSearch(query) },
        modifier = modifier.fillMaxWidth()
    )
}
