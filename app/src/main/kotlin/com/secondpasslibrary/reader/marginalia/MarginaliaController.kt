package com.secondpasslibrary.reader.marginalia

import com.secondpasslibrary.reader.connection.AuthenticatedClientProvider
import com.secondpasslibrary.reader.connection.AuthenticatedConnectionIdentity
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.connection.authenticatedConnectionIdentity
import com.secondpasslibrary.reader.marginalia.detail.ReadingSessionAuthoritativeUpdateSink
import com.secondpasslibrary.reader.marginalia.detail.ReadingSessionDetailController
import com.secondpasslibrary.reader.marginalia.history.ReadingSessionsController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow

internal class MarginaliaController(
    clientProvider: AuthenticatedClientProvider,
    scope: CoroutineScope,
    val sessions: ReadingSessionsController = ReadingSessionsController(clientProvider, scope),
    val detail: ReadingSessionDetailController =
        ReadingSessionDetailController(
            clientProvider,
            scope,
            ReadingSessionAuthoritativeUpdateSink(sessions::reconcileAuthoritativeUpdate)
        )
) {
    private val mutableState = MutableStateFlow(MarginaliaState())
    val state = mutableState.asStateFlow()

    private val navigationChannel = Channel<MarginaliaExternalNavigationIntent>(Channel.BUFFERED)
    val navigation = navigationChannel.receiveAsFlow()

    val connectionEvents = merge(sessions.connectionEvents, detail.connectionEvents)

    private var connectionIdentity: AuthenticatedConnectionIdentity? = null

    fun initialize(
        profile: ConnectionProfile,
        initialContext: MarginaliaHistoryContext = MarginaliaHistoryContext.Global
    ) {
        val nextConnectionIdentity = profile.authenticatedConnectionIdentity
        val identityChanged = nextConnectionIdentity != connectionIdentity
        val activeContext = when (val destination = state.value.destination) {
            is MarginaliaDestination.History -> destination.context
            is MarginaliaDestination.SessionDetail -> destination.returnContext
        }
        val contextChanged = activeContext != initialContext
        sessions.prepare(profile)
        detail.prepare(profile)
        if (identityChanged) {
            connectionIdentity = nextConnectionIdentity
            mutableState.value = MarginaliaState(MarginaliaDestination.History(initialContext))
        } else if (contextChanged) {
            detail.clear()
            mutableState.value = MarginaliaState(MarginaliaDestination.History(initialContext))
        }
        val destination = state.value.destination
        if (destination is MarginaliaDestination.History) {
            sessions.enter(destination.context, identityChanged || contextChanged)
        }
    }

    fun showGlobalHistory() = showHistory(MarginaliaHistoryContext.Global)

    fun showBookHistory(bookId: String) = showHistory(MarginaliaHistoryContext.Book(bookId))

    fun selectSession(sessionId: String) {
        val current = state.value.destination as? MarginaliaDestination.History ?: return
        detail.select(sessionId)
        mutableState.value =
            MarginaliaState(MarginaliaDestination.SessionDetail(sessionId, current.context))
    }

    fun backFromDetail() {
        val current = state.value.destination as? MarginaliaDestination.SessionDetail ?: return
        detail.clear()
        mutableState.value = MarginaliaState(MarginaliaDestination.History(current.returnContext))
    }

    fun openBookDetail(bookId: String) {
        require(bookId.isNotBlank()) { "Book ID must not be blank." }
        navigationChannel.trySend(MarginaliaExternalNavigationIntent.BookDetail(bookId))
    }

    fun openReader(bookId: String, sessionId: String) {
        require(bookId.isNotBlank()) { "Book ID must not be blank." }
        require(sessionId.isNotBlank()) { "Reading Session ID must not be blank." }
        navigationChannel.trySend(MarginaliaExternalNavigationIntent.Reader(bookId, sessionId))
    }

    fun close() {
        sessions.close()
        detail.close()
    }

    private fun showHistory(context: MarginaliaHistoryContext) {
        detail.clear()
        mutableState.value = MarginaliaState(MarginaliaDestination.History(context))
        sessions.enter(context)
    }
}
