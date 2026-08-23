package com.secondpasslibrary.reader.marginalia

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
        MarginaliaDestination.History(MarginaliaHistoryContext.Global)
)

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
