package com.secondpasslibrary.reader.shelves

import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine

internal data class ShelvesNavigationState(
    val destination: ShelvesDestination =
        ShelvesDestination.Collection(ShelvesCollection.PERSONAL)
)

@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
internal class ShelvesStateFlow(
    private val navigation: StateFlow<ShelvesNavigationState>,
    private val personal: StateFlow<ShelfCollectionState>,
    private val shared: StateFlow<ShelfCollectionState>,
    private val detail: StateFlow<ShelfDetailState>
) : StateFlow<ShelvesState> {
    override val value: ShelvesState
        get() =
            ShelvesState(
                navigation.value.destination,
                personal.value,
                shared.value,
                detail.value
            )

    override val replayCache: List<ShelvesState>
        get() = listOf(value)

    override suspend fun collect(collector: FlowCollector<ShelvesState>): Nothing {
        combine(navigation, personal, shared, detail) {
                nav,
                personalState,
                sharedState,
                detailState
            ->
            ShelvesState(nav.destination, personalState, sharedState, detailState)
        }.collect(collector)
        error("Shelves state sources completed unexpectedly.")
    }
}
