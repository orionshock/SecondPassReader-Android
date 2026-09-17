package com.secondpasslibrary.reader.shelves

import com.secondpasslibrary.reader.shelves.collection.ShelfCollectionState
import com.secondpasslibrary.reader.shelves.detail.ShelfDetailState
import com.secondpasslibrary.reader.shelves.editor.ShelfContentsEditorState
import com.secondpasslibrary.reader.shelves.management.CreatePersonalShelfState
import com.secondpasslibrary.reader.shelves.management.DeletePersonalShelfState
import com.secondpasslibrary.reader.shelves.management.EditPersonalShelfState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ShelvesCombinedStateTest {
    @Test
    fun `initial value uses current source snapshots before collection starts`() = runTest {
        val destination = ShelvesDestination.Collection(ShelvesCollection.GROUP)
        val sources = ShelvesStateSources(
            navigation = ShelvesNavigationState(destination, createOpen = true),
            personal = ShelfCollectionState(totalCount = 4)
        )

        val state = sources.aggregate(backgroundScope)

        assertEquals(destination, state.value.destination)
        assertEquals(4, state.value.personal.totalCount)
        assertEquals(true, state.value.createOpen)
    }

    @Test
    fun `eager aggregate tracks navigation collection detail and editor without a collector`() =
        runTest {
            val sources = ShelvesStateSources()
            val state = sources.aggregate(backgroundScope)
            runCurrent()

            sources.navigation.value = ShelvesNavigationState(
                ShelvesDestination.Detail("shelf-1", ShelvesCollection.SHARED)
            )
            sources.group.value = ShelfCollectionState(totalCount = 8)
            sources.detail.value = ShelfDetailState(shelfId = "shelf-1")
            sources.editor.value = ShelfContentsEditorState(shelfId = "shelf-1")
            sources.create.value = CreatePersonalShelfState(name = "new")
            sources.edit.value = EditPersonalShelfState(shelfId = "shelf-1")
            sources.delete.value = DeletePersonalShelfState(shelfId = "shelf-2")
            runCurrent()

            assertEquals("shelf-1", state.value.detail.shelfId)
            assertEquals("shelf-1", state.value.editor.shelfId)
            assertEquals(8, state.value.group.totalCount)
            assertEquals("new", state.value.create.name)
            assertEquals("shelf-1", state.value.edit.shelfId)
            assertEquals("shelf-2", state.value.delete.shelfId)
            assertEquals(state.value, async { state.first() }.await())
        }

    @Test
    fun `aggregate stops changing when its owning scope is cancelled`() = runTest {
        val owner = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        val sources = ShelvesStateSources()
        val state = sources.aggregate(owner)
        runCurrent()
        val lastOwnedState = state.value

        owner.cancel()
        sources.shared.value = ShelfCollectionState(totalCount = 99)
        runCurrent()

        assertEquals(lastOwnedState, state.value)
    }
}

private class ShelvesStateSources(
    navigation: ShelvesNavigationState = ShelvesNavigationState(),
    personal: ShelfCollectionState = ShelfCollectionState(),
    shared: ShelfCollectionState = ShelfCollectionState(),
    group: ShelfCollectionState = ShelfCollectionState(),
    detail: ShelfDetailState = ShelfDetailState(),
    editor: ShelfContentsEditorState = ShelfContentsEditorState(),
    create: CreatePersonalShelfState = CreatePersonalShelfState(),
    edit: EditPersonalShelfState = EditPersonalShelfState(),
    delete: DeletePersonalShelfState = DeletePersonalShelfState()
) {
    val navigation = MutableStateFlow(navigation)
    val personal = MutableStateFlow(personal)
    val shared = MutableStateFlow(shared)
    val group = MutableStateFlow(group)
    val detail = MutableStateFlow(detail)
    val editor = MutableStateFlow(editor)
    val create = MutableStateFlow(create)
    val edit = MutableStateFlow(edit)
    val delete = MutableStateFlow(delete)

    fun aggregate(scope: CoroutineScope) = shelvesStateFlow(
        scope,
        navigation,
        personal,
        shared,
        group,
        detail,
        editor,
        create,
        edit,
        delete
    )
}
