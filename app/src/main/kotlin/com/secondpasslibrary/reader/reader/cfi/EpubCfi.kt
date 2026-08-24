package com.secondpasslibrary.reader.reader.cfi

private const val MAX_EPUB_CFI_LENGTH = 8 * 1024

/** Renderer-neutral, deliberately opaque EPUB CFI retained exactly as supplied. */
@JvmInline
internal value class EpubCfi(val value: String) {
    init {
        require(value.isNotBlank()) { "EPUB CFI must not be blank." }
        require(value.length <= MAX_EPUB_CFI_LENGTH) {
            "EPUB CFI must be at most $MAX_EPUB_CFI_LENGTH characters."
        }
    }

    override fun toString(): String = value
}
