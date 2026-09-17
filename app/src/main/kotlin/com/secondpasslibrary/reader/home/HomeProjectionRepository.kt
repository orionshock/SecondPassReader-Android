package com.secondpasslibrary.reader.home

import com.secondpasslibrary.client.RecentReadingItem
import com.secondpasslibrary.client.RecentReadingOptions
import com.secondpasslibrary.client.ShelfListOptions
import com.secondpasslibrary.client.ShelfSummary
import com.secondpasslibrary.client.SplClientException
import com.secondpasslibrary.client.toSummary
import com.secondpasslibrary.reader.connection.AuthenticatedClientProvider
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.coroutines.runSuspendCatching
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class HomeProjectionAccount(val profile: ConnectionProfile, val profileId: String) {
    internal val scope = HomeAccountScope(profile.serverOrigin, profileId)
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

    suspend fun hasCachedProjection(scope: HomeAccountScope): Boolean =
        store.hasSnapshot(scope.storageKey)

    suspend fun readCachedReadingHistory(
        scope: HomeAccountScope,
        variant: HomeRecentReadingVariant
    ): HomeProjectionState<RecentReadingItem> = HomeProjectionState(
        content = store.readRecentReading(scope.storageKey, variant).toContent(),
        refresh = HomeProjectionRefresh.Idle
    )

    suspend fun readCachedShelves(scope: HomeAccountScope): HomeProjectionState<ShelfSummary> =
        HomeProjectionState(
            content =
                store.readShelves(
                    scope.storageKey,
                    HomeShelfVariant.FirstPageWithPreviews
                ).toContent(),
            refresh = HomeProjectionRefresh.Idle
        )

    suspend fun refreshReadingHistory(
        account: HomeProjectionAccount,
        variant: HomeRecentReadingVariant
    ): HomeProjectionRefresh =
        refreshOnce(ProjectionRequestKey.recent(account.scope.storageKey, variant)) {
            val client = clientProvider.forProfile(account.profile)
            val items =
                client.marginalia.sessions.recent(
                    RecentReadingOptions(variant.limit, variant.includeClosed)
                )
            store.replaceRecentReading(account.scope.storageKey, variant, items, clock.instant())
        }

    suspend fun refreshShelves(account: HomeProjectionAccount): HomeProjectionRefresh {
        val variant = HomeShelfVariant.FirstPageWithPreviews
        return refreshOnce(ProjectionRequestKey.shelves(account.scope.storageKey, variant)) {
            val client = clientProvider.forProfile(account.profile)
            val items =
                client.shelves.list(
                    ShelfListOptions(
                        page = variant.page,
                        pageSize = variant.pageSize,
                        previewLimit = variant.previewLimit
                    )
                ).shelves.map { it.toSummary() }
            store.replaceShelves(account.scope.storageKey, variant, items, clock.instant())
        }
    }

    private suspend fun refreshOnce(
        key: ProjectionRequestKey,
        operation: suspend () -> Unit
    ): HomeProjectionRefresh {
        val pending = CompletableDeferred<HomeProjectionRefresh>()
        val active = inFlightLock.withLock { inFlight.putIfAbsent(key, pending) }
        if (active != null) return active.await()

        try {
            val operationResult = runSuspendCatching { operation() }
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
