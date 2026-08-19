package com.secondpasslibrary.reader.shelves

import com.secondpasslibrary.client.Shelf
import com.secondpasslibrary.client.ShelfMutationField
import com.secondpasslibrary.client.ShelfVisibility
import com.secondpasslibrary.client.SplClientException
import com.secondpasslibrary.client.UpdatePersonalShelfInput
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

internal class EditPersonalShelfController(
    private val clientProvider: AuthenticatedClientProvider,
    private val coroutineScope: CoroutineScope
) {
    private val mutableState = MutableStateFlow(EditPersonalShelfState())
    val state = mutableState.asStateFlow()

    private val connectionEventChannel = Channel<ShelvesConnectionEvent>(Channel.BUFFERED)
    val connectionEvents = connectionEventChannel.receiveAsFlow()

    private var profile: ConnectionProfile? = null
    private var connectionIdentity: String? = null
    private var submitJob: Job? = null

    fun prepare(profile: ConnectionProfile) {
        val identity = "${profile.apiBaseUrl}\u0000${profile.clientSessionId}"
        this.profile = profile
        if (identity == connectionIdentity) return
        connectionIdentity = identity
        reset()
    }

    fun begin(shelf: Shelf) {
        val name = shelf.name.trim()
        val description = shelf.description.orEmpty().trim()
        mutableState.value =
            EditPersonalShelfState(
                shelfId = shelf.id,
                name = name,
                description = description,
                visibility = shelf.visibility,
                originalName = name,
                originalDescription = description,
                originalVisibility = shelf.visibility
            )
    }

    fun updateName(name: String) {
        mutableState.value = state.value.copy(name = name, nameError = null, failure = null)
    }

    fun updateDescription(description: String) {
        mutableState.value =
            state.value.copy(description = description, descriptionError = null, failure = null)
    }

    fun updateVisibility(visibility: ShelfVisibility) {
        mutableState.value =
            state.value.copy(visibility = visibility, visibilityError = null, failure = null)
    }

    fun submit(onUpdated: (Shelf) -> Unit) {
        val activeProfile = profile
        val current = state.value
        val shelfId = current.shelfId
        val unavailable =
            activeProfile == null || shelfId == null || submitJob?.isActive == true
        if (unavailable || !current.changed) return
        val normalizedName = current.name.trim()
        val validation = validateShelfMetadataName(normalizedName)
        if (validation != null) {
            mutableState.value = current.copy(nameError = validation, failure = null)
            return
        }
        val input = current.toUpdateInput(normalizedName)
        mutableState.value = current.copy(submitting = true, failure = null)
        submitJob = coroutineScope.launch {
            val result = runCatching {
                clientProvider.forProfile(activeProfile).shelves.update(shelfId, input)
            }
            (result.exceptionOrNull() as? CancellationException)?.let { throw it }
            result.fold(
                onSuccess = {
                    reset()
                    onUpdated(it)
                },
                onFailure = ::applyFailure
            )
        }
    }

    fun reset() {
        submitJob?.cancel()
        submitJob = null
        mutableState.value = EditPersonalShelfState()
    }

    fun close() = submitJob?.cancel()

    private fun applyFailure(throwable: Throwable) {
        val mutation = throwable as? SplClientException.ShelfMutationRejected
        mutableState.value =
            state.value.copy(
                submitting = false,
                nameError = mutation.fieldError(ShelfMutationField.NAME),
                descriptionError = mutation.fieldError(ShelfMutationField.DESCRIPTION),
                visibilityError = mutation.fieldError(ShelfMutationField.VISIBILITY),
                failure = throwable.toShelfManagementFailure()
            )
        if (throwable is SplClientException.AuthenticationRejected) {
            connectionEventChannel.trySend(ShelvesConnectionEvent.AuthenticationRejected)
        }
    }
}

private fun EditPersonalShelfState.toUpdateInput(normalizedName: String): UpdatePersonalShelfInput {
    val normalizedDescription = description.trim()
    return UpdatePersonalShelfInput(
        name = normalizedName.takeIf { it != originalName },
        description = normalizedDescription.takeIf { it != originalDescription },
        visibility = visibility.takeIf { it != originalVisibility }
    )
}

private fun SplClientException.ShelfMutationRejected?.fieldError(
    field: ShelfMutationField
): ShelfMetadataFieldError? =
    ShelfMetadataFieldError.SERVER_REJECTED.takeIf { this?.fields?.contains(field) == true }

internal fun validateShelfMetadataName(name: String): ShelfMetadataFieldError? = when {
    name.isEmpty() -> ShelfMetadataFieldError.REQUIRED
    name.length > MAX_SHELF_METADATA_NAME_LENGTH -> ShelfMetadataFieldError.TOO_LONG
    else -> null
}

private const val MAX_SHELF_METADATA_NAME_LENGTH = 255
