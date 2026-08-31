package com.secondpasslibrary.reader.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondpasslibrary.client.AuthorOrdering
import com.secondpasslibrary.client.BookOrdering
import com.secondpasslibrary.client.LibraryCatalogTag
import com.secondpasslibrary.client.LibraryScope
import com.secondpasslibrary.client.LibrarySearchOrdering
import com.secondpasslibrary.client.SeriesOrdering
import com.secondpasslibrary.reader.connection.AuthenticatedClientProvider
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.library.books.LibraryBooksController
import com.secondpasslibrary.reader.library.books.LibraryBooksEntry
import com.secondpasslibrary.reader.library.books.LibraryBooksLayout
import com.secondpasslibrary.reader.library.books.LibraryBooksOrdering
import com.secondpasslibrary.reader.library.books.LibraryDisplayPreferenceStore
import com.secondpasslibrary.reader.library.offline.OfflineLibraryCatalog
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
@Suppress("TooManyFunctions") // Typed UI intents remain on the Library parent facade.
internal class LibraryViewModel
@Inject
constructor(
    clientProvider: AuthenticatedClientProvider,
    displayPreferenceStore: LibraryDisplayPreferenceStore,
    offlineCatalog: OfflineLibraryCatalog
) : ViewModel() {
    private val controller =
        LibraryController(
            clientProvider,
            displayPreferenceStore,
            viewModelScope,
            books = LibraryBooksController(
                clientProvider,
                displayPreferenceStore,
                viewModelScope,
                offlineCatalog
            )
        )

    val state = controller.state
    val connectionEvents = controller.connectionEvents

    fun initialize(
        profile: ConnectionProfile,
        entry: LibraryBooksEntry,
        advancedGroupsEnabled: Boolean
    ) = controller.initialize(profile, entry, advancedGroupsEnabled)

    fun initializeOffline(profile: ConnectionProfile, profileId: String, entry: LibraryBooksEntry) =
        controller.initializeOffline(profile, profileId, entry)

    fun commitSearch(query: String) = controller.commitSearch(query)

    fun changeBrowseOrdering(ordering: BookOrdering) = controller.changeBrowseOrdering(ordering)

    fun changeBroadSearchOrdering(ordering: LibrarySearchOrdering) =
        controller.changeBroadSearchOrdering(ordering)

    fun changeAuthorOrdering(ordering: AuthorOrdering) = controller.changeAuthorOrdering(ordering)

    fun changeSeriesOrdering(ordering: SeriesOrdering) = controller.changeSeriesOrdering(ordering)

    fun loadNextPage() = controller.loadNextPage()

    fun setLayout(layout: LibraryBooksLayout) = controller.setBookLayout(layout)

    fun selectScope(scope: LibraryScope) = controller.selectScope(scope)

    fun selectAxis(axis: LibraryAxis) = controller.selectAxis(axis)

    fun retryGroups() = controller.retryGroups()

    fun selectTag(tag: LibraryCatalogTag?) = controller.selectTag(tag)

    fun retryTags() = controller.retryTags()

    fun refresh() = controller.refreshBooks()

    fun retry() = controller.retry()

    fun selectAuthor(authorId: String) = controller.selectAuthor(authorId)

    fun selectSeries(seriesId: String) = controller.selectSeries(seriesId)

    fun clearSelectedAuthorSeries() = controller.clearSelectedAuthorSeries()

    fun retryAuthorDetail() = controller.retryAuthorDetail()

    fun retrySeriesDetail() = controller.retrySeriesDetail()

    fun navigateTo(target: LibraryExternalNavigation) = controller.navigateTo(target)

    override fun onCleared() {
        controller.close()
    }
}
