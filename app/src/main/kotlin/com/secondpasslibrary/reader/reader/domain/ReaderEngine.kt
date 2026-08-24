package com.secondpasslibrary.reader.reader.domain

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.secondpasslibrary.reader.reader.cfi.EpubCfiNavigator
import java.io.File
import kotlinx.coroutines.flow.Flow

internal class ReaderEngineOpenException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

/** An open renderer session for one immutable EPUB asset. */
internal interface ReaderEngine : AutoCloseable {
    val viewport: ReaderViewport
    val cfiNavigator: EpubCfiNavigator
    val viewportMovements: ReaderViewportMovements
}

/** Renderer-neutral notification that the visible reading position has settled after movement. */
internal data class ReaderViewportMovement(val sequence: Long)

internal fun interface ReaderViewportMovements {
    fun settled(): Flow<ReaderViewportMovement>
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
}
