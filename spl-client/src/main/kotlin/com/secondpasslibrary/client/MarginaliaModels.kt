package com.secondpasslibrary.client

data class MarginaliaBookListOptions(
    val page: Int = DEFAULT_MARGINALIA_PAGE,
    val pageSize: Int = DEFAULT_MARGINALIA_PAGE_SIZE,
    val q: String? = null
) {
    init {
        validateMarginaliaPage(page, pageSize)
    }
}

data class ReadingSessionListOptions(
    val status: ReadingSessionStatus? = null,
    val q: String? = null,
    val hasAnnotations: Boolean? = null,
    val page: Int = DEFAULT_MARGINALIA_PAGE,
    val pageSize: Int = DEFAULT_MARGINALIA_PAGE_SIZE
) {
    init {
        validateMarginaliaPage(page, pageSize)
    }
}

data class BookReadingSessionListOptions(
    val status: ReadingSessionStatus? = null,
    val q: String? = null,
    val page: Int = DEFAULT_MARGINALIA_PAGE,
    val pageSize: Int = DEFAULT_MARGINALIA_PAGE_SIZE
) {
    init {
        validateMarginaliaPage(page, pageSize)
    }
}

data class MarginaliaPage<T>(
    val totalCount: Int,
    val results: List<T>,
    val hasNext: Boolean,
    val hasPrevious: Boolean,
    val page: Int,
    val pageSize: Int
)

data class MarginaliaBookSummary(
    val id: String,
    val title: String,
    val authors: List<BookAuthorSummary>,
    val series: MarginaliaBookSeries?,
    val cover: PublicBookCoverReference?,
    val canOpen: Boolean,
    val sessionCount: Int,
    val activeSessionCount: Int,
    val lastActivityAt: String?
)

data class MarginaliaBookSeries(val id: String, val name: String, val seriesIndex: SeriesIndex?)

data class ReadingSessionSummary(
    val id: String,
    val name: String,
    val notes: String,
    val status: ReadingSessionStatus,
    val startedAt: String,
    val closedAt: String?,
    val updatedAt: String,
    val lastActivityAt: String,
    val annotationCount: Int
)

data class ReadingSessionDetail(val summary: ReadingSessionSummary, val progress: ReadingProgress?)

data class ReadingSessionListItem(val session: ReadingSessionSummary, val book: ReadingSessionBook)

data class ReadingSessionDetailResult(
    val book: ReadingSessionBook,
    val session: ReadingSessionDetail
)

data class BookReadingSessionHistory(
    val book: ReadingSessionBook,
    val sessions: MarginaliaPage<ReadingSessionSummary>
)

data class ReadingSessionMetadataInput(val name: String? = null, val notes: String? = null) {
    init {
        require(name == null || name.length <= MAX_READING_SESSION_NAME_LENGTH) {
            "Reading Session name must be at most $MAX_READING_SESSION_NAME_LENGTH characters."
        }
    }
}

data class ReadingProgressInput(val cfi: String, val locationLabel: String? = null) {
    init {
        require(cfi.isNotBlank()) { "Reading progress CFI must not be blank." }
        require(cfi.length <= MAX_CFI_LENGTH) {
            "Reading progress CFI must be at most $MAX_CFI_LENGTH characters."
        }
        require(locationLabel == null || locationLabel.length <= MAX_LOCATION_LABEL_LENGTH) {
            "Reading progress location label must be at most $MAX_LOCATION_LABEL_LENGTH characters."
        }
    }
}

data class ReadingSessionFinalization(
    val name: String? = null,
    val notes: String? = null,
    val progress: ReadingProgressInput? = null
) {
    init {
        require(name == null || name.length <= MAX_READING_SESSION_NAME_LENGTH) {
            "Reading Session name must be at most $MAX_READING_SESSION_NAME_LENGTH characters."
        }
    }
}

class MarginaliaIdempotencyKey private constructor(val value: String) {
    override fun equals(other: Any?): Boolean =
        other is MarginaliaIdempotencyKey && value == other.value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = "MarginaliaIdempotencyKey(redacted)"

    companion object {
        fun fromStableValue(value: String): MarginaliaIdempotencyKey {
            val normalized = value.trim()
            require(normalized.isNotEmpty()) { "Idempotency key must not be blank." }
            require(normalized.length <= MAX_IDEMPOTENCY_KEY_LENGTH) {
                "Idempotency key must be at most $MAX_IDEMPOTENCY_KEY_LENGTH characters."
            }
            require(normalized.none(Char::isISOControl)) {
                "Idempotency key must not contain control characters."
            }
            return MarginaliaIdempotencyKey(normalized)
        }
    }
}

data class ReadingSessionBootstrap(
    val created: Boolean,
    val book: ReadingSessionBook,
    val activeSession: ReadingSessionDetail?,
    val annotations: List<MarginaliaAnnotation>,
    val closedSessions: ClosedReadingSessionPage
)

data class ClosedReadingSessionPage(
    val totalCount: Int,
    val results: List<ReadingSessionSummary>,
    val hasNext: Boolean,
    val hasPrevious: Boolean
)

data class MarginaliaAnnotationLocation(val cfi: String, val locationLabel: String?)

sealed interface MarginaliaAnnotation {
    val id: String
    val clientId: String
    val location: MarginaliaAnnotationLocation
    val createdAt: String
    val updatedAt: String

    data class Bookmark(
        override val id: String,
        override val clientId: String,
        override val location: MarginaliaAnnotationLocation,
        override val createdAt: String,
        override val updatedAt: String
    ) : MarginaliaAnnotation

    data class Highlight(
        override val id: String,
        override val clientId: String,
        override val location: MarginaliaAnnotationLocation,
        override val createdAt: String,
        override val updatedAt: String,
        val body: MarginaliaHighlightBody
    ) : MarginaliaAnnotation
}

data class MarginaliaHighlightBody(
    val text: String,
    val prefix: String?,
    val suffix: String?,
    val color: MarginaliaHighlightColor,
    val note: String?
)

enum class MarginaliaHighlightColor {
    YELLOW,
    GREEN,
    BLUE,
    PINK,
    PURPLE,
    ORANGE
}

internal const val DEFAULT_MARGINALIA_PAGE = 1
internal const val DEFAULT_MARGINALIA_PAGE_SIZE = 20
private const val MAX_MARGINALIA_PAGE_SIZE = 200
private const val MAX_READING_SESSION_NAME_LENGTH = 255
private const val MAX_IDEMPOTENCY_KEY_LENGTH = 128
internal const val MAX_CFI_LENGTH = 8 * 1024
internal const val MAX_LOCATION_LABEL_LENGTH = 255

private fun validateMarginaliaPage(page: Int, pageSize: Int) {
    require(page > 0) { "Marginalia page must be positive." }
    require(pageSize in 1..MAX_MARGINALIA_PAGE_SIZE) {
        "Marginalia page size must be between 1 and $MAX_MARGINALIA_PAGE_SIZE."
    }
}
