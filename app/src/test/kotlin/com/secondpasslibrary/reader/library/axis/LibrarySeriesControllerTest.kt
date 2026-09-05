package com.secondpasslibrary.reader.library.axis

import com.secondpasslibrary.client.LibraryScope
import com.secondpasslibrary.client.SeriesOrdering
import com.secondpasslibrary.client.SplClientException
import com.secondpasslibrary.reader.library.DEFAULT_LIBRARY_PAGE_SIZE
import com.secondpasslibrary.reader.library.LibraryFailure
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibrarySeriesControllerTest {
    @Test
    fun `initial load uses name order page size and bounded previews`() = runTest {
        val client = FakeLibraryAxisClient().apply {
            seriesList = {
                catalogPage(
                    1,
                    listOf(series("b"), series("a")),
                    8,
                    hasNext = true,
                    catalogTags = listOf(catalogTag("series-context"))
                )
            }
        }
        val controller = controller(client)
        controller.prepare(libraryProfile(), LibraryScope.Global)
        controller.activate()
        advanceUntilIdle()

        assertEquals(listOf("b", "a"), controller.state.value.items.map { it.id })
        assertEquals(SeriesOrdering.NAME, client.seriesRequests.single().ordering)
        assertEquals(DEFAULT_LIBRARY_PAGE_SIZE, client.seriesRequests.single().pageSize)
        assertEquals(LIBRARY_AXIS_PREVIEW_LIMIT, client.seriesRequests.single().previewLimit)
        assertTrue(controller.state.value.hasNext)
        assertEquals(
            listOf("series-context"),
            controller.state.value.contextualCatalogTags.map { it.slug }
        )
        assertTrue(controller.state.value.hasContextualCatalogTagsResponse)
    }

    @Test
    fun `committed search and ordering reset to page one`() = runTest {
        val client = FakeLibraryAxisClient()
        val controller = controller(client)
        controller.prepare(libraryProfile(), LibraryScope.Global)
        controller.activate()
        advanceUntilIdle()

        controller.commitSearch("cycle")
        advanceUntilIdle()
        controller.changeOrdering(SeriesOrdering.BOOK_COUNT_DESCENDING)
        advanceUntilIdle()

        assertEquals("cycle", controller.state.value.committedQuery)
        assertEquals(SeriesOrdering.BOOK_COUNT_DESCENDING, controller.state.value.ordering)
        assertEquals(1, client.seriesRequests.last().page)
        assertEquals("cycle", client.seriesRequests.last().q)
    }

    @Test
    fun `next page appends and duplicate request is suppressed`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val client = FakeLibraryAxisClient().apply {
            seriesList = { request ->
                if (request.page == 1) {
                    catalogPage(1, listOf(series("first")), 2, hasNext = true)
                } else {
                    gate.await()
                    catalogPage(2, listOf(series("second")), 2)
                }
            }
        }
        val controller = controller(client)
        controller.prepare(libraryProfile(), LibraryScope.Global)
        controller.activate()
        advanceUntilIdle()

        controller.loadNextPage()
        controller.loadNextPage()
        runCurrent()
        assertEquals(listOf(1, 2), client.seriesRequests.map { it.page })
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf("first", "second"), controller.state.value.items.map { it.id })
    }

    @Test
    fun `stale search response is rejected`() = runTest {
        val staleGate = CompletableDeferred<Unit>()
        val client = FakeLibraryAxisClient().apply {
            seriesList = { request ->
                when (request.q) {
                    "old" -> withContext(NonCancellable) {
                        staleGate.await()
                        catalogPage(1, listOf(series("stale")))
                    }

                    "new" -> catalogPage(1, listOf(series("current")))

                    else -> catalogPage(1, emptyList())
                }
            }
        }
        val controller = controller(client)
        controller.prepare(libraryProfile(), LibraryScope.Global)
        controller.activate()
        advanceUntilIdle()

        controller.commitSearch("old")
        runCurrent()
        controller.commitSearch("new")
        runCurrent()
        staleGate.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf("current"), controller.state.value.items.map { it.id })
    }

    @Test
    fun `group scope uses group Series and clears selected detail`() = runTest {
        val client = FakeLibraryAxisClient().apply {
            groupSeriesList = { _, options -> catalogPage(options.page, listOf(series("group"))) }
        }
        val controller = controller(client)
        controller.prepare(libraryProfile(), LibraryScope.Global)
        controller.activate()
        advanceUntilIdle()
        controller.selectSeries("old")
        advanceUntilIdle()

        controller.selectScope(LibraryScope.Group("group-1"), activate = true)
        advanceUntilIdle()

        assertEquals("group-1", client.groupSeriesRequests.single().first)
        assertEquals(listOf("group"), controller.state.value.items.map { it.id })
        assertNull(controller.state.value.selected)
    }

    @Test
    fun `selected Series detail and explicit detail failure are retained in state`() = runTest {
        val client = FakeLibraryAxisClient()
        val controller = controller(client)
        controller.prepare(libraryProfile(), LibraryScope.Global)
        controller.selectSeries("good")
        advanceUntilIdle()

        assertEquals("Summary good", controller.state.value.selected?.detail?.summary)
        assertEquals(
            LIBRARY_ENTITY_DETAIL_PREVIEW_LIMIT,
            client.seriesDetailRequests.single().second.previewLimit
        )

        client.seriesDetail = { throw SplClientException.ProtocolInvalid("series") }
        controller.selectSeries("bad")
        advanceUntilIdle()

        val selected = controller.state.value.selected
        assertEquals("bad", selected?.id)
        assertEquals(LibraryFailure.PROTOCOL_INVALID, selected?.failure)
        assertFalse(selected?.loading ?: true)
    }

    @Test
    fun `next page failure retains Series and retry appends`() = runTest {
        var attempts = 0
        val client = FakeLibraryAxisClient().apply {
            seriesList = { request ->
                if (request.page == 1) {
                    catalogPage(1, listOf(series("first")), 2, hasNext = true)
                } else {
                    attempts += 1
                    if (attempts == 1) throw SplClientException.ServerUnreachable()
                    catalogPage(2, listOf(series("second")), 2)
                }
            }
        }
        val controller = controller(client)
        controller.prepare(libraryProfile(), LibraryScope.Global)
        controller.activate()
        advanceUntilIdle()
        controller.loadNextPage()
        advanceUntilIdle()

        assertEquals(listOf("first"), controller.state.value.items.map { it.id })
        assertEquals(PagedLibraryAxisLoadPhase.NEXT_PAGE, controller.state.value.error?.phase)
        controller.retry()
        advanceUntilIdle()

        assertEquals(listOf("first", "second"), controller.state.value.items.map { it.id })
        assertNull(controller.state.value.error)
    }

    private fun kotlinx.coroutines.test.TestScope.controller(client: FakeLibraryAxisClient) =
        LibrarySeriesController(FakeLibraryAxisClientProvider(client), this)
}
