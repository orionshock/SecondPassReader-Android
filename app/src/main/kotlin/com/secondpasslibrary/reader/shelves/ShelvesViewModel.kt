package com.secondpasslibrary.reader.shelves

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondpasslibrary.client.ShelfItemMove
import com.secondpasslibrary.client.ShelfItemOrdering
import com.secondpasslibrary.client.ShelfOrdering
import com.secondpasslibrary.client.ShelfVisibility
import com.secondpasslibrary.reader.connection.AuthenticatedClientProvider
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.shelves.detail.ShelfBooksLayout
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
    val createState = controller.create.state
    val editState = controller.edit.state
    val deleteState = controller.delete.state
    val editorState = controller.editor.state

    fun initialize(profile: ConnectionProfile) = controller.initialize(profile)

    fun showCollection(collection: ShelvesCollection) = controller.showCollection(collection)

    fun selectShelf(shelfId: String) = controller.selectShelf(shelfId)

    fun openShelf(entry: ShelfDetailEntry) = controller.openShelf(entry)

    fun backFromDetail() = controller.backFromDetail()

    fun changeCollectionOrdering(ordering: ShelfOrdering) =
        controller.changeCollectionOrdering(ordering)

    fun loadNextPersonalPage() = controller.personal.loadNextPage()

    fun loadNextSharedPage() = controller.shared.loadNextPage()

    fun loadNextGroupPage() = controller.group.loadNextPage()

    fun retryPersonal() = controller.personal.retry()

    fun retryShared() = controller.shared.retry()

    fun retryGroup() = controller.group.retry()

    fun openCreate() = controller.openCreate()

    fun dismissCreate() = controller.dismissCreate()

    fun updateCreateName(name: String) = controller.create.updateName(name)

    fun updateCreateDescription(description: String) =
        controller.create.updateDescription(description)

    fun updateCreateVisibility(visibility: ShelfVisibility) =
        controller.create.updateVisibility(visibility)

    fun submitCreate() = controller.submitCreate()

    fun openEdit() = controller.openEdit()

    fun dismissEdit() = controller.edit.reset()

    fun updateEditName(name: String) = controller.edit.updateName(name)

    fun updateEditDescription(description: String) = controller.edit.updateDescription(description)

    fun updateEditVisibility(visibility: ShelfVisibility) =
        controller.edit.updateVisibility(visibility)

    fun submitEdit() = controller.submitEdit()

    fun openDelete() = controller.openDelete()

    fun dismissDelete() = controller.delete.reset()

    fun confirmDelete() = controller.confirmDelete()

    fun openContentsEditor() = controller.openContentsEditor()

    fun backFromContentsEditor() = controller.backFromContentsEditor()

    fun loadNextEditorPage() = controller.editor.loadNextPage()

    fun retryEditor() = controller.editor.retry()

    fun moveEditorItemUp(itemId: String) = controller.editor.move(itemId, ShelfItemMove.UP)

    fun moveEditorItemDown(itemId: String) = controller.editor.move(itemId, ShelfItemMove.DOWN)

    fun openEditorPosition(itemId: String) = controller.editor.openPosition(itemId)

    fun updateEditorPosition(value: String) = controller.editor.updatePosition(value)

    fun submitEditorPosition() = controller.editor.submitPosition()

    fun dismissEditorPosition() = controller.editor.dismissPosition()

    fun requestEditorRemoval(itemId: String) = controller.editor.requestRemoval(itemId)

    fun confirmEditorRemoval() = controller.editor.confirmRemoval()

    fun dismissEditorRemoval() = controller.editor.dismissRemoval()

    fun dismissEditorMutationFailure() = controller.editor.dismissMutationFailure()

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
