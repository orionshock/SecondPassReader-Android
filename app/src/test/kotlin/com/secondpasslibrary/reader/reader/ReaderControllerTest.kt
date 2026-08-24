package com.secondpasslibrary.reader.reader

import com.secondpasslibrary.client.SplClientException
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.reader.asset.ReaderBookAssetResolver
import com.secondpasslibrary.reader.reader.asset.ReaderEpubUnavailableException
import com.secondpasslibrary.reader.reader.asset.ResolvedReaderBook
import com.secondpasslibrary.reader.reader.domain.ReaderEngine
import com.secondpasslibrary.reader.reader.domain.ReaderEngineOpenException
import com.secondpasslibrary.reader.reader.domain.ReaderEngineOpener
import com.secondpasslibrary.reader.reader.domain.ReaderViewport
import java.nio.file.Files
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderControllerTest {
    @Test
    fun `download then publication open reaches ready in order`() = runTest {
        val file = Files.createTempFile("reader", ".epub").toFile()
        val engine = FakeEngine()
        val resolver = ReaderBookAssetResolver { _, onDownloadStarted ->
            onDownloadStarted()
            ResolvedReaderBook("Academ's Fury", file, reused = false)
        }
        val controller = ReaderController(resolver, ReaderEngineOpener { engine }, this)
        val states = mutableListOf<ReaderState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            controller.state.collect(states::add)
        }

        controller.initialize(profile(), "profile-1", "book-1")
        advanceUntilIdle()

        assertEquals(
            listOf(
                ReaderState.Resolving::class,
                ReaderState.Downloading::class,
                ReaderState.Opening::class,
                ReaderState.Ready::class
            ),
            states.map { it::class }
        )
        assertEquals("Academ's Fury", (controller.state.value as ReaderState.Ready).title)
        controller.close()
        assertTrue(engine.closed)
    }

    @Test
    fun `download failure is classified without opening a publication`() = runTest {
        var opened = false
        val controller = ReaderController(
            ReaderBookAssetResolver { _, onDownloadStarted ->
                onDownloadStarted()
                throw SplClientException.ServerUnreachable()
            },
            ReaderEngineOpener {
                opened = true
                FakeEngine()
            },
            this
        )

        controller.initialize(profile(), "profile-1", "book-1")
        advanceUntilIdle()

        assertEquals(ReaderState.Failure(ReaderFailure.DOWNLOAD), controller.state.value)
        assertTrue(!opened)
    }

    @Test
    fun `publication and format failures remain distinct`() = runTest {
        val file = Files.createTempFile("reader", ".epub").toFile()
        val resolved = ReaderBookAssetResolver { _, _ ->
            ResolvedReaderBook("Book", file, reused = true)
        }
        val openFailure = ReaderController(
            resolved,
            ReaderEngineOpener { throw ReaderEngineOpenException("broken") },
            this
        )
        val noEpub = ReaderController(
            ReaderBookAssetResolver { _, _ -> throw ReaderEpubUnavailableException() },
            ReaderEngineOpener { FakeEngine() },
            this
        )

        openFailure.initialize(profile(), "profile-1", "book-1")
        noEpub.initialize(profile(), "profile-1", "book-2")
        advanceUntilIdle()

        assertEquals(ReaderState.Failure(ReaderFailure.OPEN), openFailure.state.value)
        assertEquals(ReaderState.Failure(ReaderFailure.NO_EPUB), noEpub.state.value)
    }

    @Test
    fun `reinitialization closes a late engine without replacing the current request`() = runTest {
        val file = Files.createTempFile("reader", ".epub").toFile()
        val firstEngine = FakeEngine()
        val secondEngine = FakeEngine()
        val resolver = ReaderBookAssetResolver { request, _ ->
            ResolvedReaderBook(request.bookId, file, reused = true)
        }
        lateinit var controller: ReaderController
        var openCount = 0
        controller = ReaderController(
            resolver,
            ReaderEngineOpener {
                if (openCount++ == 0) {
                    controller.initialize(profile(), "profile-1", "book-2")
                    firstEngine
                } else {
                    secondEngine
                }
            },
            this
        )

        controller.initialize(profile(), "profile-1", "book-1")
        advanceUntilIdle()

        assertTrue(firstEngine.closed)
        assertEquals("book-2", (controller.state.value as ReaderState.Ready).title)
        assertTrue(!secondEngine.closed)
        controller.close()
        assertTrue(secondEngine.closed)
    }

    @Test
    fun `authentication rejection is surfaced to connection ownership`() = runTest {
        val controller = ReaderController(
            ReaderBookAssetResolver { _, _ -> throw SplClientException.AuthenticationRejected() },
            ReaderEngineOpener { FakeEngine() },
            this
        )

        controller.initialize(profile(), "profile-1", "book-1")
        assertEquals(
            ReaderConnectionEvent.AuthenticationRejected,
            controller.connectionEvents.first()
        )
    }

    private class FakeEngine : ReaderEngine {
        var closed = false
        override val viewport = ReaderViewport { }

        override fun close() {
            closed = true
        }
    }

    private fun profile() = ConnectionProfile(
        serverOrigin = "https://library.example",
        serverBaseUrl = "https://library.example/",
        apiBaseUrl = "https://library.example/api/v1/",
        serverName = "Library",
        serverDescription = "",
        serverVersion = "1",
        serverReleaseDate = "2026-08-23",
        clientSessionId = "session-1",
        clientName = "Reader",
        clientType = "reader"
    )
}
