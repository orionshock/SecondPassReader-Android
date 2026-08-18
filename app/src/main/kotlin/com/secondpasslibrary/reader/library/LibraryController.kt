package com.secondpasslibrary.reader.library

import com.secondpasslibrary.client.LibraryGroupListOptions
import com.secondpasslibrary.client.LibraryGroupOrdering
import com.secondpasslibrary.reader.connection.AuthenticatedClientProvider
import com.secondpasslibrary.reader.connection.ConnectionProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

internal class LibraryController(
    private val clientProvider: AuthenticatedClientProvider,
    displayPreferenceStore: LibraryDisplayPreferenceStore,
    private val scope: CoroutineScope,
    val books: LibraryBooksController =
        LibraryBooksController(clientProvider, displayPreferenceStore, scope),
    val authors: LibraryAuthorsController = LibraryAuthorsController(),
    val series: LibrarySeriesController = LibrarySeriesController()
) {
    private val chrome = MutableStateFlow(LibraryChromeState())
    val state: StateFlow<LibraryState> = LibraryStateFlow(chrome, books.state)

    private val connectionEventChannel = Channel<LibraryConnectionEvent>(Channel.BUFFERED)
    val connectionEvents = merge(connectionEventChannel.receiveAsFlow(), books.connectionEvents)

    private var profile: ConnectionProfile? = null
    private var entryIdentity: String? = null
    private var connectionIdentity: String? = null
    private var groupsJob: Job? = null

    fun initialize(
        profile: ConnectionProfile,
        entry: LibraryBooksEntry,
        advancedGroupsEnabled: Boolean
    ) {
        val connection =
            "${profile.apiBaseUrl}\u0000${profile.clientSessionId}\u0000$advancedGroupsEnabled"
        val identity = "$connection\u0000$entry"
        if (identity == entryIdentity) return
        val previous = chrome.value
        val sameConnection = connection == connectionIdentity
        val selectedScope =
            if (sameConnection && advancedGroupsEnabled) previous.scope else LibraryScope.AllLibrary
        this.profile = profile
        connectionIdentity = connection
        entryIdentity = identity
        chrome.value =
            LibraryChromeState(
                axis = LibraryAxis.BOOKS,
                scope = selectedScope,
                advancedGroupsEnabled = advancedGroupsEnabled,
                groupSelector =
                    if (sameConnection && advancedGroupsEnabled) {
                        previous.groupSelector
                    } else {
                        LibraryGroupSelectorState(loading = advancedGroupsEnabled)
                    }
            )
        when (entry) {
            LibraryBooksEntry.Browse ->
                books.initialize(profile, LibraryBooksMode.BROWSE, "", selectedScope)

            is LibraryBooksEntry.BroadSearch ->
                books.initialize(
                    profile,
                    LibraryBooksMode.BROAD_SEARCH,
                    entry.query,
                    selectedScope
                )
        }
        if (advancedGroupsEnabled && !chrome.value.groupSelector.loaded) loadGroups()
    }

    fun selectScope(selected: LibraryScope) {
        val current = chrome.value
        if (selected == current.scope) return
        if (selected is LibraryScope.Group &&
            current.groupSelector.groups.none { it.id == selected.id }
        ) {
            return
        }
        chrome.value = current.copy(scope = selected)
        if (current.axis == LibraryAxis.BOOKS) books.selectScope(selected)
    }

    fun selectAxis(selected: LibraryAxis) {
        val current = chrome.value
        if (selected == current.axis) return
        chrome.value = current.copy(axis = selected)
        if (selected == LibraryAxis.BOOKS) books.selectScope(current.scope)
    }

    fun commitSearch(query: String) {
        when (books.state.value.mode) {
            LibraryBooksMode.BROWSE -> books.commitBrowseQuery(query)
            LibraryBooksMode.BROAD_SEARCH -> books.commitBroadSearch(query)
        }
    }

    fun retryGroups() = loadGroups()

    fun close() {
        groupsJob?.cancel()
        books.close()
    }

    private fun loadGroups() {
        val activeProfile = profile ?: return
        if (!chrome.value.advancedGroupsEnabled || groupsJob?.isActive == true) return
        chrome.value =
            chrome.value.copy(
                groupSelector = chrome.value.groupSelector.copy(loading = true, failure = null)
            )
        groupsJob = scope.launch {
            val result = runCatching {
                val groups = clientProvider.forProfile(activeProfile).library.groups
                buildList {
                    var pageNumber = 1
                    do {
                        val page =
                            groups.listGroups(
                                LibraryGroupListOptions(
                                    ordering = LibraryGroupOrdering.NAME,
                                    page = pageNumber,
                                    pageSize = MAX_GROUP_SELECTOR_PAGE_SIZE
                                )
                            )
                        addAll(page.results)
                        pageNumber += 1
                    } while (page.hasNext)
                }
            }
            (result.exceptionOrNull() as? CancellationException)?.let { throw it }
            result.fold(
                onSuccess = { groups ->
                    chrome.value =
                        chrome.value.copy(
                            groupSelector =
                                LibraryGroupSelectorState(loaded = true, groups = groups)
                        )
                },
                onFailure = { failure ->
                    val classified = failure.toLibraryFailure()
                    chrome.value =
                        chrome.value.copy(
                            groupSelector = LibraryGroupSelectorState(failure = classified)
                        )
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

private data class LibraryChromeState(
    val axis: LibraryAxis = LibraryAxis.BOOKS,
    val scope: LibraryScope = LibraryScope.AllLibrary,
    val advancedGroupsEnabled: Boolean = false,
    val groupSelector: LibraryGroupSelectorState = LibraryGroupSelectorState()
) {
    fun toState(books: LibraryBooksState) =
        LibraryState(axis, scope, advancedGroupsEnabled, groupSelector, books)
}

@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
private class LibraryStateFlow(
    private val chrome: StateFlow<LibraryChromeState>,
    private val books: StateFlow<LibraryBooksState>
) : StateFlow<LibraryState> {
    override val value: LibraryState
        get() = chrome.value.toState(books.value)

    override val replayCache: List<LibraryState>
        get() = listOf(value)

    override suspend fun collect(collector: FlowCollector<LibraryState>): Nothing {
        combine(chrome, books) { parent, booksState -> parent.toState(booksState) }
            .collect(collector)
        error("Library state sources completed unexpectedly.")
    }
}

private const val MAX_GROUP_SELECTOR_PAGE_SIZE = 200
