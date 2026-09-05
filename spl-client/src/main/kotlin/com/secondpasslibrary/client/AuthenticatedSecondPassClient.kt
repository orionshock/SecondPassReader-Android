package com.secondpasslibrary.client

interface AuthenticatedSecondPassClient {
    val library: AuthenticatedLibraryClient
    val shelves: AuthenticatedShelvesClient
    val marginalia: AuthenticatedMarginaliaClient
}

interface AuthenticatedMarginaliaClient {
    val books: AuthenticatedMarginaliaBooksClient
    val sessions: AuthenticatedReadingSessionsClient
}

interface AuthenticatedMarginaliaBooksClient {
    suspend fun list(
        options: MarginaliaBookListOptions = MarginaliaBookListOptions()
    ): MarginaliaPage<MarginaliaBookSummary>

    suspend fun get(bookId: String): MarginaliaBookSummary

    suspend fun listSessions(
        bookId: String,
        options: BookReadingSessionListOptions = BookReadingSessionListOptions()
    ): BookReadingSessionHistory

    suspend fun getActiveSession(bookId: String): ReadingSessionBootstrap

    suspend fun openSession(
        bookId: String,
        metadata: ReadingSessionMetadataInput = ReadingSessionMetadataInput()
    ): ReadingSessionBootstrap

    suspend fun startOver(
        bookId: String,
        idempotencyKey: MarginaliaIdempotencyKey,
        finalization: ReadingSessionFinalization = ReadingSessionFinalization()
    ): ReadingSessionBootstrap
}

interface AuthenticatedReadingSessionsClient {
    suspend fun list(
        options: ReadingSessionListOptions = ReadingSessionListOptions()
    ): MarginaliaPage<ReadingSessionListItem>

    suspend fun recent(options: RecentReadingOptions): List<RecentReadingItem>

    suspend fun get(sessionId: String): ReadingSessionDetailResult

    suspend fun updateMetadata(
        sessionId: String,
        metadata: ReadingSessionMetadataInput
    ): ReadingSessionDetailResult

    suspend fun close(
        sessionId: String,
        finalization: ReadingSessionFinalization = ReadingSessionFinalization()
    ): ReadingSessionDetailResult

    suspend fun getProgress(sessionId: String): ReadingProgress?

    suspend fun replaceProgress(sessionId: String, progress: ReadingProgressInput): ReadingProgress

    suspend fun listAnnotations(sessionId: String): List<MarginaliaAnnotation>

    suspend fun synchronizeAnnotations(
        sessionId: String,
        operations: List<MarginaliaAnnotationOperation>
    ): List<MarginaliaAnnotation>
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

    suspend fun create(input: CreatePersonalShelfInput): Shelf

    suspend fun update(shelfId: String, input: UpdatePersonalShelfInput): Shelf

    suspend fun delete(shelfId: String)

    suspend fun addItem(shelfId: String, input: AddShelfItemInput): ShelfItem

    suspend fun moveItem(shelfId: String, itemId: String, direction: ShelfItemMove): ShelfItem

    suspend fun setItemPosition(shelfId: String, itemId: String, position: Int): ShelfItem

    suspend fun removeItem(shelfId: String, itemId: String)
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

    /** Streams the server-authorized immutable Book asset into [destination]. */
    suspend fun downloadBook(
        reference: AuthenticatedBookDownloadReference,
        destination: java.io.OutputStream
    )

    suspend fun list(
        scope: LibraryScope = LibraryScope.Global,
        options: BookListOptions = BookListOptions()
    ): CatalogResultPage<CompactBook>

    suspend fun search(
        scope: LibraryScope = LibraryScope.Global,
        options: LibrarySearchOptions = LibrarySearchOptions()
    ): CatalogResultPage<CompactBook>
}

interface AuthenticatedLibraryAuthorsClient {
    suspend fun list(
        scope: LibraryScope = LibraryScope.Global,
        options: AuthorListOptions = AuthorListOptions()
    ): CatalogResultPage<LibraryAuthor>

    suspend fun getAuthor(
        authorId: String,
        options: LibraryEntityDetailOptions = LibraryEntityDetailOptions()
    ): LibraryAuthor
}

interface AuthenticatedLibrarySeriesClient {
    suspend fun list(
        scope: LibraryScope = LibraryScope.Global,
        options: SeriesListOptions = SeriesListOptions()
    ): CatalogResultPage<LibrarySeries>

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
