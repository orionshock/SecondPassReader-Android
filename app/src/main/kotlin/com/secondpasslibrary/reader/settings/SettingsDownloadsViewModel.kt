package com.secondpasslibrary.reader.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondpasslibrary.reader.app.storage.AccountLocalDownload
import com.secondpasslibrary.reader.app.storage.AccountLocalDownloadRepository
import com.secondpasslibrary.reader.app.storage.AccountLocalScope
import com.secondpasslibrary.reader.app.storage.OfflineBookAvailabilityController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class SettingsDownloadsState(
    val downloads: List<AccountLocalDownload> = emptyList(),
    val loading: Boolean = true,
    val busy: Boolean = false,
    val error: Boolean = false
) {
    val totalBytes: Long get() = downloads.sumOf(AccountLocalDownload::sizeBytes)
}

@HiltViewModel
internal class SettingsDownloadsViewModel @Inject constructor(
    private val downloads: AccountLocalDownloadRepository,
    private val offlineBooks: OfflineBookAvailabilityController
) : ViewModel() {
    private val mutableState = MutableStateFlow(SettingsDownloadsState())
    val state = mutableState.asStateFlow()
    private var account: AccountLocalScope? = null
    private var generation = 0

    init {
        viewModelScope.launch { offlineBooks.revision.collect { if (account != null) refresh() } }
    }

    fun initialize(scope: AccountLocalScope) {
        if (account == scope) return
        account = scope
        mutableState.value = SettingsDownloadsState()
        val request = ++generation
        viewModelScope.launch { load(scope, request) }
    }

    fun refresh() {
        val scope = account ?: return
        if (mutableState.value.busy) return
        mutableState.value = mutableState.value.copy(loading = true, error = false)
        val request = ++generation
        viewModelScope.launch { load(scope, request) }
    }

    fun remove(bookId: String) = mutate { scope -> offlineBooks.remove(scope, bookId) }

    fun removeAll() = mutate(offlineBooks::removeAll)

    private fun mutate(operation: suspend (AccountLocalScope) -> Unit) {
        val scope = account ?: return
        if (mutableState.value.busy) return
        mutableState.value = mutableState.value.copy(busy = true, error = false)
        val request = ++generation
        viewModelScope.launch {
            val result = runCatching {
                operation(scope)
                downloads.downloads(scope)
            }
            if (account == scope && generation == request) {
                mutableState.value = result.fold(
                    onSuccess = { SettingsDownloadsState(downloads = it, loading = false) },
                    onFailure = {
                        mutableState.value.copy(busy = false, loading = false, error = true)
                    }
                )
            }
        }
    }

    private suspend fun load(scope: AccountLocalScope, request: Int) {
        val result = runCatching { downloads.downloads(scope) }
        if (account == scope && generation == request) {
            mutableState.value = result.fold(
                onSuccess = { SettingsDownloadsState(downloads = it, loading = false) },
                onFailure = { SettingsDownloadsState(loading = false, error = true) }
            )
        }
    }
}
