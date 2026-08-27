package com.secondpasslibrary.reader.reader.marginalia.preferences

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.secondpasslibrary.reader.connection.AuthenticatedConnectionIdentity
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.IOException
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first

private val Context.readerMarginaliaLayerDataStore by preferencesDataStore(
    name = "reader_marginalia_layers",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

internal interface ReaderMarginaliaLayerPreferenceStore {
    suspend fun readAutoShowPrevious(): Boolean

    suspend fun writeAutoShowPrevious(enabled: Boolean)
}

internal data class ReaderMarginaliaVisibilityScope(
    val connectionIdentity: AuthenticatedConnectionIdentity,
    val bookId: String
)

internal interface ReaderMarginaliaLayerVisibilityStore {
    suspend fun read(
        scope: ReaderMarginaliaVisibilityScope,
        sessionId: String,
        now: Instant
    ): Boolean?

    suspend fun write(
        scope: ReaderMarginaliaVisibilityScope,
        sessionId: String,
        visible: Boolean,
        touchedAt: Instant
    )
}

@Singleton
internal class DataStoreReaderMarginaliaLayerStore
@Inject
constructor(
    @ApplicationContext private val context: Context
) : ReaderMarginaliaLayerPreferenceStore,
    ReaderMarginaliaLayerVisibilityStore {
    override suspend fun readAutoShowPrevious(): Boolean =
        preferences()[AUTO_SHOW_PREVIOUS_KEY] ?: DEFAULT_AUTO_SHOW_PREVIOUS

    override suspend fun writeAutoShowPrevious(enabled: Boolean) {
        context.readerMarginaliaLayerDataStore.edit { it[AUTO_SHOW_PREVIOUS_KEY] = enabled }
    }

    override suspend fun read(
        scope: ReaderMarginaliaVisibilityScope,
        sessionId: String,
        now: Instant
    ): Boolean? {
        val key = visibilityKey(scope, sessionId)
        val values = preferences()
        val touchedAt = values[key.touchedAt]?.let(Instant::ofEpochMilli)
        val fresh = touchedAt != null && isReaderMarginaliaVisibilityFresh(touchedAt, now)
        if (touchedAt != null && !fresh) {
            context.readerMarginaliaLayerDataStore.edit {
                it.remove(key.visible)
                it.remove(key.touchedAt)
            }
        }
        return values[key.visible].takeIf { fresh }
    }

    override suspend fun write(
        scope: ReaderMarginaliaVisibilityScope,
        sessionId: String,
        visible: Boolean,
        touchedAt: Instant
    ) {
        val key = visibilityKey(scope, sessionId)
        context.readerMarginaliaLayerDataStore.edit {
            it[key.visible] = visible
            it[key.touchedAt] = touchedAt.toEpochMilli()
        }
    }

    private suspend fun preferences(): Preferences = context.readerMarginaliaLayerDataStore.data
        .catch { failure ->
            if (failure is IOException) emit(emptyPreferences()) else throw failure
        }
        .first()
}

internal fun isReaderMarginaliaVisibilityFresh(touchedAt: Instant, now: Instant): Boolean {
    val age = Duration.between(touchedAt, now)
    return !age.isNegative && age < VISIBILITY_EXPIRY
}

private data class VisibilityPreferenceKey(
    val visible: Preferences.Key<Boolean>,
    val touchedAt: Preferences.Key<Long>
)

private fun visibilityKey(
    scope: ReaderMarginaliaVisibilityScope,
    sessionId: String
): VisibilityPreferenceKey {
    val digest = readerMarginaliaVisibilityCacheKey(scope, sessionId)
    return VisibilityPreferenceKey(
        visible = booleanPreferencesKey("visibility_$digest"),
        touchedAt = longPreferencesKey("touched_$digest")
    )
}

internal fun readerMarginaliaVisibilityCacheKey(
    scope: ReaderMarginaliaVisibilityScope,
    sessionId: String
): String {
    val identity = scope.connectionIdentity
    return sha256(
        listOf(identity.apiBaseUrl, identity.clientSessionId, scope.bookId, sessionId)
            .joinToString("\u0000")
    )
}

private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8))
    .joinToString("") { byte -> "%02x".format(byte) }

internal const val DEFAULT_AUTO_SHOW_PREVIOUS = true
internal val VISIBILITY_EXPIRY: Duration = Duration.ofDays(90)
private val AUTO_SHOW_PREVIOUS_KEY = booleanPreferencesKey("auto_show_previous")

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ReaderMarginaliaLayerStorageModule {
    @Binds
    @Singleton
    abstract fun bindPreferenceStore(
        store: DataStoreReaderMarginaliaLayerStore
    ): ReaderMarginaliaLayerPreferenceStore

    @Binds
    @Singleton
    abstract fun bindVisibilityStore(
        store: DataStoreReaderMarginaliaLayerStore
    ): ReaderMarginaliaLayerVisibilityStore
}
