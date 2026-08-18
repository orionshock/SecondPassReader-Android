package com.secondpasslibrary.reader.library

import com.secondpasslibrary.client.LibraryBookDetail
import com.secondpasslibrary.reader.connection.AuthenticatedClientProvider
import com.secondpasslibrary.reader.connection.ConnectionProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

internal data class LibraryBookDetailState(
    val bookId: String? = null,
    val loading: Boolean = false,
    val detail: LibraryBookDetail? = null,
    val failure: LibraryFailure? = null
)

internal class LibraryBookDetailController(
    private val clientProvider: AuthenticatedClientProvider,
    private val scope: CoroutineScope
) {
    private val mutableState = MutableStateFlow(LibraryBookDetailState())
    val state = mutableState.asStateFlow()
    private val connectionEventChannel = Channel<LibraryConnectionEvent>(Channel.BUFFERED)
    val connectionEvents = connectionEventChannel.receiveAsFlow()

    private var profile: ConnectionProfile? = null
    private var loadJob: Job? = null
    private var generation = 0L

    fun prepare(profile: ConnectionProfile) {
        this.profile = profile
    }

    fun select(bookId: String) {
        require(bookId.isNotBlank()) { "Book ID must not be blank." }
        if (mutableState.value.bookId == bookId && loadJob?.isActive == true) return
        generation += 1
        mutableState.value = LibraryBookDetailState(bookId = bookId, loading = true)
        load(bookId, generation)
    }

    fun retry() {
        val bookId = mutableState.value.bookId ?: return
        if (loadJob?.isActive == true) return
        generation += 1
        mutableState.value = mutableState.value.copy(loading = true, failure = null)
        load(bookId, generation)
    }

    fun clear() {
        generation += 1
        loadJob?.cancel()
        mutableState.value = LibraryBookDetailState()
    }

    fun close() {
        clear()
        connectionEventChannel.close()
    }

    private fun load(bookId: String, requestGeneration: Long) {
        val activeProfile = checkNotNull(profile) { "Book Detail is not prepared." }
        loadJob = scope.launch {
            val result = runCatching {
                clientProvider.forProfile(activeProfile).library.books.getBook(bookId)
            }
            (result.exceptionOrNull() as? CancellationException)?.let { throw it }
            if (generation != requestGeneration ||
                mutableState.value.bookId != bookId
            ) {
                return@launch
            }
            result.fold(
                onSuccess = { detail ->
                    mutableState.value = LibraryBookDetailState(bookId, detail = detail)
                },
                onFailure = { failure ->
                    val classified = failure.toLibraryFailure()
                    mutableState.value = LibraryBookDetailState(bookId, failure = classified)
                    if (classified == LibraryFailure.AUTHENTICATION_REJECTED) {
                        connectionEventChannel.trySend(
                            LibraryConnectionEvent.AuthenticationRejected
                        )
                    }
                }
            )
        }
    }
}
