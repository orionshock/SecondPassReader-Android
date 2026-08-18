package com.secondpasslibrary.client

interface AuthenticatedSecondPassClient {
    val library: AuthenticatedLibraryClient

    suspend fun recentReading(options: RecentReadingOptions): List<RecentReadingItem>

    suspend fun listShelves(options: ShelfListOptions = ShelfListOptions()): ShelfPage
}

interface AuthenticatedLibraryClient {
    val books: AuthenticatedLibraryBooksClient
    val authors: AuthenticatedLibraryAuthorsClient
    val series: AuthenticatedLibrarySeriesClient
    val groups: AuthenticatedLibraryGroupsClient
}

interface AuthenticatedLibraryBooksClient {
    suspend fun list(
        scope: LibraryScope = LibraryScope.Global,
        options: BookListOptions = BookListOptions()
    ): LibraryPage<CompactBook>

    suspend fun search(
        scope: LibraryScope = LibraryScope.Global,
        options: LibrarySearchOptions = LibrarySearchOptions()
    ): LibraryPage<CompactBook>
}

interface AuthenticatedLibraryAuthorsClient {
    suspend fun list(
        scope: LibraryScope = LibraryScope.Global,
        options: AuthorListOptions = AuthorListOptions()
    ): LibraryPage<LibraryAuthor>

    suspend fun getAuthor(
        authorId: String,
        options: LibraryEntityDetailOptions = LibraryEntityDetailOptions()
    ): LibraryAuthor
}

interface AuthenticatedLibrarySeriesClient {
    suspend fun list(
        scope: LibraryScope = LibraryScope.Global,
        options: SeriesListOptions = SeriesListOptions()
    ): LibraryPage<LibrarySeries>

    suspend fun getSeries(
        seriesId: String,
        options: LibraryEntityDetailOptions = LibraryEntityDetailOptions()
    ): LibrarySeries
}

interface AuthenticatedLibraryGroupsClient {
    suspend fun listGroups(
        options: LibraryGroupListOptions = LibraryGroupListOptions()
    ): LibraryPage<LibraryGroupSummary>
}

interface AuthenticatedSecondPassClientFactory {
    fun authenticated(
        apiBaseUrl: String,
        credential: BearerCredential
    ): AuthenticatedSecondPassClient
}
