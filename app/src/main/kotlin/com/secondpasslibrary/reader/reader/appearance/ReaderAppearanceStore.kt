package com.secondpasslibrary.reader.reader.appearance

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.secondpasslibrary.reader.reader.domain.ReaderAppearance
import com.secondpasslibrary.reader.reader.domain.ReaderTheme
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

private val Context.readerAppearanceDataStore by preferencesDataStore(
    name = "reader_appearance",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

internal interface ReaderAppearanceStore {
    suspend fun read(): ReaderAppearance

    suspend fun write(appearance: ReaderAppearance)
}

@Singleton
internal class DataStoreReaderAppearanceStore
@Inject
constructor(
    @ApplicationContext private val context: Context
) : ReaderAppearanceStore {
    override suspend fun read(): ReaderAppearance = context.readerAppearanceDataStore.data
        .catch { failure ->
            if (failure is IOException) emit(emptyPreferences()) else throw failure
        }
        .map(Preferences::toReaderAppearance)
        .first()

    override suspend fun write(appearance: ReaderAppearance) {
        val saved = appearance.toPersistedReaderAppearance()
        context.readerAppearanceDataStore.edit { preferences ->
            preferences[THEME_KEY] = checkNotNull(saved.theme)
            preferences[FONT_SCALE_KEY] = checkNotNull(saved.fontScale)
            preferences[LINE_HEIGHT_KEY] = checkNotNull(saved.lineHeight)
            preferences[PUBLISHER_STYLES_KEY] = checkNotNull(saved.publisherStylesEnabled)
        }
    }
}

private fun Preferences.toReaderAppearance() = PersistedReaderAppearance(
    theme = this[THEME_KEY],
    fontScale = this[FONT_SCALE_KEY],
    lineHeight = this[LINE_HEIGHT_KEY],
    publisherStylesEnabled = this[PUBLISHER_STYLES_KEY]
).toReaderAppearance()

internal data class PersistedReaderAppearance(
    val theme: String?,
    val fontScale: String?,
    val lineHeight: String?,
    val publisherStylesEnabled: Boolean?
)

internal fun ReaderAppearance.toPersistedReaderAppearance() = PersistedReaderAppearance(
    theme = theme.name,
    fontScale = fontScale.toString(),
    lineHeight = lineHeight.toString(),
    publisherStylesEnabled = publisherStylesEnabled
)

internal fun PersistedReaderAppearance.toReaderAppearance(): ReaderAppearance {
    val defaults = ReaderAppearance()
    return ReaderAppearance(
        theme = theme
            ?.let { saved -> ReaderTheme.entries.firstOrNull { it.name == saved } }
            ?: defaults.theme,
        fontScale = fontScale.boundedDouble(
            defaults.fontScale,
            ReaderAppearance.FONT_SCALE_RANGE
        ),
        lineHeight = lineHeight.boundedDouble(
            defaults.lineHeight,
            ReaderAppearance.LINE_HEIGHT_RANGE
        ),
        publisherStylesEnabled = publisherStylesEnabled ?: defaults.publisherStylesEnabled
    )
}

private fun String?.boundedDouble(
    default: Double,
    range: ClosedFloatingPointRange<Double>
): Double {
    val value = this?.toDoubleOrNull()?.takeIf(Double::isFinite) ?: return default
    return value.coerceIn(range)
}

private val THEME_KEY = stringPreferencesKey("theme")
private val FONT_SCALE_KEY = stringPreferencesKey("font_scale")
private val LINE_HEIGHT_KEY = stringPreferencesKey("line_height")
private val PUBLISHER_STYLES_KEY = booleanPreferencesKey("publisher_styles_enabled")

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ReaderAppearanceStorageModule {
    @Binds
    @Singleton
    abstract fun bindReaderAppearanceStore(
        store: DataStoreReaderAppearanceStore
    ): ReaderAppearanceStore
}
