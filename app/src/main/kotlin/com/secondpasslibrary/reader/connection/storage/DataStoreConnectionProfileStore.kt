package com.secondpasslibrary.reader.connection.storage

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.connection.ConnectionProfileStorageException
import com.secondpasslibrary.reader.connection.ConnectionProfileStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

private val Context.connectionProfileDataStore by preferencesDataStore(name = "connection_profile")

@Singleton
class DataStoreConnectionProfileStore
@Inject
constructor(
    @ApplicationContext private val context: Context
) : ConnectionProfileStore {
    override suspend fun read(): ConnectionProfile? = try {
        context.connectionProfileDataStore.data.first().toProfile()
    } catch (failure: IOException) {
        throw ConnectionProfileStorageException(
            "Connection profile could not be read.",
            failure
        )
    }

    override suspend fun write(profile: ConnectionProfile) {
        try {
            context.connectionProfileDataStore.edit { values -> values.putProfile(profile) }
        } catch (failure: IOException) {
            throw ConnectionProfileStorageException(
                "Connection profile could not be stored.",
                failure
            )
        }
    }

    override suspend fun clear() {
        try {
            context.connectionProfileDataStore.edit { it.clear() }
        } catch (failure: IOException) {
            throw ConnectionProfileStorageException(
                "Connection profile could not be cleared.",
                failure
            )
        }
    }

    private fun Preferences.toProfile(): ConnectionProfile? {
        val serverId = this[Keys.SERVER_ID]
        val libraryBaseUrl = this[Keys.LIBRARY_BASE_URL]
        if (serverId == null || libraryBaseUrl == null) return null
        return ConnectionProfile(
            serverId = serverId,
            serverOrigin = this[Keys.SERVER_ORIGIN].orEmpty(),
            libraryBaseUrl = libraryBaseUrl,
            serverName = this[Keys.SERVER_NAME].orEmpty(),
            serverDescription = this[Keys.SERVER_DESCRIPTION].orEmpty(),
            serverVersion = this[Keys.SERVER_VERSION].orEmpty(),
            serverReleaseDate = this[Keys.SERVER_RELEASE_DATE].orEmpty(),
            clientSessionId = this[Keys.CLIENT_SESSION_ID].orEmpty(),
            clientName = this[Keys.CLIENT_NAME].orEmpty(),
            clientType = this[Keys.CLIENT_TYPE].orEmpty()
        )
    }

    private fun MutablePreferences.putProfile(profile: ConnectionProfile) {
        this[Keys.SERVER_ID] = profile.serverId
        this[Keys.SERVER_ORIGIN] = profile.serverOrigin
        this[Keys.LIBRARY_BASE_URL] = profile.libraryBaseUrl
        this[Keys.SERVER_NAME] = profile.serverName
        this[Keys.SERVER_DESCRIPTION] = profile.serverDescription
        this[Keys.SERVER_VERSION] = profile.serverVersion
        this[Keys.SERVER_RELEASE_DATE] = profile.serverReleaseDate
        this[Keys.CLIENT_SESSION_ID] = profile.clientSessionId
        this[Keys.CLIENT_NAME] = profile.clientName
        this[Keys.CLIENT_TYPE] = profile.clientType
    }

    private object Keys {
        val SERVER_ID = stringPreferencesKey("server_id")
        val SERVER_ORIGIN = stringPreferencesKey("server_origin")
        val LIBRARY_BASE_URL = stringPreferencesKey("library_base_url")
        val SERVER_NAME = stringPreferencesKey("server_name")
        val SERVER_DESCRIPTION = stringPreferencesKey("server_description")
        val SERVER_VERSION = stringPreferencesKey("server_version")
        val SERVER_RELEASE_DATE = stringPreferencesKey("server_release_date")
        val CLIENT_SESSION_ID = stringPreferencesKey("client_session_id")
        val CLIENT_NAME = stringPreferencesKey("client_name")
        val CLIENT_TYPE = stringPreferencesKey("client_type")
    }
}
