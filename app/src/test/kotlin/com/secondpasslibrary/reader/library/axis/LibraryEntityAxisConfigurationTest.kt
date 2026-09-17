package com.secondpasslibrary.reader.library.axis

import com.secondpasslibrary.client.AuthorOrdering
import com.secondpasslibrary.client.LibraryScope
import com.secondpasslibrary.client.SeriesOrdering
import com.secondpasslibrary.reader.library.DEFAULT_LIBRARY_PAGE_SIZE
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryEntityAxisConfigurationTest {
    @Test
    fun `Author configuration owns typed list and detail requests`() = runTest {
        val client = FakeLibraryAxisClient()
        val controller = libraryAuthorsAxisController(FakeLibraryAxisClientProvider(client), this)

        controller.prepare(libraryProfile(), LibraryScope.Global, tagSlug = "fiction")
        controller.activate()
        controller.select("author-1")
        advanceUntilIdle()

        val request = client.authorRequests.single()
        assertEquals(null, request.q)
        assertEquals("fiction", request.tagSlug)
        assertEquals(AuthorOrdering.NAME, request.ordering)
        assertEquals(1, request.page)
        assertEquals(DEFAULT_LIBRARY_PAGE_SIZE, request.pageSize)
        assertEquals(LIBRARY_AXIS_PREVIEW_LIMIT, request.previewLimit)
        assertEquals(
            LIBRARY_ENTITY_DETAIL_PREVIEW_LIMIT,
            client.authorDetailRequests.single().second.previewLimit
        )
        assertTrue(client.seriesRequests.isEmpty())
        assertTrue(client.seriesDetailRequests.isEmpty())
    }

    @Test
    fun `Author configuration follows scoped Author endpoint`() = runTest {
        val client = FakeLibraryAxisClient()
        val controller = libraryAuthorsAxisController(FakeLibraryAxisClientProvider(client), this)

        controller.prepare(libraryProfile(), LibraryScope.Group("group-1"))
        controller.activate()
        advanceUntilIdle()

        assertEquals("group-1", client.groupAuthorRequests.single().first)
        assertTrue(client.groupSeriesRequests.isEmpty())
    }

    @Test
    fun `Series configuration owns typed list and detail requests`() = runTest {
        val client = FakeLibraryAxisClient()
        val controller = librarySeriesAxisController(FakeLibraryAxisClientProvider(client), this)

        controller.prepare(libraryProfile(), LibraryScope.Global, tagSlug = "cycles")
        controller.activate()
        controller.select("series-1")
        advanceUntilIdle()

        val request = client.seriesRequests.single()
        assertEquals(null, request.q)
        assertEquals("cycles", request.tagSlug)
        assertEquals(SeriesOrdering.NAME, request.ordering)
        assertEquals(1, request.page)
        assertEquals(DEFAULT_LIBRARY_PAGE_SIZE, request.pageSize)
        assertEquals(LIBRARY_AXIS_PREVIEW_LIMIT, request.previewLimit)
        assertEquals(
            LIBRARY_ENTITY_DETAIL_PREVIEW_LIMIT,
            client.seriesDetailRequests.single().second.previewLimit
        )
        assertTrue(client.authorRequests.isEmpty())
        assertTrue(client.authorDetailRequests.isEmpty())
    }

    @Test
    fun `Series configuration follows scoped Series endpoint`() = runTest {
        val client = FakeLibraryAxisClient()
        val controller = librarySeriesAxisController(FakeLibraryAxisClientProvider(client), this)

        controller.prepare(libraryProfile(), LibraryScope.Group("group-1"))
        controller.activate()
        advanceUntilIdle()

        assertEquals("group-1", client.groupSeriesRequests.single().first)
        assertTrue(client.groupAuthorRequests.isEmpty())
    }
}
