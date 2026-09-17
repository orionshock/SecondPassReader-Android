package com.secondpasslibrary.reader.marginalia

import com.secondpasslibrary.reader.marginalia.books.MarginaliaBooksState
import com.secondpasslibrary.reader.marginalia.detail.ReadingSessionDetailState
import com.secondpasslibrary.reader.marginalia.detail.annotations.ReadingSessionAnnotationsState
import com.secondpasslibrary.reader.marginalia.detail.close.ReadingSessionCloseState
import com.secondpasslibrary.reader.marginalia.detail.metadata.ReadingSessionMetadataEditState
import com.secondpasslibrary.reader.marginalia.history.ReadingSessionStatusFilter
import com.secondpasslibrary.reader.marginalia.history.ReadingSessionsState

internal sealed interface MarginaliaHistoryContext {
    data object Global : MarginaliaHistoryContext

    data class Book(val bookId: String) : MarginaliaHistoryContext
}

internal data class ReadingSessionDetailEntry(
    val sessionId: String,
    val action: ReadingSessionDetailEntryAction = ReadingSessionDetailEntryAction.VIEW
)

internal enum class ReadingSessionDetailEntryAction {
    VIEW,
    EDIT,
    CLOSE
}

internal enum class MarginaliaBrowseMode {
    SESSIONS,
    BOOKS
}

internal sealed interface MarginaliaDestination {
    data class History(
        val context: MarginaliaHistoryContext,
        val returnToBooks: Boolean = false,
        val returnToDetail: SessionDetail? = null
    ) : MarginaliaDestination

    data class SessionDetail(
        val sessionId: String,
        val returnContext: MarginaliaHistoryContext,
        val returnToBooks: Boolean = false,
        val returnToDetail: SessionDetail? = null
    ) : MarginaliaDestination
}

internal data class MarginaliaState(
    val browseMode: MarginaliaBrowseMode = MarginaliaBrowseMode.SESSIONS,
    val destination: MarginaliaDestination =
        MarginaliaDestination.History(MarginaliaHistoryContext.Global),
    val sessions: ReadingSessionsState = ReadingSessionsState(),
    val books: MarginaliaBooksState = MarginaliaBooksState(),
    val detail: ReadingSessionDetailState = ReadingSessionDetailState(),
    val annotations: ReadingSessionAnnotationsState = ReadingSessionAnnotationsState(),
    val metadataEdit: ReadingSessionMetadataEditState = ReadingSessionMetadataEditState(),
    val close: ReadingSessionCloseState = ReadingSessionCloseState()
)

internal sealed interface MarginaliaIntent {
    data class SelectBrowseMode(val mode: MarginaliaBrowseMode) : MarginaliaIntent
    data class ChangeStatus(val filter: ReadingSessionStatusFilter) : MarginaliaIntent
    data class CommitSessionsSearch(val query: String) : MarginaliaIntent
    data object LoadNextSessionsPage : MarginaliaIntent
    data object RetrySessions : MarginaliaIntent
    data class CommitBooksSearch(val query: String) : MarginaliaIntent
    data object LoadNextBooksPage : MarginaliaIntent
    data object RetryBooks : MarginaliaIntent
    data class SelectBook(val bookId: String) : MarginaliaIntent
    data object BackFromBookHistory : MarginaliaIntent
    data object ShowDetailBookHistory : MarginaliaIntent
    data object OpenDetailBook : MarginaliaIntent
    data class SelectSession(val sessionId: String) : MarginaliaIntent
    data object BackFromDetail : MarginaliaIntent
    data object RetryDetail : MarginaliaIntent
    data object RetryAnnotations : MarginaliaIntent
    data object BeginEdit : MarginaliaIntent
    data class EditName(val value: String) : MarginaliaIntent
    data class EditNotes(val value: String) : MarginaliaIntent
    data object SaveEdit : MarginaliaIntent
    data object CancelEdit : MarginaliaIntent
    data object BeginClose : MarginaliaIntent
    data class CloseName(val value: String) : MarginaliaIntent
    data class CloseNotes(val value: String) : MarginaliaIntent
    data object ConfirmClose : MarginaliaIntent
    data object CancelClose : MarginaliaIntent
    data object ShowGlobalHistory : MarginaliaIntent
    data class ShowBookHistory(val bookId: String) : MarginaliaIntent
    data class OpenBookDetail(val bookId: String) : MarginaliaIntent
    data class OpenReader(val bookId: String, val sessionId: String) : MarginaliaIntent
}

internal sealed interface MarginaliaExternalNavigationIntent {
    data class BookDetail(val bookId: String) : MarginaliaExternalNavigationIntent

    data class Reader(val bookId: String, val sessionId: String) :
        MarginaliaExternalNavigationIntent
}

internal enum class MarginaliaFailure {
    UNREACHABLE,
    AUTHENTICATION_REJECTED,
    PROTOCOL_INVALID,
    OTHER
}

internal sealed interface MarginaliaConnectionEvent {
    data object AuthenticationRejected : MarginaliaConnectionEvent
}
