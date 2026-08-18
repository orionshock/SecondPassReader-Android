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
internal class LibraryBooksViewModel
@Inject
constructor(
    clientProvider: AuthenticatedClientProvider,
    displayPreferenceStore: LibraryDisplayPreferenceStore
) : ViewModel() {
    private val controller =
        LibraryBooksController(clientProvider, displayPreferenceStore, viewModelScope)

    val state = controller.state
    val connectionEvents = controller.connectionEvents

    fun initializeBrowse(profile: ConnectionProfile) = controller.initializeBrowse(profile)

    fun initializeBroadSearch(profile: ConnectionProfile, query: String) =
        controller.initializeBroadSearch(profile, query)

    fun commitSearch(query: String) {
        when (state.value.mode) {
            LibraryBooksMode.BROWSE -> controller.commitBrowseQuery(query)
            LibraryBooksMode.BROAD_SEARCH -> controller.commitBroadSearch(query)
        }
    }

    fun changeBrowseOrdering(ordering: BookOrdering) = controller.changeBrowseOrdering(ordering)

    fun changeBroadSearchOrdering(ordering: LibrarySearchOrdering) =
        controller.changeBroadSearchOrdering(ordering)

    fun loadNextPage() = controller.loadNextPage()

    fun setLayout(layout: LibraryBooksLayout) = controller.setLayout(layout)

    fun refresh() = controller.refresh()

    fun retry() = controller.retry()

    override fun onCleared() {
        controller.close()
    }
}
