package com.secondpasslibrary.reader.reader.navigation

import androidx.compose.ui.Modifier
import com.secondpasslibrary.reader.reader.ReaderProgressRestore
import com.secondpasslibrary.reader.reader.ReaderSessionAuthority
import com.secondpasslibrary.reader.reader.ReaderState
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotation
import com.secondpasslibrary.reader.reader.appearance.ReaderAppearance
import com.secondpasslibrary.reader.reader.appearance.ReaderAppearanceController
import com.secondpasslibrary.reader.reader.cfi.EpubCfi
import com.secondpasslibrary.reader.reader.cfi.EpubCfiFailure
import com.secondpasslibrary.reader.reader.cfi.EpubCfiNavigator
import com.secondpasslibrary.reader.reader.cfi.EpubCfiOutcome
import com.secondpasslibrary.reader.reader.cfi.EpubCfiReadiness
import com.secondpasslibrary.reader.reader.cfi.EpubCfiResolution
import com.secondpasslibrary.reader.reader.cfi.EpubCfiSelection
import com.secondpasslibrary.reader.reader.domain.ReaderEngine
import com.secondpasslibrary.reader.reader.domain.ReaderViewport
import com.secondpasslibrary.reader.reader.domain.ReaderViewportMovements
import com.secondpasslibrary.reader.reader.session.ReaderSessionContext
import com.secondpasslibrary.reader.reader.session.ReaderSessionStatus
import com.secondpasslibrary.reader.reader.toc.ReaderPublicationNavigationResult
import com.secondpasslibrary.reader.reader.toc.ReaderPublicationTarget
import com.secondpasslibrary.reader.reader.toc.ReaderTableOfContents
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderNavigationControllerTest {
    @Test
    fun `successful current-engine navigation reaches exact targets without failure`() = runTest {
        val navigator = RecordingNavigator()
        val toc = RecordingTableOfContents()
        val fixture = fixture(this, navigator, toc)
        val failures = fixture.collectFailures(backgroundScope)
        val target = ReaderPublicationTarget("text/chapter-2.xhtml#section")

        fixture.controller.accept(ReaderNavigationIntent.GoToAnnotation(highlight()))
        advanceUntilIdle()
        fixture.controller.accept(ReaderNavigationIntent.GoToBookmark(bookmark()))
        advanceUntilIdle()
        fixture.controller.accept(ReaderNavigationIntent.GoToPublicationTarget(target))
        advanceUntilIdle()

        assertEquals(listOf(EpubCfi(CFI), EpubCfi(CFI)), navigator.destinations)
        assertEquals(listOf(target), toc.destinations)
        assertEquals(0, failures.size)
    }

    @Test
    fun `current-engine failure keeps Reader ready and publishes one notice`() = runTest {
        val navigator = RecordingNavigator(
            EpubCfiOutcome.Failure(EpubCfiFailure.NAVIGATION_FAILED)
        )
        val fixture = fixture(this, navigator)
        val ready = fixture.state.value
        val failures = fixture.collectFailures(backgroundScope)

        fixture.controller.accept(ReaderNavigationIntent.GoToBookmark(bookmark()))
        advanceUntilIdle()

        assertSame(ready, fixture.state.value)
        assertEquals(1, failures.size)
    }

    @Test
    fun `replaced engine cancels stale failure without notifying new Reader`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val navigator = DeferredNavigator().apply { this.gate = gate }
        val fixture = fixture(this, navigator)
        val failures = fixture.collectFailures(backgroundScope)

        fixture.controller.accept(ReaderNavigationIntent.GoToAnnotation(highlight()))
        runCurrent()
        fixture.state.value = ready(FakeEngine(RecordingNavigator()))
        runCurrent()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(0, failures.size)
    }

    @Test
    fun `navigation cancellation is silent`() = runTest {
        val fixture = fixture(this, CancellingNavigator)
        val failures = fixture.collectFailures(backgroundScope)

        fixture.controller.accept(ReaderNavigationIntent.GoToBookmark(bookmark()))
        advanceUntilIdle()

        assertEquals(0, failures.size)
    }

    @Test
    fun `new navigation cancels an older completion on the same engine`() = runTest {
        val firstGate = CompletableDeferred<Unit>()
        val navigator = ReplacingNavigator().apply { this.firstGate = firstGate }
        val fixture = fixture(this, navigator)
        val failures = fixture.collectFailures(backgroundScope)

        fixture.controller.accept(ReaderNavigationIntent.GoToAnnotation(highlight()))
        runCurrent()
        fixture.controller.accept(ReaderNavigationIntent.GoToBookmark(bookmark()))
        runCurrent()
        firstGate.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf(EpubCfi(CFI), EpubCfi(CFI)), navigator.destinations)
        assertEquals(0, failures.size)
    }

    private fun fixture(
        scope: CoroutineScope,
        navigator: EpubCfiNavigator = RecordingNavigator(),
        toc: ReaderTableOfContents = RecordingTableOfContents()
    ): Fixture {
        val state = MutableStateFlow<ReaderState>(ready(FakeEngine(navigator, toc)))
        return Fixture(state, ReaderNavigationController(state, scope))
    }

    private fun ready(engine: ReaderEngine) = ReaderState.Ready(
        title = "Book",
        engine = engine,
        session = ReaderSessionContext("session-1", ReaderSessionStatus.ACTIVE, null),
        restore = ReaderProgressRestore.NOT_NEEDED,
        authority = ReaderSessionAuthority.SERVER
    )

    private data class Fixture(
        val state: MutableStateFlow<ReaderState>,
        val controller: ReaderNavigationController
    ) {
        fun collectFailures(scope: CoroutineScope): MutableList<Unit> {
            val failures = mutableListOf<Unit>()
            scope.launch(UnconfinedTestDispatcher()) {
                controller.failures.collect(failures::add)
            }
            return failures
        }
    }

    private open class RecordingNavigator(
        private val result: EpubCfiOutcome<Unit> = EpubCfiOutcome.Success(Unit)
    ) : EpubCfiNavigator {
        override val readiness = MutableStateFlow<EpubCfiReadiness>(EpubCfiReadiness.Available)
        val destinations = mutableListOf<EpubCfi>()

        override suspend fun goTo(cfi: EpubCfi): EpubCfiOutcome<Unit> {
            destinations += cfi
            return result
        }

        override suspend fun currentSelection() = unavailable<EpubCfiSelection?>()

        override suspend fun resolve(cfi: EpubCfi) = unavailable<EpubCfiResolution>()
    }

    private class DeferredNavigator : RecordingNavigator() {
        lateinit var gate: CompletableDeferred<Unit>

        override suspend fun goTo(cfi: EpubCfi): EpubCfiOutcome<Unit> {
            destinations += cfi
            withContext(NonCancellable) { gate.await() }
            return EpubCfiOutcome.Failure(EpubCfiFailure.NAVIGATION_FAILED)
        }
    }

    private class ReplacingNavigator : RecordingNavigator() {
        lateinit var firstGate: CompletableDeferred<Unit>
        private var calls = 0

        override suspend fun goTo(cfi: EpubCfi): EpubCfiOutcome<Unit> {
            destinations += cfi
            calls += 1
            return if (calls == 1) {
                withContext(NonCancellable) { firstGate.await() }
                EpubCfiOutcome.Failure(EpubCfiFailure.NAVIGATION_FAILED)
            } else {
                EpubCfiOutcome.Success(Unit)
            }
        }
    }

    private data object CancellingNavigator : EpubCfiNavigator {
        override val readiness = MutableStateFlow<EpubCfiReadiness>(EpubCfiReadiness.Available)

        override suspend fun goTo(cfi: EpubCfi): EpubCfiOutcome<Unit> =
            throw CancellationException("cancelled")

        override suspend fun currentSelection() = unavailable<EpubCfiSelection?>()

        override suspend fun resolve(cfi: EpubCfi) = unavailable<EpubCfiResolution>()
    }

    private class RecordingTableOfContents(
        private val result: ReaderPublicationNavigationResult =
            ReaderPublicationNavigationResult.NAVIGATED
    ) : ReaderTableOfContents {
        override val entries = emptyList<com.secondpasslibrary.reader.reader.toc.ReaderTocEntry>()
        val destinations = mutableListOf<ReaderPublicationTarget>()

        override suspend fun goTo(
            target: ReaderPublicationTarget
        ): ReaderPublicationNavigationResult {
            destinations += target
            return result
        }
    }

    private class FakeEngine(
        override val cfiNavigator: EpubCfiNavigator,
        override val tableOfContents: ReaderTableOfContents = RecordingTableOfContents()
    ) : ReaderEngine {
        override val viewport = ReaderViewport { _: Modifier -> }
        override val viewportMovements = ReaderViewportMovements { emptyFlow() }
        override val appearance = object : ReaderAppearanceController {
            override val appearance = MutableStateFlow(ReaderAppearance())

            override suspend fun update(appearance: ReaderAppearance) {
                this.appearance.value = appearance
            }
        }

        override fun close() = Unit
    }

    private companion object {
        const val CFI = "epubcfi(/6/2!/4/2:3)"

        fun highlight() = ReaderAnnotation.Highlight(
            id = "highlight",
            clientId = "highlight-client",
            cfi = CFI,
            locationLabel = "Chapter 1",
            updatedAt = "2026-08-30T00:00:00Z",
            quote = "quote",
            prefix = null,
            suffix = null,
            note = null,
            color = com.secondpasslibrary.reader.reader.annotations.ReaderAnnotationColor.YELLOW
        )

        fun bookmark() = ReaderAnnotation.Bookmark(
            id = "bookmark",
            clientId = "bookmark-client",
            cfi = CFI,
            locationLabel = "Chapter 1",
            updatedAt = "2026-08-30T00:00:00Z"
        )

        fun <T> unavailable(): EpubCfiOutcome<T> =
            EpubCfiOutcome.Failure(EpubCfiFailure.NAVIGATOR_UNAVAILABLE)
    }
}
