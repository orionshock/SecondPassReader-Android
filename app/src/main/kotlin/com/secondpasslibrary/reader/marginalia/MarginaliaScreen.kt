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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.secondpasslibrary.reader.design.components.AppBarNavigation
import com.secondpasslibrary.reader.design.components.AppBarPresentation
import com.secondpasslibrary.reader.design.components.CompactSegmentedTextControl
import com.secondpasslibrary.reader.design.components.ContextualAppBar
import com.secondpasslibrary.reader.design.components.SegmentedTextOption
import com.secondpasslibrary.reader.marginalia.books.MarginaliaBooksContent
import com.secondpasslibrary.reader.marginalia.books.MarginaliaBooksState
import com.secondpasslibrary.reader.marginalia.detail.ReadingSessionDetailActions
import com.secondpasslibrary.reader.marginalia.detail.ReadingSessionDetailContent
import com.secondpasslibrary.reader.marginalia.detail.ReadingSessionDetailState
import com.secondpasslibrary.reader.marginalia.detail.appBarPresentation as detailAppBarPresentation
import com.secondpasslibrary.reader.marginalia.history.ReadingSessionStatusFilter
import com.secondpasslibrary.reader.marginalia.history.ReadingSessionsContent
import com.secondpasslibrary.reader.marginalia.history.ReadingSessionsState
import com.secondpasslibrary.reader.marginalia.history.appBarPresentation as historyAppBarPresentation

@Composable
internal fun MarginaliaScreen(
    state: MarginaliaState,
    onIntent: (MarginaliaIntent) -> Unit,
    onOpenDrawer: () -> Unit,
    onBackFromHistory: (() -> Unit)? = null,
    onBackFromDetail: (() -> Unit)? = null
) {
    val detail = state.destination as? MarginaliaDestination.SessionDetail
    val history = state.destination as? MarginaliaDestination.History
    val bookHistory = history?.context is MarginaliaHistoryContext.Book
    val bookHistoryBack = when {
        !bookHistory -> null

        history.returnDestination != MarginaliaReturnDestination.Global ->
            ({ onIntent(MarginaliaIntent.BackFromBookHistory) })

        else -> onBackFromHistory
    }
    val sessionsListState = rememberLazyListState()
    val booksListState = rememberLazyListState()

    MarginaliaBackHandler(detail != null, bookHistoryBack, onBackFromDetail, onIntent)
    MarginaliaScaffold(
        presentation = marginaliaAppBar(
            state,
            state.sessions,
            state.books,
            state.detail,
            bookHistory
        ),
        onNavigation = marginaliaNavigation(
            detail != null,
            bookHistoryBack,
            onBackFromDetail,
            onOpenDrawer,
            onIntent
        ),
        titleActions = {
            if (detail == null) {
                MarginaliaChromeControls(
                    browseMode = state.browseMode,
                    status = state.sessions.statusFilter,
                    bookScoped = bookHistory,
                    onBrowseModeSelected = {
                        onIntent(MarginaliaIntent.SelectBrowseMode(it))
                    },
                    onStatusSelected = { onIntent(MarginaliaIntent.ChangeStatus(it)) }
                )
            }
        }
    ) { modifier ->
        MarginaliaDestinationContent(
            state,
            sessionsListState,
            booksListState,
            bookHistory,
            onIntent,
            modifier
        )
    }
}

@Composable
private fun MarginaliaBackHandler(
    detailVisible: Boolean,
    bookHistoryBack: (() -> Unit)?,
    onBackFromDetail: (() -> Unit)?,
    onIntent: (MarginaliaIntent) -> Unit
) {
    BackHandler(enabled = detailVisible || bookHistoryBack != null) {
        if (detailVisible) {
            onBackFromDetail?.invoke() ?: onIntent(MarginaliaIntent.BackFromDetail)
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
    onIntent: (MarginaliaIntent) -> Unit
): () -> Unit = when {
    detailVisible -> onBackFromDetail ?: { onIntent(MarginaliaIntent.BackFromDetail) }
    bookHistoryBack != null -> bookHistoryBack
    else -> onOpenDrawer
}

@Composable
private fun MarginaliaDestinationContent(
    state: MarginaliaState,
    sessionsListState: LazyListState,
    booksListState: LazyListState,
    bookHistory: Boolean,
    onIntent: (MarginaliaIntent) -> Unit,
    modifier: Modifier
) {
    when {
        state.destination is MarginaliaDestination.SessionDetail ->
            ReadingSessionDetailContent(
                state.detail,
                state.annotations,
                state.metadataEdit,
                state.close,
                detailActions(onIntent),
                modifier
            )

        state.browseMode == MarginaliaBrowseMode.BOOKS && !bookHistory ->
            MarginaliaBooksContent(
                state.books,
                booksListState,
                { onIntent(MarginaliaIntent.CommitBooksSearch(it)) },
                { onIntent(MarginaliaIntent.LoadNextBooksPage) },
                { onIntent(MarginaliaIntent.RetryBooks) },
                { onIntent(MarginaliaIntent.SelectBook(it)) },
                modifier
            )

        else -> ReadingSessionsContent(
            state.sessions,
            sessionsListState,
            { onIntent(MarginaliaIntent.CommitSessionsSearch(it)) },
            { onIntent(MarginaliaIntent.LoadNextSessionsPage) },
            { onIntent(MarginaliaIntent.RetrySessions) },
            { onIntent(MarginaliaIntent.SelectSession(it)) },
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

private fun detailActions(onIntent: (MarginaliaIntent) -> Unit) = ReadingSessionDetailActions(
    retryDetail = { onIntent(MarginaliaIntent.RetryDetail) },
    retryAnnotations = { onIntent(MarginaliaIntent.RetryAnnotations) },
    beginEdit = { onIntent(MarginaliaIntent.BeginEdit) },
    editNameChanged = { onIntent(MarginaliaIntent.EditName(it)) },
    editNotesChanged = { onIntent(MarginaliaIntent.EditNotes(it)) },
    saveEdit = { onIntent(MarginaliaIntent.SaveEdit) },
    cancelEdit = { onIntent(MarginaliaIntent.CancelEdit) },
    beginClose = { onIntent(MarginaliaIntent.BeginClose) },
    closeNameChanged = { onIntent(MarginaliaIntent.CloseName(it)) },
    closeNotesChanged = { onIntent(MarginaliaIntent.CloseNotes(it)) },
    confirmClose = { onIntent(MarginaliaIntent.ConfirmClose) },
    cancelClose = { onIntent(MarginaliaIntent.CancelClose) },
    openBookMarginalia = { onIntent(MarginaliaIntent.ShowDetailBookHistory) },
    openBookDetail = { onIntent(MarginaliaIntent.OpenDetailBook) }
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
