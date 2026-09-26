package com.secondpasslibrary.reader.shelves

import com.secondpasslibrary.client.Shelf
import com.secondpasslibrary.client.ShelfOrdering
import com.secondpasslibrary.reader.connection.AuthenticatedClientProvider
import com.secondpasslibrary.reader.connection.AuthenticatedSessionIdentity
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.connection.authenticatedSessionIdentity
import com.secondpasslibrary.reader.shelves.collection.GroupShelvesController
import com.secondpasslibrary.reader.shelves.collection.PersonalShelvesController
import com.secondpasslibrary.reader.shelves.collection.SharedShelvesController
import com.secondpasslibrary.reader.shelves.collection.ShelfCollectionChange
import com.secondpasslibrary.reader.shelves.collection.ShelfCollectionController
import com.secondpasslibrary.reader.shelves.collection.ShelfSearchIntent
import com.secondpasslibrary.reader.shelves.detail.ShelfDetailController
import com.secondpasslibrary.reader.shelves.editor.ShelfContentsEditorController
import com.secondpasslibrary.reader.shelves.management.CreatePersonalShelfController
import com.secondpasslibrary.reader.shelves.management.DeletePersonalShelfController
import com.secondpasslibrary.reader.shelves.management.EditPersonalShelfController
import com.secondpasslibrary.reader.shelves.management.canManageShelf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.merge

// Parent exhaustively routes typed coordination intents to its bounded children.
@Suppress("CyclomaticComplexMethod", "TooManyFunctions")
internal class ShelvesController(
    clientProvider: AuthenticatedClientProvider,
    scope: CoroutineScope,
    private val personal: PersonalShelvesController =
        PersonalShelvesController(clientProvider, scope),
    private val shared: SharedShelvesController = SharedShelvesController(clientProvider, scope),
    private val group: GroupShelvesController = GroupShelvesController(clientProvider, scope),
    private val detail: ShelfDetailController = ShelfDetailController(clientProvider, scope),
    private val create: CreatePersonalShelfController =
        CreatePersonalShelfController(clientProvider, scope),
    private val edit: EditPersonalShelfController =
        EditPersonalShelfController(clientProvider, scope),
    private val delete: DeletePersonalShelfController =
        DeletePersonalShelfController(clientProvider, scope)
) {
    private val lifetimeJob = SupervisorJob(scope.coroutineContext[Job])
    private val lifetimeScope = CoroutineScope(scope.coroutineContext + lifetimeJob)
    private val editor =
        ShelfContentsEditorController(clientProvider, scope, ::reconcileShelfContents)
    private val navigation = MutableStateFlow(ShelvesNavigationState())
    val state =
        shelvesStateFlow(
            lifetimeScope,
            navigation,
            personal.state,
            shared.state,
            group.state,
            detail.state,
            editor.state,
            create.state,
            edit.state,
            delete.state
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

    private var connectionIdentity: AuthenticatedSessionIdentity? = null

    fun initialize(profile: ConnectionProfile) {
        val nextConnectionIdentity = profile.authenticatedSessionIdentity
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

    fun accept(intent: ShelvesIntent) {
        when (intent) {
            is ShelvesIntent.ShowCollection -> showCollection(intent.collection)

            is ShelvesIntent.SelectShelf -> selectShelf(intent.shelfId)

            is ShelvesIntent.OpenShelf -> openShelf(intent.entry)

            ShelvesIntent.BackFromDetail -> backFromDetail()

            is ShelvesIntent.ChangeCollectionOrdering ->
                changeCollectionOrdering(intent.ordering)

            is ShelvesIntent.UpdateSearchQuery ->
                currentCollectionController()?.acceptSearch(ShelfSearchIntent.Update(intent.value))

            ShelvesIntent.SubmitSearch ->
                currentCollectionController()?.acceptSearch(ShelfSearchIntent.Submit)

            ShelvesIntent.ClearSearch ->
                currentCollectionController()?.acceptSearch(ShelfSearchIntent.Clear)

            is ShelvesIntent.LoadNextCollectionPage ->
                intent.collection.controller().loadNextPage()

            is ShelvesIntent.RetryCollection -> intent.collection.controller().retry()

            ShelvesIntent.OpenCreate -> openCreate()

            ShelvesIntent.DismissCreate -> dismissCreate()

            is ShelvesIntent.UpdateCreateName -> create.updateName(intent.value)

            is ShelvesIntent.UpdateCreateDescription -> create.updateDescription(intent.value)

            is ShelvesIntent.UpdateCreateVisibility -> create.updateVisibility(intent.value)

            ShelvesIntent.SubmitCreate -> submitCreate()

            ShelvesIntent.OpenEdit -> openEdit()

            ShelvesIntent.DismissEdit -> edit.reset()

            is ShelvesIntent.UpdateEditName -> edit.updateName(intent.value)

            is ShelvesIntent.UpdateEditDescription -> edit.updateDescription(intent.value)

            is ShelvesIntent.UpdateEditVisibility -> edit.updateVisibility(intent.value)

            ShelvesIntent.SubmitEdit -> submitEdit()

            ShelvesIntent.OpenDelete -> openDelete()

            ShelvesIntent.DismissDelete -> delete.reset()

            ShelvesIntent.ConfirmDelete -> confirmDelete()

            ShelvesIntent.OpenContentsEditor -> openContentsEditor()

            ShelvesIntent.BackFromContentsEditor -> backFromContentsEditor()

            ShelvesIntent.LoadNextEditorPage -> editor.loadNextPage()

            ShelvesIntent.RetryEditor -> editor.retry()

            is ShelvesIntent.MoveEditorItem -> editor.move(intent.itemId, intent.direction)

            is ShelvesIntent.OpenEditorPosition -> editor.openPosition(intent.itemId)

            is ShelvesIntent.UpdateEditorPosition -> editor.updatePosition(intent.value)

            ShelvesIntent.SubmitEditorPosition -> editor.submitPosition()

            ShelvesIntent.DismissEditorPosition -> editor.dismissPosition()

            is ShelvesIntent.RequestEditorRemoval -> editor.requestRemoval(intent.itemId)

            ShelvesIntent.ConfirmEditorRemoval -> editor.confirmRemoval()

            ShelvesIntent.DismissEditorRemoval -> editor.dismissRemoval()

            ShelvesIntent.DismissEditorMutationFailure -> editor.dismissMutationFailure()

            is ShelvesIntent.ChangeItemOrdering -> detail.changeItemOrdering(intent.ordering)

            is ShelvesIntent.SetItemLayout -> detail.setLayout(intent.layout)

            ShelvesIntent.LoadNextItemPage -> detail.loadNextPage()

            ShelvesIntent.RetryDetail -> detail.retryDetail()

            ShelvesIntent.RetryItems -> detail.retryItems()

            ShelvesIntent.LeaveMutationSurfaces -> leaveMutationSurfaces()
        }
    }

    private fun showCollection(collection: ShelvesCollection) {
        val currentCollection =
            (navigation.value.destination as? ShelvesDestination.Collection)?.collection
        if (navigation.value.destination !is ShelvesDestination.Collection) {
            detail.clear()
            editor.clear()
            edit.reset()
            delete.reset()
        }
        if (currentCollection != collection) {
            collection.controller().acceptSearch(ShelfSearchIntent.Clear)
        }
        collection.controller().activate()
        navigation.value = ShelvesNavigationState(ShelvesDestination.Collection(collection))
    }

    private fun selectShelf(shelfId: String) {
        val current = navigation.value.destination as? ShelvesDestination.Collection ?: return
        edit.reset()
        delete.reset()
        detail.select(shelfId)
        navigation.value =
            ShelvesNavigationState(ShelvesDestination.Detail(shelfId, current.collection))
    }

    private fun openShelf(entry: ShelfDetailEntry) {
        edit.reset()
        delete.reset()
        detail.select(entry.shelfId)
        navigation.value =
            ShelvesNavigationState(ShelvesDestination.Detail(entry.shelfId, entry.origin))
    }

    private fun backFromDetail() {
        val current = navigation.value.destination as? ShelvesDestination.Detail ?: return
        edit.reset()
        delete.reset()
        detail.clear()
        navigation.value = ShelvesNavigationState(ShelvesDestination.Collection(current.origin))
    }

    private fun changeCollectionOrdering(ordering: ShelfOrdering) {
        val destination = navigation.value.destination as? ShelvesDestination.Collection ?: return
        destination.collection.controller().changeOrdering(ordering)
    }

    private fun currentCollectionController(): ShelfCollectionController? =
        (navigation.value.destination as? ShelvesDestination.Collection)?.collection?.controller()

    private fun openContentsEditor() {
        val current = navigation.value.destination as? ShelvesDestination.Detail ?: return
        val shelf = detail.state.value.detail.shelf
        if (!canManageShelf(current.origin, shelf)) return
        editor.open(current.shelfId)
        navigation.value =
            ShelvesNavigationState(
                ShelvesDestination.ContentsEditor(current.shelfId, current.origin)
            )
    }

    private fun backFromContentsEditor() {
        val current =
            navigation.value.destination as? ShelvesDestination.ContentsEditor ?: return
        editor.clear()
        navigation.value =
            ShelvesNavigationState(ShelvesDestination.Detail(current.shelfId, current.origin))
    }

    private fun openCreate() {
        val destination = navigation.value.destination as? ShelvesDestination.Collection ?: return
        if (destination.collection != ShelvesCollection.PERSONAL) return
        navigation.value = navigation.value.copy(createOpen = true)
    }

    private fun dismissCreate() {
        create.reset()
        navigation.value = navigation.value.copy(createOpen = false)
    }

    private fun submitCreate() {
        create.submit { shelf ->
            navigation.value = navigation.value.copy(createOpen = false)
            personal.applyAuthoritativeChange(ShelfCollectionChange.Added(shelf), refresh = true)
        }
    }

    private fun openEdit() {
        val destination = currentManagedShelfDestination() ?: return
        val shelf = detail.state.value.detail.shelf
        if (!canManageShelf(destination.origin, shelf)) return
        delete.reset()
        edit.begin(requireNotNull(shelf))
    }

    private fun submitEdit() {
        edit.submit { shelf ->
            detail.applyAuthoritativeShelf(shelf)
            personal.applyAuthoritativeChange(ShelfCollectionChange.Updated(shelf))
        }
    }

    private fun openDelete() {
        val destination = currentManagedShelfDestination() ?: return
        val shelf = detail.state.value.detail.shelf
        if (!canManageShelf(destination.origin, shelf)) return
        edit.reset()
        delete.begin(requireNotNull(shelf))
    }

    private fun confirmDelete() {
        delete.confirm { shelfId ->
            personal.applyAuthoritativeChange(ShelfCollectionChange.Removed(shelfId))
            leaveDeletedShelf()
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
        lifetimeScope.cancel()
    }

    private fun leaveMutationSurfaces() {
        dismissCreate()
        edit.reset()
        delete.reset()
        editor.dismissPosition()
        editor.dismissRemoval()
        if (navigation.value.destination is ShelvesDestination.ContentsEditor) {
            backFromContentsEditor()
        }
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

    private fun currentManagedShelfDestination(): ManagedShelfDestination? =
        when (val destination = navigation.value.destination) {
            is ShelvesDestination.Detail ->
                ManagedShelfDestination(destination.origin)

            is ShelvesDestination.ContentsEditor ->
                ManagedShelfDestination(destination.origin)

            is ShelvesDestination.Collection -> null
        }

    private fun leaveDeletedShelf() {
        val destination = currentManagedShelfDestination() ?: return
        edit.reset()
        editor.clear()
        detail.clear()
        navigation.value =
            ShelvesNavigationState(ShelvesDestination.Collection(destination.origin))
        destination.origin.controller().activate()
    }

    private fun ShelvesCollection.controller(): ShelfCollectionController = when (this) {
        ShelvesCollection.PERSONAL -> personal
        ShelvesCollection.SHARED -> shared
        ShelvesCollection.GROUP -> group
    }
}

private data class ManagedShelfDestination(val origin: ShelvesCollection)
