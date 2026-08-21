package com.secondpasslibrary.reader.library.axis

import com.secondpasslibrary.client.AuthorListOptions
import com.secondpasslibrary.client.AuthorOrdering
import com.secondpasslibrary.client.LibraryAuthor
import com.secondpasslibrary.client.LibraryEntityDetailOptions
import com.secondpasslibrary.client.LibraryScope
import com.secondpasslibrary.reader.connection.AuthenticatedClientProvider
import com.secondpasslibrary.reader.connection.ConnectionProfile
import kotlinx.coroutines.CoroutineScope

@Suppress("TooManyFunctions") // Thin facade exposes the Author child's bounded intents.
internal class LibraryAuthorsController(
    clientProvider: AuthenticatedClientProvider,
    scope: CoroutineScope
) {
    private val delegate =
        PagedLibraryAxisController(
            clientProvider = clientProvider,
            coroutineScope = scope,
            defaultOrdering = AuthorOrdering.NAME,
            pageLoader = { client, request ->
                val options =
                    AuthorListOptions(
                        q = request.query.takeIf(String::isNotBlank),
                        tagSlug = request.tagSlug,
                        ordering = request.ordering,
                        page = request.page,
                        pageSize = request.pageSize,
                        previewLimit = LIBRARY_AXIS_PREVIEW_LIMIT
                    )
                client.library.authors.list(request.scope, options)
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

    fun prepare(profile: ConnectionProfile, scope: LibraryScope, tagSlug: String? = null) =
        delegate.prepare(profile, scope, tagSlug)

    fun activate() = delegate.activate()

    fun selectScope(scope: LibraryScope, activate: Boolean, tagSlug: String? = null) =
        delegate.selectScope(scope, activate, tagSlug)

    fun selectTag(tagSlug: String?, activate: Boolean) = delegate.selectTag(tagSlug, activate)

    fun commitSearch(query: String) = delegate.commitSearch(query)

    fun changeOrdering(ordering: AuthorOrdering) = delegate.changeOrdering(ordering)

    fun loadNextPage() = delegate.loadNextPage()

    fun retry() = delegate.retry()

    fun selectAuthor(authorId: String) = delegate.select(authorId)

    fun retryDetail() = delegate.retryDetail()

    fun clearSelection() = delegate.clearSelection()

    fun close() = delegate.close()
}

internal typealias LibraryAuthorsState = PagedLibraryAxisState<LibraryAuthor, AuthorOrdering>
