package com.secondpasslibrary.reader.reader.navigation

import androidx.compose.ui.Modifier
import com.secondpasslibrary.reader.reader.ReaderProgressRestore
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
import com.secondpasslibrary.reader.reader.toc.ReaderPublicationNavigationResult
import com.secondpasslibrary.reader.reader.toc.ReaderPublicationTarget
import com.secondpasslibrary.reader.reader.toc.ReaderTableOfContents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderNavigationControllerTest {
    @Test
    fun `annotation and bookmark intents route their exact stored CFI`() = runTest {
        val navigator = RecordingNavigator()
        val controller = controller(this, navigator = navigator)
        val highlight = highlight()
        val bookmark = bookmark()

        controller.accept(ReaderNavigationIntent.GoToAnnotation(highlight))
        runCurrent()
        assertEquals(ReaderNavigationResult.NAVIGATED, controller.events.first().result)
        controller.accept(ReaderNavigationIntent.GoToBookmark(bookmark))
        runCurrent()
        assertEquals(ReaderNavigationResult.NAVIGATED, controller.events.first().result)

        assertEquals(listOf(EpubCfi(CFI), EpubCfi(CFI)), navigator.destinations)
    }

    @Test
    fun `publication intent retains direct publication target semantics`() = runTest {
        val toc = RecordingTableOfContents(ReaderPublicationNavigationResult.NAVIGATED)
        val controller = controller(this, toc = toc)
        val target = ReaderPublicationTarget("text/chapter-2.xhtml#section")

        controller.accept(ReaderNavigationIntent.GoToPublicationTarget(target))
        runCurrent()

        assertEquals(listOf(target), toc.destinations)
        assertEquals(ReaderNavigationResult.NAVIGATED, controller.events.first().result)
    }

    @Test
    fun `navigation failures are exposed without renderer types`() = runTest {
        val controller = controller(
            this,
            navigator = RecordingNavigator(
                EpubCfiOutcome.Failure(EpubCfiFailure.NAVIGATION_FAILED)
            ),
            toc = RecordingTableOfContents(ReaderPublicationNavigationResult.REJECTED)
        )

        controller.accept(ReaderNavigationIntent.GoToAnnotation(highlight(cfi = "")))
        runCurrent()
        assertEquals(ReaderNavigationResult.INVALID_TARGET, controller.events.first().result)
        controller.accept(ReaderNavigationIntent.GoToBookmark(bookmark()))
        runCurrent()
        assertEquals(ReaderNavigationResult.UNAVAILABLE, controller.events.first().result)
        controller.accept(
            ReaderNavigationIntent.GoToPublicationTarget(ReaderPublicationTarget("chapter.xhtml"))
        )
        runCurrent()
        assertEquals(ReaderNavigationResult.REJECTED, controller.events.first().result)
    }

    @Test
    fun `intent without a ready Reader reports unavailable`() = runTest {
        val state = MutableStateFlow<ReaderState>(ReaderState.Opening)
        val controller = ReaderNavigationController(state, this)
        val intent = ReaderNavigationIntent.GoToBookmark(bookmark())

        controller.accept(intent)

        assertEquals(
            ReaderNavigationEvent(intent, ReaderNavigationResult.UNAVAILABLE),
            controller.events.first()
        )
    }

    private fun controller(
        scope: CoroutineScope,
        navigator: EpubCfiNavigator = RecordingNavigator(),
        toc: ReaderTableOfContents = RecordingTableOfContents()
    ): ReaderNavigationController {
        val state = MutableStateFlow<ReaderState>(
            ReaderState.Ready(
                title = "Book",
                engine = FakeEngine(navigator, toc),
                session = null,
                restore = ReaderProgressRestore.NOT_NEEDED
            )
        )
        return ReaderNavigationController(state, scope)
    }

    private class RecordingNavigator(
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
        override val tableOfContents: ReaderTableOfContents
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

    companion object {
        private const val CFI = "epubcfi(/6/2!/4/2:3)"

        private fun highlight(cfi: String = CFI) = ReaderAnnotation.Highlight(
            id = "highlight",
            clientId = "highlight-client",
            cfi = cfi,
            locationLabel = "Chapter 1",
            updatedAt = "2026-08-30T00:00:00Z",
            quote = "quote",
            prefix = null,
            suffix = null,
            note = null,
            color = com.secondpasslibrary.reader.reader.annotations.ReaderAnnotationColor.YELLOW
        )

        private fun bookmark() = ReaderAnnotation.Bookmark(
            id = "bookmark",
            clientId = "bookmark-client",
            cfi = CFI,
            locationLabel = "Chapter 1",
            updatedAt = "2026-08-30T00:00:00Z"
        )

        private fun <T> unavailable(): EpubCfiOutcome<T> =
            EpubCfiOutcome.Failure(EpubCfiFailure.NAVIGATOR_UNAVAILABLE)
    }
}
