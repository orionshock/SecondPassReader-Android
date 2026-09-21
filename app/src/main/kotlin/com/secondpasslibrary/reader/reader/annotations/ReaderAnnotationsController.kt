package com.secondpasslibrary.reader.reader.annotations

import com.secondpasslibrary.client.SplClientException
import com.secondpasslibrary.reader.connection.AuthenticatedSessionIdentity
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.connection.authenticatedSessionIdentity
import com.secondpasslibrary.reader.coroutines.runSuspendCatching
import com.secondpasslibrary.reader.reader.LocalReaderAccountKey
import com.secondpasslibrary.reader.reader.ReaderSessionAuthority
import com.secondpasslibrary.reader.reader.persistence.LocalReaderStateStore
import com.secondpasslibrary.reader.reader.session.ReaderSessionContext
import com.secondpasslibrary.reader.reader.session.ReaderSessionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

internal data class ReaderAnnotationsState(
    val sessionId: String? = null,
    val annotations: List<ReaderAnnotation> = emptyList(),
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val failure: ReaderAnnotationsFailure? = null
)

internal enum class ReaderAnnotationsFailure {
    AUTHENTICATION_REQUIRED,
    UNAVAILABLE
}

internal class ReaderAnnotationsController(
    private val loader: ReaderAnnotationsLoader,
    private val scope: CoroutineScope,
    private val localStore: LocalReaderStateStore
) {
    private val mutableState = MutableStateFlow(ReaderAnnotationsState())
    val state = mutableState.asStateFlow()

    private val authenticationRequired = Channel<Unit>(Channel.BUFFERED)
    val authenticationRequiredEvents = authenticationRequired.receiveAsFlow()

    private var connectionIdentity: AuthenticatedSessionIdentity? = null
    private var profile: ConnectionProfile? = null
    private var profileId: String? = null
    private var session: ReaderSessionContext? = null
    private var authority = ReaderSessionAuthority.SERVER
    private var generation = 0L
    private var loadJob: Job? = null

    fun select(
        profile: ConnectionProfile,
        profileId: String,
        session: ReaderSessionContext,
        authority: ReaderSessionAuthority
    ) {
        val sessionId = session.sessionId
        require(sessionId.isNotBlank()) { "Reading Session ID must not be blank." }
        val nextIdentity = profile.authenticatedSessionIdentity
        if (state.value.sessionId == sessionId && connectionIdentity == nextIdentity &&
            this.authority == authority
        ) {
            return
        }
        this.profile = profile
        this.profileId = profileId
        this.session = session
        this.authority = authority
        connectionIdentity = nextIdentity
        loadJob?.cancel()
        generation += 1
        mutableState.value = ReaderAnnotationsState(sessionId = sessionId, loading = true)
        load(sessionId, generation)
    }

    fun select(profile: ConnectionProfile, sessionId: String) {
        select(
            profile,
            profileId = profile.clientSessionId,
            session = ReaderSessionContext(sessionId, ReaderSessionStatus.ACTIVE, null),
            authority = ReaderSessionAuthority.SERVER
        )
    }

    fun retry() {
        val sessionId = state.value.sessionId ?: return
        if (state.value.failure == null) return
        load(sessionId, generation)
    }

    /** Replaces the visible projection after a committed local or authoritative Room change. */
    fun replaceProjection(sessionId: String, annotations: List<ReaderAnnotation>) {
        if (state.value.sessionId != sessionId) return
        loadJob?.cancel()
        generation += 1
        mutableState.value = ReaderAnnotationsState(
            sessionId = sessionId,
            annotations = annotations,
            loaded = true
        )
    }

    fun clear() {
        loadJob?.cancel()
        generation += 1
        mutableState.value = ReaderAnnotationsState()
        profileId = null
        session = null
        authority = ReaderSessionAuthority.SERVER
    }

    fun close() {
        clear()
        authenticationRequired.close()
    }

    private fun load(sessionId: String, activeGeneration: Long) {
        val context = loadContext() ?: return
        mutableState.value = state.value.copy(loading = true, failure = null)
        loadJob = scope.launch {
            val account = LocalReaderAccountKey.from(
                context.profile.serverId,
                context.profileId
            )
            val result = runSuspendCatching {
                when (authority) {
                    ReaderSessionAuthority.LOCAL ->
                        localStore.readAnnotations(account, sessionId)

                    ReaderSessionAuthority.SERVER -> {
                        val serverSessionId = requireNotNull(context.session.serverSessionId) {
                            "Server Reader authority requires a server Session identity."
                        }
                        val authoritative = loader.load(context.profile, serverSessionId)
                        localStore.replaceAuthoritativeAnnotations(
                            account,
                            sessionId,
                            authoritative
                        )
                        localStore.readAnnotations(account, sessionId)
                    }
                }
            }
            if (activeGeneration != generation) return@launch
            result.fold(
                onSuccess = { annotations ->
                    mutableState.value = ReaderAnnotationsState(
                        sessionId = sessionId,
                        annotations = annotations,
                        loaded = true
                    )
                },
                onFailure = { failure -> publishFailure(sessionId, failure) }
            )
        }
    }

    private fun loadContext(): LoadContext? {
        val activeProfile = profile
        val activeProfileId = profileId
        val activeSession = session
        return if (activeProfile != null && activeProfileId != null && activeSession != null) {
            LoadContext(activeProfile, activeProfileId, activeSession)
        } else {
            null
        }
    }

    private fun publishFailure(sessionId: String, failure: Throwable) {
        val kind = if (failure is SplClientException.AuthenticationRejected) {
            ReaderAnnotationsFailure.AUTHENTICATION_REQUIRED
        } else {
            ReaderAnnotationsFailure.UNAVAILABLE
        }
        mutableState.value = state.value.copy(
            sessionId = sessionId,
            loading = false,
            failure = kind
        )
        if (kind == ReaderAnnotationsFailure.AUTHENTICATION_REQUIRED) {
            authenticationRequired.trySend(Unit)
        }
    }

    private data class LoadContext(
        val profile: ConnectionProfile,
        val profileId: String,
        val session: ReaderSessionContext
    )
}
