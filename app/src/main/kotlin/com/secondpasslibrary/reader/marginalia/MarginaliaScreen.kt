package com.secondpasslibrary.reader.marginalia

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.secondpasslibrary.reader.design.icons.AppIcon
import com.secondpasslibrary.reader.design.icons.AppIconGraphic

@Composable
internal fun MarginaliaScreen(viewModel: MarginaliaViewModel, onOpenDrawer: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sessionsState by viewModel.sessionsState.collectAsStateWithLifecycle()
    val detailState by viewModel.detailState.collectAsStateWithLifecycle()
    val detail = state.destination as? MarginaliaDestination.SessionDetail
    val listState = rememberLazyListState()

    BackHandler(enabled = detail != null, onBack = viewModel::backFromDetail)
    MarginaliaScaffold(
        title = if (detail == null) sessionsState.screenTitle() else "Reading session",
        child = detail != null,
        onNavigation = if (detail == null) onOpenDrawer else viewModel::backFromDetail
    ) { modifier ->
        if (detail == null) {
            MarginaliaHistoryContent(
                state = sessionsState,
                listState = listState,
                onStatusSelected = viewModel::changeStatus,
                onSearch = viewModel::commitSearch,
                onLoadNextPage = viewModel::loadNextPage,
                onRetry = viewModel::retrySessions,
                onSessionSelected = viewModel::selectSession,
                modifier = modifier
            )
        } else {
            ReadingSessionDetailPlaceholder(
                detailState,
                viewModel::retryDetail,
                modifier
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MarginaliaScaffold(
    title: String,
    child: Boolean,
    onNavigation: () -> Unit,
    content: @Composable (Modifier) -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onNavigation) {
                        AppIconGraphic(
                            if (child) AppIcon.Back else AppIcon.NavigationMenu,
                            if (child) "Back" else "Open navigation drawer"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        }
    ) { padding -> content(Modifier.fillMaxSize().padding(padding)) }
}
