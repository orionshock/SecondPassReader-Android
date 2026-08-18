package com.secondpasslibrary.reader

import com.secondpasslibrary.client.AuthenticatedLibraryAuthorsClient
import com.secondpasslibrary.client.AuthenticatedLibraryBooksClient
import com.secondpasslibrary.client.AuthenticatedLibraryClient
import com.secondpasslibrary.client.AuthenticatedLibraryGroupsClient
import com.secondpasslibrary.client.AuthenticatedLibrarySeriesClient
import com.secondpasslibrary.client.AuthenticatedLibraryTagsClient
import com.secondpasslibrary.client.AuthorListOptions
import com.secondpasslibrary.client.BookListOptions
import com.secondpasslibrary.client.CatalogTagListOptions
import com.secondpasslibrary.client.CompactBook
import com.secondpasslibrary.client.LibraryAuthor
import com.secondpasslibrary.client.LibraryCatalogTag
import com.secondpasslibrary.client.LibraryEntityDetailOptions
import com.secondpasslibrary.client.LibraryGroupListOptions
import com.secondpasslibrary.client.LibraryGroupSummary
import com.secondpasslibrary.client.LibraryPage
import com.secondpasslibrary.client.LibraryScope
import com.secondpasslibrary.client.LibrarySearchOptions
import com.secondpasslibrary.client.LibrarySeries
import com.secondpasslibrary.client.SeriesListOptions

internal class FakeAuthenticatedLibraryClient(
    override val books: AuthenticatedLibraryBooksClient = UnsupportedLibraryBooksClient,
    override val authors: AuthenticatedLibraryAuthorsClient = UnsupportedLibraryAuthorsClient,
    override val series: AuthenticatedLibrarySeriesClient = UnsupportedLibrarySeriesClient,
    override val groups: AuthenticatedLibraryGroupsClient = UnsupportedLibraryGroupsClient,
    override val tags: AuthenticatedLibraryTagsClient = UnsupportedLibraryTagsClient
) : AuthenticatedLibraryClient

private object UnsupportedLibraryBooksClient : AuthenticatedLibraryBooksClient {
    override suspend fun list(
        scope: LibraryScope,
        options: BookListOptions
    ): LibraryPage<CompactBook> = unsupported()

    override suspend fun search(
        scope: LibraryScope,
        options: LibrarySearchOptions
    ): LibraryPage<CompactBook> = unsupported()
}

private object UnsupportedLibraryAuthorsClient : AuthenticatedLibraryAuthorsClient {
    override suspend fun list(
        scope: LibraryScope,
        options: AuthorListOptions
    ): LibraryPage<LibraryAuthor> = unsupported()

    override suspend fun getAuthor(
        authorId: String,
        options: LibraryEntityDetailOptions
    ): LibraryAuthor = unsupported()
}

private object UnsupportedLibrarySeriesClient : AuthenticatedLibrarySeriesClient {
    override suspend fun list(
        scope: LibraryScope,
        options: SeriesListOptions
    ): LibraryPage<LibrarySeries> = unsupported()

    override suspend fun getSeries(
        seriesId: String,
        options: LibraryEntityDetailOptions
    ): LibrarySeries = unsupported()
}

private object UnsupportedLibraryGroupsClient : AuthenticatedLibraryGroupsClient {
    override suspend fun listGroups(
        options: LibraryGroupListOptions
    ): LibraryPage<LibraryGroupSummary> = unsupported()
}

private object UnsupportedLibraryTagsClient : AuthenticatedLibraryTagsClient {
    override suspend fun list(
        scope: LibraryScope,
        options: CatalogTagListOptions
    ): LibraryPage<LibraryCatalogTag> = unsupported()

    override suspend fun get(tagId: String): LibraryCatalogTag = unsupported()
}

private fun unsupported(): Nothing = error("Library capability is outside this test fixture.")
