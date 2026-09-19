package com.secondpasslibrary.reader.bookdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondpasslibrary.client.SplClientException
import com.secondpasslibrary.reader.app.AppAvailability
import com.secondpasslibrary.reader.app.storage.AccountLocalScope
import com.secondpasslibrary.reader.app.storage.OfflineBookAvailabilityController
import com.secondpasslibrary.reader.bookdetail.shelfpicker.BookShelfPickerController
import com.secondpasslibrary.reader.connection.AuthenticatedClientProvider
import com.secondpasslibrary.reader.connection.AuthenticatedConnectionIdentity
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.connection.authenticatedConnectionIdentity
import com.secondpasslibrary.reader.coroutines.runSuspendCatching
import dagger.hilt.android.lifecycle.HiltViewModel
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
    private val offlineBooks: OfflineBookAvailabilityController
) : ViewModel() {
    private val controller = BookDetailController(clientProvider, viewModelScope)
    private val shelfPicker = BookShelfPickerController(clientProvider, viewModelScope)
    private var connectionIdentity: AuthenticatedConnectionIdentity? = null
    private val mutableOfflineReadable = MutableStateFlow(false)
    val offlineReadable = mutableOfflineReadable.asStateFlow()
    private val mutableOfflineAction = MutableStateFlow(BookOfflineActionState())
    val offlineAction = mutableOfflineAction.asStateFlow()
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
        controller.select(bookId)
        selectedProfile = profile
        selectedProfileId = profileId
        selectedAvailability = availability
        selectedBookId = bookId
        offlineGeneration++
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
            if (generation != offlineGeneration) return@launch
            mutableOfflineAction.value = mutableOfflineAction.value.copy(available = available)
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
    val downloading: Boolean = false,
    val error: Boolean = false
)
