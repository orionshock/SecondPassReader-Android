package com.secondpasslibrary.reader.marginalia

import com.secondpasslibrary.reader.connection.AuthenticatedClientProvider
import com.secondpasslibrary.reader.connection.AuthenticatedConnectionIdentity
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.connection.authenticatedConnectionIdentity
import com.secondpasslibrary.reader.marginalia.books.MarginaliaBooksController
import com.secondpasslibrary.reader.marginalia.detail.ReadingSessionAuthoritativeUpdateSink
import com.secondpasslibrary.reader.marginalia.detail.ReadingSessionDetailController
import com.secondpasslibrary.reader.marginalia.history.ReadingSessionsController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

// Parent exhaustively routes typed coordination intents to its bounded children.
@Suppress("CyclomaticComplexMethod", "TooManyFunctions")
internal class MarginaliaController(
    clientProvider: AuthenticatedClientProvider,
    private val scope: CoroutineScope,
    private val sessions: ReadingSessionsController =
        ReadingSessionsController(clientProvider, scope),
    private val books: MarginaliaBooksController = MarginaliaBooksController(clientProvider, scope),
    private val detail: ReadingSessionDetailController =
        ReadingSessionDetailController(
            clientProvider,
            scope,
            ReadingSessionAuthoritativeUpdateSink(sessions::reconcileAuthoritativeUpdate)
        )
) {
    private val lifetimeJob = SupervisorJob(scope.coroutineContext[Job])
    private val lifetimeScope = CoroutineScope(scope.coroutineContext + lifetimeJob)
    private val navigationState = MutableStateFlow(MarginaliaNavigationState())
    val state =
        marginaliaStateFlow(
            lifetimeScope,
            navigationState,
            sessions.state,
            books.state,
            detail.state,
            detail.annotations.state,
            detail.metadataEditor.state,
            detail.closeFlow.state
        )

    private val navigationChannel = Channel<MarginaliaExternalNavigationIntent>(Channel.BUFFERED)
    val navigation = navigationChannel.receiveAsFlow()

    val connectionEvents =
        merge(sessions.connectionEvents, books.connectionEvents, detail.connectionEvents)

    private var connectionIdentity: AuthenticatedConnectionIdentity? = null
    private var detailEntryJob: Job? = null

    fun initialize(
        profile: ConnectionProfile,
        initialContext: MarginaliaHistoryContext = MarginaliaHistoryContext.Global,
        detailEntry: ReadingSessionDetailEntry? = null
    ) {
        val nextConnectionIdentity = profile.authenticatedConnectionIdentity
        val identityChanged = nextConnectionIdentity != connectionIdentity
        val activeContext = when (val destination = navigationState.value.destination) {
            is MarginaliaDestination.History -> destination.context
            is MarginaliaDestination.SessionDetail -> destination.returnContext
        }
        val contextChanged = activeContext != initialContext
        sessions.prepare(profile)
        books.prepare(profile)
        detail.prepare(profile)
        if (identityChanged) {
            connectionIdentity = nextConnectionIdentity
            navigationState.value =
                MarginaliaNavigationState(
                    destination = MarginaliaDestination.History(initialContext)
                )
        } else if (contextChanged) {
            detail.clear()
            navigationState.value =
                navigationState.value.copy(
                    browseMode = MarginaliaBrowseMode.SESSIONS,
                    destination = MarginaliaDestination.History(initialContext)
                )
        }
        if (detailEntry != null) {
            val current = navigationState.value.destination as? MarginaliaDestination.SessionDetail
            if (identityChanged || contextChanged || current?.sessionId != detailEntry.sessionId) {
                enterSessionDetail(detailEntry, initialContext)
            }
            return
        }
        val destination = navigationState.value.destination
        if (destination is MarginaliaDestination.History) {
            sessions.enter(destination.context, identityChanged || contextChanged)
        }
    }

    fun accept(intent: MarginaliaIntent) {
        when (intent) {
            is MarginaliaIntent.SelectBrowseMode -> selectBrowseMode(intent.mode)

            is MarginaliaIntent.ChangeStatus -> sessions.changeStatus(intent.filter)

            is MarginaliaIntent.CommitSessionsSearch -> sessions.commitSearch(intent.query)

            MarginaliaIntent.LoadNextSessionsPage -> sessions.loadNextPage()

            MarginaliaIntent.RetrySessions -> sessions.retry()

            is MarginaliaIntent.CommitBooksSearch -> books.commitSearch(intent.query)

            MarginaliaIntent.LoadNextBooksPage -> books.loadNextPage()

            MarginaliaIntent.RetryBooks -> books.retry()

            is MarginaliaIntent.SelectBook -> selectBook(intent.bookId)

            MarginaliaIntent.BackFromBookHistory -> backFromBookHistory()

            MarginaliaIntent.ShowDetailBookHistory -> showDetailBookHistory()

            MarginaliaIntent.OpenDetailBook -> openDetailBook()

            is MarginaliaIntent.SelectSession -> selectSession(intent.sessionId)

            MarginaliaIntent.BackFromDetail -> backFromDetail()

            MarginaliaIntent.RetryDetail -> detail.retry()

            MarginaliaIntent.RetryAnnotations -> detail.annotations.retry()

            MarginaliaIntent.BeginEdit -> detail.beginMetadataEdit()

            is MarginaliaIntent.EditName -> detail.metadataEditor.updateName(intent.value)

            is MarginaliaIntent.EditNotes -> detail.metadataEditor.updateNotes(intent.value)

            MarginaliaIntent.SaveEdit -> detail.saveMetadata()

            MarginaliaIntent.CancelEdit -> detail.metadataEditor.reset()

            MarginaliaIntent.BeginClose -> detail.beginClose()

            is MarginaliaIntent.CloseName -> detail.closeFlow.updateName(intent.value)

            is MarginaliaIntent.CloseNotes -> detail.closeFlow.updateNotes(intent.value)

            MarginaliaIntent.ConfirmClose -> detail.confirmClose()

            MarginaliaIntent.CancelClose -> detail.closeFlow.reset()

            MarginaliaIntent.ShowGlobalHistory -> showHistory(MarginaliaHistoryContext.Global)

            is MarginaliaIntent.ShowBookHistory ->
                showHistory(MarginaliaHistoryContext.Book(intent.bookId))

            is MarginaliaIntent.OpenBookDetail -> openBookDetail(intent.bookId)

            is MarginaliaIntent.OpenReader -> openReader(intent.bookId, intent.sessionId)
        }
    }

    fun close() {
        detailEntryJob?.cancel()
        sessions.close()
        books.close()
        detail.close()
        lifetimeScope.cancel()
    }

    private fun selectBrowseMode(mode: MarginaliaBrowseMode) {
        val history = navigationState.value.destination as? MarginaliaDestination.History ?: return
        if (history.context != MarginaliaHistoryContext.Global ||
            navigationState.value.browseMode == mode
        ) {
            return
        }
        navigationState.value = navigationState.value.copy(browseMode = mode)
        when (mode) {
            MarginaliaBrowseMode.SESSIONS -> sessions.enter(MarginaliaHistoryContext.Global)
            MarginaliaBrowseMode.BOOKS -> books.enter()
        }
    }

    private fun selectBook(bookId: String) {
        require(bookId.isNotBlank()) { "Book ID must not be blank." }
        detailEntryJob?.cancel()
        detail.clear()
        navigationState.value =
            navigationState.value.copy(
                destination =
                    MarginaliaDestination.History(
                        MarginaliaHistoryContext.Book(bookId),
                        returnToBooks = true
                    )
            )
        sessions.enter(MarginaliaHistoryContext.Book(bookId))
    }

    private fun backFromBookHistory() {
        val current = navigationState.value.destination as? MarginaliaDestination.History ?: return
        when {
            current.returnToDetail != null -> {
                navigationState.value =
                    navigationState.value.copy(destination = current.returnToDetail)
            }

            current.returnToBooks -> {
                navigationState.value =
                    navigationState.value.copy(
                        destination = MarginaliaDestination.History(MarginaliaHistoryContext.Global)
                    )
                books.enter()
            }
        }
    }

    private fun showDetailBookHistory() {
        val current =
            navigationState.value.destination as? MarginaliaDestination.SessionDetail
                ?: return
        val bookId = detail.state.value.detail?.book?.id ?: return
        navigationState.value =
            navigationState.value.copy(
                destination =
                    MarginaliaDestination.History(
                        context = MarginaliaHistoryContext.Book(bookId),
                        returnToDetail = current
                    )
            )
        sessions.enter(MarginaliaHistoryContext.Book(bookId))
    }

    private fun selectSession(sessionId: String) {
        val current = navigationState.value.destination as? MarginaliaDestination.History ?: return
        enterSessionDetail(
            ReadingSessionDetailEntry(sessionId),
            current.context,
            current.returnToBooks,
            current.returnToDetail
        )
    }

    private fun backFromDetail() {
        val current =
            navigationState.value.destination as? MarginaliaDestination.SessionDetail
                ?: return
        detailEntryJob?.cancel()
        detail.clear()
        navigationState.value =
            navigationState.value.copy(
                destination =
                    MarginaliaDestination.History(
                        current.returnContext,
                        current.returnToBooks,
                        current.returnToDetail
                    )
            )
    }

    private fun openDetailBook() {
        detail.state.value.detail?.book?.id?.let(::openBookDetail)
    }

    private fun openBookDetail(bookId: String) {
        require(bookId.isNotBlank()) { "Book ID must not be blank." }
        navigationChannel.trySend(MarginaliaExternalNavigationIntent.BookDetail(bookId))
    }

    private fun openReader(bookId: String, sessionId: String) {
        require(bookId.isNotBlank()) { "Book ID must not be blank." }
        require(sessionId.isNotBlank()) { "Reading Session ID must not be blank." }
        navigationChannel.trySend(MarginaliaExternalNavigationIntent.Reader(bookId, sessionId))
    }

    private fun showHistory(context: MarginaliaHistoryContext) {
        detailEntryJob?.cancel()
        detail.clear()
        navigationState.value =
            navigationState.value.copy(
                browseMode = MarginaliaBrowseMode.SESSIONS,
                destination = MarginaliaDestination.History(context)
            )
        sessions.enter(context)
    }

    private fun enterSessionDetail(
        entry: ReadingSessionDetailEntry,
        returnContext: MarginaliaHistoryContext,
        returnToBooks: Boolean = false,
        returnToDetail: MarginaliaDestination.SessionDetail? = null
    ) {
        require(entry.sessionId.isNotBlank()) { "Reading Session ID must not be blank." }
        detailEntryJob?.cancel()
        detail.select(entry.sessionId)
        navigationState.value =
            navigationState.value.copy(
                destination =
                    MarginaliaDestination.SessionDetail(
                        entry.sessionId,
                        returnContext,
                        returnToBooks,
                        returnToDetail
                    )
            )
        if (entry.action == ReadingSessionDetailEntryAction.VIEW) return
        detailEntryJob = scope.launch {
            detail.state.first { it.sessionId == entry.sessionId && it.detail != null }
            val current = navigationState.value.destination as? MarginaliaDestination.SessionDetail
            if (current?.sessionId != entry.sessionId) return@launch
            when (entry.action) {
                ReadingSessionDetailEntryAction.VIEW -> Unit
                ReadingSessionDetailEntryAction.EDIT -> detail.beginMetadataEdit()
                ReadingSessionDetailEntryAction.CLOSE -> detail.beginClose()
            }
        }
    }
}
