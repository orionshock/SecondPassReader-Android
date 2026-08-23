package com.secondpasslibrary.reader.library

import com.secondpasslibrary.client.AuthorOrdering
import com.secondpasslibrary.client.BookOrdering
import com.secondpasslibrary.client.LibraryCatalogTag
import com.secondpasslibrary.client.LibraryScope
import com.secondpasslibrary.client.LibrarySearchOrdering
import com.secondpasslibrary.client.SeriesOrdering
import com.secondpasslibrary.reader.connection.AuthenticatedClientProvider
import com.secondpasslibrary.reader.connection.AuthenticatedConnectionIdentity
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.connection.authenticatedConnectionIdentity
import com.secondpasslibrary.reader.library.axis.LibraryAuthorsController
import com.secondpasslibrary.reader.library.axis.LibrarySeriesController
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
    private val authors: LibraryAuthorsController =
        LibraryAuthorsController(clientProvider, scope),
    private val series: LibrarySeriesController = LibrarySeriesController(clientProvider, scope),
    private val vocabulary: LibraryFilterVocabularyController =
        LibraryFilterVocabularyController(clientProvider, scope)
) {
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
        )

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
                axis = LibraryAxis.BOOKS,
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

    private fun hasSameConnection(
        identity: AuthenticatedConnectionIdentity,
        advancedGroupsEnabled: Boolean
    ): Boolean = identity == connectionIdentity && advancedGroupsEnabled == advancedGroupsCapability

    fun selectScope(selected: LibraryScope) {
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
        vocabulary.selectScope(selected)
    }

    fun selectAxis(selected: LibraryAxis) {
        var current = chrome.value
        if (selected == current.axis) {
            if (current.isSelectedAuthorSeriesBooks) clearSelectedAuthorSeries()
            return
        }
        if (current.isSelectedAuthorSeriesBooks) {
            clearSelectedAuthorSeries()
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

    fun changeBrowseOrdering(ordering: BookOrdering) = books.changeBrowseOrdering(ordering)

    fun changeBroadSearchOrdering(ordering: LibrarySearchOrdering) =
        books.changeBroadSearchOrdering(ordering)

    fun changeAuthorOrdering(ordering: AuthorOrdering) = authors.changeOrdering(ordering)

    fun changeSeriesOrdering(ordering: SeriesOrdering) = series.changeOrdering(ordering)

    fun setBookLayout(layout: LibraryBooksLayout) = books.setLayout(layout)

    fun refreshBooks() = books.refresh()

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

    fun retryAuthorDetail() = authors.retryDetail()

    fun retrySeriesDetail() = series.retryDetail()

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

    fun navigateTo(target: LibraryExternalNavigation) {
        when (target) {
            is LibraryExternalNavigation.Author -> {
                val selectedId = chrome.value.takeIf { it.isSelectedAuthorSeriesBooks }
                    ?.let { authors.state.value.selected?.detail?.id }
                if (chrome.value.axis != LibraryAxis.AUTHORS || selectedId != target.id) {
                    selectAuthor(target.id)
                }
            }

            is LibraryExternalNavigation.Series -> {
                val selectedId = chrome.value.takeIf { it.isSelectedAuthorSeriesBooks }
                    ?.let { series.state.value.selected?.detail?.id }
                if (chrome.value.axis != LibraryAxis.SERIES || selectedId != target.id) {
                    selectSeries(target.id)
                }
            }

            is LibraryExternalNavigation.Tag -> navigateToTag(target)
        }
    }

    fun clearSelectedAuthorSeries() {
        when (chrome.value.axis) {
            LibraryAxis.AUTHORS -> authors.clearSelection()
            LibraryAxis.SERIES -> series.clearSelection()
            LibraryAxis.BOOKS -> Unit
        }
        books.clearEntityFilter()
        chrome.value = chrome.value.copy(resultKind = chrome.value.axis.indexResultKind)
    }

    fun retryGroups() = vocabulary.retryGroups()

    fun selectTag(tag: LibraryCatalogTag?) {
        val current = chrome.value
        if (tag != null &&
            vocabulary.state.value.tagSelector.tags.none {
                it.id == tag.id && it.slug == tag.slug
            }
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

    fun retryTags() {
        vocabulary.retryTags()
        awaitPendingTagResolution()
    }

    fun close() {
        pendingTagResolutionJob?.cancel()
        vocabulary.close()
        books.close()
        authors.close()
        series.close()
    }

    private fun navigateToTag(target: LibraryExternalNavigation.Tag) {
        if (chrome.value.axis != LibraryAxis.BOOKS) selectAxis(LibraryAxis.BOOKS)
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
