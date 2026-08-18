package com.secondpasslibrary.reader.library

import com.secondpasslibrary.client.LibraryCatalogTag
import com.secondpasslibrary.client.LibraryScope
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

@Suppress("TooManyFunctions") // Parent facade exposes bounded cross-axis coordination intents.
internal class LibraryController(
    private val clientProvider: AuthenticatedClientProvider,
    displayPreferenceStore: LibraryDisplayPreferenceStore,
    private val scope: CoroutineScope,
    val books: LibraryBooksController =
        LibraryBooksController(clientProvider, displayPreferenceStore, scope),
    val authors: LibraryAuthorsController = LibraryAuthorsController(clientProvider, scope),
    val series: LibrarySeriesController = LibrarySeriesController(clientProvider, scope),
    val bookDetail: LibraryBookDetailController = LibraryBookDetailController(clientProvider, scope)
) {
    private val chrome = MutableStateFlow(LibraryChromeState())
    val state: StateFlow<LibraryState> =
        LibraryStateFlow(chrome, books.state, authors.state, series.state, bookDetail.state)

    private val connectionEventChannel = Channel<LibraryConnectionEvent>(Channel.BUFFERED)
    val connectionEvents =
        merge(
            connectionEventChannel.receiveAsFlow(),
            books.connectionEvents,
            authors.connectionEvents,
            series.connectionEvents,
            bookDetail.connectionEvents
        )

    private var profile: ConnectionProfile? = null
    private var entryIdentity: String? = null
    private var connectionIdentity: String? = null
    private var groupsJob: Job? = null
    private var tagsJob: Job? = null

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
            if (sameConnection && advancedGroupsEnabled) previous.scope else LibraryScope.Global
        val preserveTag = sameConnection && previous.scope == selectedScope
        val selectedTag = previous.selectedTag.takeIf { preserveTag }
        this.profile = profile
        bookDetail.prepare(profile)
        bookDetail.clear()
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
                    },
                selectedTag = selectedTag,
                tagSelector =
                    if (preserveTag) {
                        previous.tagSelector
                    } else {
                        LibraryTagSelectorState(
                            loading = true
                        )
                    }
            )
        when (entry) {
            LibraryBooksEntry.Browse ->
                books.initialize(
                    profile,
                    LibraryBooksMode.BROWSE,
                    "",
                    selectedScope,
                    selectedTag?.slug
                )

            is LibraryBooksEntry.BroadSearch ->
                books.initialize(
                    profile,
                    LibraryBooksMode.BROAD_SEARCH,
                    entry.query,
                    selectedScope,
                    selectedTag?.slug
                )
        }
        authors.prepare(profile, selectedScope, selectedTag?.slug)
        series.prepare(profile, selectedScope, selectedTag?.slug)
        if (advancedGroupsEnabled && !chrome.value.groupSelector.loaded) loadGroups()
        if (!chrome.value.tagSelector.loaded) loadTags()
    }

    fun selectScope(selected: LibraryScope) {
        if (chrome.value.resultKind == LibraryResultKind.BOOK_DETAIL) clearBookDetail()
        var current = chrome.value
        if (selected == current.scope) return
        if (selected is LibraryScope.Group &&
            current.groupSelector.groups.none { it.id == selected.id }
        ) {
            return
        }
        if (current.isSelectedEntityBooks) {
            clearSelectedEntity()
            current = chrome.value
        }
        tagsJob?.cancel()
        chrome.value =
            current.copy(
                scope = selected,
                selectedTag = null,
                tagSelector = LibraryTagSelectorState(loading = true)
            )
        if (current.resultKind == LibraryResultKind.BOOKS) {
            books.selectScope(selected, tagSlug = null)
        }
        authors.selectScope(
            selected,
            tagSlug = null,
            activate = current.axis == LibraryAxis.AUTHORS
        )
        series.selectScope(
            selected,
            tagSlug = null,
            activate = current.axis == LibraryAxis.SERIES
        )
        loadTags()
    }

    fun selectAxis(selected: LibraryAxis) {
        if (chrome.value.resultKind == LibraryResultKind.BOOK_DETAIL) clearBookDetail()
        var current = chrome.value
        if (selected == current.axis) return
        if (current.isSelectedEntityBooks) {
            clearSelectedEntity()
            current = chrome.value
        }
        chrome.value = current.copy(axis = selected, resultKind = selected.indexResultKind)
        when (selected) {
            LibraryAxis.BOOKS -> {
                books.clearEntityFilter()
                books.selectScope(current.scope, current.selectedTag?.slug)
                books.activate()
            }

            LibraryAxis.AUTHORS -> authors.activate()

            LibraryAxis.SERIES -> series.activate()
        }
    }

    fun commitSearch(query: String) {
        if (chrome.value.resultKind == LibraryResultKind.BOOKS) {
            when (books.state.value.mode) {
                LibraryBooksMode.BROWSE -> books.commitBrowseQuery(query)
                LibraryBooksMode.BROAD_SEARCH -> books.commitBroadSearch(query)
            }
            return
        }
        when (chrome.value.axis) {
            LibraryAxis.BOOKS -> error("Books axis must render Books results.")
            LibraryAxis.AUTHORS -> authors.commitSearch(query)
            LibraryAxis.SERIES -> series.commitSearch(query)
        }
    }

    fun loadNextPage() {
        if (chrome.value.resultKind == LibraryResultKind.BOOKS) {
            books.loadNextPage()
            return
        }
        when (chrome.value.axis) {
            LibraryAxis.BOOKS -> error("Books axis must render Books results.")
            LibraryAxis.AUTHORS -> authors.loadNextPage()
            LibraryAxis.SERIES -> series.loadNextPage()
        }
    }

    fun retry() {
        if (chrome.value.resultKind == LibraryResultKind.BOOKS) {
            books.retry()
            return
        }
        when (chrome.value.axis) {
            LibraryAxis.BOOKS -> error("Books axis must render Books results.")
            LibraryAxis.AUTHORS -> authors.retry()
            LibraryAxis.SERIES -> series.retry()
        }
    }

    fun selectAuthor(authorId: String) {
        if (chrome.value.axis != LibraryAxis.AUTHORS) {
            selectAxis(LibraryAxis.AUTHORS)
        }
        authors.selectAuthor(authorId)
        books.showAuthorBooks(authorId, chrome.value.scope)
        chrome.value = chrome.value.copy(resultKind = LibraryResultKind.BOOKS)
    }

    fun selectSeries(seriesId: String) {
        if (chrome.value.axis != LibraryAxis.SERIES) {
            selectAxis(LibraryAxis.SERIES)
        }
        series.selectSeries(seriesId)
        books.showSeriesBooks(seriesId, chrome.value.scope)
        chrome.value = chrome.value.copy(resultKind = LibraryResultKind.BOOKS)
    }

    fun clearSelectedEntity() {
        when (chrome.value.axis) {
            LibraryAxis.AUTHORS -> authors.clearSelection()
            LibraryAxis.SERIES -> series.clearSelection()
            LibraryAxis.BOOKS -> Unit
        }
        books.clearEntityFilter()
        chrome.value = chrome.value.copy(resultKind = chrome.value.axis.indexResultKind)
    }

    fun selectBook(bookId: String) {
        if (chrome.value.resultKind != LibraryResultKind.BOOKS) return
        bookDetail.select(bookId)
        chrome.value = chrome.value.copy(resultKind = LibraryResultKind.BOOK_DETAIL)
    }

    fun clearBookDetail() {
        if (chrome.value.resultKind != LibraryResultKind.BOOK_DETAIL) return
        bookDetail.clear()
        chrome.value = chrome.value.copy(resultKind = LibraryResultKind.BOOKS)
    }

    fun selectBookAuthor(authorId: String) {
        if (chrome.value.resultKind != LibraryResultKind.BOOK_DETAIL) return
        bookDetail.clear()
        selectAuthor(authorId)
    }

    fun selectBookSeries(seriesId: String) {
        if (chrome.value.resultKind != LibraryResultKind.BOOK_DETAIL) return
        bookDetail.clear()
        selectSeries(seriesId)
    }

    fun selectBookTag(tagId: String, tagSlug: String) {
        if (chrome.value.resultKind != LibraryResultKind.BOOK_DETAIL) return
        val tag = chrome.value.tagSelector.tags.firstOrNull {
            it.id == tagId && it.slug == tagSlug
        } ?: return
        bookDetail.clear()
        chrome.value = chrome.value.copy(resultKind = LibraryResultKind.BOOKS)
        selectTag(tag)
    }

    fun retryGroups() = loadGroups()

    fun selectTag(tag: LibraryCatalogTag?) {
        val current = chrome.value
        if (tag != null &&
            current.tagSelector.tags.none { it.id == tag.id && it.slug == tag.slug }
        ) {
            return
        }
        val selected = tag.takeUnless { current.selectedTag?.id == tag?.id }
        chrome.value = current.copy(selectedTag = selected)
        val slug = selected?.slug
        books.selectTag(slug, activate = current.resultKind == LibraryResultKind.BOOKS)
        authors.selectTag(
            slug,
            activate = current.axis == LibraryAxis.AUTHORS &&
                current.resultKind == LibraryResultKind.AUTHOR_INDEX
        )
        series.selectTag(
            slug,
            activate = current.axis == LibraryAxis.SERIES &&
                current.resultKind == LibraryResultKind.SERIES_INDEX
        )
    }

    fun retryTags() = loadTags()

    fun close() {
        groupsJob?.cancel()
        tagsJob?.cancel()
        books.close()
        authors.close()
        series.close()
        bookDetail.close()
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
                clientProvider.forProfile(activeProfile).library.loadAllGroups()
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

    private fun loadTags() {
        val activeProfile = profile ?: return
        if (tagsJob?.isActive == true) return
        val requestedScope = chrome.value.scope
        chrome.value =
            chrome.value.copy(
                tagSelector = chrome.value.tagSelector.copy(loading = true, failure = null)
            )
        tagsJob = scope.launch {
            val result = runCatching {
                clientProvider.forProfile(activeProfile).library.loadAllTags(requestedScope)
            }
            (result.exceptionOrNull() as? CancellationException)?.let { throw it }
            if (chrome.value.scope != requestedScope) return@launch
            result.fold(
                onSuccess = { tags ->
                    chrome.value =
                        chrome.value.copy(
                            tagSelector = LibraryTagSelectorState(loaded = true, tags = tags)
                        )
                },
                onFailure = { failure ->
                    val classified = failure.toLibraryFailure()
                    chrome.value =
                        chrome.value.copy(
                            tagSelector = LibraryTagSelectorState(failure = classified)
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
