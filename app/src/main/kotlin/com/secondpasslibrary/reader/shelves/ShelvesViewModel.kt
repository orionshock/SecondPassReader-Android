package com.secondpasslibrary.reader.shelves

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondpasslibrary.reader.connection.AuthenticatedClientProvider
import com.secondpasslibrary.reader.connection.ConnectionProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
internal class ShelvesViewModel
@Inject
constructor(clientProvider: AuthenticatedClientProvider) :
    ViewModel() {
    private val controller = ShelvesController(clientProvider, viewModelScope)

    val state = controller.state
    val connectionEvents = controller.connectionEvents

    fun initialize(profile: ConnectionProfile) = controller.initialize(profile)

    fun accept(intent: ShelvesIntent) = controller.accept(intent)

    override fun onCleared() {
        controller.close()
    }
}
