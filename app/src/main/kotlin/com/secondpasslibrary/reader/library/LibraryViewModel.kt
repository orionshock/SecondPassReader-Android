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
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
@Suppress("TooManyFunctions") // Typed UI intents remain on the Library parent facade.
internal class LibraryViewModel
@Inject
constructor(
    clientProvider: AuthenticatedClientProvider,
    displayPreferenceStore: LibraryDisplayPreferenceStore
) : ViewModel() {
    private val controller =
        LibraryController(clientProvider, displayPreferenceStore, viewModelScope)

    val state = controller.state
    val connectionEvents = controller.connectionEvents

    fun initialize(
        profile: ConnectionProfile,
        entry: LibraryBooksEntry,
        advancedGroupsEnabled: Boolean
    ) = controller.initialize(profile, entry, advancedGroupsEnabled)

    fun commitSearch(query: String) = controller.commitSearch(query)

    fun changeBrowseOrdering(ordering: BookOrdering) =
        controller.books.changeBrowseOrdering(ordering)

    fun changeBroadSearchOrdering(ordering: LibrarySearchOrdering) =
        controller.books.changeBroadSearchOrdering(ordering)

    fun changeAuthorOrdering(ordering: AuthorOrdering) = controller.authors.changeOrdering(ordering)

    fun changeSeriesOrdering(ordering: SeriesOrdering) = controller.series.changeOrdering(ordering)

    fun loadNextPage() = controller.loadNextPage()

    fun setLayout(layout: LibraryBooksLayout) = controller.books.setLayout(layout)

    fun selectScope(scope: LibraryScope) = controller.selectScope(scope)

    fun selectAxis(axis: LibraryAxis) = controller.selectAxis(axis)

    fun retryGroups() = controller.retryGroups()

    fun selectTag(tag: LibraryCatalogTag?) = controller.selectTag(tag)

    fun retryTags() = controller.retryTags()

    fun refresh() = controller.books.refresh()

    fun retry() = controller.retry()

    fun selectAuthor(authorId: String) = controller.selectAuthor(authorId)

    fun selectSeries(seriesId: String) = controller.selectSeries(seriesId)

    fun clearSelectedEntity() = controller.clearSelectedEntity()

    fun retryAuthorDetail() = controller.authors.retryDetail()

    fun retrySeriesDetail() = controller.series.retryDetail()

    override fun onCleared() {
        controller.close()
    }
}
