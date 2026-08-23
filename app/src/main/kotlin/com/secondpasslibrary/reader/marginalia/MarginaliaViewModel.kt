package com.secondpasslibrary.reader.marginalia

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondpasslibrary.reader.connection.AuthenticatedClientProvider
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.marginalia.detail.ReadingSessionDetailIntent
import com.secondpasslibrary.reader.marginalia.history.ReadingSessionStatusFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
@Suppress("TooManyFunctions") // Intent methods preserve the controller command boundary.
internal class MarginaliaViewModel
@Inject
constructor(
    clientProvider: AuthenticatedClientProvider
) : ViewModel() {
    private val controller = MarginaliaController(clientProvider, viewModelScope)

    val state = controller.state
    val sessionsState = controller.sessions.state
    val booksState = controller.books.state
    val detailState = controller.detail.state
    val annotationState = controller.detail.annotations.state
    val metadataEditState = controller.detail.metadataEditor.state
    val closeState = controller.detail.closeFlow.state
    val connectionEvents = controller.connectionEvents
    val navigation = controller.navigation

    fun initialize(
        profile: ConnectionProfile,
        initialContext: MarginaliaHistoryContext = MarginaliaHistoryContext.Global,
        detailEntry: ReadingSessionDetailEntry? = null
    ) = controller.initialize(profile, initialContext, detailEntry)

    fun changeStatus(filter: ReadingSessionStatusFilter) = controller.sessions.changeStatus(filter)

    fun selectBrowseMode(mode: MarginaliaBrowseMode) = controller.selectBrowseMode(mode)

    fun commitSearch(query: String) = controller.sessions.commitSearch(query)

    fun loadNextPage() = controller.sessions.loadNextPage()

    fun retrySessions() = controller.sessions.retry()

    fun commitBooksSearch(query: String) = controller.books.commitSearch(query)

    fun loadNextBooksPage() = controller.books.loadNextPage()

    fun retryBooks() = controller.books.retry()

    fun selectBook(bookId: String) = controller.selectBook(bookId)

    fun backFromBookHistory() = controller.backFromBookHistory()

    fun showDetailBookHistory() = controller.showDetailBookHistory()

    fun openDetailBook() {
        controller.detail.state.value.detail?.book?.id?.let(controller::openBookDetail)
    }

    fun selectSession(sessionId: String) = controller.selectSession(sessionId)

    fun backFromDetail() = controller.backFromDetail()

    fun onDetailIntent(intent: ReadingSessionDetailIntent) {
        when (intent) {
            ReadingSessionDetailIntent.RetryDetail -> controller.detail.retry()

            ReadingSessionDetailIntent.RetryAnnotations -> controller.detail.annotations.retry()

            ReadingSessionDetailIntent.BeginEdit -> controller.detail.beginMetadataEdit()

            is ReadingSessionDetailIntent.EditName ->
                controller.detail.metadataEditor.updateName(intent.value)

            is ReadingSessionDetailIntent.EditNotes ->
                controller.detail.metadataEditor.updateNotes(intent.value)

            ReadingSessionDetailIntent.SaveEdit -> controller.detail.saveMetadata()

            ReadingSessionDetailIntent.CancelEdit -> controller.detail.metadataEditor.reset()

            ReadingSessionDetailIntent.BeginClose -> controller.detail.beginClose()

            is ReadingSessionDetailIntent.CloseName ->
                controller.detail.closeFlow.updateName(intent.value)

            is ReadingSessionDetailIntent.CloseNotes ->
                controller.detail.closeFlow.updateNotes(intent.value)

            ReadingSessionDetailIntent.ConfirmClose -> controller.detail.confirmClose()

            ReadingSessionDetailIntent.CancelClose -> controller.detail.closeFlow.reset()
        }
    }

    override fun onCleared() {
        controller.close()
    }
}
