package com.secondpasslibrary.reader.bookdetail

import com.secondpasslibrary.client.LibraryBookDetail
import com.secondpasslibrary.client.SplClientException
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

internal data class BookDetailState(
    val bookId: String? = null,
    val loading: Boolean = false,
    val detail: LibraryBookDetail? = null,
    val failure: BookDetailFailure? = null
)

internal class BookDetailController(
    private val clientProvider: AuthenticatedClientProvider,
    private val scope: CoroutineScope
) {
    private val mutableState = MutableStateFlow(BookDetailState())
    val state = mutableState.asStateFlow()
    private val connectionEventChannel = Channel<BookDetailConnectionEvent>(Channel.BUFFERED)
    val connectionEvents = connectionEventChannel.receiveAsFlow()

    private var profile: ConnectionProfile? = null
    private var loadJob: Job? = null
    private var generation = 0L

    fun prepare(profile: ConnectionProfile) {
        this.profile = profile
    }

    fun select(bookId: String) {
        require(bookId.isNotBlank()) { "Book ID must not be blank." }
        if (mutableState.value.bookId == bookId &&
            (loadJob?.isActive == true || mutableState.value.detail != null)
        ) {
            return
        }
        generation += 1
        mutableState.value = BookDetailState(bookId = bookId, loading = true)
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
        mutableState.value = BookDetailState()
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
                    mutableState.value = BookDetailState(bookId, detail = detail)
                },
                onFailure = { failure ->
                    val classified = failure.toBookDetailFailure()
                    mutableState.value = BookDetailState(bookId, failure = classified)
                    if (classified == BookDetailFailure.AUTHENTICATION_REJECTED) {
                        connectionEventChannel.trySend(
                            BookDetailConnectionEvent.AuthenticationRejected
                        )
                    }
                }
            )
        }
    }
}

internal enum class BookDetailFailure {
    UNREACHABLE,
    AUTHENTICATION_REJECTED,
    PROTOCOL_INVALID,
    OTHER
}

internal sealed interface BookDetailConnectionEvent {
    data object AuthenticationRejected : BookDetailConnectionEvent
}

private fun Throwable.toBookDetailFailure(): BookDetailFailure = when (this) {
    is SplClientException.ServerUnreachable -> BookDetailFailure.UNREACHABLE

    is SplClientException.AuthenticationRejected -> BookDetailFailure.AUTHENTICATION_REJECTED

    is SplClientException.ProtocolInvalid,
    is SplClientException.NotSecondPassServer -> BookDetailFailure.PROTOCOL_INVALID

    else -> BookDetailFailure.OTHER
}
