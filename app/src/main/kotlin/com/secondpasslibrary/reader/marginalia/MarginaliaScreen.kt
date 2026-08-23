package com.secondpasslibrary.reader.marginalia

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.secondpasslibrary.reader.design.components.AppBarNavigation
import com.secondpasslibrary.reader.design.components.AppBarPresentation
import com.secondpasslibrary.reader.design.components.CompactSegmentedTextControl
import com.secondpasslibrary.reader.design.components.ContextualAppBar
import com.secondpasslibrary.reader.design.components.SegmentedTextOption
import com.secondpasslibrary.reader.marginalia.books.MarginaliaBooksContent
import com.secondpasslibrary.reader.marginalia.books.MarginaliaBooksState
import com.secondpasslibrary.reader.marginalia.detail.ReadingSessionDetailActions
import com.secondpasslibrary.reader.marginalia.detail.ReadingSessionDetailContent
import com.secondpasslibrary.reader.marginalia.detail.ReadingSessionDetailIntent
import com.secondpasslibrary.reader.marginalia.detail.ReadingSessionDetailState
import com.secondpasslibrary.reader.marginalia.detail.annotations.ReadingSessionAnnotationsState
import com.secondpasslibrary.reader.marginalia.detail.appBarPresentation as detailAppBarPresentation
import com.secondpasslibrary.reader.marginalia.detail.close.ReadingSessionCloseState
import com.secondpasslibrary.reader.marginalia.detail.metadata.ReadingSessionMetadataEditState
import com.secondpasslibrary.reader.marginalia.history.ReadingSessionStatusFilter
import com.secondpasslibrary.reader.marginalia.history.ReadingSessionsContent
import com.secondpasslibrary.reader.marginalia.history.ReadingSessionsState
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
    val booksState by viewModel.booksState.collectAsStateWithLifecycle()
    val detailState by viewModel.detailState.collectAsStateWithLifecycle()
    val annotationState by viewModel.annotationState.collectAsStateWithLifecycle()
    val metadataEditState by viewModel.metadataEditState.collectAsStateWithLifecycle()
    val closeState by viewModel.closeState.collectAsStateWithLifecycle()
    val detail = state.destination as? MarginaliaDestination.SessionDetail
    val history = state.destination as? MarginaliaDestination.History
    val bookHistory = history?.context is MarginaliaHistoryContext.Book
    val bookHistoryBack = when {
        !bookHistory -> null
        history.returnToBooks || history.returnToDetail != null -> viewModel::backFromBookHistory
        else -> onBackFromHistory
    }
    val sessionsListState = rememberLazyListState()
    val booksListState = rememberLazyListState()

    MarginaliaBackHandler(detail != null, bookHistoryBack, onBackFromDetail, viewModel)
    MarginaliaScaffold(
        presentation = marginaliaAppBar(state, sessionsState, booksState, detailState, bookHistory),
        onNavigation = marginaliaNavigation(
            detail != null,
            bookHistoryBack,
            onBackFromDetail,
            onOpenDrawer,
            viewModel
        ),
        titleActions = {
            if (detail == null) {
                MarginaliaChromeControls(
                    browseMode = state.browseMode,
                    status = sessionsState.statusFilter,
                    bookScoped = bookHistory,
                    onBrowseModeSelected = viewModel::selectBrowseMode,
                    onStatusSelected = viewModel::changeStatus
                )
            }
        }
    ) { modifier ->
        MarginaliaDestinationContent(
            state,
            sessionsState,
            booksState,
            detailState,
            annotationState,
            metadataEditState,
            closeState,
            sessionsListState,
            booksListState,
            bookHistory,
            viewModel,
            modifier
        )
    }
}

@Composable
private fun MarginaliaBackHandler(
    detailVisible: Boolean,
    bookHistoryBack: (() -> Unit)?,
    onBackFromDetail: (() -> Unit)?,
    viewModel: MarginaliaViewModel
) {
    BackHandler(enabled = detailVisible || bookHistoryBack != null) {
        if (detailVisible) {
            onBackFromDetail?.invoke() ?: viewModel.backFromDetail()
        } else {
            bookHistoryBack?.invoke()
        }
    }
}

private fun marginaliaAppBar(
    state: MarginaliaState,
    sessions: ReadingSessionsState,
    books: MarginaliaBooksState,
    detail: ReadingSessionDetailState,
    bookHistory: Boolean
): AppBarPresentation = when {
    state.destination is MarginaliaDestination.SessionDetail -> detail.detailAppBarPresentation()

    state.browseMode == MarginaliaBrowseMode.BOOKS && !bookHistory ->
        AppBarPresentation(
            navigation = AppBarNavigation.MENU,
            title = "Marginalia",
            metadata = if (books.currentPage > 0) {
                if (books.totalCount == 1) "1 book" else "${books.totalCount} books"
            } else {
                null
            },
            metadataSlotWidth = MARGINALIA_COUNT_SLOT_WIDTH
        )

    else -> sessions.historyAppBarPresentation()
}

private fun marginaliaNavigation(
    detailVisible: Boolean,
    bookHistoryBack: (() -> Unit)?,
    onBackFromDetail: (() -> Unit)?,
    onOpenDrawer: () -> Unit,
    viewModel: MarginaliaViewModel
): () -> Unit = when {
    detailVisible -> onBackFromDetail ?: viewModel::backFromDetail
    bookHistoryBack != null -> bookHistoryBack
    else -> onOpenDrawer
}

@Composable
private fun MarginaliaDestinationContent(
    state: MarginaliaState,
    sessions: ReadingSessionsState,
    books: MarginaliaBooksState,
    detail: ReadingSessionDetailState,
    annotations: ReadingSessionAnnotationsState,
    metadataEdit: ReadingSessionMetadataEditState,
    close: ReadingSessionCloseState,
    sessionsListState: LazyListState,
    booksListState: LazyListState,
    bookHistory: Boolean,
    viewModel: MarginaliaViewModel,
    modifier: Modifier
) {
    when {
        state.destination is MarginaliaDestination.SessionDetail ->
            ReadingSessionDetailContent(
                detail,
                annotations,
                metadataEdit,
                close,
                viewModel.detailActions(),
                modifier
            )

        state.browseMode == MarginaliaBrowseMode.BOOKS && !bookHistory ->
            MarginaliaBooksContent(
                books,
                booksListState,
                viewModel::commitBooksSearch,
                viewModel::loadNextBooksPage,
                viewModel::retryBooks,
                viewModel::selectBook,
                modifier
            )

        else -> ReadingSessionsContent(
            sessions,
            sessionsListState,
            viewModel::commitSearch,
            viewModel::loadNextPage,
            viewModel::retrySessions,
            viewModel::selectSession,
            modifier
        )
    }
}

@Composable
private fun MarginaliaChromeControls(
    browseMode: MarginaliaBrowseMode,
    status: ReadingSessionStatusFilter,
    bookScoped: Boolean,
    onBrowseModeSelected: (MarginaliaBrowseMode) -> Unit,
    onStatusSelected: (ReadingSessionStatusFilter) -> Unit
) {
    Row(
        modifier = Modifier.padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        marginaliaChromeControlOrder(browseMode, bookScoped).forEach { control ->
            when (control) {
                MarginaliaChromeControl.STATUS -> CompactSegmentedTextControl(
                    selected = status,
                    options = ReadingSessionStatusFilter.entries.map {
                        SegmentedTextOption(
                            it,
                            it.name.lowercase().replaceFirstChar(Char::uppercase)
                        )
                    },
                    onSelected = onStatusSelected
                )

                MarginaliaChromeControl.BROWSE_MODE -> CompactSegmentedTextControl(
                    selected = browseMode,
                    options = listOf(
                        SegmentedTextOption(MarginaliaBrowseMode.SESSIONS, "Sessions"),
                        SegmentedTextOption(MarginaliaBrowseMode.BOOKS, "Books")
                    ),
                    onSelected = onBrowseModeSelected
                )
            }
        }
    }
}

internal enum class MarginaliaChromeControl {
    STATUS,
    BROWSE_MODE
}

internal fun marginaliaChromeControlOrder(
    browseMode: MarginaliaBrowseMode,
    bookScoped: Boolean
): List<MarginaliaChromeControl> = buildList {
    if (bookScoped ||
        browseMode == MarginaliaBrowseMode.SESSIONS
    ) {
        add(MarginaliaChromeControl.STATUS)
    }
    if (!bookScoped) add(MarginaliaChromeControl.BROWSE_MODE)
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
    cancelClose = { onDetailIntent(ReadingSessionDetailIntent.CancelClose) },
    openBookMarginalia = ::showDetailBookHistory,
    openBookDetail = ::openDetailBook
)

private val MARGINALIA_COUNT_SLOT_WIDTH = 84.dp

@Composable
private fun MarginaliaScaffold(
    presentation: AppBarPresentation,
    onNavigation: () -> Unit,
    titleActions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
    content: @Composable (Modifier) -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ContextualAppBar(presentation, titleActions, onNavigation)
        }
    ) { padding -> content(Modifier.fillMaxSize().padding(padding)) }
}
