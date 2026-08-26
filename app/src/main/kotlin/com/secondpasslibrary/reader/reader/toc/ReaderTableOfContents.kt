package com.secondpasslibrary.reader.reader.toc

@JvmInline
internal value class ReaderPublicationTarget(val reference: String) {
    init {
        require(reference.isNotBlank()) { "Publication target must not be blank." }
        require(reference.length <= MAX_PUBLICATION_TARGET_LENGTH) {
            "Publication target is too long."
        }
    }
}

internal data class ReaderTocEntry(
    val title: String,
    val target: ReaderPublicationTarget?,
    val children: List<ReaderTocEntry> = emptyList()
)

internal enum class ReaderPublicationNavigationResult {
    NAVIGATED,
    UNAVAILABLE,
    REJECTED
}

internal interface ReaderTableOfContents {
    val entries: List<ReaderTocEntry>

    suspend fun goTo(target: ReaderPublicationTarget): ReaderPublicationNavigationResult
}

internal data object EmptyReaderTableOfContents : ReaderTableOfContents {
    override val entries = emptyList<ReaderTocEntry>()

    override suspend fun goTo(target: ReaderPublicationTarget) =
        ReaderPublicationNavigationResult.REJECTED
}

private const val MAX_PUBLICATION_TARGET_LENGTH = 8 * 1024
