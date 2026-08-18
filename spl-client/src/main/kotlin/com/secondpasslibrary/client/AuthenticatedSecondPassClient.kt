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
    suspend fun listBooks(options: BookListOptions = BookListOptions()): LibraryPage<CompactBook>

    suspend fun searchLibrary(
        options: LibrarySearchOptions = LibrarySearchOptions()
    ): LibraryPage<CompactBook>

    suspend fun listGroupBooks(
        groupId: String,
        options: GroupBookListOptions = GroupBookListOptions()
    ): LibraryPage<CompactBook>
}

interface AuthenticatedLibraryAuthorsClient {
    suspend fun listAuthors(
        options: AuthorListOptions = AuthorListOptions()
    ): LibraryPage<LibraryAuthor>

    suspend fun getAuthor(
        authorId: String,
        options: LibraryEntityDetailOptions = LibraryEntityDetailOptions()
    ): LibraryAuthor

    suspend fun listGroupAuthors(
        groupId: String,
        options: AuthorListOptions = AuthorListOptions()
    ): LibraryPage<LibraryAuthor>
}

interface AuthenticatedLibrarySeriesClient {
    suspend fun listSeries(
        options: SeriesListOptions = SeriesListOptions()
    ): LibraryPage<LibrarySeries>

    suspend fun getSeries(
        seriesId: String,
        options: LibraryEntityDetailOptions = LibraryEntityDetailOptions()
    ): LibrarySeries

    suspend fun listGroupSeries(
        groupId: String,
        options: SeriesListOptions = SeriesListOptions()
    ): LibraryPage<LibrarySeries>
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
