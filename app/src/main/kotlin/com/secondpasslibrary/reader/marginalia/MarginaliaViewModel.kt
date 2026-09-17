package com.secondpasslibrary.reader.marginalia

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondpasslibrary.reader.connection.AuthenticatedClientProvider
import com.secondpasslibrary.reader.connection.ConnectionProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
internal class MarginaliaViewModel
@Inject
constructor(
    clientProvider: AuthenticatedClientProvider
) : ViewModel() {
    private val controller = MarginaliaController(clientProvider, viewModelScope)

    val state = controller.state
    val connectionEvents = controller.connectionEvents
    val navigation = controller.navigation

    fun initialize(
        profile: ConnectionProfile,
        initialContext: MarginaliaHistoryContext = MarginaliaHistoryContext.Global,
        detailEntry: ReadingSessionDetailEntry? = null
    ) = controller.initialize(profile, initialContext, detailEntry)

    fun accept(intent: MarginaliaIntent) = controller.accept(intent)

    override fun onCleared() {
        controller.close()
    }
}
