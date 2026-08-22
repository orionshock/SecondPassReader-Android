package com.secondpasslibrary.reader.shelves

import com.secondpasslibrary.reader.shelves.collection.ShelfCollectionState
import com.secondpasslibrary.reader.shelves.detail.ShelfDetailState
import com.secondpasslibrary.reader.shelves.editor.ShelfContentsEditorState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

internal fun shelvesStateFlow(
    scope: CoroutineScope,
    navigation: StateFlow<ShelvesNavigationState>,
    personal: StateFlow<ShelfCollectionState>,
    shared: StateFlow<ShelfCollectionState>,
    group: StateFlow<ShelfCollectionState>,
    detail: StateFlow<ShelfDetailState>,
    editor: StateFlow<ShelfContentsEditorState>
): StateFlow<ShelvesState> {
    val core = combine(navigation, personal, shared, group, detail, ::ShelvesCoreState)
    return combine(core, editor, ::toShelvesState).stateIn(
        scope,
        // Eager sharing matches the former aggregate, which stayed current without a collector.
        SharingStarted.Eagerly,
        toShelvesState(
            ShelvesCoreState(
                navigation.value,
                personal.value,
                shared.value,
                group.value,
                detail.value
            ),
            editor.value
        )
    )
}

private fun toShelvesState(core: ShelvesCoreState, editor: ShelfContentsEditorState): ShelvesState =
    ShelvesState(
        core.navigation.destination,
        core.personal,
        core.shared,
        core.group,
        core.detail,
        editor,
        core.navigation.createOpen
    )

private data class ShelvesCoreState(
    val navigation: ShelvesNavigationState,
    val personal: ShelfCollectionState,
    val shared: ShelfCollectionState,
    val group: ShelfCollectionState,
    val detail: ShelfDetailState
)
