package com.secondpasslibrary.reader.library

import com.secondpasslibrary.client.AuthorOrdering
import com.secondpasslibrary.client.BookOrdering
import com.secondpasslibrary.client.LibraryAuthor
import com.secondpasslibrary.client.LibraryCatalogTag
import com.secondpasslibrary.client.LibraryScope
import com.secondpasslibrary.client.LibrarySearchOrdering
import com.secondpasslibrary.client.LibrarySeries
import com.secondpasslibrary.client.SeriesOrdering
import com.secondpasslibrary.reader.connection.AuthenticatedClientProvider
import com.secondpasslibrary.reader.connection.AuthenticatedConnectionIdentity
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.connection.authenticatedConnectionIdentity
import com.secondpasslibrary.reader.library.axis.PagedLibraryAxisController
import com.secondpasslibrary.reader.library.axis.PagedLibraryAxisDetailState
import com.secondpasslibrary.reader.library.axis.libraryAuthorsAxisController
import com.secondpasslibrary.reader.library.axis.librarySeriesAxisController
import com.secondpasslibrary.reader.library.books.LibraryBooksController
import com.secondpasslibrary.reader.library.books.LibraryBooksEntry
import com.secondpasslibrary.reader.library.books.LibraryBooksLayout
import com.secondpasslibrary.reader.library.books.LibraryBooksMode
import com.secondpasslibrary.reader.library.books.LibraryDisplayPreferenceStore
import com.secondpasslibrary.reader.library.chrome.LibraryFilterVocabularyController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

@Suppress("TooManyFunctions") // Parent facade exposes bounded cross-axis coordination intents.
internal class LibraryController(
    clientProvider: AuthenticatedClientProvider,
    displayPreferenceStore: LibraryDisplayPreferenceStore,
    private val scope: CoroutineScope,
    private val books: LibraryBooksController =
        LibraryBooksController(clientProvider, displayPreferenceStore, scope),
    private val authors: PagedLibraryAxisController<LibraryAuthor, AuthorOrdering> =
        libraryAuthorsAxisController(clientProvider, scope),
    private val series: PagedLibraryAxisController<LibrarySeries, SeriesOrdering> =
        librarySeriesAxisController(clientProvider, scope),
    private val vocabulary: LibraryFilterVocabularyController =
        LibraryFilterVocabularyController(clientProvider, scope)
) {
    private var authorityMode = LibraryAuthorityMode.OFFLINE
    private val chrome = MutableStateFlow(LibraryChromeState())
    val state: StateFlow<LibraryState> =
        libraryStateFlow(
            scope,
            chrome,
            vocabulary.state,
            books.state,
            authors.state,
            series.state
        )

    val connectionEvents =
        merge(
            books.connectionEvents,
            authors.connectionEvents,
            series.connectionEvents,
            vocabulary.connectionEvents
        ).filter { authorityMode == LibraryAuthorityMode.ONLINE }

    private var entryKey: LibraryBooksEntry? = null
    private var connectionIdentity: AuthenticatedConnectionIdentity? = null
    private var advancedGroupsCapability: Boolean? = null
    private var pendingTagNavigation: LibraryExternalNavigation.Tag? = null
    private var pendingTagResolutionJob: Job? = null

    fun initialize(
        profile: ConnectionProfile,
        entry: LibraryBooksEntry,
        advancedGroupsEnabled: Boolean
    ) {
        authorityMode = LibraryAuthorityMode.ONLINE
        val nextConnectionIdentity = profile.authenticatedConnectionIdentity
        val sameConnection = hasSameConnection(nextConnectionIdentity, advancedGroupsEnabled)
        if (sameConnection && entry == entryKey) return
        if (!sameConnection) {
            pendingTagResolutionJob?.cancel()
            pendingTagNavigation = null
        }
        val previous = chrome.value
        val selectedScope =
            if (sameConnection && advancedGroupsEnabled) previous.scope else LibraryScope.Global
        val preserveTag = sameConnection && previous.scope == selectedScope
        val selectedTag = previous.selectedTag.takeIf { preserveTag }
        connectionIdentity = nextConnectionIdentity
        advancedGroupsCapability = advancedGroupsEnabled
        entryKey = entry
        chrome.value =
            LibraryChromeState(
                result = LibraryResultState.Books(books.state.value),
                scope = selectedScope,
                advancedGroupsEnabled = advancedGroupsEnabled,
                selectedTag = selectedTag
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
        vocabulary.prepare(profile, advancedGroupsEnabled, selectedScope)
    }

    fun initializeOffline(profile: ConnectionProfile, profileId: String, entry: LibraryBooksEntry) {
        authorityMode = LibraryAuthorityMode.OFFLINE
        connectionIdentity = null
        entryKey = null
        advancedGroupsCapability = null
        pendingTagResolutionJob?.cancel()
        pendingTagNavigation = null
        authors.deactivate()
        series.deactivate()
        vocabulary.deactivate()
        chrome.value = LibraryChromeState()
        val query = (entry as? LibraryBooksEntry.BroadSearch)?.query.orEmpty()
        books.initializeOffline(profile, profileId, query)
    }

    private fun hasSameConnection(
        identity: AuthenticatedConnectionIdentity,
        advancedGroupsEnabled: Boolean
    ): Boolean = identity == connectionIdentity && advancedGroupsEnabled == advancedGroupsCapability

    @Suppress("ReturnCount") // Offline capability rejection joins the existing validation exits.
    fun selectScope(selected: LibraryScope) {
        if (authorityMode != LibraryAuthorityMode.ONLINE) return
        var current = chrome.value
        if (selected == current.scope) return
        if (selected is LibraryScope.Group &&
            vocabulary.state.value.groupSelector.groups.none { it.id == selected.id }
        ) {
            return
        }
        if (current.isSelectedAuthorSeriesBooks) {
            clearSelectedAuthorSeries()
            current = chrome.value
        }
        chrome.value =
            current.copy(
                scope = selected,
                selectedTag = null
            )
        if (current.result.isBookResults) {
            books.selectScope(selected, tagSlug = null)
        }
        authors.selectScope(
            selected,
            tagSlug = null,
            activate = current.result is LibraryResultState.AuthorIndex
        )
        series.selectScope(
            selected,
            tagSlug = null,
            activate = current.result is LibraryResultState.SeriesIndex
        )
        vocabulary.selectScope(selected)
    }

    fun selectAxis(selected: LibraryAxis) {
        if (authorityMode != LibraryAuthorityMode.ONLINE) return
        var current = chrome.value
        if (selected == current.result.axis) {
            if (current.isSelectedAuthorSeriesBooks) clearSelectedAuthorSeries()
            return
        }
        if (current.isSelectedAuthorSeriesBooks) {
            clearSelectedAuthorSeries()
            current = chrome.value
        }
        chrome.value =
            current.copy(
                result = selected.indexResultState(
                    books.state.value,
                    authors.state.value,
                    series.state.value
                )
            )
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
        when (chrome.value.result) {
            is LibraryResultState.Books,
            is LibraryResultState.AuthorBooks,
            is LibraryResultState.SeriesBooks -> commitBooksSearch(query)

            is LibraryResultState.AuthorIndex -> authors.commitSearch(query)

            is LibraryResultState.SeriesIndex -> series.commitSearch(query)
        }
    }

    private fun commitBooksSearch(query: String) {
        when (books.state.value.mode) {
            LibraryBooksMode.BROWSE -> books.commitBrowseQuery(query)
            LibraryBooksMode.BROAD_SEARCH -> books.commitBroadSearch(query)
        }
    }

    fun changeBrowseOrdering(ordering: BookOrdering) = books.changeBrowseOrdering(ordering)

    fun changeBroadSearchOrdering(ordering: LibrarySearchOrdering) =
        books.changeBroadSearchOrdering(ordering)

    fun changeAuthorOrdering(ordering: AuthorOrdering) = authors.changeOrdering(ordering)

    fun changeSeriesOrdering(ordering: SeriesOrdering) = series.changeOrdering(ordering)

    fun setBookLayout(layout: LibraryBooksLayout) = books.setLayout(layout)

    fun refreshBooks() = books.refresh()

    fun loadNextPage() {
        when (chrome.value.result) {
            is LibraryResultState.Books,
            is LibraryResultState.AuthorBooks,
            is LibraryResultState.SeriesBooks -> books.loadNextPage()

            is LibraryResultState.AuthorIndex -> authors.loadNextPage()

            is LibraryResultState.SeriesIndex -> series.loadNextPage()
        }
    }

    fun retry() {
        when (chrome.value.result) {
            is LibraryResultState.Books,
            is LibraryResultState.AuthorBooks,
            is LibraryResultState.SeriesBooks -> books.retry()

            is LibraryResultState.AuthorIndex -> authors.retry()

            is LibraryResultState.SeriesIndex -> series.retry()
        }
    }

    fun retryAuthorDetail() = authors.retryDetail()

    fun retrySeriesDetail() = series.retryDetail()

    fun selectAuthor(authorId: String) {
        if (authorityMode != LibraryAuthorityMode.ONLINE) return
        if (chrome.value.result.axis != LibraryAxis.AUTHORS) {
            selectAxis(LibraryAxis.AUTHORS)
        }
        authors.select(authorId)
        books.showAuthorBooks(authorId, chrome.value.scope)
        chrome.value =
            chrome.value.copy(
                result =
                    LibraryResultState.AuthorBooks(
                        PagedLibraryAxisDetailState(authorId, loading = true),
                        authors.state.value.items.firstOrNull { it.id == authorId },
                        books.state.value
                    )
            )
    }

    fun selectSeries(seriesId: String) {
        if (authorityMode != LibraryAuthorityMode.ONLINE) return
        if (chrome.value.result.axis != LibraryAxis.SERIES) {
            selectAxis(LibraryAxis.SERIES)
        }
        series.select(seriesId)
        books.showSeriesBooks(seriesId, chrome.value.scope)
        chrome.value =
            chrome.value.copy(
                result =
                    LibraryResultState.SeriesBooks(
                        PagedLibraryAxisDetailState(seriesId, loading = true),
                        series.state.value.items.firstOrNull { it.id == seriesId },
                        books.state.value
                    )
            )
    }

    fun navigateTo(target: LibraryExternalNavigation) {
        if (authorityMode != LibraryAuthorityMode.ONLINE) return
        when (target) {
            is LibraryExternalNavigation.Author -> {
                val selectedId =
                    (chrome.value.result as? LibraryResultState.AuthorBooks)?.author?.id
                if (chrome.value.result.axis != LibraryAxis.AUTHORS || selectedId != target.id) {
                    selectAuthor(target.id)
                }
            }

            is LibraryExternalNavigation.Series -> {
                val selectedId =
                    (chrome.value.result as? LibraryResultState.SeriesBooks)?.series?.id
                if (chrome.value.result.axis != LibraryAxis.SERIES || selectedId != target.id) {
                    selectSeries(target.id)
                }
            }

            is LibraryExternalNavigation.Tag -> navigateToTag(target)
        }
    }

    fun clearSelectedAuthorSeries() {
        val current = chrome.value
        val next = when (current.result) {
            is LibraryResultState.Books -> current.result

            is LibraryResultState.AuthorIndex -> {
                authors.clearSelection()
                LibraryResultState.AuthorIndex(authors.state.value)
            }

            is LibraryResultState.AuthorBooks -> {
                authors.clearSelection()
                LibraryResultState.AuthorIndex(authors.state.value)
            }

            is LibraryResultState.SeriesIndex -> {
                series.clearSelection()
                LibraryResultState.SeriesIndex(series.state.value)
            }

            is LibraryResultState.SeriesBooks -> {
                series.clearSelection()
                LibraryResultState.SeriesIndex(series.state.value)
            }
        }
        books.clearEntityFilter()
        chrome.value = current.copy(result = next)
    }

    fun retryGroups() = vocabulary.retryGroups()

    fun selectTag(tag: LibraryCatalogTag?) {
        if (authorityMode != LibraryAuthorityMode.ONLINE) return
        val current = chrome.value
        val selectableTags =
            state.value.tagSelector.tags + vocabulary.state.value.tagSelector.tags
        if (tag != null &&
            selectableTags.none {
                it.id == tag.id && it.slug == tag.slug
            }
        ) {
            return
        }
        val selected = tag.takeUnless { current.selectedTag?.id == tag?.id }
        chrome.value = current.copy(selectedTag = selected)
        val slug = selected?.slug
        books.selectTag(slug, activate = current.result.isBookResults)
        authors.selectTag(
            slug,
            activate = current.result is LibraryResultState.AuthorIndex
        )
        series.selectTag(
            slug,
            activate = current.result is LibraryResultState.SeriesIndex
        )
    }

    fun retryTags() {
        vocabulary.retryTags()
        awaitPendingTagResolution()
    }

    fun close() {
        authorityMode = LibraryAuthorityMode.OFFLINE
        pendingTagResolutionJob?.cancel()
        vocabulary.close()
        books.close()
        authors.close()
        series.close()
    }

    private fun navigateToTag(target: LibraryExternalNavigation.Tag) {
        if (chrome.value.result.axis != LibraryAxis.BOOKS) selectAxis(LibraryAxis.BOOKS)
        val tag = vocabulary.state.value.tagSelector.tags.firstOrNull {
            it.id == target.id && it.slug == target.slug
        }
        if (tag != null) {
            pendingTagResolutionJob?.cancel()
            pendingTagNavigation = null
            if (chrome.value.selectedTag?.id != tag.id) selectTag(tag)
        } else {
            pendingTagNavigation = target
            if (!vocabulary.state.value.tagSelector.loading) vocabulary.retryTags()
            awaitPendingTagResolution()
        }
    }

    private fun awaitPendingTagResolution() {
        if (pendingTagNavigation == null) return
        pendingTagResolutionJob?.cancel()
        pendingTagResolutionJob = scope.launch {
            val tagState =
                vocabulary.state.map { it.tagSelector }.first { it.loaded || it.failure != null }
            if (tagState.loaded) applyPendingTagNavigation()
        }
    }

    private fun applyPendingTagNavigation() {
        val pending = pendingTagNavigation ?: return
        val tag = vocabulary.state.value.tagSelector.tags.firstOrNull {
            it.id == pending.id && it.slug == pending.slug
        } ?: return
        pendingTagNavigation = null
        selectTag(tag)
    }
}
