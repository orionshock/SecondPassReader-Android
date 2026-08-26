package com.secondpasslibrary.reader.reader.annotations

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Owns the exact Session whose read-only annotations are installed in one Reader engine. */
internal class ReaderAnnotationDecorationController {
    private val mutex = Mutex()
    private var owner: DecorationOwner? = null

    suspend fun replace(
        sessionId: String,
        target: ReaderAnnotationDecorations,
        annotations: List<ReaderAnnotation>
    ) = mutex.withLock {
        val next = DecorationOwner(sessionId, target)
        if (owner != null && owner != next) owner?.target?.clear()
        owner = next
        target.replace(annotations.mapNotNull(ReaderAnnotation::toDecoration))
    }

    suspend fun clear() = mutex.withLock {
        owner?.target?.clear()
        owner = null
    }

    private data class DecorationOwner(
        val sessionId: String,
        val target: ReaderAnnotationDecorations
    )
}
