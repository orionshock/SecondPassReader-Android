package com.secondpasslibrary.client

sealed interface LibraryScope {
    data object Global : LibraryScope

    data class Group(val id: String) : LibraryScope {
        init {
            require(id.isNotBlank()) { "Library group ID must not be blank." }
        }
    }
}

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
    val tagSlug: String? = null,
    val ordering: LibrarySearchOrdering? = null,
    val page: Int = DEFAULT_LIBRARY_PAGE,
    val pageSize: Int = DEFAULT_LIBRARY_PAGE_SIZE
) {
    init {
        validateLibraryPage(page, pageSize)
        require(tagSlug == null || tagSlug.isNotBlank()) { "Tag slug must not be blank." }
    }
}

data class LibraryGroupListOptions(
    val ordering: LibraryGroupOrdering = LibraryGroupOrdering.NAME,
    val page: Int = DEFAULT_LIBRARY_PAGE,
    val pageSize: Int = DEFAULT_LIBRARY_PAGE_SIZE
) {
    init {
        validateLibraryPage(page, pageSize)
    }
}

data class AuthorListOptions(
    val q: String? = null,
    val tagSlug: String? = null,
    val ordering: AuthorOrdering = AuthorOrdering.NAME,
    val page: Int = DEFAULT_LIBRARY_PAGE,
    val pageSize: Int = DEFAULT_LIBRARY_PAGE_SIZE,
    val previewLimit: Int = 0
) {
    init {
        validateLibraryPage(page, pageSize)
        validatePreviewLimit(previewLimit)
        require(tagSlug == null || tagSlug.isNotBlank()) { "Tag slug must not be blank." }
    }
}

data class SeriesListOptions(
    val q: String? = null,
    val tagSlug: String? = null,
    val ordering: SeriesOrdering = SeriesOrdering.NAME,
    val page: Int = DEFAULT_LIBRARY_PAGE,
    val pageSize: Int = DEFAULT_LIBRARY_PAGE_SIZE,
    val previewLimit: Int = 0
) {
    init {
        validateLibraryPage(page, pageSize)
        validatePreviewLimit(previewLimit)
        require(tagSlug == null || tagSlug.isNotBlank()) { "Tag slug must not be blank." }
    }
}

data class CatalogTagListOptions(
    val q: String? = null,
    val ordering: CatalogTagOrdering = CatalogTagOrdering.NAME,
    val page: Int = DEFAULT_LIBRARY_PAGE,
    val pageSize: Int = DEFAULT_LIBRARY_PAGE_SIZE
) {
    init {
        validateLibraryPage(page, pageSize)
    }
}

data class LibraryEntityDetailOptions(val previewLimit: Int = 0) {
    init {
        validatePreviewLimit(previewLimit)
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

enum class LibraryGroupOrdering(internal val queryValue: String) {
    NAME("name"),
    NAME_DESCENDING("-name")
}

enum class AuthorOrdering(internal val queryValue: String) {
    NAME("name"),
    NAME_DESCENDING("-name"),
    BOOK_COUNT("book_count"),
    BOOK_COUNT_DESCENDING("-book_count")
}

enum class SeriesOrdering(internal val queryValue: String) {
    NAME("name"),
    NAME_DESCENDING("-name"),
    BOOK_COUNT("book_count"),
    BOOK_COUNT_DESCENDING("-book_count")
}

enum class CatalogTagOrdering(internal val queryValue: String) {
    NAME("name"),
    NAME_DESCENDING("-name"),
    BOOK_COUNT("book_count"),
    BOOK_COUNT_DESCENDING("-book_count")
}

data class LibraryGroupSummary(val id: String, val name: String, val isPublicGroup: Boolean)

data class LibraryCatalogTag(val id: String, val name: String, val slug: String, val bookCount: Int)

data class LibraryPreviewBook(
    val id: String,
    val title: String,
    val cover: PublicBookCoverReference?
)

data class LibraryAuthor(
    val id: String,
    val name: String,
    val sortName: String,
    val biography: String,
    val bookCount: Int,
    val previewBooks: List<LibraryPreviewBook>?
)

data class LibrarySeries(
    val id: String,
    val name: String,
    val sortName: String,
    val summary: String,
    val bookCount: Int,
    val previewBooks: List<LibraryPreviewBook>?
)

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

data class LibraryBookDetail(
    val id: String,
    val title: String,
    val sortTitle: String,
    val subtitle: String,
    val authors: List<BookAuthorSummary>,
    val series: BookSeriesSummary?,
    val language: String?,
    val publisher: String?,
    val publishedYear: Int?,
    val publishedMonth: Int?,
    val publishedDay: Int?,
    val publicationDatePrecision: PublicationDatePrecision,
    val cover: PublicBookCoverReference?,
    val description: String,
    val identifiers: List<BookIdentifier>,
    val catalogTags: List<CatalogTagSummary>,
    val file: BookFile?,
    val groups: List<BookGroup>
)

data class BookIdentifier(val id: String, val scheme: String, val value: String)

data class BookFile(
    val format: String,
    val fileSize: Long,
    val checksum: String?,
    val download: AuthenticatedBookDownloadReference
)

@JvmInline
value class AuthenticatedBookDownloadReference private constructor(val url: String) {
    companion object {
        internal fun fromServer(url: String): AuthenticatedBookDownloadReference =
            AuthenticatedBookDownloadReference(requireAbsoluteHttpUrl(url, "book file"))
    }
}

data class BookGroup(
    val id: String,
    val name: String,
    val description: String,
    val isPublicGroup: Boolean
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

        internal fun fromServer(value: String, context: String = "compact book"): SeriesIndex =
            runCatching { fromExactValue(value) }.getOrElse { invalidProtocol(context) }
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
private const val MAX_LIBRARY_PREVIEW_LIMIT = 24
private val SERIES_INDEX_PATTERN = Regex("^-?\\d+\\.\\d{2}$")

private fun validateLibraryPage(page: Int, pageSize: Int) {
    require(page > 0) { "Library page must be positive." }
    require(pageSize in 1..MAX_LIBRARY_PAGE_SIZE) {
        "Library page size must be between 1 and $MAX_LIBRARY_PAGE_SIZE."
    }
}

private fun validatePreviewLimit(previewLimit: Int) {
    require(previewLimit in 0..MAX_LIBRARY_PREVIEW_LIMIT) {
        "Library preview limit must be between 0 and $MAX_LIBRARY_PREVIEW_LIMIT."
    }
}
