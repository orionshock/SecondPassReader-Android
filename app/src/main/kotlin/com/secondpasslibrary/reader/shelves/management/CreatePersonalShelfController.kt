package com.secondpasslibrary.reader.shelves.management

import com.secondpasslibrary.client.CreatePersonalShelfInput
import com.secondpasslibrary.client.Shelf
import com.secondpasslibrary.client.ShelfMutationField
import com.secondpasslibrary.client.ShelfVisibility
import com.secondpasslibrary.client.SplClientException
import com.secondpasslibrary.reader.connection.AuthenticatedClientProvider
import com.secondpasslibrary.reader.connection.AuthenticatedConnectionIdentity
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.connection.authenticatedConnectionIdentity
import com.secondpasslibrary.reader.coroutines.runSuspendCatching
import com.secondpasslibrary.reader.shelves.ShelvesConnectionEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

internal class CreatePersonalShelfController(
    private val clientProvider: AuthenticatedClientProvider,
    private val coroutineScope: CoroutineScope
) {
    private val mutableState = MutableStateFlow(CreatePersonalShelfState())
    val state = mutableState.asStateFlow()

    private val connectionEventChannel = Channel<ShelvesConnectionEvent>(Channel.BUFFERED)
    val connectionEvents = connectionEventChannel.receiveAsFlow()

    private var profile: ConnectionProfile? = null
    private var connectionIdentity: AuthenticatedConnectionIdentity? = null
    private var submitJob: Job? = null

    fun prepare(profile: ConnectionProfile) {
        val nextConnectionIdentity = profile.authenticatedConnectionIdentity
        this.profile = profile
        if (nextConnectionIdentity == connectionIdentity) return
        connectionIdentity = nextConnectionIdentity
        reset()
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

    fun submit(onCreated: (Shelf) -> Unit) {
        val activeProfile = profile
        if (activeProfile == null || submitJob?.isActive == true) return
        val current = state.value
        val normalizedName = current.name.trim()
        val validation = validateName(normalizedName)
        if (validation != null) {
            mutableState.value = current.copy(nameError = validation, failure = null)
            return
        }
        val input =
            CreatePersonalShelfInput(
                name = normalizedName,
                description = current.description.trim(),
                visibility = current.visibility
            )
        mutableState.value = current.copy(submitting = true, nameError = null, failure = null)
        submitJob = coroutineScope.launch {
            val result = runSuspendCatching {
                clientProvider.forProfile(activeProfile).shelves.create(input)
            }
            result.fold(
                onSuccess = {
                    reset()
                    onCreated(it)
                },
                onFailure = ::applyFailure
            )
        }
    }

    fun reset() {
        submitJob?.cancel()
        submitJob = null
        mutableState.value = CreatePersonalShelfState()
    }

    fun close() = submitJob?.cancel()

    private fun applyFailure(throwable: Throwable) {
        val mutation = throwable as? SplClientException.ShelfMutationRejected
        val current = state.value
        mutableState.value =
            current.copy(
                submitting = false,
                nameError =
                    CreateShelfFieldError.SERVER_REJECTED.takeIf {
                        mutation?.fields?.contains(ShelfMutationField.NAME) == true
                    },
                descriptionError =
                    CreateShelfFieldError.SERVER_REJECTED.takeIf {
                        mutation?.fields?.contains(ShelfMutationField.DESCRIPTION) == true
                    },
                visibilityError =
                    CreateShelfFieldError.SERVER_REJECTED.takeIf {
                        mutation?.fields?.contains(ShelfMutationField.VISIBILITY) == true
                    },
                failure = throwable.toCreateShelfFailure(mutation)
            )
        if (throwable is SplClientException.AuthenticationRejected) {
            connectionEventChannel.trySend(ShelvesConnectionEvent.AuthenticationRejected)
        }
    }
}

private fun validateName(name: String): CreateShelfFieldError? = when {
    name.isEmpty() -> CreateShelfFieldError.REQUIRED
    name.length > MAX_SHELF_NAME_LENGTH -> CreateShelfFieldError.TOO_LONG
    else -> null
}

private fun Throwable.toCreateShelfFailure(
    mutation: SplClientException.ShelfMutationRejected?
): CreateShelfFailure = when (this) {
    is SplClientException.ServerUnreachable -> CreateShelfFailure.UNREACHABLE

    is SplClientException.AuthenticationRejected -> CreateShelfFailure.AUTHENTICATION_REJECTED

    is SplClientException.ShelfMutationRejected ->
        if (mutation?.fields?.any { it != ShelfMutationField.GENERAL } == true) {
            CreateShelfFailure.FIELD_VALIDATION
        } else {
            CreateShelfFailure.REJECTED
        }

    else -> CreateShelfFailure.OTHER
}

private const val MAX_SHELF_NAME_LENGTH = 255
