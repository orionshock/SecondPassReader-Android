package com.secondpasslibrary.reader.bookdetail

import com.secondpasslibrary.client.ShelfMutationRejection
import com.secondpasslibrary.client.ShelfOwner
import com.secondpasslibrary.client.ShelfScope
import com.secondpasslibrary.client.ShelfVisibility
import com.secondpasslibrary.client.SplClientException
import com.secondpasslibrary.reader.shelves.RecordingShelvesCapability
import com.secondpasslibrary.reader.shelves.ShelvesTestClient
import com.secondpasslibrary.reader.shelves.ShelvesTestClientProvider
import com.secondpasslibrary.reader.shelves.shelf
import com.secondpasslibrary.reader.shelves.shelfItem
import com.secondpasslibrary.reader.shelves.shelfPage
import com.secondpasslibrary.reader.shelves.shelvesProfile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BookShelfPickerControllerTest {
    @Test
    fun `loads every page and keeps only editable user-owned targets`() = runTest {
        val capability = RecordingShelvesCapability().apply {
            listCall = { options ->
                when {
                    options.bookId != null -> shelfPage(options.page, emptyList())

                    options.page == 1 -> shelfPage(1, listOf(target("z", "Zulu")), 4, true)

                    else -> shelfPage(
                        2,
                        listOf(
                            target("a", "Alpha"),
                            target("readonly", "Read only", canEdit = false),
                            target("group", "Group", groupOwned = true)
                        ),
                        4
                    )
                }
            }
        }
        val controller = controller(capability, this)

        controller.open("book-1")
        advanceUntilIdle()

        assertEquals(listOf("Alpha", "Zulu"), controller.state.value.targets.map { it.name })
        assertTrue(capability.listRequests.all { it.scope == ShelfScope.PERSONAL })
        assertEquals(
            listOf(1, 2),
            capability.listRequests.filter {
                it.bookId == null
            }.map { it.page }
        )
        assertEquals(
            listOf(1),
            capability.listRequests.filter {
                it.bookId != null
            }.map { it.page }
        )
    }

    @Test
    fun `membership pages mark matching shelves Added`() = runTest {
        val capability = RecordingShelvesCapability().apply {
            listCall = { options ->
                when {
                    options.bookId == null -> shelfPage(
                        1,
                        listOf(target("a", "Alpha"), target("b", "Beta"))
                    )

                    options.page == 1 -> shelfPage(1, listOf(target("b", "Beta")), 2, true)

                    else -> shelfPage(2, listOf(target("other", "Other")), 2)
                }
            }
        }
        val controller = controller(capability, this)

        controller.open("book-1")
        advanceUntilIdle()

        assertFalse(controller.state.value.targets.single { it.shelfId == "a" }.added)
        assertTrue(controller.state.value.targets.single { it.shelfId == "b" }.added)
        assertEquals(
            listOf(1, 2),
            capability.listRequests.filter {
                it.bookId == "book-1"
            }.map { it.page }
        )
    }

    @Test
    fun `successful add uses positionless append and marks only target Added`() = runTest {
        val capability = targetsCapability()
        val controller = controller(capability, this)
        controller.open("book-1")
        advanceUntilIdle()

        controller.addTo("a")
        advanceUntilIdle()

        val request = capability.addRequests.single()
        assertEquals("a", request.first)
        assertEquals("book-1", request.second.bookId)
        assertNull(request.second.position)
        assertTrue(controller.state.value.targets.single { it.shelfId == "a" }.added)
        assertFalse(controller.state.value.targets.single { it.shelfId == "b" }.added)
    }

    @Test
    fun `duplicate response is reconciled as already Added`() = runTest {
        val capability = targetsCapability().apply {
            addCall = { _, _ ->
                throw SplClientException.ShelfMutationRejected(
                    ShelfMutationRejection.DUPLICATE_BOOK
                )
            }
        }
        val controller = controller(capability, this)
        controller.open("book-1")
        advanceUntilIdle()

        controller.addTo("a")
        advanceUntilIdle()

        assertTrue(controller.state.value.targets.single { it.shelfId == "a" }.added)
        assertNull(controller.state.value.targets.single { it.shelfId == "a" }.failure)
        assertEquals(1, capability.addRequests.size)
    }

    @Test
    fun `ambiguous add rechecks membership without retrying POST`() = runTest {
        var membershipReads = 0
        val capability = targetsCapability().apply {
            listCall = { options ->
                if (options.bookId == null) {
                    shelfPage(1, listOf(target("a", "Alpha"), target("b", "Beta")))
                } else {
                    membershipReads += 1
                    shelfPage(
                        1,
                        if (membershipReads ==
                            1
                        ) {
                            emptyList()
                        } else {
                            listOf(target("a", "Alpha"))
                        }
                    )
                }
            }
            addCall = { _, _ -> throw SplClientException.ServerUnreachable() }
        }
        val controller = controller(capability, this)
        controller.open("book-1")
        advanceUntilIdle()

        controller.addTo("a")
        advanceUntilIdle()

        assertEquals(1, capability.addRequests.size)
        assertEquals(2, membershipReads)
        assertTrue(controller.state.value.targets.single { it.shelfId == "a" }.added)
    }

    @Test
    fun `ambiguous add with absent membership preserves targets and reports failure`() = runTest {
        val capability = targetsCapability().apply {
            addCall = { _, _ -> throw SplClientException.ServerUnreachable() }
        }
        val controller = controller(capability, this)
        controller.open("book-1")
        advanceUntilIdle()

        controller.addTo("a")
        advanceUntilIdle()

        assertEquals(2, controller.state.value.targets.size)
        val target = controller.state.value.targets.single { it.shelfId == "a" }
        assertFalse(target.added)
        assertEquals(BookShelfPickerFailure.UNREACHABLE, target.failure)
        assertEquals(1, capability.addRequests.size)
    }

    @Test
    fun `ordinary mutation failure keeps picker content`() = runTest {
        val capability = targetsCapability().apply {
            addCall = { _, _ ->
                throw SplClientException.ShelfMutationRejected(
                    ShelfMutationRejection.NOT_AUTHORIZED
                )
            }
        }
        val controller = controller(capability, this)
        controller.open("book-1")
        advanceUntilIdle()

        controller.addTo("a")
        advanceUntilIdle()

        assertEquals(2, controller.state.value.targets.size)
        assertEquals(
            BookShelfPickerFailure.NOT_AUTHORIZED,
            controller.state.value.targets.single { it.shelfId == "a" }.failure
        )
    }

    private fun controller(
        capability: RecordingShelvesCapability,
        scope: kotlinx.coroutines.CoroutineScope
    ) = BookShelfPickerController(
        ShelvesTestClientProvider(ShelvesTestClient(capability)),
        scope
    ).also { it.prepare(shelvesProfile()) }

    private fun targetsCapability() = RecordingShelvesCapability().apply {
        listCall = { options ->
            shelfPage(
                options.page,
                if (options.bookId == null) {
                    listOf(target("a", "Alpha"), target("b", "Beta"))
                } else {
                    emptyList()
                }
            )
        }
        addCall = { shelfId, _ -> shelfItem("added", 0).copy(shelfId = shelfId) }
    }
}

private fun target(id: String, name: String, canEdit: Boolean = true, groupOwned: Boolean = false) =
    shelf(id, canEdit).copy(
        name = name,
        owner = if (groupOwned) {
            ShelfOwner.Group(
                "group-1",
                "Group",
                false
            )
        } else {
            ShelfOwner.User("p", "u")
        },
        visibility = ShelfVisibility.PRIVATE
    )
