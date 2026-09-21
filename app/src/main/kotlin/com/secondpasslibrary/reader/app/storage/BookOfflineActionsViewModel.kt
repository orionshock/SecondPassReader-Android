package com.secondpasslibrary.reader.app.storage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondpasslibrary.client.PublicBookCoverReference
import com.secondpasslibrary.client.SplClientException
import com.secondpasslibrary.reader.app.AppAvailability
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.coroutines.runSuspendCatching
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

internal data class BookOfflineActionsState(
    val availableBookIds: Set<String> = emptySet(),
    val busyBookIds: Set<String> = emptySet(),
    val localCovers: Map<String, File> = emptyMap(),
    val pendingRemoval: String? = null,
    val error: Boolean = false
)

@HiltViewModel
internal class BookOfflineActionsViewModel @Inject constructor(
    private val books: OfflineBookAvailabilityController
) : ViewModel() {
    private val mutableState = MutableStateFlow(BookOfflineActionsState())
    val state = mutableState.asStateFlow()
    val changes = books.revision
    private val connectionEventChannel = Channel<Unit>(Channel.BUFFERED)
    val authenticationRejected = connectionEventChannel.receiveAsFlow()
    private var profile: ConnectionProfile? = null
    private var profileId: String? = null
    private var availability: AppAvailability = AppAvailability.Online
    private var visibleBookIds: Set<String> = emptySet()
    private var visibleCovers: Map<String, PublicBookCoverReference> = emptyMap()
    private var refreshJob: Job? = null
    private var backfillJob: Job? = null
    private var generation = 0L

    init {
        viewModelScope.launch { books.revision.collect { refresh() } }
        viewModelScope.launch {
            books.busy.collect { keys ->
                val account = account() ?: return@collect
                mutableState.value = mutableState.value.copy(
                    busyBookIds = keys.filterTo(mutableSetOf()) { it.account == account }
                        .mapTo(mutableSetOf()) { it.bookId }
                )
            }
        }
    }

    fun initialize(profile: ConnectionProfile, profileId: String, availability: AppAvailability) {
        val changed = this.profile != profile || this.profileId != profileId
        val becameOnline = this.availability is AppAvailability.Offline &&
            availability !is AppAvailability.Offline
        this.profile = profile
        this.profileId = profileId
        this.availability = availability
        if (changed) {
            generation++
            backfillJob?.cancel()
            mutableState.value = BookOfflineActionsState()
            refresh()
        }
        if (becameOnline) backfillVisibleCovers()
    }

    fun observeBooks(books: Map<String, PublicBookCoverReference?>) {
        val covers = books.mapNotNull { (id, cover) -> cover?.let { id to it } }.toMap()
        if (visibleBookIds == books.keys && visibleCovers == covers) return
        visibleBookIds = books.keys
        visibleCovers = covers
        refresh()
        backfillVisibleCovers()
    }

    fun refresh() {
        val account = account() ?: return
        val bookIds = visibleBookIds
        val requestGeneration = ++generation
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val available = bookIds.filterTo(mutableSetOf()) {
                runSuspendCatching { books.isAvailable(account, it) }.getOrDefault(false)
            }
            val covers = available.mapNotNull { id ->
                runSuspendCatching { books.localCover(account, id) }.getOrNull()?.let { id to it }
            }.toMap()
            if (requestGeneration == generation && account == account()) {
                mutableState.value = mutableState.value.copy(
                    availableBookIds = available,
                    localCovers = covers
                )
            }
        }
    }

    private fun backfillVisibleCovers() {
        backfillJob?.cancel()
        if (availability is AppAvailability.Offline) return
        val account = account() ?: return
        val covers = visibleCovers
        backfillJob = viewModelScope.launch {
            covers.forEach { (id, cover) ->
                if (account != account()) return@launch
                runSuspendCatching { books.backfillCover(account, id, cover) }
            }
        }
    }

    fun makeAvailable(bookId: String) {
        val currentProfile = profile
        val currentProfileId = profileId
        if (currentProfile == null || currentProfileId == null) return
        if (bookId in state.value.busyBookIds) return
        mutate(bookId) {
            books.makeAvailable(currentProfile, currentProfileId, bookId, availability)
        }
    }

    fun requestRemoval(bookId: String) {
        mutableState.value = mutableState.value.copy(pendingRemoval = bookId)
    }

    fun dismissRemoval() {
        mutableState.value = mutableState.value.copy(pendingRemoval = null)
    }

    fun confirmRemoval() {
        val bookId = state.value.pendingRemoval ?: return
        val account = account() ?: return
        dismissRemoval()
        mutate(bookId) { books.remove(account, bookId) }
    }

    fun dismissError() {
        mutableState.value = mutableState.value.copy(error = false)
    }

    private fun mutate(bookId: String, operation: suspend () -> Unit) {
        val currentAccount = account() ?: return
        mutableState.value = mutableState.value.copy(
            busyBookIds = state.value.busyBookIds + bookId,
            error = false
        )
        viewModelScope.launch {
            val result = runSuspendCatching { operation() }
            if (account() == currentAccount) {
                if (result.exceptionOrNull() is SplClientException.AuthenticationRejected) {
                    connectionEventChannel.trySend(Unit)
                }
                mutableState.value = mutableState.value.copy(
                    busyBookIds = state.value.busyBookIds - bookId,
                    error = result.isFailure &&
                        result.exceptionOrNull() !is SplClientException.AuthenticationRejected
                )
                refresh()
            }
        }
    }

    private fun account(): AccountLocalScope? = profile?.let { currentProfile ->
        profileId?.let { currentProfileId ->
            AccountLocalScope.from(currentProfile.serverId, currentProfileId)
        }
    }
}
