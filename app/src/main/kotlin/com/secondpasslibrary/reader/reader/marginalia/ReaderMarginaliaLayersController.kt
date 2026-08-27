package com.secondpasslibrary.reader.reader.marginalia

import com.secondpasslibrary.client.SplClientException
import com.secondpasslibrary.reader.connection.AuthenticatedConnectionIdentity
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.connection.authenticatedConnectionIdentity
import com.secondpasslibrary.reader.reader.session.ReaderSessionContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

internal data class ReaderMarginaliaLayersState(
    val currentLayer: ReaderMarginaliaLayerSummary? = null,
    val previousLayers: List<ReaderPreviousMarginaliaLayer> = emptyList(),
    val isInitialLoading: Boolean = false,
    val isAppending: Boolean = false,
    val currentPage: Int = 0,
    val hasMore: Boolean = false,
    val failure: ReaderMarginaliaLayersFailure? = null
)

internal enum class ReaderMarginaliaLayersFailure {
    AUTHENTICATION_REQUIRED,
    UNAVAILABLE
}

internal class ReaderMarginaliaLayersController(
    private val loader: ReaderMarginaliaLayerHistoryLoader,
    private val scope: CoroutineScope
) {
    private val mutableState = MutableStateFlow(ReaderMarginaliaLayersState())
    val state = mutableState.asStateFlow()

    private val authenticationRequired = Channel<Unit>(Channel.BUFFERED)
    val authenticationRequiredEvents = authenticationRequired.receiveAsFlow()

    private var profile: ConnectionProfile? = null
    private var connectionIdentity: AuthenticatedConnectionIdentity? = null
    private var bookId: String? = null
    private var generation = 0L
    private var loadJob: Job? = null

    fun select(profile: ConnectionProfile, bookId: String, currentSession: ReaderSessionContext) {
        require(bookId.isNotBlank()) { "Book ID must not be blank." }
        val identity = profile.authenticatedConnectionIdentity
        val unchanged = this.bookId == bookId &&
            state.value.currentLayer?.sessionId == currentSession.sessionId &&
            connectionIdentity == identity
        if (unchanged) return
        this.profile = profile
        this.bookId = bookId
        connectionIdentity = identity
        loadJob?.cancel()
        generation += 1
        mutableState.value = ReaderMarginaliaLayersState(
            currentLayer = currentSession.toCurrentMarginaliaLayer(),
            isInitialLoading = true
        )
        load(page = 1, append = false, activeGeneration = generation)
    }

    fun loadMore() {
        val current = state.value
        if (!current.hasMore || current.isInitialLoading || current.isAppending) return
        load(current.currentPage + 1, append = true, activeGeneration = generation)
    }

    fun retry() {
        val current = state.value
        if (current.failure == null) return
        if (current.currentPage == 0) {
            load(page = 1, append = false, activeGeneration = generation)
        } else if (current.hasMore) {
            load(current.currentPage + 1, append = true, activeGeneration = generation)
        }
    }

    fun clear() {
        loadJob?.cancel()
        generation += 1
        profile = null
        connectionIdentity = null
        bookId = null
        mutableState.value = ReaderMarginaliaLayersState()
    }

    fun close() {
        clear()
        authenticationRequired.close()
    }

    private fun load(page: Int, append: Boolean, activeGeneration: Long) {
        val activeProfile = profile ?: return
        val activeBookId = bookId ?: return
        mutableState.value = state.value.copy(
            isInitialLoading = !append,
            isAppending = append,
            failure = null
        )
        loadJob = scope.launch {
            val result = runCatching { loader.load(activeProfile, activeBookId, page) }
            (result.exceptionOrNull() as? CancellationException)?.let { throw it }
            if (activeGeneration != generation) return@launch
            result.fold(
                onSuccess = { applyPage(it, append) },
                onFailure = ::applyFailure
            )
        }
    }

    private fun applyPage(page: ReaderMarginaliaLayerHistoryPage, append: Boolean) {
        val current = state.value
        val currentSessionId = current.currentLayer?.sessionId ?: return
        val candidates = page.layers
            .asSequence()
            .filter { layer -> layer.sessionId != currentSessionId }
            .filter { layer -> (layer.annotationCount ?: 0) > 0 }
            .map(::ReaderPreviousMarginaliaLayer)
            .toList()
        val existing = if (append) current.previousLayers else emptyList()
        val knownIds = existing.asSequence().map { it.summary.sessionId }.toMutableSet()
        val uniqueCandidates = candidates.filter { knownIds.add(it.summary.sessionId) }
        mutableState.value = current.copy(
            previousLayers = existing + uniqueCandidates,
            isInitialLoading = false,
            isAppending = false,
            currentPage = page.page,
            hasMore = page.hasMore,
            failure = null
        )
    }

    private fun applyFailure(failure: Throwable) {
        val kind = if (failure is SplClientException.AuthenticationRejected) {
            ReaderMarginaliaLayersFailure.AUTHENTICATION_REQUIRED
        } else {
            ReaderMarginaliaLayersFailure.UNAVAILABLE
        }
        mutableState.value = state.value.copy(
            isInitialLoading = false,
            isAppending = false,
            failure = kind
        )
        if (kind == ReaderMarginaliaLayersFailure.AUTHENTICATION_REQUIRED) {
            authenticationRequired.trySend(Unit)
        }
    }
}
