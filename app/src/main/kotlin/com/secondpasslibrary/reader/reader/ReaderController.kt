package com.secondpasslibrary.reader.reader

import com.secondpasslibrary.client.SplClientException
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.reader.asset.ReaderBookAssetRequest
import com.secondpasslibrary.reader.reader.asset.ReaderBookAssetResolver
import com.secondpasslibrary.reader.reader.asset.ReaderEpubUnavailableException
import com.secondpasslibrary.reader.reader.cfi.EpubCfi
import com.secondpasslibrary.reader.reader.cfi.EpubCfiFailure
import com.secondpasslibrary.reader.reader.cfi.EpubCfiOutcome
import com.secondpasslibrary.reader.reader.domain.ReaderEngine
import com.secondpasslibrary.reader.reader.domain.ReaderEngineOpener
import com.secondpasslibrary.reader.reader.progress.ReaderProgressController
import com.secondpasslibrary.reader.reader.progress.ReaderProgressSyncController
import com.secondpasslibrary.reader.reader.progress.ReaderProgressWriter
import com.secondpasslibrary.reader.reader.session.ReaderProgressLoadFailure
import com.secondpasslibrary.reader.reader.session.ReaderSessionContext
import com.secondpasslibrary.reader.reader.session.ReaderSessionCoordinator
import com.secondpasslibrary.reader.reader.session.ReaderSessionRequest
import com.secondpasslibrary.reader.reader.session.ReaderSessionStatus
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
    data class Ready(
        val title: String,
        val engine: ReaderEngine,
        val session: ReaderSessionContext,
        val restore: ReaderProgressRestore
    ) : ReaderState
    data class Failure(val kind: ReaderFailure) : ReaderState
}

internal enum class ReaderProgressRestore {
    NOT_NEEDED,
    WAITING,
    RESTORED,
    SKIPPED
}

internal enum class ReaderFailure { DOWNLOAD, OPEN, NO_EPUB, SESSION }

internal sealed interface ReaderConnectionEvent {
    data object AuthenticationRejected : ReaderConnectionEvent
}

internal class ReaderController(
    private val assetResolver: ReaderBookAssetResolver,
    private val engineOpener: ReaderEngineOpener,
    private val sessionCoordinator: ReaderSessionCoordinator,
    progressWriter: ReaderProgressWriter,
    private val scope: CoroutineScope
) {
    private val mutableState = MutableStateFlow<ReaderState>(ReaderState.Resolving)
    val state = mutableState.asStateFlow()
    private val connectionEventChannel = Channel<ReaderConnectionEvent>(Channel.BUFFERED)
    val connectionEvents = connectionEventChannel.receiveAsFlow()
    private val progressController = ReaderProgressController(scope)
    val progress = progressController.state
    private val progressSyncController = ReaderProgressSyncController(
        scope,
        progressWriter,
        onAuthenticationRejected = {
            connectionEventChannel.trySend(ReaderConnectionEvent.AuthenticationRejected)
        }
    )
    val progressSync = progressSyncController.state
    private var job: Job? = null
    private var request: ReaderRequest? = null

    fun initialize(
        profile: ConnectionProfile,
        profileId: String,
        bookId: String,
        existingSessionId: String?
    ) {
        val next = ReaderRequest(profile, profileId, bookId, existingSessionId)
        if (request == next && (job?.isActive == true || state.value is ReaderState.Ready)) return
        request = next
        load(next)
    }

    fun retry() {
        request?.let(::load)
    }

    fun setAuthorityAvailable(available: Boolean) {
        progressSyncController.setAuthorityAvailable(available)
    }

    fun close() {
        job?.cancel()
        progressSyncController.close()
        progressController.reset()
        closeEngine()
        connectionEventChannel.close()
    }

    private fun load(request: ReaderRequest) {
        job?.cancel()
        progressSyncController.reset()
        progressController.reset()
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
                    failureKind = ReaderFailure.SESSION
                    val ready = prepareReady(request, book.title, engine)
                    coroutineContext.ensureActive()
                    progressController.prepare(ready.session, engine)
                    mutableState.value = ready
                    openedEngine = null
                    restoreProgress(ready)
                }
                (result.exceptionOrNull() as? CancellationException)?.let { throw it }
                result.fold(
                    onSuccess = { ready ->
                        mutableState.value = ready
                        startProgressSynchronization(request.profile, ready.session.status)
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
                        progressController.reset()
                        progressSyncController.reset()
                        mutableState.value = ReaderState.Failure(kind)
                    }
                )
            } finally {
                openedEngine?.close()
            }
        }
    }

    private fun startProgressSynchronization(
        profile: ConnectionProfile,
        sessionStatus: ReaderSessionStatus
    ) {
        progressController.enableAfterStartupRestore()
        if (sessionStatus == ReaderSessionStatus.ACTIVE) {
            progressSyncController.start(profile, progressController.state)
        }
    }

    private fun closeEngine() {
        (mutableState.value as? ReaderState.Ready)?.engine?.close()
    }

    private suspend fun prepareReady(
        request: ReaderRequest,
        title: String,
        engine: ReaderEngine
    ): ReaderState.Ready {
        val session = sessionCoordinator.resolve(
            request.profile,
            ReaderSessionRequest(request.bookId, request.existingSessionId)
        )
        if (session.progressFailure == ReaderProgressLoadFailure.AUTHENTICATION_REQUIRED) {
            connectionEventChannel.trySend(ReaderConnectionEvent.AuthenticationRejected)
        }
        return ReaderState.Ready(
            title = title,
            engine = engine,
            session = session,
            restore = if (session.savedProgressCfi == null) {
                ReaderProgressRestore.NOT_NEEDED
            } else {
                ReaderProgressRestore.WAITING
            }
        )
    }

    private suspend fun restoreProgress(ready: ReaderState.Ready): ReaderState.Ready = try {
        val rawCfi = ready.session.savedProgressCfi ?: return ready
        val cfi = runCatching { EpubCfi(rawCfi) }.getOrNull()
            ?: return ready.copy(restore = ReaderProgressRestore.SKIPPED)
        val navigator = ready.engine.cfiNavigator
        val firstAttempt = navigator.awaitNavigationAvailable().then { navigator.goTo(cfi) }
        val outcome = if (firstAttempt.isTransientRestoreFailure()) {
            navigator.awaitNavigationAvailable().then { navigator.goTo(cfi) }
        } else {
            firstAttempt
        }
        ready.copy(
            restore = if (outcome is EpubCfiOutcome.Success) {
                ReaderProgressRestore.RESTORED
            } else {
                ReaderProgressRestore.SKIPPED
            }
        )
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        ready.copy(restore = ReaderProgressRestore.SKIPPED)
    }
}

private data class ReaderRequest(
    val profile: ConnectionProfile,
    val profileId: String,
    val bookId: String,
    val existingSessionId: String?
) {
    init {
        require(profileId.isNotBlank()) { "Profile ID must not be blank." }
        require(bookId.isNotBlank()) { "Book ID must not be blank." }
        require(existingSessionId == null || existingSessionId.isNotBlank()) {
            "Existing Reading Session ID must not be blank."
        }
    }
}

private suspend inline fun EpubCfiOutcome<Unit>.then(
    operation: suspend () -> EpubCfiOutcome<Unit>
): EpubCfiOutcome<Unit> = when (this) {
    is EpubCfiOutcome.Success -> operation()
    is EpubCfiOutcome.Failure -> this
}

private fun EpubCfiOutcome<Unit>.isTransientRestoreFailure(): Boolean =
    this is EpubCfiOutcome.Failure && reason in transientRestoreFailures

private val transientRestoreFailures = setOf(
    EpubCfiFailure.NAVIGATOR_UNAVAILABLE,
    EpubCfiFailure.RESOURCE_CHANGED_DURING_OPERATION
)
