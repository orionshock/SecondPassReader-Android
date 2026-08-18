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

    @Test
    fun `tag vocabulary is complete and selected tag filters every axis`() = runTest {
        val selected = catalogTag("fiction", "fiction")
        val client = FakeLibraryAxisClient().apply {
            bookList = { options ->
                axisPage(options.page, emptyList(), hasNext = options.page == 1)
            }
            tags = { _, options ->
                if (options.page == 1) {
                    axisPage(1, listOf(selected), total = 2, hasNext = true)
                } else {
                    axisPage(2, listOf(catalogTag("history")), total = 2)
                }
            }
        }
        val controller = controller(client)
        controller.initialize(libraryProfile(), LibraryBooksEntry.Browse, false)
        advanceUntilIdle()
        controller.commitSearch("dune")
        advanceUntilIdle()
        controller.loadNextPage()
        advanceUntilIdle()

        controller.selectTag(selected)
        advanceUntilIdle()
        assertEquals("fiction", client.bookRequests.last().tagSlug)
        assertEquals("dune", client.bookRequests.last().q)
        assertEquals(1, client.bookRequests.last().page)
        assertEquals(
            listOf("fiction", "history"),
            controller.state.value.tagSelector.tags.map {
                it.slug
            }
        )
        assertEquals(selected, controller.state.value.selectedTag)

        controller.selectAxis(LibraryAxis.AUTHORS)
        advanceUntilIdle()
        assertEquals("fiction", client.authorRequests.last().tagSlug)

        controller.selectAxis(LibraryAxis.SERIES)
        advanceUntilIdle()
        assertEquals("fiction", client.seriesRequests.last().tagSlug)
    }

    @Test
    fun `selected entity Books retain shared tag and selecting it again clears it`() = runTest {
        val selected = catalogTag("fiction", "fiction")
        val client = FakeLibraryAxisClient().apply {
            tags = { _, options -> axisPage(options.page, listOf(selected)) }
        }
        val controller = controller(client)
        controller.initialize(libraryProfile(), LibraryBooksEntry.Browse, false)
        advanceUntilIdle()
        controller.selectTag(selected)
        controller.selectAuthor("author-1")
        advanceUntilIdle()

        assertEquals("author-1", client.bookRequests.last().authorId)
        assertEquals("fiction", client.bookRequests.last().tagSlug)

        controller.selectTag(selected)
        advanceUntilIdle()
        assertEquals(null, controller.state.value.selectedTag)
        assertEquals(null, client.bookRequests.last().tagSlug)
        assertEquals("author-1", client.bookRequests.last().authorId)
    }

    @Test
    fun `scope change clears tag selection reloads vocabulary and retains Library results`() =
        runTest {
            val group = LibraryGroupSummary("group-1", "Group", false)
            val globalTag = catalogTag("global")
            val scopedTag = catalogTag("scoped")
            val client = FakeLibraryAxisClient().apply {
                groups = { axisPage(it.page, listOf(group)) }
                tags = { scope, options ->
                    axisPage(
                        options.page,
                        listOf(if (scope == LibraryScope.Global) globalTag else scopedTag)
                    )
                }
                bookList = { axisPage(it.page, listOf(axisBook("book"))) }
            }
            val controller = controller(client)
            controller.initialize(libraryProfile(), LibraryBooksEntry.Browse, true)
            advanceUntilIdle()
            controller.selectTag(globalTag)
            advanceUntilIdle()

            controller.selectScope(LibraryScope.Group(group.id))
            advanceUntilIdle()

            assertEquals(null, controller.state.value.selectedTag)
            assertEquals(listOf("scoped"), controller.state.value.tagSelector.tags.map { it.slug })
            assertEquals(
                listOf(LibraryScope.Global, LibraryScope.Group(group.id)),
                client.tagRequests.map {
                    it.first
                }
            )
            assertEquals(LibraryScope.Group(group.id), controller.state.value.scope)
        }

    @Test
    fun `tag vocabulary failure does not destroy loaded Books`() = runTest {
        val client = FakeLibraryAxisClient().apply {
            bookList = { axisPage(it.page, listOf(axisBook("book"))) }
            tags = { _, _ -> throw SplClientException.ProtocolInvalid("tags") }
        }
        val controller = controller(client)

        controller.initialize(libraryProfile(), LibraryBooksEntry.Browse, false)
        advanceUntilIdle()

        assertEquals(listOf("book"), controller.state.value.books.books.map { it.id })
        assertEquals(LibraryFailure.PROTOCOL_INVALID, controller.state.value.tagSelector.failure)
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
