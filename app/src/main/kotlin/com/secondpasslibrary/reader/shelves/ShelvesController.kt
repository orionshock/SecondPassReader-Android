package com.secondpasslibrary.reader.shelves

import com.secondpasslibrary.client.Shelf
import com.secondpasslibrary.client.ShelfOrdering
import com.secondpasslibrary.reader.connection.AuthenticatedClientProvider
import com.secondpasslibrary.reader.connection.AuthenticatedConnectionIdentity
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.connection.authenticatedConnectionIdentity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.merge

@Suppress("TooManyFunctions") // Parent exposes typed coordination intents for its bounded children.
internal class ShelvesController(
    clientProvider: AuthenticatedClientProvider,
    scope: CoroutineScope,
    val personal: PersonalShelvesController = PersonalShelvesController(clientProvider, scope),
    val shared: SharedShelvesController = SharedShelvesController(clientProvider, scope),
    val group: GroupShelvesController = GroupShelvesController(clientProvider, scope),
    val detail: ShelfDetailController = ShelfDetailController(clientProvider, scope)
) {
    val create = CreatePersonalShelfController(clientProvider, scope)
    val edit = EditPersonalShelfController(clientProvider, scope)
    val delete = DeletePersonalShelfController(clientProvider, scope)
    val editor =
        ShelfContentsEditorController(clientProvider, scope, ::reconcileShelfContents)

    private val navigation = MutableStateFlow(ShelvesNavigationState())
    val state =
        ShelvesStateFlow(
            navigation,
            personal.state,
            shared.state,
            group.state,
            detail.state,
            editor.state
        )
    val connectionEvents =
        merge(
            personal.connectionEvents,
            shared.connectionEvents,
            group.connectionEvents,
            detail.connectionEvents,
            create.connectionEvents,
            edit.connectionEvents,
            delete.connectionEvents,
            editor.connectionEvents
        )

    private var connectionIdentity: AuthenticatedConnectionIdentity? = null

    fun initialize(profile: ConnectionProfile) {
        val nextConnectionIdentity = profile.authenticatedConnectionIdentity
        personal.prepare(profile)
        shared.prepare(profile)
        group.prepare(profile)
        detail.prepare(profile)
        create.prepare(profile)
        edit.prepare(profile)
        delete.prepare(profile)
        editor.prepare(profile)
        if (nextConnectionIdentity != connectionIdentity) {
            connectionIdentity = nextConnectionIdentity
            navigation.value = ShelvesNavigationState()
        }
        activateCurrentCollection()
    }

    fun showCollection(collection: ShelvesCollection) {
        if (navigation.value.destination !is ShelvesDestination.Collection) {
            detail.clear()
            editor.clear()
            edit.reset()
            delete.reset()
        }
        navigation.value = ShelvesNavigationState(ShelvesDestination.Collection(collection))
        collection.controller().activate()
    }

    fun selectShelf(shelfId: String) {
        val current = navigation.value.destination as? ShelvesDestination.Collection ?: return
        edit.reset()
        delete.reset()
        detail.select(shelfId)
        navigation.value =
            ShelvesNavigationState(ShelvesDestination.Detail(shelfId, current.collection))
    }

    fun backFromDetail() {
        val current = navigation.value.destination as? ShelvesDestination.Detail ?: return
        edit.reset()
        delete.reset()
        detail.clear()
        navigation.value = ShelvesNavigationState(ShelvesDestination.Collection(current.origin))
    }

    fun changeCollectionOrdering(ordering: ShelfOrdering) {
        val destination = navigation.value.destination as? ShelvesDestination.Collection ?: return
        destination.collection.controller().changeOrdering(ordering)
    }

    fun openContentsEditor() {
        val current = navigation.value.destination as? ShelvesDestination.Detail ?: return
        val shelf = detail.state.value.detail.shelf
        if (!canManageShelf(current.origin, shelf)) return
        editor.open(current.shelfId)
        navigation.value =
            ShelvesNavigationState(
                ShelvesDestination.ContentsEditor(current.shelfId, current.origin)
            )
    }

    fun backFromContentsEditor() {
        val current =
            navigation.value.destination as? ShelvesDestination.ContentsEditor ?: return
        editor.clear()
        navigation.value =
            ShelvesNavigationState(ShelvesDestination.Detail(current.shelfId, current.origin))
    }

    fun openCreate() {
        val destination = navigation.value.destination as? ShelvesDestination.Collection ?: return
        if (destination.collection != ShelvesCollection.PERSONAL) return
        navigation.value = navigation.value.copy(createOpen = true)
    }

    fun dismissCreate() {
        create.reset()
        navigation.value = navigation.value.copy(createOpen = false)
    }

    fun submitCreate() {
        create.submit { shelf ->
            navigation.value = navigation.value.copy(createOpen = false)
            personal.applyAuthoritativeChange(ShelfCollectionChange.Added(shelf), refresh = true)
        }
    }

    fun openEdit() {
        val destination = navigation.value.destination as? ShelvesDestination.Detail ?: return
        val shelf = detail.state.value.detail.shelf
        if (!canManageShelf(destination.origin, shelf)) return
        delete.reset()
        edit.begin(requireNotNull(shelf))
    }

    fun submitEdit() {
        edit.submit { shelf ->
            detail.applyAuthoritativeShelf(shelf)
            personal.applyAuthoritativeChange(ShelfCollectionChange.Updated(shelf))
        }
    }

    fun openDelete() {
        val destination = navigation.value.destination as? ShelvesDestination.Detail ?: return
        val shelf = detail.state.value.detail.shelf
        if (!canManageShelf(destination.origin, shelf)) return
        edit.reset()
        delete.begin(requireNotNull(shelf))
    }

    fun confirmDelete() {
        delete.confirm { shelfId ->
            personal.applyAuthoritativeChange(ShelfCollectionChange.Removed(shelfId))
            backFromDetail()
        }
    }

    fun close() {
        personal.close()
        shared.close()
        group.close()
        detail.close()
        create.close()
        edit.close()
        delete.close()
        editor.close()
    }

    private fun activateCurrentCollection() {
        val destination = navigation.value.destination
        when (destination) {
            is ShelvesDestination.Collection -> destination.collection.controller().activate()
            is ShelvesDestination.Detail -> Unit
            is ShelvesDestination.ContentsEditor -> Unit
        }
    }

    private fun reconcileShelfContents(shelf: Shelf) {
        detail.applyAuthoritativeShelf(shelf)
        detail.reloadItems()
        personal.applyAuthoritativeChange(ShelfCollectionChange.Updated(shelf))
    }

    private fun ShelvesCollection.controller(): ShelfCollectionController = when (this) {
        ShelvesCollection.PERSONAL -> personal
        ShelvesCollection.SHARED -> shared
        ShelvesCollection.GROUP -> group
    }
}
