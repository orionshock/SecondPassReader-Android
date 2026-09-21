package com.secondpasslibrary.reader.connection.storage

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.secondpasslibrary.reader.connection.AuthenticatedConnectionIdentity
import com.secondpasslibrary.reader.connection.PersistedAccountContext
import com.secondpasslibrary.reader.connection.serverOrigin
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

private val Context.persistedAccountContextDataStore by
    preferencesDataStore(name = "persisted_account_context")

internal interface PersistedAccountContextStore {
    suspend fun read(): PersistedAccountContext?

    suspend fun write(context: PersistedAccountContext)

    suspend fun clear()
}

@Singleton
internal class DataStorePersistedAccountContextStore
@Inject
constructor(
    @ApplicationContext private val applicationContext: Context
) : PersistedAccountContextStore {
    override suspend fun read(): PersistedAccountContext? = try {
        applicationContext.persistedAccountContextDataStore.data.first().toAccountContext()
    } catch (failure: IOException) {
        throw PersistedAccountContextStorageException(
            "Persisted account context could not be read.",
            failure
        )
    }

    override suspend fun write(context: PersistedAccountContext) {
        try {
            applicationContext.persistedAccountContextDataStore.edit {
                it.putAccountContext(context)
            }
        } catch (failure: IOException) {
            throw PersistedAccountContextStorageException(
                "Persisted account context could not be stored.",
                failure
            )
        }
    }

    override suspend fun clear() {
        try {
            applicationContext.persistedAccountContextDataStore.edit {
                it.clearAccountContext()
            }
        } catch (failure: IOException) {
            throw PersistedAccountContextStorageException(
                "Persisted account context could not be cleared.",
                failure
            )
        }
    }
}

internal class PersistedAccountContextStorageException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

internal fun Preferences.toAccountContext(): PersistedAccountContext? {
    val libraryBaseUrl = this[PersistedAccountContextKeys.LIBRARY_BASE_URL]
    val clientSessionId = this[PersistedAccountContextKeys.CLIENT_SESSION_ID]
    val profileId = this[PersistedAccountContextKeys.PROFILE_ID]
    if (libraryBaseUrl == null || clientSessionId == null || profileId == null) return null
    return PersistedAccountContext(
        connectionIdentity = AuthenticatedConnectionIdentity(libraryBaseUrl, clientSessionId),
        profileId = profileId,
        accountServerOrigin = this[PersistedAccountContextKeys.ACCOUNT_SERVER_ORIGIN]
            ?: AuthenticatedConnectionIdentity(libraryBaseUrl, clientSessionId).serverOrigin
    )
}

internal fun MutablePreferences.putAccountContext(context: PersistedAccountContext) {
    this[PersistedAccountContextKeys.LIBRARY_BASE_URL] = context.connectionIdentity.libraryBaseUrl
    this[PersistedAccountContextKeys.CLIENT_SESSION_ID] =
        context.connectionIdentity.clientSessionId
    this[PersistedAccountContextKeys.PROFILE_ID] = context.profileId
    this[PersistedAccountContextKeys.ACCOUNT_SERVER_ORIGIN] = context.accountServerOrigin
}

internal fun MutablePreferences.clearAccountContext() {
    remove(PersistedAccountContextKeys.LIBRARY_BASE_URL)
    remove(PersistedAccountContextKeys.CLIENT_SESSION_ID)
    remove(PersistedAccountContextKeys.PROFILE_ID)
    remove(PersistedAccountContextKeys.ACCOUNT_SERVER_ORIGIN)
}

private object PersistedAccountContextKeys {
    val LIBRARY_BASE_URL = stringPreferencesKey("library_base_url")
    val CLIENT_SESSION_ID = stringPreferencesKey("client_session_id")
    val PROFILE_ID = stringPreferencesKey("profile_id")
    val ACCOUNT_SERVER_ORIGIN = stringPreferencesKey("account_server_origin")
}
