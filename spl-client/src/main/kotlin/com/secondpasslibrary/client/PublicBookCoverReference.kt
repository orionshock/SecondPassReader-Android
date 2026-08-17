package com.secondpasslibrary.client

@JvmInline
value class PublicBookCoverReference private constructor(val url: String) {
    companion object {
        /** Rehydrates a persisted public cover reference after validating its absolute URL. */
        fun fromAbsoluteUrl(url: String): PublicBookCoverReference = PublicBookCoverReference(
            requireNotNull(absoluteHttpUrlOrNull(url)) {
                "Book cover reference must be an absolute HTTP(S) URL."
            }
        )

        internal fun fromServer(url: String): PublicBookCoverReference =
            absoluteHttpUrlOrNull(url)?.let(::PublicBookCoverReference)
                ?: invalidProtocol("book cover")
    }
}
