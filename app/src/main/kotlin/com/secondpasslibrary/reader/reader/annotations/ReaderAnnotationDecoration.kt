package com.secondpasslibrary.reader.reader.annotations

import com.secondpasslibrary.reader.reader.cfi.EpubCfi
import kotlinx.coroutines.flow.StateFlow

internal data class ReaderAnnotationDecoration(
    val annotationId: String,
    val cfi: EpubCfi,
    val kind: ReaderAnnotationKind,
    val color: ReaderAnnotationColor?
)

internal enum class ReaderAnnotationKind { BOOKMARK, HIGHLIGHT }

internal enum class ReaderAnnotationDecorationFailure {
    INVALID_LOCATION,
    UNSUPPORTED,
    UNAVAILABLE
}

internal interface ReaderAnnotationDecorations {
    val failures: StateFlow<Map<String, ReaderAnnotationDecorationFailure>>

    suspend fun replace(decorations: List<ReaderAnnotationDecoration>)

    suspend fun clear()
}

internal object EmptyReaderAnnotationDecorations : ReaderAnnotationDecorations {
    override val failures = kotlinx.coroutines.flow.MutableStateFlow(
        emptyMap<String, ReaderAnnotationDecorationFailure>()
    )

    override suspend fun replace(decorations: List<ReaderAnnotationDecoration>) = Unit

    override suspend fun clear() = Unit
}

internal fun ReaderAnnotation.toDecoration(): ReaderAnnotationDecoration? {
    val canonicalCfi = runCatching { EpubCfi(cfi) }.getOrNull() ?: return null
    return when (this) {
        is ReaderAnnotation.Bookmark -> ReaderAnnotationDecoration(
            annotationId = id,
            cfi = canonicalCfi,
            kind = ReaderAnnotationKind.BOOKMARK,
            color = null
        )

        is ReaderAnnotation.Highlight -> ReaderAnnotationDecoration(
            annotationId = id,
            cfi = canonicalCfi,
            kind = ReaderAnnotationKind.HIGHLIGHT,
            color = color
        )
    }
}
