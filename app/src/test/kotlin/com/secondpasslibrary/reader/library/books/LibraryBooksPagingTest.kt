package com.secondpasslibrary.reader.library.books

import com.secondpasslibrary.client.SplClientException
import com.secondpasslibrary.reader.library.LibraryConnectionEvent
import com.secondpasslibrary.reader.library.LibraryFailure
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryBooksPagingTest {
    @Test
    fun `page two appends without reordering prior results`() = runTest {
        val client = FakeLibraryClient().apply {
            listCall = { request ->
                if (request.page == 1) {
                    page(1, listOf("b", "a"), 4, hasNext = true)
                } else {
                    page(2, listOf("d", "c"), 4)
                }
            }
        }
        val controller = libraryBooksController(client)
        controller.initializeBrowse(profile())
        advanceUntilIdle()

        controller.loadNextPage()
        advanceUntilIdle()

        val state = controller.state.value
        assertEquals(listOf("b", "a", "d", "c"), state.books.map { it.id })
        assertEquals(2, state.currentPage)
        assertEquals(4, state.totalCount)
        assertFalse(state.hasNext)
        assertEquals(listOf(1, 2), client.bookRequests.map { it.page })
    }

    @Test
    fun `page results accumulate while contextual tags remain authoritative response metadata`() =
        runTest {
            val client = FakeLibraryClient().apply {
                listCall = { request ->
                    if (request.page == 1) {
                        page(
                            1,
                            listOf("only-result"),
                            2,
                            hasNext = true,
                            catalogTags = listOf(aggregateTag("fiction", 91))
                        )
                    } else {
                        page(
                            2,
                            listOf("second-result"),
                            2,
                            catalogTags = listOf(aggregateTag("history", 47))
                        )
                    }
                }
            }
            val controller = libraryBooksController(client)
            controller.initializeBrowse(profile())
            advanceUntilIdle()

            assertEquals(91, controller.state.value.contextualCatalogTags.single().bookCount)
            controller.loadNextPage()
            advanceUntilIdle()

            assertEquals(
                listOf("only-result", "second-result"),
                controller.state.value.books.map { it.id }
            )
            assertEquals(
                listOf("history" to 47),
                controller.state.value.contextualCatalogTags.map { it.slug to it.bookCount }
            )
        }

    @Test
    fun `duplicate next page requests are suppressed while one is active`() = runTest {
        val pageTwoGate = CompletableDeferred<Unit>()
        val client = FakeLibraryClient().apply {
            listCall = { request ->
                if (request.page == 1) {
                    page(1, listOf("first"), 2, hasNext = true)
                } else {
                    pageTwoGate.await()
                    page(2, listOf("second"), 2)
                }
            }
        }
        val controller = libraryBooksController(client)
        controller.initializeBrowse(profile())
        advanceUntilIdle()

        controller.loadNextPage()
        controller.loadNextPage()
        runCurrent()

        assertEquals(listOf(1, 2), client.bookRequests.map { it.page })
        assertTrue(controller.state.value.nextPageLoading)
        pageTwoGate.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun `stale response is ignored after committed query changes`() = runTest {
        val staleGate = CompletableDeferred<Unit>()
        val client = FakeLibraryClient().apply {
            listCall = { request ->
                when (request.q) {
                    "old" -> withContext(NonCancellable) {
                        staleGate.await()
                        page(1, listOf("stale"), 1)
                    }

                    "new" ->
                        page(
                            1,
                            listOf("current"),
                            1,
                            catalogTags = listOf(aggregateTag("new-context", 12))
                        )

                    else -> page(1, emptyList(), 0)
                }
            }
        }
        val controller = libraryBooksController(client)
        controller.initializeBrowse(profile())
        advanceUntilIdle()

        controller.commitBrowseQuery("old")
        runCurrent()
        controller.commitBrowseQuery("new")
        runCurrent()
        staleGate.complete(Unit)
        advanceUntilIdle()

        assertEquals("new", controller.state.value.committedQuery)
        assertEquals(listOf("current"), controller.state.value.books.map { it.id })
        assertEquals(
            listOf("new-context"),
            controller.state.value.contextualCatalogTags.map { it.slug }
        )
    }

    @Test
    fun `next page failure retains accumulated results and can retry`() = runTest {
        var pageTwoAttempts = 0
        val client = FakeLibraryClient().apply {
            listCall = { request ->
                if (request.page == 1) {
                    page(1, listOf("first"), 2, hasNext = true)
                } else {
                    pageTwoAttempts += 1
                    if (pageTwoAttempts == 1) throw SplClientException.ServerUnreachable()
                    page(2, listOf("second"), 2)
                }
            }
        }
        val controller = libraryBooksController(client)
        controller.initializeBrowse(profile())
        advanceUntilIdle()

        controller.loadNextPage()
        advanceUntilIdle()

        assertEquals(listOf("first"), controller.state.value.books.map { it.id })
        assertEquals(
            LibraryBooksLoadError(
                LibraryFailure.UNREACHABLE,
                LibraryBooksLoadPhase.NEXT_PAGE
            ),
            controller.state.value.error
        )
        assertTrue(controller.state.value.hasNext)

        controller.retry()
        advanceUntilIdle()
        assertEquals(listOf("first", "second"), controller.state.value.books.map { it.id })
    }

    @Test
    fun `initial failure exposes retry without results`() = runTest {
        var attempts = 0
        val client = FakeLibraryClient().apply {
            listCall = {
                attempts += 1
                if (attempts == 1) throw SplClientException.ProtocolInvalid("book page")
                page(1, listOf("recovered"), 1)
            }
        }
        val controller = libraryBooksController(client)

        controller.initializeBrowse(profile())
        advanceUntilIdle()

        assertTrue(controller.state.value.books.isEmpty())
        assertEquals(0, controller.state.value.currentPage)
        assertEquals(
            LibraryBooksLoadError(
                LibraryFailure.PROTOCOL_INVALID,
                LibraryBooksLoadPhase.INITIAL
            ),
            controller.state.value.error
        )

        controller.retry()
        advanceUntilIdle()
        assertEquals(listOf("recovered"), controller.state.value.books.map { it.id })
    }

    @Test
    fun `refresh failure retains loaded results`() = runTest {
        var refresh = false
        val client = FakeLibraryClient().apply {
            listCall = {
                if (refresh) throw SplClientException.ServerUnreachable()
                page(1, listOf("visible"), 1)
            }
        }
        val controller = libraryBooksController(client)
        controller.initializeBrowse(profile())
        advanceUntilIdle()
        refresh = true

        controller.refresh()
        advanceUntilIdle()

        assertEquals(listOf("visible"), controller.state.value.books.map { it.id })
        assertEquals(
            LibraryBooksLoadPhase.REFRESH,
            controller.state.value.error?.phase
        )
        assertFalse(controller.state.value.refreshing)
    }

    @Test
    fun `authentication rejection remains distinct and reaches connection ownership`() = runTest {
        val client = FakeLibraryClient().apply {
            listCall = { throw SplClientException.AuthenticationRejected() }
        }
        val controller = libraryBooksController(client)
        val event = async { controller.connectionEvents.first() }
        runCurrent()

        controller.initializeBrowse(profile())
        advanceUntilIdle()

        assertEquals(LibraryConnectionEvent.AuthenticationRejected, event.await())
        assertEquals(
            LibraryFailure.AUTHENTICATION_REJECTED,
            controller.state.value.error?.failure
        )
    }
}
