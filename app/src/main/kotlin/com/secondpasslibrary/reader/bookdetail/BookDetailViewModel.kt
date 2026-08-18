package com.secondpasslibrary.reader.bookdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondpasslibrary.reader.connection.AuthenticatedClientProvider
import com.secondpasslibrary.reader.connection.ConnectionProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
internal class BookDetailViewModel
@Inject
constructor(
    clientProvider: AuthenticatedClientProvider
) : ViewModel() {
    private val controller = BookDetailController(clientProvider, viewModelScope)
    private var connectionIdentity: String? = null

    val state = controller.state
    val connectionEvents = controller.connectionEvents

    fun initialize(profile: ConnectionProfile, bookId: String) {
        val identity = "${profile.apiBaseUrl}\u0000${profile.clientSessionId}"
        if (identity != connectionIdentity) {
            connectionIdentity = identity
            controller.clear()
        }
        controller.prepare(profile)
        controller.select(bookId)
    }

    fun retry() = controller.retry()

    override fun onCleared() {
        controller.close()
    }
}
