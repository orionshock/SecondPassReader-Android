package com.secondpasslibrary.reader.connection.storage

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.secondpasslibrary.reader.connection.AuthenticatedConnectionIdentity
import com.secondpasslibrary.reader.connection.PersistedAccountContext
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
    val apiBaseUrl = this[PersistedAccountContextKeys.API_BASE_URL]
    val clientSessionId = this[PersistedAccountContextKeys.CLIENT_SESSION_ID]
    val profileId = this[PersistedAccountContextKeys.PROFILE_ID]
    if (apiBaseUrl == null || clientSessionId == null || profileId == null) return null
    return PersistedAccountContext(
        connectionIdentity = AuthenticatedConnectionIdentity(apiBaseUrl, clientSessionId),
        profileId = profileId
    )
}

internal fun MutablePreferences.putAccountContext(context: PersistedAccountContext) {
    this[PersistedAccountContextKeys.API_BASE_URL] = context.connectionIdentity.apiBaseUrl
    this[PersistedAccountContextKeys.CLIENT_SESSION_ID] =
        context.connectionIdentity.clientSessionId
    this[PersistedAccountContextKeys.PROFILE_ID] = context.profileId
}

internal fun MutablePreferences.clearAccountContext() {
    remove(PersistedAccountContextKeys.API_BASE_URL)
    remove(PersistedAccountContextKeys.CLIENT_SESSION_ID)
    remove(PersistedAccountContextKeys.PROFILE_ID)
}

private object PersistedAccountContextKeys {
    val API_BASE_URL = stringPreferencesKey("api_base_url")
    val CLIENT_SESSION_ID = stringPreferencesKey("client_session_id")
    val PROFILE_ID = stringPreferencesKey("profile_id")
}
