package com.secondpasslibrary.reader.bookdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondpasslibrary.client.LibraryBookDetail
import com.secondpasslibrary.client.SplClientException
import com.secondpasslibrary.reader.app.AppAvailability
import com.secondpasslibrary.reader.app.storage.AccountLocalBookCatalog
import com.secondpasslibrary.reader.app.storage.AccountLocalScope
import com.secondpasslibrary.reader.app.storage.OfflineBookAvailabilityController
import com.secondpasslibrary.reader.bookdetail.shelfpicker.BookShelfPickerController
import com.secondpasslibrary.reader.connection.AuthenticatedClientProvider
import com.secondpasslibrary.reader.connection.AuthenticatedConnectionIdentity
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.connection.authenticatedConnectionIdentity
import com.secondpasslibrary.reader.coroutines.runSuspendCatching
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@HiltViewModel
@Suppress("TooManyFunctions") // Book Detail facade exposes its bounded shelf and asset intents.
internal class BookDetailViewModel
@Inject
constructor(
    clientProvider: AuthenticatedClientProvider,
    private val offlineBooks: OfflineBookAvailabilityController,
    private val catalog: AccountLocalBookCatalog
) : ViewModel() {
    private val controller = BookDetailController(clientProvider, viewModelScope)
    private val shelfPicker = BookShelfPickerController(clientProvider, viewModelScope)
    private var connectionIdentity: AuthenticatedConnectionIdentity? = null
    private val mutableOfflineReadable = MutableStateFlow(false)
    val offlineReadable = mutableOfflineReadable.asStateFlow()
    private val mutableOfflineAction = MutableStateFlow(BookOfflineActionState())
    val offlineAction = mutableOfflineAction.asStateFlow()
    private val mutableOfflineDetail = MutableStateFlow<LibraryBookDetail?>(null)
    val offlineDetail = mutableOfflineDetail.asStateFlow()
    private val mutableOfflineDetailLoaded = MutableStateFlow(false)
    val offlineDetailLoaded = mutableOfflineDetailLoaded.asStateFlow()
    private var offlineAvailabilityJob: Job? = null
    private var selectedProfile: ConnectionProfile? = null
    private var selectedProfileId: String? = null
    private var selectedAvailability: AppAvailability = AppAvailability.Online
    private var selectedBookId: String? = null
    private var offlineGeneration = 0L
    private val offlineConnectionEvents = Channel<BookDetailConnectionEvent>(Channel.BUFFERED)

    val state = controller.state
    val shelfPickerState = shelfPicker.state
    val connectionEvents = merge(
        controller.connectionEvents,
        shelfPicker.connectionEvents,
        offlineConnectionEvents.receiveAsFlow()
    )

    init {
        viewModelScope.launch {
            controller.state.collect { loaded ->
                val detail = loaded.detail ?: return@collect
                val profile = selectedProfile ?: return@collect
                val profileId = selectedProfileId ?: return@collect
                if (selectedAvailability is AppAvailability.Offline ||
                    selectedBookId != detail.id
                ) {
                    return@collect
                }
                detail.cover?.let { cover ->
                    offlineBooks.backfillCover(
                        AccountLocalScope.from(profile.serverOrigin, profileId),
                        detail.id,
                        cover
                    )
                    refreshOfflineStatus()
                }
            }
        }
        viewModelScope.launch { offlineBooks.revision.collect { refreshOfflineStatus() } }
        viewModelScope.launch {
            offlineBooks.busy.collect { busy ->
                val profile = selectedProfile ?: return@collect
                val profileId = selectedProfileId ?: return@collect
                val bookId = selectedBookId ?: return@collect
                val key = com.secondpasslibrary.reader.app.storage.OfflineBookKey(
                    AccountLocalScope.from(profile.serverOrigin, profileId),
                    bookId
                )
                mutableOfflineAction.value = mutableOfflineAction.value.copy(
                    downloading = key in busy
                )
            }
        }
    }

    fun initialize(
        profile: ConnectionProfile,
        profileId: String,
        availability: AppAvailability,
        bookId: String
    ) {
        val nextConnectionIdentity = profile.authenticatedConnectionIdentity
        if (nextConnectionIdentity != connectionIdentity) {
            connectionIdentity = nextConnectionIdentity
            controller.clear()
            shelfPicker.dismiss()
        }
        controller.prepare(profile)
        shelfPicker.prepare(profile)
        selectedProfile = profile
        selectedProfileId = profileId
        selectedAvailability = availability
        selectedBookId = bookId
        offlineGeneration++
        mutableOfflineDetail.value = null
        mutableOfflineDetailLoaded.value = false
        if (availability is AppAvailability.Offline) {
            controller.clear()
            val generation = offlineGeneration
            viewModelScope.launch {
                val account = AccountLocalScope.from(profile.serverOrigin, profileId)
                val book = runSuspendCatching {
                    catalog.downloadedBooks(account).firstOrNull { it.id == bookId }
                }.getOrNull()
                if (generation == offlineGeneration) {
                    mutableOfflineDetail.value = book?.let {
                        LibraryBookDetail(
                            id = it.id,
                            title = it.title,
                            sortTitle = it.sortTitle,
                            subtitle = it.subtitle,
                            authors = it.authors,
                            series = it.series,
                            language = it.language,
                            publisher = it.publisher,
                            publishedYear = it.publishedYear,
                            publishedMonth = it.publishedMonth,
                            publishedDay = it.publishedDay,
                            publicationDatePrecision = it.publicationDatePrecision,
                            cover = it.cover,
                            description = "",
                            identifiers = emptyList(),
                            catalogTags = it.catalogTags,
                            file = null,
                            groups = emptyList()
                        )
                    }
                    mutableOfflineDetailLoaded.value = true
                }
            }
        } else {
            controller.select(bookId)
        }
        mutableOfflineAction.value = BookOfflineActionState()
        offlineAvailabilityJob?.cancel()
        refreshOfflineStatus()
    }

    fun makeAvailable() = mutateOfflineBook { profile, profileId, bookId ->
        offlineBooks.makeAvailable(profile, profileId, bookId, selectedAvailability)
    }

    fun removeDownload() = mutateOfflineBook { profile, profileId, bookId ->
        offlineBooks.remove(AccountLocalScope.from(profile.serverOrigin, profileId), bookId)
    }

    private fun mutateOfflineBook(operation: suspend (ConnectionProfile, String, String) -> Unit) {
        val profile = selectedProfile
        val profileId = selectedProfileId
        val bookId = selectedBookId
        if (profile == null || profileId == null || bookId == null) return
        if (mutableOfflineAction.value.downloading) return
        val generation = offlineGeneration
        viewModelScope.launch {
            mutableOfflineAction.value =
                mutableOfflineAction.value.copy(downloading = true, error = false)
            val result = runSuspendCatching { operation(profile, profileId, bookId) }
            if (generation == offlineGeneration) {
                if (result.exceptionOrNull() is SplClientException.AuthenticationRejected) {
                    offlineConnectionEvents.trySend(
                        BookDetailConnectionEvent.AuthenticationRejected
                    )
                }
                mutableOfflineAction.value = mutableOfflineAction.value.copy(
                    downloading = false,
                    error = result.isFailure &&
                        result.exceptionOrNull() !is SplClientException.AuthenticationRejected
                )
                refreshOfflineStatus()
            }
        }
    }

    private fun refreshOfflineStatus() {
        val profile = selectedProfile
        val profileId = selectedProfileId
        val bookId = selectedBookId
        if (profile == null || profileId == null || bookId == null) return
        val generation = offlineGeneration
        offlineAvailabilityJob?.cancel()
        offlineAvailabilityJob = viewModelScope.launch {
            val available = offlineBooks.isAvailable(
                AccountLocalScope.from(profile.serverOrigin, profileId),
                bookId
            )
            val cover = offlineBooks.localCover(
                AccountLocalScope.from(profile.serverOrigin, profileId),
                bookId
            )
            if (generation != offlineGeneration) return@launch
            mutableOfflineAction.value = mutableOfflineAction.value.copy(
                available = available,
                localCover = cover
            )
            mutableOfflineReadable.value =
                selectedAvailability !is AppAvailability.Offline || available
        }
    }

    fun retry() = controller.retry()

    fun refreshOfflineAvailability() = refreshOfflineStatus()

    fun openShelfPicker() {
        state.value.bookId?.let(shelfPicker::open)
    }

    fun dismissShelfPicker() = shelfPicker.dismiss()

    fun retryShelfPicker() = shelfPicker.retry()

    fun addToShelf(shelfId: String) = shelfPicker.addTo(shelfId)

    override fun onCleared() {
        offlineAvailabilityJob?.cancel()
        controller.close()
        shelfPicker.close()
    }
}

internal data class BookOfflineActionState(
    val available: Boolean = false,
    val localCover: File? = null,
    val downloading: Boolean = false,
    val error: Boolean = false
)
