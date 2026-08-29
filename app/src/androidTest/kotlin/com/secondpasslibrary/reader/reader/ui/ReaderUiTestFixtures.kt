package com.secondpasslibrary.reader.reader.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.secondpasslibrary.reader.reader.ReaderProgressRestore
import com.secondpasslibrary.reader.reader.ReaderState
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
import com.secondpasslibrary.reader.reader.domain.ReaderHudEvents
import com.secondpasslibrary.reader.reader.domain.ReaderReadingStatus
import com.secondpasslibrary.reader.reader.domain.ReaderViewport
import com.secondpasslibrary.reader.reader.domain.ReaderViewportMovements
import com.secondpasslibrary.reader.reader.session.ReaderSessionContext
import com.secondpasslibrary.reader.reader.session.ReaderSessionStatus
import com.secondpasslibrary.reader.reader.toc.ReaderPublicationNavigationResult
import com.secondpasslibrary.reader.reader.toc.ReaderPublicationResource
import com.secondpasslibrary.reader.reader.toc.ReaderPublicationTarget
import com.secondpasslibrary.reader.reader.toc.ReaderTableOfContents
import com.secondpasslibrary.reader.reader.toc.ReaderTocEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow

internal const val TEST_ANNOTATION_CFI = "epubcfi(/6/2!/4/2:3)"
internal const val TEST_PUBLICATION_CONTENT_TAG = "test_publication_content"
internal val TEST_CHAPTER_ONE = ReaderPublicationTarget("text/chapter-1.xhtml")
internal val TEST_CHAPTER_TWO = ReaderPublicationTarget("text/chapter-2.xhtml#section")
internal val TEST_RESOURCE_ONE = ReaderPublicationResource("text/chapter-1.xhtml")
internal val TEST_RESOURCE_TWO = ReaderPublicationResource("text/chapter-2.xhtml")

internal fun readerReadyState(
    toc: ReaderTableOfContents = RecordingReaderToc(),
    appearance: ReaderAppearanceController = RecordingReaderAppearance(),
    navigator: EpubCfiNavigator = UnusedReaderCfiNavigator,
    status: ReaderSessionStatus = ReaderSessionStatus.ACTIVE,
    hudEvents: ReaderHudEvents = RecordingReaderHudEvents()
) = ReaderState.Ready(
    title = "A deliberately long Reader title that remains one line",
    engine = FakeReaderEngine(toc, appearance, navigator, hudEvents),
    session = ReaderSessionContext("session-1", status, null),
    restore = ReaderProgressRestore.NOT_NEEDED
)

internal class RecordingReaderToc(
    override val entries: List<ReaderTocEntry> = listOf(
        ReaderTocEntry(
            title = "Part One",
            target = TEST_CHAPTER_ONE,
            children = listOf(
                ReaderTocEntry(
                    "Chapter Two",
                    TEST_CHAPTER_TWO,
                    resource = TEST_RESOURCE_TWO
                )
            ),
            resource = TEST_RESOURCE_ONE
        )
    ),
    resource: ReaderPublicationResource? = TEST_RESOURCE_ONE
) : ReaderTableOfContents {
    override val currentResource = MutableStateFlow(resource)
    val destinations = mutableListOf<ReaderPublicationTarget>()

    override suspend fun goTo(target: ReaderPublicationTarget): ReaderPublicationNavigationResult {
        destinations += target
        return ReaderPublicationNavigationResult.UNAVAILABLE
    }
}

internal class RecordingReaderAppearance : ReaderAppearanceController {
    private val mutableAppearance = MutableStateFlow(ReaderAppearance())
    override val appearance = mutableAppearance

    override suspend fun update(appearance: ReaderAppearance) {
        mutableAppearance.value = appearance
    }

    fun record(appearance: ReaderAppearance) {
        mutableAppearance.value = appearance
    }
}

internal class RecordingReaderCfiNavigator : EpubCfiNavigator {
    override val readiness = MutableStateFlow<EpubCfiReadiness>(EpubCfiReadiness.Available)
    val destinations = mutableListOf<EpubCfi>()

    override suspend fun goTo(cfi: EpubCfi): EpubCfiOutcome<Unit> {
        destinations += cfi
        return EpubCfiOutcome.Success(Unit)
    }

    override suspend fun currentPosition() = unavailable<EpubCfi>()
    override suspend fun currentSelection() = unavailable<EpubCfiSelection?>()
    override suspend fun resolve(cfi: EpubCfi) = unavailable<EpubCfiResolution>()
}

internal class RecordingReaderHudEvents(status: ReaderReadingStatus? = null) : ReaderHudEvents {
    override val readingStatus = MutableStateFlow(status)
    private val taps = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override fun publicationTaps(): Flow<Unit> = taps

    fun tap() {
        taps.tryEmit(Unit)
    }
}

private class FakeReaderEngine(
    override val tableOfContents: ReaderTableOfContents,
    override val appearance: ReaderAppearanceController,
    override val cfiNavigator: EpubCfiNavigator,
    override val hudEvents: ReaderHudEvents
) : ReaderEngine {
    override val viewport = ReaderViewport { modifier ->
        Box(modifier) {
            Box(Modifier.fillMaxSize().testTag(TEST_PUBLICATION_CONTENT_TAG))
        }
    }
    override val viewportMovements = ReaderViewportMovements { emptyFlow() }
    override fun close() = Unit
}

private data object UnusedReaderCfiNavigator : EpubCfiNavigator {
    override val readiness = MutableStateFlow<EpubCfiReadiness>(EpubCfiReadiness.Available)
    override suspend fun goTo(cfi: EpubCfi) = unavailable<Unit>()
    override suspend fun currentPosition() = unavailable<EpubCfi>()
    override suspend fun currentSelection() = unavailable<EpubCfiSelection?>()
    override suspend fun resolve(cfi: EpubCfi) = unavailable<EpubCfiResolution>()
}

private fun <T> unavailable(): EpubCfiOutcome<T> =
    EpubCfiOutcome.Failure(EpubCfiFailure.NAVIGATOR_UNAVAILABLE)
