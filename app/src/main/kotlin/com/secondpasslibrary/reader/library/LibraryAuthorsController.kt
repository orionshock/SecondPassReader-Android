package com.secondpasslibrary.reader.library

import com.secondpasslibrary.client.AuthorListOptions
import com.secondpasslibrary.client.AuthorOrdering
import com.secondpasslibrary.client.LibraryAuthor
import com.secondpasslibrary.client.LibraryEntityDetailOptions
import com.secondpasslibrary.reader.connection.AuthenticatedClientProvider
import com.secondpasslibrary.reader.connection.ConnectionProfile
import kotlinx.coroutines.CoroutineScope

internal class LibraryAuthorsController(
    clientProvider: AuthenticatedClientProvider,
    scope: CoroutineScope
) {
    private val delegate =
        LibraryEntityController(
            clientProvider = clientProvider,
            coroutineScope = scope,
            defaultOrdering = AuthorOrdering.NAME,
            pageLoader = { client, request ->
                val options =
                    AuthorListOptions(
                        q = request.query.takeIf(String::isNotBlank),
                        ordering = request.ordering,
                        page = request.page,
                        pageSize = request.pageSize,
                        previewLimit = LIBRARY_AXIS_PREVIEW_LIMIT
                    )
                when (val selectedScope = request.scope) {
                    LibraryScope.AllLibrary -> client.library.authors.listAuthors(options)

                    is LibraryScope.Group ->
                        client.library.authors.listGroupAuthors(selectedScope.id, options)
                }
            },
            detailLoader = { client, id ->
                client.library.authors.getAuthor(
                    id,
                    LibraryEntityDetailOptions(previewLimit = LIBRARY_AXIS_PREVIEW_LIMIT)
                )
            }
        )

    val state = delegate.state
    val connectionEvents = delegate.connectionEvents

    fun prepare(profile: ConnectionProfile, scope: LibraryScope) = delegate.prepare(profile, scope)

    fun activate() = delegate.activate()

    fun selectScope(scope: LibraryScope, activate: Boolean) = delegate.selectScope(scope, activate)

    fun commitSearch(query: String) = delegate.commitSearch(query)

    fun changeOrdering(ordering: AuthorOrdering) = delegate.changeOrdering(ordering)

    fun loadNextPage() = delegate.loadNextPage()

    fun retry() = delegate.retry()

    fun selectAuthor(authorId: String) = delegate.select(authorId)

    fun retryDetail() = delegate.retryDetail()

    fun clearSelection() = delegate.clearSelection()

    fun close() = delegate.close()
}

internal typealias LibraryAuthorsState = LibraryEntityState<LibraryAuthor, AuthorOrdering>
