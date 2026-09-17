package com.secondpasslibrary.reader.marginalia

import com.secondpasslibrary.reader.marginalia.books.MarginaliaBooksState
import com.secondpasslibrary.reader.marginalia.detail.ReadingSessionDetailState
import com.secondpasslibrary.reader.marginalia.detail.annotations.ReadingSessionAnnotationsState
import com.secondpasslibrary.reader.marginalia.detail.close.ReadingSessionCloseState
import com.secondpasslibrary.reader.marginalia.detail.metadata.ReadingSessionMetadataEditState
import com.secondpasslibrary.reader.marginalia.history.ReadingSessionsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

internal data class MarginaliaNavigationState(
    val browseMode: MarginaliaBrowseMode = MarginaliaBrowseMode.SESSIONS,
    val destination: MarginaliaDestination =
        MarginaliaDestination.History(MarginaliaHistoryContext.Global)
)

internal fun marginaliaStateFlow(
    scope: CoroutineScope,
    navigation: StateFlow<MarginaliaNavigationState>,
    sessions: StateFlow<ReadingSessionsState>,
    books: StateFlow<MarginaliaBooksState>,
    detail: StateFlow<ReadingSessionDetailState>,
    annotations: StateFlow<ReadingSessionAnnotationsState>,
    metadataEdit: StateFlow<ReadingSessionMetadataEditState>,
    close: StateFlow<ReadingSessionCloseState>
): StateFlow<MarginaliaState> {
    val core = combine(navigation, sessions, books, detail, annotations, ::MarginaliaCoreState)
    val dialogs = combine(metadataEdit, close, ::MarginaliaDialogState)
    return combine(core, dialogs, ::toMarginaliaState).stateIn(
        scope,
        SharingStarted.Eagerly,
        toMarginaliaState(
            MarginaliaCoreState(
                navigation.value,
                sessions.value,
                books.value,
                detail.value,
                annotations.value
            ),
            MarginaliaDialogState(metadataEdit.value, close.value)
        )
    )
}

private fun toMarginaliaState(core: MarginaliaCoreState, dialogs: MarginaliaDialogState) =
    MarginaliaState(
        browseMode = core.navigation.browseMode,
        destination = core.navigation.destination,
        sessions = core.sessions,
        books = core.books,
        detail = core.detail,
        annotations = core.annotations,
        metadataEdit = dialogs.metadataEdit,
        close = dialogs.close
    )

private data class MarginaliaCoreState(
    val navigation: MarginaliaNavigationState,
    val sessions: ReadingSessionsState,
    val books: MarginaliaBooksState,
    val detail: ReadingSessionDetailState,
    val annotations: ReadingSessionAnnotationsState
)

private data class MarginaliaDialogState(
    val metadataEdit: ReadingSessionMetadataEditState,
    val close: ReadingSessionCloseState
)
