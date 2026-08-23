package com.secondpasslibrary.reader.marginalia

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.secondpasslibrary.reader.design.components.AppBarPresentation
import com.secondpasslibrary.reader.design.components.ContextualAppBar
import com.secondpasslibrary.reader.marginalia.detail.ReadingSessionDetailActions
import com.secondpasslibrary.reader.marginalia.detail.ReadingSessionDetailContent
import com.secondpasslibrary.reader.marginalia.detail.ReadingSessionDetailIntent
import com.secondpasslibrary.reader.marginalia.detail.appBarPresentation as detailAppBarPresentation
import com.secondpasslibrary.reader.marginalia.history.ReadingSessionsContent
import com.secondpasslibrary.reader.marginalia.history.appBarPresentation as historyAppBarPresentation

@Composable
internal fun MarginaliaScreen(
    viewModel: MarginaliaViewModel,
    onOpenDrawer: () -> Unit,
    onBackFromHistory: (() -> Unit)? = null,
    onBackFromDetail: (() -> Unit)? = null
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
        if (detail != null) {
            onBackFromDetail?.invoke() ?: viewModel.backFromDetail()
        } else {
            onBackFromHistory?.invoke()
        }
    }
    MarginaliaScaffold(
        presentation =
            if (detail == null) {
                sessionsState.historyAppBarPresentation()
            } else {
                detailState.detailAppBarPresentation()
            },
        onNavigation = when {
            detail != null -> onBackFromDetail ?: viewModel::backFromDetail
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

@Composable
private fun MarginaliaScaffold(
    presentation: AppBarPresentation,
    onNavigation: () -> Unit,
    content: @Composable (Modifier) -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ContextualAppBar(presentation, onNavigation)
        }
    ) { padding -> content(Modifier.fillMaxSize().padding(padding)) }
}
