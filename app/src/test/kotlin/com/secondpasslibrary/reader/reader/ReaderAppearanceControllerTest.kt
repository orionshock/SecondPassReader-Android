package com.secondpasslibrary.reader.reader

import com.secondpasslibrary.reader.reader.appearance.ReaderAppearance
import com.secondpasslibrary.reader.reader.appearance.ReaderTheme
import com.secondpasslibrary.reader.reader.asset.ReaderBookAssetResolver
import com.secondpasslibrary.reader.reader.asset.ResolvedReaderBook
import com.secondpasslibrary.reader.reader.domain.ReaderEngine
import com.secondpasslibrary.reader.reader.domain.ReaderEngineOpener
import java.nio.file.Files
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class ReaderAppearanceControllerTest : ReaderControllerTestSupport() {
    @Test
    fun `persisted appearance initializes a newly opened engine`() = runTest {
        val file = Files.createTempFile("reader", ".epub").toFile()
        val saved = ReaderAppearance(
            theme = ReaderTheme.LIGHT,
            fontScale = 1.2,
            lineHeight = 1.6,
            publisherStylesEnabled = true
        )
        var openedWith: ReaderAppearance? = null
        val engine = FakeEngine()
        val opener = object : ReaderEngineOpener {
            override suspend fun open(file: java.io.File) = error("Initial appearance is required")

            override suspend fun open(
                file: java.io.File,
                initialAppearance: ReaderAppearance
            ): ReaderEngine {
                openedWith = initialAppearance
                engine.appearance.update(initialAppearance)
                return engine
            }
        }
        val controller = ReaderController(
            ReaderBookAssetResolver { _, _ -> ResolvedReaderBook("Book", file, reused = true) },
            opener,
            coordinator(),
            this,
            fakeLocalStore(),
            FakeAppearanceStore(saved)
        )

        controller.initialize(profile(), "profile-1", "book-1", null)
        advanceUntilIdle()

        assertEquals(saved, openedWith)
        assertEquals(
            saved,
            (controller.state.value as ReaderState.Ready).engine.appearance.appearance.value
        )
        controller.close()
    }

    @Test
    fun `live appearance applies before asynchronous persistence completes`() = runTest {
        val file = Files.createTempFile("reader", ".epub").toFile()
        val writeGate = CompletableDeferred<Unit>()
        val store = FakeAppearanceStore(ReaderAppearance(), writeGate)
        val engine = FakeEngine()
        val controller = ReaderController(
            ReaderBookAssetResolver { _, _ -> ResolvedReaderBook("Book", file, reused = true) },
            ReaderEngineOpener { engine },
            coordinator(),
            this,
            fakeLocalStore(),
            store
        )
        controller.initialize(profile(), "profile-1", "book-1", null)
        advanceUntilIdle()
        val updated = ReaderAppearance(theme = ReaderTheme.DARK, fontScale = 1.1)

        controller.updateAppearance(updated)
        runCurrent()

        assertEquals(updated, engine.appearance.appearance.value)
        assertEquals(null, store.written)
        writeGate.complete(Unit)
        advanceUntilIdle()
        assertEquals(updated, store.written)
        controller.close()
    }
}
