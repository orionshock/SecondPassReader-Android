package com.secondpasslibrary.reader.library

import com.secondpasslibrary.client.LibraryGroupSummary
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
        assertEquals("author-1", controller.state.value.authors.selected?.detail?.id)

        controller.selectSeries("series-1")
        advanceUntilIdle()
        assertEquals(LibraryAxis.SERIES, controller.state.value.axis)
        assertEquals("series-1", controller.state.value.series.selected?.detail?.id)

        val childTypes =
            listOf(LibraryAuthorsController::class.java, LibrarySeriesController::class.java)
        assertFalse(
            childTypes.flatMap { it.declaredConstructors.toList() }
                .flatMap { it.parameterTypes.toList() }
                .any { it == LibraryBooksController::class.java }
        )
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
