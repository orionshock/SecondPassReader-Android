package com.secondpasslibrary.reader

import com.secondpasslibrary.client.AuthenticatedLibraryAuthorsClient
import com.secondpasslibrary.client.AuthenticatedLibraryBooksClient
import com.secondpasslibrary.client.AuthenticatedLibraryClient
import com.secondpasslibrary.client.AuthenticatedLibraryGroupsClient
import com.secondpasslibrary.client.AuthenticatedLibrarySeriesClient
import com.secondpasslibrary.client.AuthorListOptions
import com.secondpasslibrary.client.BookListOptions
import com.secondpasslibrary.client.CompactBook
import com.secondpasslibrary.client.GroupBookListOptions
import com.secondpasslibrary.client.LibraryAuthor
import com.secondpasslibrary.client.LibraryEntityDetailOptions
import com.secondpasslibrary.client.LibraryGroupListOptions
import com.secondpasslibrary.client.LibraryGroupSummary
import com.secondpasslibrary.client.LibraryPage
import com.secondpasslibrary.client.LibrarySearchOptions
import com.secondpasslibrary.client.LibrarySeries
import com.secondpasslibrary.client.SeriesListOptions

internal class FakeAuthenticatedLibraryClient(
    override val books: AuthenticatedLibraryBooksClient = UnsupportedLibraryBooksClient,
    override val authors: AuthenticatedLibraryAuthorsClient = UnsupportedLibraryAuthorsClient,
    override val series: AuthenticatedLibrarySeriesClient = UnsupportedLibrarySeriesClient,
    override val groups: AuthenticatedLibraryGroupsClient = UnsupportedLibraryGroupsClient
) : AuthenticatedLibraryClient

private object UnsupportedLibraryBooksClient : AuthenticatedLibraryBooksClient {
    override suspend fun listBooks(options: BookListOptions): LibraryPage<CompactBook> =
        unsupported()

    override suspend fun searchLibrary(options: LibrarySearchOptions): LibraryPage<CompactBook> =
        unsupported()

    override suspend fun listGroupBooks(
        groupId: String,
        options: GroupBookListOptions
    ): LibraryPage<CompactBook> = unsupported()
}

private object UnsupportedLibraryAuthorsClient : AuthenticatedLibraryAuthorsClient {
    override suspend fun listAuthors(options: AuthorListOptions): LibraryPage<LibraryAuthor> =
        unsupported()

    override suspend fun getAuthor(
        authorId: String,
        options: LibraryEntityDetailOptions
    ): LibraryAuthor = unsupported()

    override suspend fun listGroupAuthors(
        groupId: String,
        options: AuthorListOptions
    ): LibraryPage<LibraryAuthor> = unsupported()
}

private object UnsupportedLibrarySeriesClient : AuthenticatedLibrarySeriesClient {
    override suspend fun listSeries(options: SeriesListOptions): LibraryPage<LibrarySeries> =
        unsupported()

    override suspend fun getSeries(
        seriesId: String,
        options: LibraryEntityDetailOptions
    ): LibrarySeries = unsupported()

    override suspend fun listGroupSeries(
        groupId: String,
        options: SeriesListOptions
    ): LibraryPage<LibrarySeries> = unsupported()
}

private object UnsupportedLibraryGroupsClient : AuthenticatedLibraryGroupsClient {
    override suspend fun listGroups(
        options: LibraryGroupListOptions
    ): LibraryPage<LibraryGroupSummary> = unsupported()
}

private fun unsupported(): Nothing = error("Library capability is outside this test fixture.")
