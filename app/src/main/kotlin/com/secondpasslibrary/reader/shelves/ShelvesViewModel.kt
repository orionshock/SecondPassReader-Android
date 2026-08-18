package com.secondpasslibrary.reader.shelves

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondpasslibrary.client.ShelfItemOrdering
import com.secondpasslibrary.client.ShelfOrdering
import com.secondpasslibrary.reader.connection.AuthenticatedClientProvider
import com.secondpasslibrary.reader.connection.ConnectionProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
@Suppress("TooManyFunctions") // Typed UI intents mirror the bounded Shelves ownership tree.
internal class ShelvesViewModel
@Inject
constructor(clientProvider: AuthenticatedClientProvider) :
    ViewModel() {
    private val controller = ShelvesController(clientProvider, viewModelScope)

    val state = controller.state
    val connectionEvents = controller.connectionEvents

    fun initialize(profile: ConnectionProfile) = controller.initialize(profile)

    fun showCollection(collection: ShelvesCollection) = controller.showCollection(collection)

    fun selectShelf(shelfId: String) = controller.selectShelf(shelfId)

    fun backFromDetail() = controller.backFromDetail()

    fun changeCollectionOrdering(ordering: ShelfOrdering) =
        controller.changeCollectionOrdering(ordering)

    fun loadNextPersonalPage() = controller.personal.loadNextPage()

    fun loadNextSharedPage() = controller.shared.loadNextPage()

    fun loadNextGroupPage() = controller.group.loadNextPage()

    fun retryPersonal() = controller.personal.retry()

    fun retryShared() = controller.shared.retry()

    fun retryGroup() = controller.group.retry()

    fun changeItemOrdering(ordering: ShelfItemOrdering) =
        controller.detail.changeItemOrdering(ordering)

    fun setItemLayout(layout: ShelfBooksLayout) = controller.detail.setLayout(layout)

    fun loadNextItemPage() = controller.detail.loadNextPage()

    fun retryDetail() = controller.detail.retryDetail()

    fun retryItems() = controller.detail.retryItems()

    override fun onCleared() {
        controller.close()
    }
}
