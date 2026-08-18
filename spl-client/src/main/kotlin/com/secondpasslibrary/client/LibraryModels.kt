package com.secondpasslibrary.client

data class BookListOptions(
    val q: String? = null,
    val authorId: String? = null,
    val seriesId: String? = null,
    val tagSlug: String? = null,
    val ordering: BookOrdering? = null,
    val page: Int = DEFAULT_LIBRARY_PAGE,
    val pageSize: Int = DEFAULT_LIBRARY_PAGE_SIZE
) {
    init {
        validateLibraryPage(page, pageSize)
        require(authorId == null || authorId.isNotBlank()) { "Author ID must not be blank." }
        require(seriesId == null || seriesId.isNotBlank()) { "Series ID must not be blank." }
        require(tagSlug == null || tagSlug.isNotBlank()) { "Tag slug must not be blank." }
    }
}

data class LibrarySearchOptions(
    val q: String = "",
    val ordering: LibrarySearchOrdering? = null,
    val page: Int = DEFAULT_LIBRARY_PAGE,
    val pageSize: Int = DEFAULT_LIBRARY_PAGE_SIZE
) {
    init {
        validateLibraryPage(page, pageSize)
    }
}

enum class BookOrdering(internal val queryValue: String) {
    TITLE("title"),
    TITLE_DESCENDING("-title"),
    AUTHOR("author"),
    AUTHOR_DESCENDING("-author"),
    SERIES("series"),
    SERIES_DESCENDING("-series"),
    SERIES_INDEX("series_index"),
    SERIES_INDEX_DESCENDING("-series_index"),
    PUBLISHER("publisher"),
    PUBLISHER_DESCENDING("-publisher")
}

enum class LibrarySearchOrdering(internal val queryValue: String) {
    TITLE("title"),
    TITLE_DESCENDING("-title"),
    AUTHOR("author"),
    AUTHOR_DESCENDING("-author"),
    SERIES("series"),
    SERIES_DESCENDING("-series")
}

data class LibraryPage<T>(
    val totalCount: Int,
    val results: List<T>,
    val hasNext: Boolean,
    val hasPrevious: Boolean,
    val page: Int,
    val pageSize: Int
)

data class CompactBook(
    val id: String,
    val title: String,
    val sortTitle: String,
    val subtitle: String,
    val authors: List<BookAuthorSummary>,
    val series: BookSeriesSummary?,
    val catalogTags: List<CatalogTagSummary>,
    val language: String?,
    val publisher: String?,
    val publishedYear: Int?,
    val publishedMonth: Int?,
    val publishedDay: Int?,
    val publicationDatePrecision: PublicationDatePrecision,
    val cover: PublicBookCoverReference?,
    val fileFormat: String
)

data class BookAuthorSummary(val id: String, val name: String)

data class BookSeriesSummary(
    val id: String,
    val name: String,
    val sortName: String,
    val seriesIndex: SeriesIndex?
)

@JvmInline
value class SeriesIndex private constructor(val value: String) {
    companion object {
        fun fromExactValue(value: String): SeriesIndex {
            require(SERIES_INDEX_PATTERN.matches(value)) {
                "Series index must use an exact two-decimal representation."
            }
            return SeriesIndex(value)
        }

        internal fun fromServer(value: String): SeriesIndex =
            runCatching { fromExactValue(value) }.getOrElse { invalidProtocol("compact book") }
    }
}

data class CatalogTagSummary(val id: String, val name: String, val slug: String)

enum class PublicationDatePrecision {
    UNSPECIFIED,
    YEAR,
    MONTH,
    DAY
}

internal const val DEFAULT_LIBRARY_PAGE = 1
internal const val DEFAULT_LIBRARY_PAGE_SIZE = 20
private const val MAX_LIBRARY_PAGE_SIZE = 200
private val SERIES_INDEX_PATTERN = Regex("^-?\\d+\\.\\d{2}$")

private fun validateLibraryPage(page: Int, pageSize: Int) {
    require(page > 0) { "Library page must be positive." }
    require(pageSize in 1..MAX_LIBRARY_PAGE_SIZE) {
        "Library page size must be between 1 and $MAX_LIBRARY_PAGE_SIZE."
    }
}
