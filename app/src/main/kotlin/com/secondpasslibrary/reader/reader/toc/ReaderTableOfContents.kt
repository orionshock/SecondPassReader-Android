package com.secondpasslibrary.reader.reader.toc

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@JvmInline
internal value class ReaderPublicationTarget(val reference: String) {
    init {
        require(reference.isNotBlank()) { "Publication target must not be blank." }
        require(reference.length <= MAX_PUBLICATION_TARGET_LENGTH) {
            "Publication target is too long."
        }
    }
}

@JvmInline
internal value class ReaderPublicationResource(val reference: String) {
    init {
        require(reference.isNotBlank()) { "Publication resource must not be blank." }
    }
}

internal data class ReaderTocEntry(
    val title: String,
    val target: ReaderPublicationTarget?,
    val children: List<ReaderTocEntry> = emptyList(),
    val resource: ReaderPublicationResource? = null
)

internal enum class ReaderPublicationNavigationResult {
    NAVIGATED,
    UNAVAILABLE,
    REJECTED
}

internal interface ReaderTableOfContents {
    val entries: List<ReaderTocEntry>
    val currentResource: StateFlow<ReaderPublicationResource?>
        get() = EMPTY_CURRENT_RESOURCE

    suspend fun goTo(target: ReaderPublicationTarget): ReaderPublicationNavigationResult
}

internal data object EmptyReaderTableOfContents : ReaderTableOfContents {
    override val entries = emptyList<ReaderTocEntry>()

    override suspend fun goTo(target: ReaderPublicationTarget) =
        ReaderPublicationNavigationResult.REJECTED
}

private val EMPTY_CURRENT_RESOURCE = MutableStateFlow<ReaderPublicationResource?>(null)
private const val MAX_PUBLICATION_TARGET_LENGTH = 8 * 1024
