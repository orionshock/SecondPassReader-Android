package com.secondpasslibrary.reader.reader

import com.secondpasslibrary.client.SplClientException
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.reader.asset.ReaderBookAssetRequest
import com.secondpasslibrary.reader.reader.asset.ReaderBookAssetResolver
import com.secondpasslibrary.reader.reader.asset.ReaderEpubUnavailableException
import com.secondpasslibrary.reader.reader.publication.ReaderPublication
import com.secondpasslibrary.reader.reader.publication.ReaderPublicationOpener
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

internal sealed interface ReaderState {
    data object Resolving : ReaderState
    data object Downloading : ReaderState
    data object Opening : ReaderState
    data class Ready(val title: String, val publication: ReaderPublication) : ReaderState
    data class Failure(val kind: ReaderFailure) : ReaderState
}

internal enum class ReaderFailure { DOWNLOAD, OPEN, NO_EPUB }

internal sealed interface ReaderConnectionEvent {
    data object AuthenticationRejected : ReaderConnectionEvent
}

internal class ReaderController(
    private val assetResolver: ReaderBookAssetResolver,
    private val publicationOpener: ReaderPublicationOpener,
    private val scope: CoroutineScope
) {
    private val mutableState = MutableStateFlow<ReaderState>(ReaderState.Resolving)
    val state = mutableState.asStateFlow()
    private val connectionEventChannel = Channel<ReaderConnectionEvent>(Channel.BUFFERED)
    val connectionEvents = connectionEventChannel.receiveAsFlow()
    private var job: Job? = null
    private var request: ReaderRequest? = null

    fun initialize(profile: ConnectionProfile, profileId: String, bookId: String) {
        val next = ReaderRequest(profile, profileId, bookId)
        if (request == next && (job?.isActive == true || state.value is ReaderState.Ready)) return
        request = next
        load(next)
    }

    fun retry() {
        request?.let(::load)
    }

    fun close() {
        job?.cancel()
        closePublication()
        connectionEventChannel.close()
    }

    private fun load(request: ReaderRequest) {
        job?.cancel()
        closePublication()
        mutableState.value = ReaderState.Resolving
        job = scope.launch {
            val result = runCatching {
                val book = assetResolver.resolve(
                    ReaderBookAssetRequest(request.profile, request.profileId, request.bookId),
                    onDownloadStarted = { mutableState.value = ReaderState.Downloading }
                )
                mutableState.value = ReaderState.Opening
                val publication = publicationOpener.open(book.file)
                ReaderState.Ready(book.title, publication)
            }
            (result.exceptionOrNull() as? CancellationException)?.let { throw it }
            result.fold(
                onSuccess = { ready -> mutableState.value = ready },
                onFailure = { failure ->
                    if (failure is SplClientException.AuthenticationRejected) {
                        connectionEventChannel.trySend(ReaderConnectionEvent.AuthenticationRejected)
                    }
                    mutableState.value = ReaderState.Failure(failure.toReaderFailure())
                }
            )
        }
    }

    private fun closePublication() {
        (mutableState.value as? ReaderState.Ready)?.publication?.close()
    }
}

private data class ReaderRequest(
    val profile: ConnectionProfile,
    val profileId: String,
    val bookId: String
) {
    init {
        require(profileId.isNotBlank()) { "Profile ID must not be blank." }
        require(bookId.isNotBlank()) { "Book ID must not be blank." }
    }
}

private fun Throwable.toReaderFailure(): ReaderFailure = when (this) {
    is ReaderEpubUnavailableException -> ReaderFailure.NO_EPUB

    is com.secondpasslibrary.reader.reader.publication.ReaderPublicationOpenException ->
        ReaderFailure.OPEN

    else -> ReaderFailure.DOWNLOAD
}
