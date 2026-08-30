package com.secondpasslibrary.reader.reader.marginalia

import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotation
import com.secondpasslibrary.reader.reader.annotations.decoration.ReaderAnnotationDecoration
import com.secondpasslibrary.reader.reader.annotations.decoration.ReaderAnnotationDecorationGroupId
import com.secondpasslibrary.reader.reader.annotations.decoration.ReaderAnnotationDecorations
import com.secondpasslibrary.reader.reader.annotations.decoration.toDecoration
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Reconciles visible historical Session layers without touching current decorations. */
internal class ReaderMarginaliaLayerDecorationController {
    private val mutex = Mutex()
    private var owner: DecorationOwner? = null
    private var installed =
        emptyMap<ReaderAnnotationDecorationGroupId.Previous, List<ReaderAnnotationDecoration>>()

    suspend fun replace(
        readerSessionId: String,
        target: ReaderAnnotationDecorations,
        layers: List<ReaderPreviousMarginaliaLayer>
    ) = mutex.withLock {
        val nextOwner = DecorationOwner(readerSessionId, target)
        if (owner != null && owner != nextOwner) clearInstalled()
        owner = nextOwner
        val desired = layers
            .asSequence()
            .filter { it.loadState == ReaderMarginaliaLayerLoadState.LOADED }
            .filter { it.visibility == ReaderMarginaliaLayerVisibility.VISIBLE }
            .associate { layer ->
                ReaderAnnotationDecorationGroupId.Previous(layer.summary.sessionId) to
                    layer.annotations.mapNotNull { it.toDecoration(layer.summary.sessionId) }
            }
        (installed.keys - desired.keys).forEach { target.clear(it) }
        desired.forEach { (groupId, decorations) ->
            if (installed[groupId] != decorations) target.replace(groupId, decorations)
        }
        installed = desired
    }

    suspend fun clear() = mutex.withLock {
        clearInstalled()
        owner = null
    }

    private suspend fun clearInstalled() {
        val target = owner?.target
        if (target != null) installed.keys.forEach { target.clear(it) }
        installed = emptyMap()
    }

    private data class DecorationOwner(
        val readerSessionId: String,
        val target: ReaderAnnotationDecorations
    )
}
