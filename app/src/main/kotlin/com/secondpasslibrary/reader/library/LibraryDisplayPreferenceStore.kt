package com.secondpasslibrary.reader.library

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.libraryPresentationDataStore by
    preferencesDataStore(name = "library_presentation")

internal interface LibraryDisplayPreferenceStore {
    suspend fun read(): LibraryBooksLayout

    suspend fun write(layout: LibraryBooksLayout)
}

@Singleton
internal class DataStoreLibraryDisplayPreferenceStore
@Inject
constructor(
    @ApplicationContext private val context: Context
) : LibraryDisplayPreferenceStore {
    override suspend fun read(): LibraryBooksLayout = context.libraryPresentationDataStore.data
        .catch { failure ->
            if (failure is IOException) {
                emit(androidx.datastore.preferences.core.emptyPreferences())
            } else {
                throw failure
            }
        }
        .map { preferences ->
            preferences[LAYOUT_KEY]
                ?.let { saved -> LibraryBooksLayout.entries.firstOrNull { it.name == saved } }
                ?: LibraryBooksLayout.GRID
        }
        .first()

    override suspend fun write(layout: LibraryBooksLayout) {
        context.libraryPresentationDataStore.edit { it[LAYOUT_KEY] = layout.name }
    }

    private companion object {
        val LAYOUT_KEY = stringPreferencesKey("books_layout")
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class LibraryPresentationModule {
    @Binds
    @Singleton
    abstract fun bindLibraryDisplayPreferenceStore(
        store: DataStoreLibraryDisplayPreferenceStore
    ): LibraryDisplayPreferenceStore
}
