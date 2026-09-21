package com.secondpasslibrary.reader.connection.storage

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

private val Context.workOfflineDataStore by preferencesDataStore(name = "work_offline")

/** Account-local user intent, separate from transport profiles and observed reachability. */
internal interface WorkOfflineStore {
    suspend fun read(accountKey: String): Boolean

    suspend fun write(accountKey: String, enabled: Boolean)

    suspend fun clearAll() = Unit
}

@Singleton
internal class DataStoreWorkOfflineStore @Inject constructor(
    @ApplicationContext private val context: Context
) : WorkOfflineStore {
    override suspend fun read(accountKey: String): Boolean =
        context.workOfflineDataStore.data.first()[key(accountKey)] == true

    override suspend fun write(accountKey: String, enabled: Boolean) {
        context.workOfflineDataStore.edit { preferences ->
            if (enabled) {
                preferences[key(accountKey)] = true
            } else {
                preferences.remove(key(accountKey))
            }
        }
    }

    override suspend fun clearAll() {
        context.workOfflineDataStore.edit { it.clear() }
    }

    private fun key(accountKey: String) = booleanPreferencesKey(accountKey)
}
