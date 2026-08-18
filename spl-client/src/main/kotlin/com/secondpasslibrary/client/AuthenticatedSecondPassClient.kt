package com.secondpasslibrary.client

interface AuthenticatedSecondPassClient : AuthenticatedLibraryClient {
    suspend fun recentReading(options: RecentReadingOptions): List<RecentReadingItem>

    suspend fun listShelves(options: ShelfListOptions = ShelfListOptions()): ShelfPage
}

interface AuthenticatedLibraryClient {
    suspend fun listBooks(options: BookListOptions = BookListOptions()): LibraryPage<CompactBook>

    suspend fun searchLibrary(
        options: LibrarySearchOptions = LibrarySearchOptions()
    ): LibraryPage<CompactBook>

    suspend fun listLibraryGroups(
        options: LibraryGroupListOptions = LibraryGroupListOptions()
    ): LibraryPage<LibraryGroupSummary>

    suspend fun listGroupBooks(
        groupId: String,
        options: GroupBookListOptions = GroupBookListOptions()
    ): LibraryPage<CompactBook>

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

interface AuthenticatedSecondPassClientFactory {
    fun authenticated(
        apiBaseUrl: String,
        credential: BearerCredential
    ): AuthenticatedSecondPassClient
}
