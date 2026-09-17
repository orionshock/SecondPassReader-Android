package com.secondpasslibrary.reader.shelves

import com.secondpasslibrary.reader.shelves.collection.ShelfCollectionState
import com.secondpasslibrary.reader.shelves.detail.ShelfDetailState
import com.secondpasslibrary.reader.shelves.editor.ShelfContentsEditorState
import com.secondpasslibrary.reader.shelves.management.CreatePersonalShelfState
import com.secondpasslibrary.reader.shelves.management.DeletePersonalShelfState
import com.secondpasslibrary.reader.shelves.management.EditPersonalShelfState
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
    editor: StateFlow<ShelfContentsEditorState>,
    create: StateFlow<CreatePersonalShelfState>,
    edit: StateFlow<EditPersonalShelfState>,
    delete: StateFlow<DeletePersonalShelfState>
): StateFlow<ShelvesState> {
    val core = combine(navigation, personal, shared, group, detail, ::ShelvesCoreState)
    val management = combine(create, edit, delete, ::ShelvesManagementState)
    return combine(core, editor, management, ::toShelvesState).stateIn(
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
            editor.value,
            ShelvesManagementState(create.value, edit.value, delete.value)
        )
    )
}

private fun toShelvesState(
    core: ShelvesCoreState,
    editor: ShelfContentsEditorState,
    management: ShelvesManagementState
): ShelvesState = ShelvesState(
    core.navigation.destination,
    core.personal,
    core.shared,
    core.group,
    core.detail,
    editor,
    management.create,
    management.edit,
    management.delete,
    core.navigation.createOpen
)

private data class ShelvesCoreState(
    val navigation: ShelvesNavigationState,
    val personal: ShelfCollectionState,
    val shared: ShelfCollectionState,
    val group: ShelfCollectionState,
    val detail: ShelfDetailState
)

private data class ShelvesManagementState(
    val create: CreatePersonalShelfState,
    val edit: EditPersonalShelfState,
    val delete: DeletePersonalShelfState
)
