package com.secondpasslibrary.client

@JvmInline
value class PublicBookCoverReference private constructor(val url: String) {
    companion object {
        internal fun fromServer(url: String): PublicBookCoverReference =
            PublicBookCoverReference(requireAbsoluteHttpUrl(url, "book cover"))
    }
}
