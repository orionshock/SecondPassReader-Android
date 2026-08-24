package com.secondpasslibrary.reader.reader

import com.secondpasslibrary.client.SplClientException
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.reader.asset.ReaderBookAssetRequest
import com.secondpasslibrary.reader.reader.asset.ReaderBookAssetResolver
import com.secondpasslibrary.reader.reader.asset.ReaderEpubUnavailableException
import com.secondpasslibrary.reader.reader.domain.ReaderEngine
import com.secondpasslibrary.reader.reader.domain.ReaderEngineOpener
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal sealed interface ReaderState {
    data object Resolving : ReaderState
    data object Downloading : ReaderState
    data object Opening : ReaderState
    data class Ready(val title: String, val engine: ReaderEngine) : ReaderState
    data class Failure(val kind: ReaderFailure) : ReaderState
}

internal enum class ReaderFailure { DOWNLOAD, OPEN, NO_EPUB }

internal sealed interface ReaderConnectionEvent {
    data object AuthenticationRejected : ReaderConnectionEvent
}

internal class ReaderController(
    private val assetResolver: ReaderBookAssetResolver,
    private val engineOpener: ReaderEngineOpener,
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
        closeEngine()
        connectionEventChannel.close()
    }

    private fun load(request: ReaderRequest) {
        job?.cancel()
        closeEngine()
        mutableState.value = ReaderState.Resolving
        job = scope.launch {
            var openedEngine: ReaderEngine? = null
            var failureKind = ReaderFailure.DOWNLOAD
            try {
                val result = runCatching {
                    val book = assetResolver.resolve(
                        ReaderBookAssetRequest(request.profile, request.profileId, request.bookId),
                        onDownloadStarted = {
                            if (isActive) mutableState.value = ReaderState.Downloading
                        }
                    )
                    coroutineContext.ensureActive()
                    failureKind = ReaderFailure.OPEN
                    mutableState.value = ReaderState.Opening
                    val engine = engineOpener.open(book.file)
                    openedEngine = engine
                    coroutineContext.ensureActive()
                    ReaderState.Ready(book.title, engine)
                }
                (result.exceptionOrNull() as? CancellationException)?.let { throw it }
                result.fold(
                    onSuccess = { ready ->
                        mutableState.value = ready
                        openedEngine = null
                    },
                    onFailure = { failure ->
                        coroutineContext.ensureActive()
                        if (failure is SplClientException.AuthenticationRejected) {
                            connectionEventChannel.trySend(
                                ReaderConnectionEvent.AuthenticationRejected
                            )
                        }
                        val kind = if (failure is ReaderEpubUnavailableException) {
                            ReaderFailure.NO_EPUB
                        } else {
                            failureKind
                        }
                        mutableState.value = ReaderState.Failure(kind)
                    }
                )
            } finally {
                openedEngine?.close()
            }
        }
    }

    private fun closeEngine() {
        (mutableState.value as? ReaderState.Ready)?.engine?.close()
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
