package com.secondpasslibrary.reader.reader.domain

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import java.io.File

internal class ReaderEngineOpenException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

/** An open renderer session for one immutable EPUB asset. */
internal interface ReaderEngine : AutoCloseable {
    val viewport: ReaderViewport
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
