package com.secondpasslibrary.reader.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondpasslibrary.client.BookOrdering
import com.secondpasslibrary.client.LibrarySearchOrdering
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

    fun loadNextPage() = controller.books.loadNextPage()

    fun setLayout(layout: LibraryBooksLayout) = controller.books.setLayout(layout)

    fun selectScope(scope: LibraryScope) = controller.selectScope(scope)

    fun selectAxis(axis: LibraryAxis) = controller.selectAxis(axis)

    fun retryGroups() = controller.retryGroups()

    fun refresh() = controller.books.refresh()

    fun retry() = controller.books.retry()

    override fun onCleared() {
        controller.close()
    }
}
