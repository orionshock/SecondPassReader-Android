package com.secondpasslibrary.client

interface AuthenticatedSecondPassClient {
    val library: AuthenticatedLibraryClient
    val shelves: AuthenticatedShelvesClient

    suspend fun recentReading(options: RecentReadingOptions): List<RecentReadingItem>
}

interface AuthenticatedShelvesClient {
    suspend fun list(options: ShelfListOptions = ShelfListOptions()): ShelfPage

    suspend fun get(shelfId: String, options: ShelfDetailOptions = ShelfDetailOptions()): Shelf

    suspend fun listItems(
        shelfId: String,
        options: ShelfItemListOptions = ShelfItemListOptions()
    ): ShelfItemPage

    suspend fun listEditorItems(
        shelfId: String,
        options: ShelfEditorListOptions = ShelfEditorListOptions()
    ): ShelfEditorPage
}

interface AuthenticatedLibraryClient {
    val books: AuthenticatedLibraryBooksClient
    val authors: AuthenticatedLibraryAuthorsClient
    val series: AuthenticatedLibrarySeriesClient
    val groups: AuthenticatedLibraryGroupsClient
    val tags: AuthenticatedLibraryTagsClient
}

interface AuthenticatedLibraryBooksClient {
    suspend fun getBook(bookId: String): LibraryBookDetail

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

interface AuthenticatedLibraryTagsClient {
    suspend fun list(
        scope: LibraryScope = LibraryScope.Global,
        options: CatalogTagListOptions = CatalogTagListOptions()
    ): LibraryPage<LibraryCatalogTag>

    suspend fun get(tagId: String): LibraryCatalogTag
}

interface AuthenticatedSecondPassClientFactory {
    fun authenticated(
        apiBaseUrl: String,
        credential: BearerCredential
    ): AuthenticatedSecondPassClient
}
