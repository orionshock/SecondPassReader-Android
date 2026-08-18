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
    clientProvider: AuthenticatedClientProvider
) : ViewModel() {
    private val controller = LibraryBooksController(clientProvider, viewModelScope)

    val state = controller.state
    val connectionEvents = controller.connectionEvents

    fun initializeBrowse(profile: ConnectionProfile) = controller.initializeBrowse(profile)

    fun initializeBroadSearch(profile: ConnectionProfile, query: String) =
        controller.initializeBroadSearch(profile, query)

    fun commitBrowseQuery(query: String) = controller.commitBrowseQuery(query)

    fun commitBroadSearch(query: String) = controller.commitBroadSearch(query)

    fun changeBrowseOrdering(ordering: BookOrdering) = controller.changeBrowseOrdering(ordering)

    fun changeBroadSearchOrdering(ordering: LibrarySearchOrdering) =
        controller.changeBroadSearchOrdering(ordering)

    fun loadNextPage() = controller.loadNextPage()

    fun refresh() = controller.refresh()

    fun retry() = controller.retry()

    override fun onCleared() {
        controller.close()
    }
}
