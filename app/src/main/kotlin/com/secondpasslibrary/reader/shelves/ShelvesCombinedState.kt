package com.secondpasslibrary.reader.shelves

import com.secondpasslibrary.reader.shelves.collection.ShelfCollectionState
import com.secondpasslibrary.reader.shelves.detail.ShelfDetailState
import com.secondpasslibrary.reader.shelves.editor.ShelfContentsEditorState
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
    private val detail: StateFlow<ShelfDetailState>,
    private val editor: StateFlow<ShelfContentsEditorState>
) : StateFlow<ShelvesState> {
    override val value: ShelvesState
        get() =
            ShelvesState(
                navigation.value.destination,
                personal.value,
                shared.value,
                group.value,
                detail.value,
                editor.value,
                navigation.value.createOpen
            )

    override val replayCache: List<ShelvesState>
        get() = listOf(value)

    override suspend fun collect(collector: FlowCollector<ShelvesState>): Nothing {
        val core = combine(navigation, personal, shared, group, detail) {
                nav,
                personalState,
                sharedState,
                groupState,
                detailState
            ->
            ShelvesCoreState(nav, personalState, sharedState, groupState, detailState)
        }
        combine(core, editor) { value, editorState ->
            ShelvesState(
                value.navigation.destination,
                value.personal,
                value.shared,
                value.group,
                value.detail,
                editorState,
                value.navigation.createOpen
            )
        }.collect(collector)
        error("Shelves state sources completed unexpectedly.")
    }
}

private data class ShelvesCoreState(
    val navigation: ShelvesNavigationState,
    val personal: ShelfCollectionState,
    val shared: ShelfCollectionState,
    val group: ShelfCollectionState,
    val detail: ShelfDetailState
)
