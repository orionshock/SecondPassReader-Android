package com.secondpasslibrary.reader.reader.domain

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.secondpasslibrary.reader.reader.annotations.decoration.EmptyReaderAnnotationDecorations
import com.secondpasslibrary.reader.reader.annotations.decoration.ReaderAnnotationDecorations
import com.secondpasslibrary.reader.reader.annotations.selection.EmptyReaderSelectionEvents
import com.secondpasslibrary.reader.reader.annotations.selection.ReaderSelectionEvents
import com.secondpasslibrary.reader.reader.appearance.ReaderAppearance
import com.secondpasslibrary.reader.reader.appearance.ReaderAppearanceController
import com.secondpasslibrary.reader.reader.cfi.EpubCfiNavigator
import com.secondpasslibrary.reader.reader.lifecycle.EmptyReaderPositionRetention
import com.secondpasslibrary.reader.reader.lifecycle.ReaderPositionRetention
import com.secondpasslibrary.reader.reader.toc.ReaderTableOfContents
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

internal class ReaderEngineOpenException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

/** An open renderer session for one immutable EPUB asset. */
internal interface ReaderEngine : AutoCloseable {
    val viewport: ReaderViewport
    val cfiNavigator: EpubCfiNavigator
    val viewportMovements: ReaderViewportMovements
    val tableOfContents: ReaderTableOfContents
    val appearance: ReaderAppearanceController
    val annotationDecorations: ReaderAnnotationDecorations
        get() = EmptyReaderAnnotationDecorations
    val selectionEvents: ReaderSelectionEvents
        get() = EmptyReaderSelectionEvents
    val positionRetention: ReaderPositionRetention
        get() = EmptyReaderPositionRetention
    val hudEvents: ReaderHudEvents
        get() = EmptyReaderHudEvents
}

/** Renderer-neutral notification that the visible reading position has settled after movement. */
internal data class ReaderViewportMovement(val sequence: Long)

internal fun interface ReaderViewportMovements {
    fun settled(): Flow<ReaderViewportMovement>
}

internal enum class ReaderReadingStatusScope { SECTION }

/** Exact rendered-page status for the currently active publication scope. */
internal data class ReaderReadingStatus(
    val pagesRemaining: Int,
    val scope: ReaderReadingStatusScope
)

/** Renderer-neutral Reader HUD input and pagination events. */
internal interface ReaderHudEvents {
    val readingStatus: StateFlow<ReaderReadingStatus?>

    fun publicationTaps(): Flow<Unit>
}

private object EmptyReaderHudEvents : ReaderHudEvents {
    override val readingStatus = MutableStateFlow<ReaderReadingStatus?>(null)

    override fun publicationTaps(): Flow<Unit> = emptyFlow()
}

/**
 * App-owned viewport boundary. Implementations attach their platform view lifecycle only while
 * this content is composed; engine-specific fragments and views never enter Reader state.
 */
internal fun interface ReaderViewport {
    @Composable
    fun Content(modifier: Modifier)
}

internal fun interface ReaderEngineOpener {
    suspend fun open(file: File): ReaderEngine

    suspend fun open(file: File, initialAppearance: ReaderAppearance): ReaderEngine = open(file)
}
