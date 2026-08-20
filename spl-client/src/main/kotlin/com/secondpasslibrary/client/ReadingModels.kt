package com.secondpasslibrary.client

data class RecentReadingOptions(val limit: Int, val includeClosed: Boolean) {
    init {
        require(limit in MIN_LIMIT..MAX_LIMIT) {
            "Recent-reading limit must be between $MIN_LIMIT and $MAX_LIMIT."
        }
    }

    private companion object {
        const val MIN_LIMIT = 1
        const val MAX_LIMIT = 50
    }
}

enum class ReadingSessionStatus {
    ACTIVE,
    CLOSED
}

data class RecentReadingItem(
    val sessionId: String,
    val sessionName: String,
    val status: ReadingSessionStatus,
    val lastActivityAt: String,
    val book: RecentReadingBook,
    val progress: ReadingProgress?
)

data class ReadingSessionBook(
    val id: String,
    val title: String,
    val cover: PublicBookCoverReference?,
    val canOpen: Boolean
)

typealias RecentReadingBook = ReadingSessionBook

data class ReadingProgress(val cfi: String, val locationLabel: String?, val updatedAt: String)
