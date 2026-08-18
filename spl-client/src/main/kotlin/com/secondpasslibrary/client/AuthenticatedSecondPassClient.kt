package com.secondpasslibrary.client

interface AuthenticatedSecondPassClient {
    suspend fun recentReading(options: RecentReadingOptions): List<RecentReadingItem>

    suspend fun listShelves(options: ShelfListOptions = ShelfListOptions()): ShelfPage

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
}

interface AuthenticatedSecondPassClientFactory {
    fun authenticated(
        apiBaseUrl: String,
        credential: BearerCredential
    ): AuthenticatedSecondPassClient
}
