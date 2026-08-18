package com.secondpasslibrary.reader.library

import com.secondpasslibrary.client.BookOrdering
import com.secondpasslibrary.client.LibraryGroupSummary
import com.secondpasslibrary.client.LibraryScope
import com.secondpasslibrary.client.SplClientException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryControllerAxisTest {
    @Test
    fun `axis switching activates children and preserves valid child state`() = runTest {
        val client = FakeLibraryAxisClient().apply {
            authorList = { axisPage(it.page, listOf(author("author"))) }
            seriesList = { axisPage(it.page, listOf(series("series"))) }
        }
        val controller = controller(client)
        controller.initialize(libraryProfile(), LibraryBooksEntry.Browse, false)
        advanceUntilIdle()

        controller.selectAxis(LibraryAxis.AUTHORS)
        advanceUntilIdle()
        controller.selectAxis(LibraryAxis.SERIES)
        advanceUntilIdle()
        controller.selectAxis(LibraryAxis.AUTHORS)
        advanceUntilIdle()

        assertEquals(LibraryAxis.AUTHORS, controller.state.value.axis)
        assertEquals(listOf("author"), controller.state.value.authors.items.map { it.id })
        assertEquals(listOf("series"), controller.state.value.series.items.map { it.id })
        assertEquals(1, client.authorRequests.size)
        assertEquals(1, client.seriesRequests.size)
    }

    @Test
    fun `scope change propagates to active child and preserves axis`() = runTest {
        val group = LibraryGroupSummary("group-1", "Group", false)
        val client = FakeLibraryAxisClient().apply {
            groups = { axisPage(it.page, listOf(group)) }
            groupAuthorList = { _, options -> axisPage(options.page, listOf(author("scoped"))) }
        }
        val controller = controller(client)
        controller.initialize(libraryProfile(), LibraryBooksEntry.Browse, true)
        advanceUntilIdle()
        controller.selectAxis(LibraryAxis.AUTHORS)
        advanceUntilIdle()

        controller.selectScope(LibraryScope.Group(group.id))
        advanceUntilIdle()

        assertEquals(LibraryAxis.AUTHORS, controller.state.value.axis)
        assertEquals(LibraryScope.Group(group.id), controller.state.value.scope)
        assertEquals(listOf("scoped"), controller.state.value.authors.items.map { it.id })
        assertEquals(group.id, client.groupAuthorRequests.single().first)
        assertTrue(client.groupSeriesRequests.isEmpty())
    }

    @Test
    fun `parent routes Author and Series selections without sibling dependencies`() = runTest {
        val client = FakeLibraryAxisClient()
        val controller = controller(client)
        controller.initialize(libraryProfile(), LibraryBooksEntry.Browse, false)
        advanceUntilIdle()

        controller.selectAuthor("author-1")
        advanceUntilIdle()
        assertEquals(LibraryAxis.AUTHORS, controller.state.value.axis)
        assertEquals(LibraryResultKind.BOOKS, controller.state.value.resultKind)
        assertTrue(controller.state.value.resultKind.supportsBookLayout)
        assertEquals("author-1", controller.state.value.authors.selected?.detail?.id)
        assertEquals("author-1", client.bookRequests.last().authorId)
        assertEquals(BookOrdering.TITLE, client.bookRequests.last().ordering)

        controller.selectSeries("series-1")
        advanceUntilIdle()
        assertEquals(LibraryAxis.SERIES, controller.state.value.axis)
        assertEquals("series-1", controller.state.value.series.selected?.detail?.id)
        assertEquals("series-1", client.bookRequests.last().seriesId)
        assertEquals(BookOrdering.SERIES_INDEX, client.bookRequests.last().ordering)

        val childTypes =
            listOf(LibraryAuthorsController::class.java, LibrarySeriesController::class.java)
        assertFalse(
            childTypes.flatMap { it.declaredConstructors.toList() }
                .flatMap { it.parameterTypes.toList() }
                .any { it == LibraryBooksController::class.java }
        )
    }

    @Test
    fun `clearing selected Author restores preserved Author index`() = runTest {
        val client = FakeLibraryAxisClient().apply {
            authorList = { axisPage(it.page, listOf(author("author-1"))) }
            bookList = { axisPage(it.page, listOf(axisBook("filtered"))) }
        }
        val controller = controller(client)
        controller.initialize(libraryProfile(), LibraryBooksEntry.Browse, false)
        controller.selectAxis(LibraryAxis.AUTHORS)
        advanceUntilIdle()

        controller.selectAuthor("author-1")
        advanceUntilIdle()
        controller.clearSelectedEntity()

        assertEquals(LibraryAxis.AUTHORS, controller.state.value.axis)
        assertEquals(LibraryResultKind.AUTHOR_INDEX, controller.state.value.resultKind)
        assertEquals(listOf("author-1"), controller.state.value.authors.items.map { it.id })
        assertEquals(null, controller.state.value.authors.selected)
    }

    @Test
    fun `selected entity search remains inside filtered Books`() = runTest {
        val client = FakeLibraryAxisClient()
        val controller = controller(client)
        controller.initialize(libraryProfile(), LibraryBooksEntry.Browse, false)
        advanceUntilIdle()

        controller.selectSeries("series-1")
        advanceUntilIdle()
        controller.commitSearch("foundation")
        advanceUntilIdle()

        val request = client.bookRequests.last()
        assertEquals("series-1", request.seriesId)
        assertEquals("foundation", request.q)
        assertEquals(LibraryAxis.SERIES, controller.state.value.axis)
        assertEquals(LibraryResultKind.BOOKS, controller.state.value.resultKind)
    }

    @Test
    fun `group selected entity Books use scoped filters and scope change clears context`() =
        runTest {
            val groupOne = LibraryGroupSummary("group-1", "One", false)
            val groupTwo = LibraryGroupSummary("group-2", "Two", false)
            val client = FakeLibraryAxisClient().apply {
                groups = { axisPage(it.page, listOf(groupOne, groupTwo)) }
            }
            val controller = controller(client)
            controller.initialize(libraryProfile(), LibraryBooksEntry.Browse, true)
            advanceUntilIdle()
            controller.selectAxis(LibraryAxis.AUTHORS)
            controller.selectScope(LibraryScope.Group(groupOne.id))
            advanceUntilIdle()

            controller.selectAuthor("author-1")
            advanceUntilIdle()

            assertEquals(groupOne.id, client.groupBookRequests.last().first)
            assertEquals("author-1", client.groupBookRequests.last().second.authorId)

            controller.selectScope(LibraryScope.Group(groupTwo.id))
            advanceUntilIdle()

            assertEquals(LibraryResultKind.AUTHOR_INDEX, controller.state.value.resultKind)
            assertEquals(null, controller.state.value.authors.selected)
            assertEquals(LibraryScope.Group(groupTwo.id), controller.state.value.scope)
        }

    @Test
    fun `detail failure does not erase filtered Books and paging stays with Books child`() =
        runTest {
            val client = FakeLibraryAxisClient().apply {
                authorDetail = { throw SplClientException.ProtocolInvalid("author") }
                bookList = { options ->
                    if (options.page == 1) {
                        axisPage(1, listOf(axisBook("one")), total = 2, hasNext = true)
                    } else {
                        axisPage(2, listOf(axisBook("two")), total = 2)
                    }
                }
            }
            val controller = controller(client)
            controller.initialize(libraryProfile(), LibraryBooksEntry.Browse, false)
            advanceUntilIdle()

            controller.selectAuthor("author-1")
            advanceUntilIdle()

            assertEquals(
                LibraryFailure.PROTOCOL_INVALID,
                controller.state.value.authors.selected?.failure
            )
            assertEquals(listOf("one"), controller.state.value.books.books.map { it.id })

            controller.loadNextPage()
            advanceUntilIdle()

            assertEquals(listOf("one", "two"), controller.state.value.books.books.map { it.id })
            assertEquals(listOf(1, 2), client.bookRequests.takeLast(2).map { it.page })
        }

    private fun kotlinx.coroutines.test.TestScope.controller(client: FakeLibraryAxisClient) =
        LibraryController(
            FakeLibraryAxisClientProvider(client),
            object : LibraryDisplayPreferenceStore {
                override suspend fun read() = LibraryBooksLayout.GRID

                override suspend fun write(layout: LibraryBooksLayout) = Unit
            },
            this
        )
}
