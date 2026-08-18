package com.secondpasslibrary.reader.shelves

import com.secondpasslibrary.client.ShelfOrdering
import com.secondpasslibrary.reader.connection.AuthenticatedClientProvider
import com.secondpasslibrary.reader.connection.ConnectionProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.merge

internal class ShelvesController(
    clientProvider: AuthenticatedClientProvider,
    scope: CoroutineScope,
    val personal: PersonalShelvesController = PersonalShelvesController(clientProvider, scope),
    val shared: SharedShelvesController = SharedShelvesController(clientProvider, scope),
    val detail: ShelfDetailController = ShelfDetailController(clientProvider, scope)
) {
    private val navigation = MutableStateFlow(ShelvesNavigationState())
    val state = ShelvesStateFlow(navigation, personal.state, shared.state, detail.state)
    val connectionEvents =
        merge(personal.connectionEvents, shared.connectionEvents, detail.connectionEvents)

    private var connectionIdentity: String? = null

    fun initialize(profile: ConnectionProfile) {
        val identity = "${profile.apiBaseUrl}\u0000${profile.clientSessionId}"
        personal.prepare(profile)
        shared.prepare(profile)
        detail.prepare(profile)
        if (identity != connectionIdentity) {
            connectionIdentity = identity
            navigation.value = ShelvesNavigationState()
        }
        activateCurrentCollection()
    }

    fun showCollection(collection: ShelvesCollection) {
        if (navigation.value.destination is ShelvesDestination.Detail) detail.clear()
        navigation.value = ShelvesNavigationState(ShelvesDestination.Collection(collection))
        collection.controller().activate()
    }

    fun selectShelf(shelfId: String) {
        val current = navigation.value.destination as? ShelvesDestination.Collection ?: return
        detail.select(shelfId)
        navigation.value =
            ShelvesNavigationState(ShelvesDestination.Detail(shelfId, current.collection))
    }

    fun backFromDetail() {
        val current = navigation.value.destination as? ShelvesDestination.Detail ?: return
        detail.clear()
        navigation.value = ShelvesNavigationState(ShelvesDestination.Collection(current.origin))
    }

    fun changeCollectionOrdering(ordering: ShelfOrdering) {
        val destination = navigation.value.destination as? ShelvesDestination.Collection ?: return
        destination.collection.controller().changeOrdering(ordering)
    }

    fun close() {
        personal.close()
        shared.close()
        detail.close()
    }

    private fun activateCurrentCollection() {
        val destination = navigation.value.destination
        when (destination) {
            is ShelvesDestination.Collection -> destination.collection.controller().activate()
            is ShelvesDestination.Detail -> Unit
        }
    }

    private fun ShelvesCollection.controller(): ShelfCollectionController = when (this) {
        ShelvesCollection.PERSONAL -> personal
        ShelvesCollection.SHARED -> shared
    }
}
