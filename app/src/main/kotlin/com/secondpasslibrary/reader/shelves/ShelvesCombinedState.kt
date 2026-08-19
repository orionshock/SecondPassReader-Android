package com.secondpasslibrary.reader.shelves

import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine

internal data class ShelvesNavigationState(
    val destination: ShelvesDestination =
        ShelvesDestination.Collection(ShelvesCollection.PERSONAL),
    val createOpen: Boolean = false
)

@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
internal class ShelvesStateFlow(
    private val navigation: StateFlow<ShelvesNavigationState>,
    private val personal: StateFlow<ShelfCollectionState>,
    private val shared: StateFlow<ShelfCollectionState>,
    private val group: StateFlow<ShelfCollectionState>,
    private val detail: StateFlow<ShelfDetailState>
) : StateFlow<ShelvesState> {
    override val value: ShelvesState
        get() =
            ShelvesState(
                navigation.value.destination,
                personal.value,
                shared.value,
                group.value,
                detail.value,
                navigation.value.createOpen
            )

    override val replayCache: List<ShelvesState>
        get() = listOf(value)

    override suspend fun collect(collector: FlowCollector<ShelvesState>): Nothing {
        combine(navigation, personal, shared, group, detail) {
                nav,
                personalState,
                sharedState,
                groupState,
                detailState
            ->
            ShelvesState(
                nav.destination,
                personalState,
                sharedState,
                groupState,
                detailState,
                nav.createOpen
            )
        }.collect(collector)
        error("Shelves state sources completed unexpectedly.")
    }
}
