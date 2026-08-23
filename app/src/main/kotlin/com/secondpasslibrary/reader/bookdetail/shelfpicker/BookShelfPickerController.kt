package com.secondpasslibrary.reader.bookdetail.shelfpicker

import com.secondpasslibrary.client.AddShelfItemInput
import com.secondpasslibrary.client.AuthenticatedSecondPassClient
import com.secondpasslibrary.client.Shelf
import com.secondpasslibrary.client.ShelfListOptions
import com.secondpasslibrary.client.ShelfMutationRejection
import com.secondpasslibrary.client.ShelfOrdering
import com.secondpasslibrary.client.ShelfOwner
import com.secondpasslibrary.client.ShelfScope
import com.secondpasslibrary.client.SplClientException
import com.secondpasslibrary.reader.bookdetail.BookDetailConnectionEvent
import com.secondpasslibrary.reader.connection.AuthenticatedClientProvider
import com.secondpasslibrary.reader.connection.ConnectionProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@Suppress("TooManyFunctions") // One bounded picker owns load, add, and ambiguity reconciliation.
internal class BookShelfPickerController(
    private val clientProvider: AuthenticatedClientProvider,
    private val scope: CoroutineScope
) {
    private val mutableState = MutableStateFlow(BookShelfPickerState())
    val state = mutableState.asStateFlow()
    private val connectionEventChannel = Channel<BookDetailConnectionEvent>(Channel.BUFFERED)
    val connectionEvents = connectionEventChannel.receiveAsFlow()
    private val addJobs = mutableMapOf<String, Job>()
    private var profile: ConnectionProfile? = null
    private var loadJob: Job? = null
    private var generation = 0L

    fun prepare(profile: ConnectionProfile) {
        this.profile = profile
    }

    fun open(bookId: String) {
        require(bookId.isNotBlank()) { "Book ID must not be blank." }
        val activeProfile = checkNotNull(profile) { "Book Shelf picker is not prepared." }
        loadJob?.cancel()
        generation += 1
        val activeGeneration = generation
        mutableState.value = BookShelfPickerState(open = true, bookId = bookId, loading = true)
        loadJob = scope.launch {
            val result = runCatching { loadTargets(activeProfile, bookId) }
            (result.exceptionOrNull() as? CancellationException)?.let { throw it }
            if (activeGeneration != generation || state.value.bookId != bookId) return@launch
            result.fold(onSuccess = ::applyTargets, onFailure = ::applyLoadFailure)
        }
    }

    fun retry() {
        val bookId = state.value.bookId ?: return
        if (loadJob?.isActive == true) return
        open(bookId)
    }

    fun dismiss() {
        loadJob?.cancel()
        generation += 1
        mutableState.value = state.value.copy(open = false, loading = false)
    }

    fun addTo(shelfId: String) {
        val activeProfile = profile
        val bookId = state.value.availableBookIdFor(shelfId) ?: return
        if (activeProfile == null || addJobs[shelfId]?.isActive == true) return
        updateTarget(bookId, shelfId) { it.copy(adding = true, failure = null) }
        addJobs[shelfId] = scope.launch {
            val result = runCatching {
                val client = clientProvider.forProfile(activeProfile)
                client.shelves.addItem(shelfId, AddShelfItemInput(bookId))
            }
            (result.exceptionOrNull() as? CancellationException)?.let { throw it }
            result.fold(
                onSuccess = { markAdded(bookId, shelfId) },
                onFailure = { resolveAddFailure(activeProfile, bookId, shelfId, it) }
            )
            addJobs.remove(shelfId)
        }
    }

    fun close() {
        loadJob?.cancel()
        addJobs.values.forEach(Job::cancel)
        addJobs.clear()
        generation += 1
        mutableState.value = BookShelfPickerState()
        connectionEventChannel.close()
    }

    private suspend fun loadTargets(
        activeProfile: ConnectionProfile,
        bookId: String
    ): List<BookShelfTarget> {
        val client = clientProvider.forProfile(activeProfile)
        val personal = client.listAllPersonalShelves()
        val memberships = client.listAllPersonalShelves(bookId).mapTo(mutableSetOf(), Shelf::id)
        return personal
            .asSequence()
            .filter { it.owner is ShelfOwner.User && it.canEdit }
            .map { shelf ->
                BookShelfTarget(
                    shelf.id,
                    shelf.name,
                    shelf.itemCount,
                    shelf.visibility,
                    added = shelf.id in memberships
                )
            }
            .sortedBy { it.name.lowercase() }
            .toList()
    }

    private suspend fun resolveAddFailure(
        activeProfile: ConnectionProfile,
        bookId: String,
        shelfId: String,
        failure: Throwable
    ) {
        val duplicate =
            (failure as? SplClientException.ShelfMutationRejected)?.reason ==
                ShelfMutationRejection.DUPLICATE_BOOK
        when {
            duplicate -> markAdded(bookId, shelfId)

            failure.isAmbiguousShelfAddFailure() ->
                reconcileAmbiguousAdd(activeProfile, bookId, shelfId, failure)

            else -> applyAddFailure(bookId, shelfId, failure)
        }
    }

    private suspend fun reconcileAmbiguousAdd(
        activeProfile: ConnectionProfile,
        bookId: String,
        shelfId: String,
        originalFailure: Throwable
    ) {
        val result = runCatching {
            clientProvider.forProfile(activeProfile).listAllPersonalShelves(bookId)
        }
        result.fold(
            onSuccess = { memberships ->
                if (memberships.any { it.id == shelfId }) {
                    markAdded(bookId, shelfId)
                } else {
                    applyAddFailure(bookId, shelfId, originalFailure)
                }
            },
            onFailure = { reconciliationFailure ->
                val reportedFailure =
                    if (reconciliationFailure.toBookShelfPickerFailure() ==
                        BookShelfPickerFailure.AUTHENTICATION_REJECTED
                    ) {
                        reconciliationFailure
                    } else {
                        originalFailure
                    }
                applyAddFailure(bookId, shelfId, reportedFailure)
            }
        )
    }

    private fun applyTargets(targets: List<BookShelfTarget>) {
        mutableState.value =
            state.value.copy(loading = false, loaded = true, targets = targets, failure = null)
    }

    private fun applyLoadFailure(failure: Throwable) {
        val classified = failure.toBookShelfPickerFailure()
        mutableState.value =
            state.value.copy(
                loading = false,
                loaded = false,
                targets = emptyList(),
                failure = classified
            )
        reportAuthenticationRejection(classified)
    }

    private fun applyAddFailure(bookId: String, shelfId: String, failure: Throwable) {
        val classified = failure.toBookShelfPickerFailure()
        updateTarget(bookId, shelfId) { it.copy(adding = false, failure = classified) }
        reportAuthenticationRejection(classified)
    }

    private fun markAdded(bookId: String, shelfId: String) {
        updateTarget(bookId, shelfId) { it.copy(added = true, adding = false, failure = null) }
    }

    private fun updateTarget(
        bookId: String,
        shelfId: String,
        transform: (BookShelfTarget) -> BookShelfTarget
    ) {
        if (state.value.bookId != bookId) return
        mutableState.value =
            state.value.copy(
                targets = state.value.targets.map {
                    if (it.shelfId ==
                        shelfId
                    ) {
                        transform(it)
                    } else {
                        it
                    }
                }
            )
    }

    private fun reportAuthenticationRejection(failure: BookShelfPickerFailure) {
        if (failure == BookShelfPickerFailure.AUTHENTICATION_REJECTED) {
            connectionEventChannel.trySend(BookDetailConnectionEvent.AuthenticationRejected)
        }
    }
}

private suspend fun AuthenticatedSecondPassClient.listAllPersonalShelves(
    bookId: String? = null
): List<Shelf> {
    val results = mutableListOf<Shelf>()
    var pageNumber = 1
    do {
        val page =
            shelves.list(
                ShelfListOptions(
                    scope = ShelfScope.PERSONAL,
                    bookId = bookId,
                    ordering = ShelfOrdering.NAME,
                    page = pageNumber,
                    pageSize = SHELF_PICKER_PAGE_SIZE
                )
            )
        results += page.shelves
        pageNumber += 1
    } while (page.hasNextPage)
    return results
}

private const val SHELF_PICKER_PAGE_SIZE = 200

private fun BookShelfPickerState.availableBookIdFor(shelfId: String): String? {
    val target = targets.find { it.shelfId == shelfId }
    return if (target == null || target.added) null else bookId
}
