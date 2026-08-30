package com.secondpasslibrary.reader.bookdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondpasslibrary.reader.app.AppAvailability
import com.secondpasslibrary.reader.bookdetail.shelfpicker.BookShelfPickerController
import com.secondpasslibrary.reader.connection.AuthenticatedClientProvider
import com.secondpasslibrary.reader.connection.AuthenticatedConnectionIdentity
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.connection.authenticatedConnectionIdentity
import com.secondpasslibrary.reader.reader.asset.ReaderAccountScope
import com.secondpasslibrary.reader.reader.asset.ReaderBookAssetStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

@HiltViewModel
internal class BookDetailViewModel
@Inject
constructor(
    clientProvider: AuthenticatedClientProvider,
    private val assetStore: ReaderBookAssetStore
) : ViewModel() {
    private val controller = BookDetailController(clientProvider, viewModelScope)
    private val shelfPicker = BookShelfPickerController(clientProvider, viewModelScope)
    private var connectionIdentity: AuthenticatedConnectionIdentity? = null
    private val mutableOfflineReadable = MutableStateFlow(false)
    val offlineReadable = mutableOfflineReadable.asStateFlow()
    private var offlineAvailabilityJob: Job? = null

    val state = controller.state
    val shelfPickerState = shelfPicker.state
    val connectionEvents = merge(controller.connectionEvents, shelfPicker.connectionEvents)

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
        offlineAvailabilityJob?.cancel()
        if (availability !is AppAvailability.Offline) {
            mutableOfflineReadable.value = true
        } else {
            mutableOfflineReadable.value = false
            offlineAvailabilityJob = viewModelScope.launch {
                mutableOfflineReadable.value = assetStore.findCompleted(
                    ReaderAccountScope(profile.serverOrigin, profileId),
                    bookId
                ) != null
            }
        }
    }

    fun retry() = controller.retry()

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
