package com.secondpasslibrary.reader.connection.storage

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.secondpasslibrary.reader.connection.KnownServerRoutes
import com.secondpasslibrary.reader.connection.KnownServerRoutesStorageException
import com.secondpasslibrary.reader.connection.KnownServerRoutesStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

private val Context.knownServerRoutesDataStore by preferencesDataStore(name = "known_server_routes")

@Singleton
internal class DataStoreKnownServerRoutesStore @Inject constructor(
    @ApplicationContext private val context: Context
) : KnownServerRoutesStore {
    override suspend fun read(serverId: String): KnownServerRoutes? = try {
        val values = context.knownServerRoutesDataStore.data.first()
        val storedId = values[SERVER_ID]
        val urls = values[SERVER_URLS]
        val active = values[ACTIVE_URL]
        if (storedId != serverId || urls.isNullOrEmpty() || active.isNullOrEmpty()) {
            null
        } else {
            KnownServerRoutes(storedId, urls.split('\n'), active)
        }
    } catch (failure: IOException) {
        throw KnownServerRoutesStorageException(failure)
    }

    override suspend fun write(routes: KnownServerRoutes) {
        try {
            context.knownServerRoutesDataStore.edit { values ->
                values[SERVER_ID] = routes.serverId
                values[SERVER_URLS] = routes.serverUrls.joinToString("\n")
                values[ACTIVE_URL] = routes.activeLibraryBaseUrl
            }
        } catch (failure: IOException) {
            throw KnownServerRoutesStorageException(failure)
        }
    }

    override suspend fun clear() {
        try {
            context.knownServerRoutesDataStore.edit { it.clear() }
        } catch (failure: IOException) {
            throw KnownServerRoutesStorageException(failure)
        }
    }

    private companion object {
        val SERVER_ID = stringPreferencesKey("server_id")
        val SERVER_URLS = stringPreferencesKey("server_urls")
        val ACTIVE_URL = stringPreferencesKey("active_library_base_url")
    }
}
