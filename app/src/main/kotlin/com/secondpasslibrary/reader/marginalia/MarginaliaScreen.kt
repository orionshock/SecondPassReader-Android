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
import com.secondpasslibrary.reader.marginalia.detail.ReadingSessionDetailActions
import com.secondpasslibrary.reader.marginalia.detail.ReadingSessionDetailContent
import com.secondpasslibrary.reader.marginalia.detail.ReadingSessionDetailIntent
import com.secondpasslibrary.reader.marginalia.detail.screenTitle as detailScreenTitle
import com.secondpasslibrary.reader.marginalia.history.ReadingSessionsContent
import com.secondpasslibrary.reader.marginalia.history.screenTitle as historyScreenTitle

@Composable
internal fun MarginaliaScreen(
    viewModel: MarginaliaViewModel,
    onOpenDrawer: () -> Unit,
    onBackFromHistory: (() -> Unit)? = null
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sessionsState by viewModel.sessionsState.collectAsStateWithLifecycle()
    val detailState by viewModel.detailState.collectAsStateWithLifecycle()
    val annotationState by viewModel.annotationState.collectAsStateWithLifecycle()
    val metadataEditState by viewModel.metadataEditState.collectAsStateWithLifecycle()
    val closeState by viewModel.closeState.collectAsStateWithLifecycle()
    val detail = state.destination as? MarginaliaDestination.SessionDetail
    val bookHistory =
        detail == null && sessionsState.context is MarginaliaHistoryContext.Book
    val bookHistoryBack = if (bookHistory) onBackFromHistory else null
    val listState = rememberLazyListState()

    BackHandler(enabled = detail != null || bookHistoryBack != null) {
        if (detail != null) viewModel.backFromDetail() else onBackFromHistory?.invoke()
    }
    MarginaliaScaffold(
        title =
            if (detail == null) {
                sessionsState.historyScreenTitle()
            } else {
                detailState.detailScreenTitle()
            },
        child = detail != null || bookHistoryBack != null,
        onNavigation = when {
            detail != null -> viewModel::backFromDetail
            bookHistoryBack != null -> bookHistoryBack
            else -> onOpenDrawer
        }
    ) { modifier ->
        if (detail == null) {
            ReadingSessionsContent(
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
            ReadingSessionDetailContent(
                detailState = detailState,
                annotationState = annotationState,
                metadataEditState = metadataEditState,
                closeState = closeState,
                actions = viewModel.detailActions(),
                modifier = modifier
            )
        }
    }
}

private fun MarginaliaViewModel.detailActions() = ReadingSessionDetailActions(
    retryDetail = { onDetailIntent(ReadingSessionDetailIntent.RetryDetail) },
    retryAnnotations = { onDetailIntent(ReadingSessionDetailIntent.RetryAnnotations) },
    beginEdit = { onDetailIntent(ReadingSessionDetailIntent.BeginEdit) },
    editNameChanged = { onDetailIntent(ReadingSessionDetailIntent.EditName(it)) },
    editNotesChanged = { onDetailIntent(ReadingSessionDetailIntent.EditNotes(it)) },
    saveEdit = { onDetailIntent(ReadingSessionDetailIntent.SaveEdit) },
    cancelEdit = { onDetailIntent(ReadingSessionDetailIntent.CancelEdit) },
    beginClose = { onDetailIntent(ReadingSessionDetailIntent.BeginClose) },
    closeNameChanged = { onDetailIntent(ReadingSessionDetailIntent.CloseName(it)) },
    closeNotesChanged = { onDetailIntent(ReadingSessionDetailIntent.CloseNotes(it)) },
    confirmClose = { onDetailIntent(ReadingSessionDetailIntent.ConfirmClose) },
    cancelClose = { onDetailIntent(ReadingSessionDetailIntent.CancelClose) }
)

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
