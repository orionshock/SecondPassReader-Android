package com.secondpasslibrary.reader.library

import com.secondpasslibrary.client.AuthorOrdering
import com.secondpasslibrary.client.SplClientException
import com.secondpasslibrary.reader.app.storage.AccountLocalBookCatalog
import com.secondpasslibrary.reader.library.axis.FakeLibraryAxisClient
import com.secondpasslibrary.reader.library.axis.FakeLibraryAxisClientProvider
import com.secondpasslibrary.reader.library.axis.author
import com.secondpasslibrary.reader.library.axis.axisBook
import com.secondpasslibrary.reader.library.axis.axisPage
import com.secondpasslibrary.reader.library.axis.catalogTag
import com.secondpasslibrary.reader.library.axis.libraryPage
import com.secondpasslibrary.reader.library.axis.libraryProfile
import com.secondpasslibrary.reader.library.axis.series
import com.secondpasslibrary.reader.library.books.LibraryBooksController
import com.secondpasslibrary.reader.library.books.LibraryBooksEntry
import com.secondpasslibrary.reader.library.books.LibraryBooksLayout
import com.secondpasslibrary.reader.library.books.LibraryDisplayPreferenceStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryAuthorityLifetimeTest {
    @Test
    fun `offline transition rejects late Author result and authentication event`() = runTest {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val client = FakeLibraryAxisClient().apply {
            authorList = {
                started.complete(Unit)
                withContext(NonCancellable) { release.await() }
                throw SplClientException.AuthenticationRejected()
            }
        }
        val controller = controller(client)
        val events = collectConnectionEvents(controller)
        controller.initialize(libraryProfile(), LibraryBooksEntry.Browse, false)
        controller.selectAxis(LibraryAxis.AUTHORS)
        started.await()

        controller.initializeOffline(libraryProfile(), PROFILE_ID, LibraryBooksEntry.Browse)
        release.complete(Unit)
        advanceUntilIdle()

        assertEquals(LibraryAxis.BOOKS, controller.state.value.axis)
        assertTrue(controller.state.value.result is LibraryResultState.Books)
        assertTrue(events.isEmpty())
    }

    @Test
    fun `offline transition rejects late Series result and authentication event`() = runTest {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val client = FakeLibraryAxisClient().apply {
            seriesList = {
                started.complete(Unit)
                withContext(NonCancellable) { release.await() }
                throw SplClientException.AuthenticationRejected()
            }
        }
        val controller = controller(client)
        val events = collectConnectionEvents(controller)
        controller.initialize(libraryProfile(), LibraryBooksEntry.Browse, false)
        controller.selectAxis(LibraryAxis.SERIES)
        started.await()

        controller.initializeOffline(libraryProfile(), PROFILE_ID, LibraryBooksEntry.Browse)
        release.complete(Unit)
        advanceUntilIdle()

        assertEquals(LibraryAxis.BOOKS, controller.state.value.axis)
        assertTrue(controller.state.value.result is LibraryResultState.Books)
        assertTrue(events.isEmpty())
    }

    @Test
    fun `offline transition rejects late filter vocabulary and authentication event`() = runTest {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val client = FakeLibraryAxisClient().apply {
            tags = { _, _ ->
                started.complete(Unit)
                withContext(NonCancellable) { release.await() }
                throw SplClientException.AuthenticationRejected()
            }
        }
        val controller = controller(client)
        val events = collectConnectionEvents(controller)
        controller.initialize(libraryProfile(), LibraryBooksEntry.Browse, false)
        started.await()

        controller.initializeOffline(libraryProfile(), PROFILE_ID, LibraryBooksEntry.Browse)
        release.complete(Unit)
        advanceUntilIdle()

        assertFalse(controller.state.value.tagSelector.loaded)
        assertTrue(controller.state.value.tagSelector.tags.isEmpty())
        assertEquals(null, controller.state.value.tagSelector.failure)
        assertTrue(events.isEmpty())
    }

    @Test
    fun `offline transition resets online surface and keeps downloaded Books`() = runTest {
        val onlineTag = catalogTag("online-tag")
        val client = FakeLibraryAxisClient().apply {
            authorList = { axisPage(it.page, listOf(author("online-author"))) }
            tags = { _, options -> libraryPage(options.page, listOf(onlineTag)) }
        }
        val controller = controller(client, offlineBooks = listOf(axisBook("offline-book")))
        controller.initialize(libraryProfile(), LibraryBooksEntry.Browse, false)
        controller.selectAxis(LibraryAxis.AUTHORS)
        controller.changeAuthorOrdering(AuthorOrdering.BOOK_COUNT_DESCENDING)
        advanceUntilIdle()

        controller.initializeOffline(libraryProfile(), PROFILE_ID, LibraryBooksEntry.Browse)
        advanceUntilIdle()

        val state = controller.state.value
        assertEquals(LibraryAxis.BOOKS, state.axis)
        val offlineBooks = (state.result as LibraryResultState.Books).state
        assertEquals(listOf("offline-book"), offlineBooks.books.map { it.id })
        assertTrue(offlineBooks.offlineDownloadedOnly)
        assertTrue(state.tagSelector.tags.isEmpty())
        assertEquals(null, state.selectedTag)
    }

    @Test
    fun `reconnect publishes fresh Author result and never resurrects pre-offline result`() =
        runTest {
            val staleStarted = CompletableDeferred<Unit>()
            val releaseStale = CompletableDeferred<Unit>()
            var requests = 0
            val client = FakeLibraryAxisClient().apply {
                authorList = {
                    requests += 1
                    if (requests == 1) {
                        staleStarted.complete(Unit)
                        withContext(NonCancellable) { releaseStale.await() }
                        axisPage(it.page, listOf(author("stale")))
                    } else {
                        axisPage(it.page, listOf(author("fresh")))
                    }
                }
            }
            val controller = controller(client)
            controller.initialize(libraryProfile(), LibraryBooksEntry.Browse, false)
            controller.selectAxis(LibraryAxis.AUTHORS)
            staleStarted.await()

            controller.initializeOffline(libraryProfile(), PROFILE_ID, LibraryBooksEntry.Browse)
            controller.initialize(libraryProfile(), LibraryBooksEntry.Browse, false)
            controller.selectAxis(LibraryAxis.AUTHORS)
            runCurrent()

            assertEquals(
                listOf("fresh"),
                (controller.state.value.result as LibraryResultState.AuthorIndex).state.items.map {
                    it.id
                }
            )
            releaseStale.complete(Unit)
            advanceUntilIdle()

            assertEquals(2, requests)
            assertEquals(
                listOf("fresh"),
                (controller.state.value.result as LibraryResultState.AuthorIndex).state.items.map {
                    it.id
                }
            )
        }

    @Test
    fun `reconnect publishes fresh vocabulary and rejects same-identity stale completion`() =
        runTest {
            val staleStarted = CompletableDeferred<Unit>()
            val releaseStale = CompletableDeferred<Unit>()
            var requests = 0
            val client = FakeLibraryAxisClient().apply {
                bookList = { throw SplClientException.ProtocolInvalid("books") }
                tags = { _, options ->
                    requests += 1
                    if (requests == 1) {
                        staleStarted.complete(Unit)
                        withContext(NonCancellable) { releaseStale.await() }
                        libraryPage(options.page, listOf(catalogTag("stale")))
                    } else {
                        libraryPage(options.page, listOf(catalogTag("fresh")))
                    }
                }
            }
            val controller = controller(client)
            controller.initialize(libraryProfile(), LibraryBooksEntry.Browse, false)
            staleStarted.await()

            controller.initializeOffline(libraryProfile(), PROFILE_ID, LibraryBooksEntry.Browse)
            controller.initialize(libraryProfile(), LibraryBooksEntry.Browse, false)
            advanceUntilIdle()

            assertEquals(listOf("fresh"), controller.state.value.tagSelector.tags.map { it.id })
            releaseStale.complete(Unit)
            advanceUntilIdle()

            assertEquals(2, requests)
            assertEquals(listOf("fresh"), controller.state.value.tagSelector.tags.map { it.id })
        }

    private fun TestScope.controller(
        client: FakeLibraryAxisClient,
        offlineBooks: List<com.secondpasslibrary.client.CompactBook> = emptyList()
    ): LibraryController {
        val provider = FakeLibraryAxisClientProvider(client)
        val controllerScope =
            CoroutineScope(
                backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)
            )
        val preferences = InMemoryDisplayPreferenceStore()
        return LibraryController(
            provider,
            preferences,
            controllerScope,
            books =
                LibraryBooksController(
                    provider,
                    preferences,
                    controllerScope,
                    AccountLocalBookCatalog { offlineBooks }
                )
        )
    }

    private fun TestScope.collectConnectionEvents(
        controller: LibraryController
    ): MutableList<LibraryConnectionEvent> {
        val events = mutableListOf<LibraryConnectionEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            controller.connectionEvents.collect(events::add)
        }
        runCurrent()
        return events
    }

    private class InMemoryDisplayPreferenceStore : LibraryDisplayPreferenceStore {
        override suspend fun read() = LibraryBooksLayout.GRID

        override suspend fun write(layout: LibraryBooksLayout) = Unit
    }

    private companion object {
        const val PROFILE_ID = "profile-1"
    }
}
