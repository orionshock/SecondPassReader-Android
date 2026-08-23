package com.secondpasslibrary.reader.app.shell

import androidx.compose.runtime.State
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.secondpasslibrary.reader.shelves.ShelfBookNavigationRequest
import com.secondpasslibrary.reader.shelves.ShelfDetailEntry
import com.secondpasslibrary.reader.shelves.ShelvesCollection
import com.secondpasslibrary.reader.shelves.ShelvesStateHost

internal fun EntryProviderScope<NavKey>.registerShelfDetailEntry(
    environment: State<AccountDestinationEnvironment>
) {
    entry<ShelfDetailRoute> { route ->
        AuthenticatedDestination(environment) { bindings ->
            ShelvesStateHost(
                profile = bindings.profile,
                onOpenDrawer = bindings.onOpenDrawer,
                onBookSelected = { bindings.navigator.openShelfBook(it) },
                onAuthenticationRejected = bindings.onAuthenticationRejected,
                initialDetail = route.toEntry(),
                onExitInitialDetail = { bindings.navigator.goBack() }
            )
        }
    }
}

internal fun AppNavigator.openShelfBook(request: ShelfBookNavigationRequest) {
    openBookDetail(
        request.bookId,
        BookDetailReturnTarget.ShelfDetail(request.shelfId, request.origin.toRouteOrigin())
    )
}

private fun ShelvesCollection.toRouteOrigin(): ShelfCollectionOrigin = when (this) {
    ShelvesCollection.PERSONAL -> ShelfCollectionOrigin.PERSONAL
    ShelvesCollection.SHARED -> ShelfCollectionOrigin.SHARED
    ShelvesCollection.GROUP -> ShelfCollectionOrigin.GROUP
}

private fun ShelfDetailRoute.toEntry() = ShelfDetailEntry(
    shelfId,
    when (origin) {
        ShelfCollectionOrigin.PERSONAL -> ShelvesCollection.PERSONAL
        ShelfCollectionOrigin.SHARED -> ShelvesCollection.SHARED
        ShelfCollectionOrigin.GROUP -> ShelvesCollection.GROUP
    }
)
