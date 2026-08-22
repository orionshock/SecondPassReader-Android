package com.secondpasslibrary.reader.marginalia.detail

import com.secondpasslibrary.reader.connection.AuthenticatedClientProvider
import com.secondpasslibrary.reader.connection.AuthenticatedConnectionIdentity
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.connection.authenticatedConnectionIdentity
import com.secondpasslibrary.reader.marginalia.MarginaliaConnectionEvent
import com.secondpasslibrary.reader.marginalia.MarginaliaFailure
import com.secondpasslibrary.reader.marginalia.detail.annotations.ReadingSessionAnnotationsController
import com.secondpasslibrary.reader.marginalia.detail.close.ReadingSessionCloseController
import com.secondpasslibrary.reader.marginalia.detail.metadata.ReadingSessionMetadataEditorController
import com.secondpasslibrary.reader.marginalia.toMarginaliaFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

internal class ReadingSessionDetailController(
    private val clientProvider: AuthenticatedClientProvider,
    private val coroutineScope: CoroutineScope,
    val annotations: ReadingSessionAnnotationsController =
        ReadingSessionAnnotationsController(clientProvider, coroutineScope),
    val metadataEditor: ReadingSessionMetadataEditorController =
        ReadingSessionMetadataEditorController(clientProvider, coroutineScope),
    val closeFlow: ReadingSessionCloseController =
        ReadingSessionCloseController(clientProvider, coroutineScope)
) {
    private val mutableState = MutableStateFlow(ReadingSessionDetailState())
    val state = mutableState.asStateFlow()

    private val connectionEventChannel = Channel<MarginaliaConnectionEvent>(Channel.BUFFERED)
    val connectionEvents = merge(
        connectionEventChannel.receiveAsFlow(),
        annotations.connectionEvents,
        metadataEditor.connectionEvents,
        closeFlow.connectionEvents
    )
    var onAuthoritativeUpdate: (
        (
            com.secondpasslibrary.client.ReadingSessionDetailResult
        ) -> Unit
    )? =
        null

    private var profile: ConnectionProfile? = null
    private var connectionIdentity: AuthenticatedConnectionIdentity? = null
    private var generation = 0L
    private var loadJob: Job? = null

    fun prepare(profile: ConnectionProfile) {
        val nextConnectionIdentity = profile.authenticatedConnectionIdentity
        this.profile = profile
        annotations.prepare(profile)
        metadataEditor.prepare(profile)
        closeFlow.prepare(profile)
        if (nextConnectionIdentity == connectionIdentity) return
        connectionIdentity = nextConnectionIdentity
        clear()
    }

    fun select(sessionId: String) {
        require(sessionId.isNotBlank()) { "Reading Session ID must not be blank." }
        if (profile == null) return
        loadJob?.cancel()
        generation += 1
        mutableState.value = ReadingSessionDetailState(sessionId = sessionId, loading = true)
        annotations.select(sessionId)
        load(sessionId, generation)
    }

    fun retry() {
        val sessionId = state.value.sessionId ?: return
        if (state.value.failure == null) return
        load(sessionId, generation)
    }

    fun beginMetadataEdit() {
        state.value.detail?.let(metadataEditor::begin)
    }

    fun saveMetadata() = metadataEditor.submit(::applyAuthoritativeDetail)

    fun beginClose() {
        state.value.detail?.let(closeFlow::begin)
    }

    fun confirmClose() = closeFlow.submit(::applyAuthoritativeDetail)

    fun clear() {
        loadJob?.cancel()
        generation += 1
        mutableState.value = ReadingSessionDetailState()
        annotations.clear()
        metadataEditor.reset()
        closeFlow.reset()
    }

    fun close() {
        loadJob?.cancel()
        annotations.close()
        metadataEditor.close()
        closeFlow.close()
    }

    private fun load(sessionId: String, activeGeneration: Long) {
        val activeProfile = profile ?: return
        mutableState.value = state.value.copy(loading = true, failure = null)
        loadJob = coroutineScope.launch {
            val result = runCatching {
                clientProvider.forProfile(activeProfile).marginalia.sessions.get(sessionId)
            }
            (result.exceptionOrNull() as? CancellationException)?.let { throw it }
            if (activeGeneration != generation) return@launch
            result.fold(
                onSuccess = { detail ->
                    mutableState.value =
                        ReadingSessionDetailState(sessionId = sessionId, detail = detail)
                },
                onFailure = { failure ->
                    val classified = failure.toMarginaliaFailure()
                    mutableState.value =
                        ReadingSessionDetailState(sessionId = sessionId, failure = classified)
                    if (classified == MarginaliaFailure.AUTHENTICATION_REJECTED) {
                        connectionEventChannel.trySend(
                            MarginaliaConnectionEvent.AuthenticationRejected
                        )
                    }
                }
            )
        }
    }

    private fun applyAuthoritativeDetail(
        detail: com.secondpasslibrary.client.ReadingSessionDetailResult
    ) {
        if (detail.session.summary.id != state.value.sessionId) return
        mutableState.value = state.value.copy(detail = detail, loading = false, failure = null)
        metadataEditor.reset()
        closeFlow.reset()
        onAuthoritativeUpdate?.invoke(detail)
    }
}
