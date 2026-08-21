package com.secondpasslibrary.reader.bookdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondpasslibrary.reader.connection.AuthenticatedClientProvider
import com.secondpasslibrary.reader.connection.AuthenticatedConnectionIdentity
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.connection.authenticatedConnectionIdentity
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.merge

@HiltViewModel
internal class BookDetailViewModel
@Inject
constructor(
    clientProvider: AuthenticatedClientProvider
) : ViewModel() {
    private val controller = BookDetailController(clientProvider, viewModelScope)
    private val shelfPicker = BookShelfPickerController(clientProvider, viewModelScope)
    private var connectionIdentity: AuthenticatedConnectionIdentity? = null

    val state = controller.state
    val shelfPickerState = shelfPicker.state
    val connectionEvents = merge(controller.connectionEvents, shelfPicker.connectionEvents)

    fun initialize(profile: ConnectionProfile, bookId: String) {
        val nextConnectionIdentity = profile.authenticatedConnectionIdentity
        if (nextConnectionIdentity != connectionIdentity) {
            connectionIdentity = nextConnectionIdentity
            controller.clear()
            shelfPicker.dismiss()
        }
        controller.prepare(profile)
        shelfPicker.prepare(profile)
        controller.select(bookId)
    }

    fun retry() = controller.retry()

    fun openShelfPicker() {
        state.value.bookId?.let(shelfPicker::open)
    }

    fun dismissShelfPicker() = shelfPicker.dismiss()

    fun retryShelfPicker() = shelfPicker.retry()

    fun addToShelf(shelfId: String) = shelfPicker.addTo(shelfId)

    override fun onCleared() {
        controller.close()
        shelfPicker.close()
    }
}
