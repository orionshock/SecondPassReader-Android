package com.secondpasslibrary.reader.reader.annotations.decoration

import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotation
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotationColor
import com.secondpasslibrary.reader.reader.cfi.EpubCfi
import kotlinx.coroutines.flow.StateFlow

internal data class ReaderAnnotationDecoration(
    val annotationId: String,
    val cfi: EpubCfi,
    val kind: ReaderAnnotationKind,
    val color: ReaderAnnotationColor?
)

internal sealed interface ReaderAnnotationDecorationGroupId {
    data object Current : ReaderAnnotationDecorationGroupId

    data class Previous(val sessionId: String) : ReaderAnnotationDecorationGroupId {
        init {
            require(sessionId.isNotBlank()) { "Decoration group Session ID must not be blank." }
        }
    }
}

internal enum class ReaderAnnotationKind { BOOKMARK, HIGHLIGHT }

internal enum class ReaderAnnotationDecorationFailure {
    INVALID_LOCATION,
    UNSUPPORTED,
    UNAVAILABLE
}

internal interface ReaderAnnotationDecorations {
    val failures: StateFlow<Map<String, ReaderAnnotationDecorationFailure>>

    suspend fun replace(
        groupId: ReaderAnnotationDecorationGroupId,
        decorations: List<ReaderAnnotationDecoration>
    )

    suspend fun clear(groupId: ReaderAnnotationDecorationGroupId)
}

internal object EmptyReaderAnnotationDecorations : ReaderAnnotationDecorations {
    override val failures = kotlinx.coroutines.flow.MutableStateFlow(
        emptyMap<String, ReaderAnnotationDecorationFailure>()
    )

    override suspend fun replace(
        groupId: ReaderAnnotationDecorationGroupId,
        decorations: List<ReaderAnnotationDecoration>
    ) = Unit

    override suspend fun clear(groupId: ReaderAnnotationDecorationGroupId) = Unit
}

internal fun ReaderAnnotation.toDecoration(): ReaderAnnotationDecoration? {
    val canonicalCfi = runCatching { EpubCfi(cfi) }.getOrNull() ?: return null
    return when (this) {
        is ReaderAnnotation.Bookmark -> null

        is ReaderAnnotation.Highlight -> ReaderAnnotationDecoration(
            annotationId = id,
            cfi = canonicalCfi,
            kind = ReaderAnnotationKind.HIGHLIGHT,
            color = color
        )
    }
}
