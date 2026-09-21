package com.secondpasslibrary.reader.library.chrome

import com.secondpasslibrary.client.LibraryScope
import com.secondpasslibrary.reader.connection.AuthenticatedClientProvider
import com.secondpasslibrary.reader.connection.AuthenticatedSessionIdentity
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.connection.authenticatedSessionIdentity
import com.secondpasslibrary.reader.coroutines.runSuspendCatching
import com.secondpasslibrary.reader.library.LibraryConnectionEvent
import com.secondpasslibrary.reader.library.LibraryFailure
import com.secondpasslibrary.reader.library.toLibraryFailure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@Suppress("TooManyFunctions") // Bounded group/tag lifecycle and retry intents remain explicit.
internal class LibraryFilterVocabularyController(
    private val clientProvider: AuthenticatedClientProvider,
    private val coroutineScope: CoroutineScope
) {
    private val mutableState = MutableStateFlow(LibraryFilterVocabularyState())
    val state = mutableState.asStateFlow()

    private val connectionEventChannel = Channel<LibraryConnectionEvent>(Channel.BUFFERED)
    val connectionEvents = connectionEventChannel.receiveAsFlow()

    private var profile: ConnectionProfile? = null
    private var connectionIdentity: AuthenticatedSessionIdentity? = null
    private var advancedGroupsEnabled: Boolean? = null
    private var tagScope: LibraryScope = LibraryScope.Global
    private var authorityGeneration = 0L
    private var groupsJob: Job? = null
    private var tagsJob: Job? = null

    fun prepare(profile: ConnectionProfile, advancedGroupsEnabled: Boolean, scope: LibraryScope) {
        val nextIdentity = profile.authenticatedSessionIdentity
        val connectionChanged =
            nextIdentity != connectionIdentity ||
                advancedGroupsEnabled != this.advancedGroupsEnabled
        this.profile = profile
        if (connectionChanged) {
            authorityGeneration += 1
            groupsJob?.cancel()
            groupsJob = null
            tagsJob?.cancel()
            tagsJob = null
            connectionIdentity = nextIdentity
            this.advancedGroupsEnabled = advancedGroupsEnabled
            tagScope = scope
            mutableState.value =
                LibraryFilterVocabularyState(
                    groupSelector = LibraryGroupSelectorState(loading = advancedGroupsEnabled),
                    tagSelector = LibraryTagSelectorState(loading = true)
                )
        } else if (scope != tagScope) {
            selectScope(scope)
            return
        }
        ensureVocabularyLoaded()
    }

    fun selectScope(scope: LibraryScope) {
        if (scope == tagScope) return
        authorityGeneration += 1
        tagsJob?.cancel()
        tagsJob = null
        tagScope = scope
        mutableState.value =
            state.value.copy(tagSelector = LibraryTagSelectorState(loading = true))
        loadTags()
    }

    fun retryGroups() = loadGroups()

    fun retryTags() = loadTags()

    fun deactivate() {
        authorityGeneration += 1
        profile = null
        connectionIdentity = null
        advancedGroupsEnabled = null
        tagScope = LibraryScope.Global
        groupsJob?.cancel()
        groupsJob = null
        tagsJob?.cancel()
        tagsJob = null
        mutableState.value = LibraryFilterVocabularyState()
        discardConnectionEvents()
    }

    fun close() {
        groupsJob?.cancel()
        tagsJob?.cancel()
    }

    private fun ensureVocabularyLoaded() {
        if (advancedGroupsEnabled == true && !state.value.groupSelector.loaded) loadGroups()
        if (!state.value.tagSelector.loaded) loadTags()
    }

    private fun loadGroups() {
        if (advancedGroupsEnabled != true || groupsJob?.isActive == true) return
        val requestContext = requestContext() ?: return
        mutableState.value =
            state.value.copy(
                groupSelector = state.value.groupSelector.copy(loading = true, failure = null)
            )
        groupsJob = coroutineScope.launch {
            val result = runSuspendCatching {
                clientProvider.forProfile(requestContext.profile).library.loadAllGroups()
            }
            if (requestContext.generation != authorityGeneration ||
                requestContext.identity != connectionIdentity ||
                advancedGroupsEnabled != true
            ) {
                return@launch
            }
            result.fold(
                onSuccess = { groups ->
                    mutableState.value =
                        state.value.copy(
                            groupSelector =
                                LibraryGroupSelectorState(loaded = true, groups = groups)
                        )
                },
                onFailure = { applyGroupFailure(it) }
            )
        }
    }

    private fun loadTags() {
        if (tagsJob?.isActive == true) return
        val requestContext = requestContext() ?: return
        val requestedScope = tagScope
        mutableState.value =
            state.value.copy(
                tagSelector = state.value.tagSelector.copy(loading = true, failure = null)
            )
        tagsJob = coroutineScope.launch {
            val result = runSuspendCatching {
                clientProvider
                    .forProfile(requestContext.profile)
                    .library
                    .loadAllTags(requestedScope)
            }
            if (requestContext.generation != authorityGeneration ||
                requestContext.identity != connectionIdentity ||
                requestedScope != tagScope
            ) {
                return@launch
            }
            result.fold(
                onSuccess = { tags ->
                    mutableState.value =
                        state.value.copy(
                            tagSelector = LibraryTagSelectorState(loaded = true, tags = tags)
                        )
                },
                onFailure = { applyTagFailure(it) }
            )
        }
    }

    private fun applyGroupFailure(failure: Throwable) {
        val classified = failure.toLibraryFailure()
        mutableState.value =
            state.value.copy(groupSelector = LibraryGroupSelectorState(failure = classified))
        reportAuthenticationRejection(classified)
    }

    private fun applyTagFailure(failure: Throwable) {
        val classified = failure.toLibraryFailure()
        mutableState.value =
            state.value.copy(tagSelector = LibraryTagSelectorState(failure = classified))
        reportAuthenticationRejection(classified)
    }

    private fun reportAuthenticationRejection(failure: LibraryFailure) {
        if (failure == LibraryFailure.AUTHENTICATION_REJECTED) {
            connectionEventChannel.trySend(LibraryConnectionEvent.AuthenticationRejected)
        }
    }

    private fun requestContext(): VocabularyRequestContext? {
        val activeProfile = profile
        val activeIdentity = connectionIdentity
        return if (activeProfile != null && activeIdentity != null) {
            VocabularyRequestContext(activeProfile, activeIdentity, authorityGeneration)
        } else {
            null
        }
    }

    private fun discardConnectionEvents() {
        while (connectionEventChannel.tryReceive().isSuccess) {
            // Events from the ended authority lifetime must not survive a later reconnect.
        }
    }
}

private data class VocabularyRequestContext(
    val profile: ConnectionProfile,
    val identity: AuthenticatedSessionIdentity,
    val generation: Long
)
