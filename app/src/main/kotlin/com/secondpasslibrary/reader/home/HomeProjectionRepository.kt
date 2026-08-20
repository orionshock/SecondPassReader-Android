package com.secondpasslibrary.reader.home

import com.secondpasslibrary.client.RecentReadingItem
import com.secondpasslibrary.client.RecentReadingOptions
import com.secondpasslibrary.client.ShelfListOptions
import com.secondpasslibrary.client.ShelfSummary
import com.secondpasslibrary.client.SplClientException
import com.secondpasslibrary.client.toSummary
import com.secondpasslibrary.reader.connection.AuthenticatedClientProvider
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.home.projection.HomeAccountScopeKey
import com.secondpasslibrary.reader.home.projection.HomeProjectionSnapshot
import com.secondpasslibrary.reader.home.projection.HomeProjectionStore
import com.secondpasslibrary.reader.home.projection.HomeRecentReadingVariant
import com.secondpasslibrary.reader.home.projection.HomeShelfVariant
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class HomeProjectionAccount(val profile: ConnectionProfile, val profileId: String) {
    internal val scopeKey = HomeAccountScopeKey.from(profile.serverOrigin, profileId)
}

@Singleton
internal class HomeProjectionRepository internal constructor(
    private val store: HomeProjectionStore,
    private val clientProvider: AuthenticatedClientProvider,
    private val clock: Clock
) {
    @Inject
    constructor(
        store: HomeProjectionStore,
        clientProvider: AuthenticatedClientProvider
    ) : this(store, clientProvider, Clock.systemUTC())

    private val inFlightLock = Mutex()
    private val inFlight =
        mutableMapOf<ProjectionRequestKey, CompletableDeferred<HomeProjectionRefresh>>()

    fun readingHistory(
        account: HomeProjectionAccount,
        variant: HomeRecentReadingVariant
    ): Flow<HomeProjectionState<RecentReadingItem>> = flow {
        val cached = store.readRecentReading(account.scopeKey, variant).toContent()
        emit(HomeProjectionState(cached, HomeProjectionRefresh.Idle))
        emit(HomeProjectionState(cached, HomeProjectionRefresh.Refreshing))

        val refresh = refreshOnce(ProjectionRequestKey.recent(account.scopeKey, variant)) {
            val client = clientProvider.forProfile(account.profile)
            val items =
                client.marginalia.sessions.recent(
                    RecentReadingOptions(variant.limit, variant.includeClosed)
                )
            store.replaceRecentReading(account.scopeKey, variant, items, clock.instant())
        }
        val content =
            if (refresh == HomeProjectionRefresh.Current) {
                checkNotNull(store.readRecentReading(account.scopeKey, variant)).toContent()
            } else {
                cached
            }
        emit(HomeProjectionState(content, refresh))
    }

    fun shelves(account: HomeProjectionAccount): Flow<HomeProjectionState<ShelfSummary>> = flow {
        val variant = HomeShelfVariant.FirstPageWithPreviews
        val cached = store.readShelves(account.scopeKey, variant).toContent()
        emit(HomeProjectionState(cached, HomeProjectionRefresh.Idle))
        emit(HomeProjectionState(cached, HomeProjectionRefresh.Refreshing))

        val refresh = refreshOnce(ProjectionRequestKey.shelves(account.scopeKey, variant)) {
            val client = clientProvider.forProfile(account.profile)
            val items =
                client.shelves.list(
                    ShelfListOptions(
                        page = variant.page,
                        pageSize = variant.pageSize,
                        previewLimit = variant.previewLimit
                    )
                ).shelves.map { it.toSummary() }
            store.replaceShelves(account.scopeKey, variant, items, clock.instant())
        }
        val content =
            if (refresh == HomeProjectionRefresh.Current) {
                checkNotNull(store.readShelves(account.scopeKey, variant)).toContent()
            } else {
                cached
            }
        emit(HomeProjectionState(content, refresh))
    }

    private suspend fun refreshOnce(
        key: ProjectionRequestKey,
        operation: suspend () -> Unit
    ): HomeProjectionRefresh {
        val pending = CompletableDeferred<HomeProjectionRefresh>()
        val active = inFlightLock.withLock { inFlight.putIfAbsent(key, pending) }
        if (active != null) return active.await()

        try {
            val operationResult = runCatching { operation() }
            (operationResult.exceptionOrNull() as? CancellationException)?.let { throw it }
            val refresh =
                operationResult.fold(
                    onSuccess = { HomeProjectionRefresh.Current },
                    onFailure = { HomeProjectionRefresh.Failed(it.toProjectionFailure()) }
                )
            pending.complete(refresh)
            return refresh
        } catch (failure: CancellationException) {
            pending.completeExceptionally(failure)
            throw failure
        } finally {
            inFlightLock.withLock { inFlight.remove(key, pending) }
        }
    }
}

private data class ProjectionRequestKey(
    val accountKey: String,
    val projection: String,
    val variant: String
) {
    companion object {
        fun recent(account: HomeAccountScopeKey, variant: HomeRecentReadingVariant) =
            ProjectionRequestKey(account.value, "recent", variant.storageKey)

        fun shelves(account: HomeAccountScopeKey, variant: HomeShelfVariant) =
            ProjectionRequestKey(account.value, "shelves", variant.storageKey)
    }
}

private fun <T> HomeProjectionSnapshot<T>?.toContent(): HomeProjectionContent<T>? =
    this?.let { HomeProjectionContent(it.items, it.fetchedAt) }

private fun Throwable.toProjectionFailure(): HomeProjectionFailure = when (this) {
    is SplClientException.ServerUnreachable -> HomeProjectionFailure.Unreachable

    is SplClientException.AuthenticationRejected -> HomeProjectionFailure.AuthenticationRejected

    is SplClientException.ProtocolInvalid,
    is SplClientException.NotSecondPassServer -> HomeProjectionFailure.ProtocolInvalid

    else -> HomeProjectionFailure.Other
}
