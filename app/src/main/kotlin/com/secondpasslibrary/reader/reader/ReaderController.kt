package com.secondpasslibrary.reader.reader

import com.secondpasslibrary.client.SplClientException
import com.secondpasslibrary.reader.app.AppAvailability
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.reader.appearance.ReaderAppearance
import com.secondpasslibrary.reader.reader.appearance.ReaderAppearanceStore
import com.secondpasslibrary.reader.reader.asset.ReaderBookAssetRequest
import com.secondpasslibrary.reader.reader.asset.ReaderBookAssetResolver
import com.secondpasslibrary.reader.reader.asset.ReaderEpubUnavailableException
import com.secondpasslibrary.reader.reader.cfi.EpubCfi
import com.secondpasslibrary.reader.reader.cfi.EpubCfiFailure
import com.secondpasslibrary.reader.reader.cfi.EpubCfiOutcome
import com.secondpasslibrary.reader.reader.domain.ReaderEngine
import com.secondpasslibrary.reader.reader.domain.ReaderEngineOpener
import com.secondpasslibrary.reader.reader.persistence.LocalReaderStateStore
import com.secondpasslibrary.reader.reader.progress.ReaderProgressController
import com.secondpasslibrary.reader.reader.progress.ReaderProgressFlushResult
import com.secondpasslibrary.reader.reader.progress.ReaderProgressPersistenceController
import com.secondpasslibrary.reader.reader.session.ReaderProgressLoadFailure
import com.secondpasslibrary.reader.reader.session.ReaderSessionContext
import com.secondpasslibrary.reader.reader.session.ReaderSessionCoordinator
import com.secondpasslibrary.reader.reader.session.ReaderSessionRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

internal sealed interface ReaderState {
    data object Resolving : ReaderState
    data object Downloading : ReaderState
    data object Opening : ReaderState
    data class Ready(
        val title: String,
        val engine: ReaderEngine,
        val session: ReaderSessionContext?,
        val restore: ReaderProgressRestore,
        val localOnly: Boolean = false
    ) : ReaderState
    data class Failure(val kind: ReaderFailure) : ReaderState
}

internal enum class ReaderProgressRestore {
    NOT_NEEDED,
    WAITING,
    RESTORED,
    SKIPPED
}

internal enum class ReaderFailure { DOWNLOAD, OPEN, NO_EPUB, OFFLINE_ASSET_UNAVAILABLE, SESSION }

internal sealed interface ReaderConnectionEvent {
    data object AuthenticationRejected : ReaderConnectionEvent
}

// Lifecycle, restore, and local/server progress stages remain explicit.
@Suppress("TooManyFunctions")
internal class ReaderController(
    private val assetResolver: ReaderBookAssetResolver,
    private val engineOpener: ReaderEngineOpener,
    private val sessionCoordinator: ReaderSessionCoordinator,
    private val scope: CoroutineScope,
    private val appearanceStore: ReaderAppearanceStore = DefaultReaderAppearanceStore,
    private val progressPersistenceScope: CoroutineScope = scope,
    private val launchPolicy: ReaderLaunchAdmission = ReaderLaunchAdmission { _, _, _, _ ->
        ReaderLaunchDecision.ONLINE
    },
    private val localStateStore: LocalReaderStateStore? = null,
    private val onSyncRequested: () -> Unit = {}
) {
    private val mutableState = MutableStateFlow<ReaderState>(ReaderState.Resolving)
    val state = mutableState.asStateFlow()
    private val connectionEventChannel = Channel<ReaderConnectionEvent>(Channel.BUFFERED)
    val connectionEvents = connectionEventChannel.receiveAsFlow()
    private val progressController = ReaderProgressController(scope)
    val progress = progressController.state
    private val progressPersistence = localStateStore?.let {
        ReaderProgressPersistenceController(progressPersistenceScope, it, onSyncRequested)
    }
    private var job: Job? = null
    private var progressCaptureFallbackJob: Job? = null
    private var request: ReaderRequest? = null

    fun initialize(
        profile: ConnectionProfile,
        profileId: String,
        bookId: String,
        existingSessionId: String?,
        titleHint: String? = null,
        availability: AppAvailability = AppAvailability.Online
    ) {
        val next = ReaderRequest(
            profile,
            profileId,
            bookId,
            existingSessionId,
            titleHint,
            availability
        )
        if (request == next && (job?.isActive == true || state.value is ReaderState.Ready)) return
        request = next
        load(next)
    }

    fun retry(availability: AppAvailability? = null) {
        request?.let { current ->
            load(
                current.copy(
                    availability =
                        availability ?: current.availability
                )
            )
        }
    }

    fun acceptReconciledSession(expectedLocalSessionId: String, session: ReaderSessionContext) {
        val ready = mutableState.value as? ReaderState.Ready ?: return
        if (ready.session?.sessionId != expectedLocalSessionId) return
        mutableState.value = ready.copy(session = session, localOnly = false)
    }

    suspend fun flushLatestProgress(): ReaderProgressFlushResult {
        val result = progressPersistence?.flushLatestLocal() ?: ReaderProgressFlushResult.CLEAN
        return result
    }

    fun updateAppearance(appearance: ReaderAppearance) {
        val engine = (state.value as? ReaderState.Ready)?.engine ?: return
        scope.launch {
            engine.appearance.update(appearance)
            runCatching { appearanceStore.write(appearance) }
        }
    }

    fun close(onProgressSyncClosed: () -> Unit = {}) {
        job?.cancel()
        progressCaptureFallbackJob?.cancel()
        progressCaptureFallbackJob = null
        progressController.stopCapture()
        connectionEventChannel.close()
        progressPersistenceScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                flushLatestProgress()
            } finally {
                progressController.reset()
                progressPersistence?.close()
                closeEngine()
                onProgressSyncClosed()
            }
        }
    }

    @Suppress("LongMethod") // Preserves staged engine ownership and failure cleanup in one job.
    private fun load(request: ReaderRequest) {
        job?.cancel()
        progressCaptureFallbackJob?.cancel()
        progressCaptureFallbackJob = null
        progressController.stopCapture()
        val previousEngine = (mutableState.value as? ReaderState.Ready)?.engine
        mutableState.value = ReaderState.Resolving
        job = scope.launch {
            var openedEngine: ReaderEngine? = null
            var failureKind = ReaderFailure.DOWNLOAD
            try {
                withContext(NonCancellable) {
                    try {
                        flushLatestProgress()
                    } finally {
                        progressPersistence?.reset()
                        progressController.reset()
                        previousEngine?.close()
                    }
                }
                val result = runCatching {
                    val initialAppearance = async { appearanceStore.readOrDefault() }
                    val resolved = resolveReaderBook(launchPolicy, assetResolver, request) {
                        if (isActive) mutableState.value = ReaderState.Downloading
                    }
                    val book = resolved.book
                    coroutineContext.ensureActive()
                    failureKind = ReaderFailure.OPEN
                    mutableState.value = ReaderState.Opening
                    val engine = engineOpener.open(book.file, initialAppearance.await())
                    openedEngine = engine
                    coroutineContext.ensureActive()
                    val ready = if (resolved.localOnly) {
                        prepareLocalReady(request, book.title, engine)
                    } else {
                        failureKind = ReaderFailure.SESSION
                        prepareReady(request, book.title, engine)
                    }
                    coroutineContext.ensureActive()
                    ready.session?.let { progressController.prepare(it, engine) }
                    mutableState.value = ready
                    openedEngine = null
                    ready.session?.let { session ->
                        scheduleProgressCaptureFallback(ready, session)
                    }
                    restoreProgress(ready)
                }
                (result.exceptionOrNull() as? CancellationException)?.let { throw it }
                result.fold(
                    onSuccess = { ready ->
                        progressCaptureFallbackJob?.cancel()
                        progressCaptureFallbackJob = null
                        mutableState.value = ready
                        ready.session?.let { session ->
                            startProgressPersistence(session)
                        }
                        openedEngine = null
                    },
                    onFailure = { failure ->
                        coroutineContext.ensureActive()
                        if (failure is SplClientException.AuthenticationRejected) {
                            connectionEventChannel.trySend(
                                ReaderConnectionEvent.AuthenticationRejected
                            )
                        }
                        val kind = when (failure) {
                            is ReaderOfflineAssetUnavailableException ->
                                ReaderFailure.OFFLINE_ASSET_UNAVAILABLE

                            is ReaderEpubUnavailableException -> ReaderFailure.NO_EPUB

                            else -> failureKind
                        }
                        progressController.reset()
                        progressPersistence?.reset()
                        mutableState.value = ReaderState.Failure(kind)
                    }
                )
            } finally {
                openedEngine?.close()
            }
        }
    }

    private fun startProgressPersistence(session: ReaderSessionContext) {
        progressController.enableAfterStartupRestore()
        val current = requireNotNull(request)
        progressPersistence?.start(
            LocalReaderAccountKey.from(current.profile.serverOrigin, current.profileId),
            session,
            progressController.state
        )
    }

    private fun scheduleProgressCaptureFallback(
        ready: ReaderState.Ready,
        session: ReaderSessionContext
    ) {
        progressCaptureFallbackJob?.cancel()
        progressCaptureFallbackJob = scope.launch {
            delay(STARTUP_PROGRESS_RESTORE_TIMEOUT_MILLIS)
            val current = mutableState.value as? ReaderState.Ready ?: return@launch
            if (
                current.engine !== ready.engine ||
                current.session?.sessionId != session.sessionId
            ) {
                return@launch
            }
            if (current.restore == ReaderProgressRestore.WAITING) {
                mutableState.value = current.copy(restore = ReaderProgressRestore.SKIPPED)
                current.engine.positionRetention.completeStartupRestore(null)
            }
            startProgressPersistence(session)
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
        val retained = localStateStore?.retainServerSession(
            LocalReaderAccountKey.from(request.profile.serverOrigin, request.profileId),
            request.bookId,
            session
        ) ?: session
        return ReaderState.Ready(
            title = title,
            engine = engine,
            session = retained,
            restore = if (retained.savedProgressCfi == null) {
                ReaderProgressRestore.NOT_NEEDED
            } else {
                ReaderProgressRestore.WAITING
            }
        )
    }

    private suspend fun prepareLocalReady(
        request: ReaderRequest,
        title: String,
        engine: ReaderEngine
    ): ReaderState.Ready {
        val store = requireNotNull(localStateStore) {
            "Offline Reader requires local Reader state storage."
        }
        val session = store.selectOfflineSession(
            LocalReaderAccountKey.from(request.profile.serverOrigin, request.profileId),
            request.bookId
        )
        return ReaderState.Ready(
            title = title,
            engine = engine,
            session = session,
            restore = if (session.savedProgressCfi == null) {
                ReaderProgressRestore.NOT_NEEDED
            } else {
                ReaderProgressRestore.WAITING
            },
            localOnly = true
        )
    }

    private suspend fun restoreProgress(ready: ReaderState.Ready): ReaderState.Ready {
        val restored = try {
            withTimeoutOrNull(STARTUP_PROGRESS_RESTORE_TIMEOUT_MILLIS) {
                restoreSavedProgress(ready)
            } ?: ready.copy(restore = ReaderProgressRestore.SKIPPED)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            ready.copy(restore = ReaderProgressRestore.SKIPPED)
        }
        restored.engine.positionRetention.completeStartupRestore(
            restored.restoredStartupPosition()
        )
        return restored
    }

    private companion object {
        const val STARTUP_PROGRESS_RESTORE_TIMEOUT_MILLIS = 15_000L
    }
}

private suspend fun restoreSavedProgress(ready: ReaderState.Ready): ReaderState.Ready {
    val rawCfi = ready.session?.savedProgressCfi
    val cfi = rawCfi?.let { runCatching { EpubCfi(it) }.getOrNull() }
    return when {
        rawCfi == null -> ready
        cfi == null -> ready.copy(restore = ReaderProgressRestore.SKIPPED)
        else -> restoreValidProgress(ready, cfi)
    }
}

private suspend fun restoreValidProgress(
    ready: ReaderState.Ready,
    cfi: EpubCfi
): ReaderState.Ready {
    val navigator = ready.engine.cfiNavigator
    val firstAttempt = navigator.awaitNavigationAvailable().then { navigator.goTo(cfi) }
    val outcome = if (firstAttempt.isTransientRestoreFailure()) {
        navigator.awaitNavigationAvailable().then { navigator.goTo(cfi) }
    } else {
        firstAttempt
    }
    return ready.copy(
        restore = if (outcome is EpubCfiOutcome.Success) {
            ReaderProgressRestore.RESTORED
        } else {
            ReaderProgressRestore.SKIPPED
        }
    )
}

private fun ReaderState.Ready.restoredStartupPosition(): EpubCfi? = session?.savedProgressCfi
    ?.takeIf { restore == ReaderProgressRestore.RESTORED }
    ?.let { runCatching { EpubCfi(it) }.getOrNull() }

private data object DefaultReaderAppearanceStore : ReaderAppearanceStore {
    override suspend fun read() = ReaderAppearance()

    override suspend fun write(appearance: ReaderAppearance) = Unit
}

private suspend fun ReaderAppearanceStore.readOrDefault(): ReaderAppearance =
    runCatching { read() }.getOrDefault(ReaderAppearance())

private data class ReaderRequest(
    val profile: ConnectionProfile,
    val profileId: String,
    val bookId: String,
    val existingSessionId: String?,
    val titleHint: String?,
    val availability: AppAvailability
) {
    init {
        require(profileId.isNotBlank()) { "Profile ID must not be blank." }
        require(bookId.isNotBlank()) { "Book ID must not be blank." }
        require(existingSessionId == null || existingSessionId.isNotBlank()) {
            "Existing Reading Session ID must not be blank."
        }
    }
}

private data class ReaderBookResolution(
    val book: com.secondpasslibrary.reader.reader.asset.ResolvedReaderBook,
    val localOnly: Boolean
)

private class ReaderOfflineAssetUnavailableException : Exception()

private suspend fun resolveReaderBook(
    launchPolicy: ReaderLaunchAdmission,
    assetResolver: ReaderBookAssetResolver,
    request: ReaderRequest,
    onDownloadStarted: () -> Unit
): ReaderBookResolution {
    val launch = launchPolicy.decide(
        request.availability,
        request.profile,
        request.profileId,
        request.bookId
    )
    if (launch == ReaderLaunchDecision.OFFLINE_ASSET_UNAVAILABLE) {
        throw ReaderOfflineAssetUnavailableException()
    }
    val localOnly = launch == ReaderLaunchDecision.LOCAL_AVAILABLE
    val book = assetResolver.resolve(
        ReaderBookAssetRequest(
            request.profile,
            request.profileId,
            request.bookId,
            request.titleHint,
            localOnly
        ),
        onDownloadStarted
    )
    return ReaderBookResolution(book, localOnly)
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
