package com.secondpasslibrary.reader.reader.readium.cfi

import com.secondpasslibrary.reader.reader.cfi.EpubCfiFailure
import com.secondpasslibrary.reader.reader.cfi.EpubCfiOutcome
import com.secondpasslibrary.reader.reader.cfi.EpubCfiReadiness
import com.secondpasslibrary.reader.reader.cfi.normalizeEpubHref
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.readium.r2.navigator.epub.EpubNavigatorFragment

internal class ReadiumCfiNavigatorBinding(private val runtime: ReadiumCfiJavascriptRuntime) :
    AutoCloseable {
    private val lock = Any()
    private val generations = AtomicLong()
    private val mutableReadiness = MutableStateFlow<EpubCfiReadiness>(
        EpubCfiReadiness.AwaitingViewport
    )
    private val liveOperations = LiveOperationExecutor()
    private var closed = false
    private var current: Lease? = null
    private var readyResource: ReadiumCfiResourceIdentity? = null
    val readiness = mutableReadiness.asStateFlow()

    fun bind(navigator: EpubNavigatorFragment) {
        val lease = synchronized(lock) {
            check(!closed) { "The CFI navigator binding is closed." }
            current?.scope?.cancel()
            Lease(
                generation = generations.incrementAndGet(),
                navigator = navigator,
                scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
                resourceHref = navigator.canonicalResourceHref()
            ).also {
                current = it
                readyResource = null
                mutableReadiness.value = EpubCfiReadiness.PreparingDocument
            }
        }
        lease.scope.launch {
            navigator.currentLocator
                .map { it.href }
                .distinctUntilChanged()
                .collectLatest { href ->
                    val expected = observeResource(lease, href.toString())
                        ?: return@collectLatest
                    awaitLiveDocument(lease, expected)
                }
        }
    }

    fun unbind(navigator: EpubNavigatorFragment) {
        val removed = synchronized(lock) {
            current?.takeIf { it.navigator === navigator }?.also {
                current = null
                readyResource = null
                mutableReadiness.value = EpubCfiReadiness.AwaitingViewport
            }
        }
        removed?.scope?.cancel()
    }

    fun resourceIdentity(navigator: EpubNavigatorFragment): ReadiumCfiResourceIdentity? =
        synchronized(lock) {
            val lease = current?.takeIf { it.navigator === navigator } ?: return@synchronized null
            val href = navigator.canonicalResourceHref()
            if (href == null) {
                readyResource = null
                mutableReadiness.value = EpubCfiReadiness.PreparingDocument
                return@synchronized null
            }
            if (lease.observe(href)) {
                readyResource = null
                mutableReadiness.value = EpubCfiReadiness.PreparingDocument
            }
            ReadiumCfiResourceIdentity(lease.generation, lease.resourceGeneration, href)
        }

    suspend fun <T> withNavigator(
        block: suspend (EpubNavigatorFragment, ReadiumCfiJavascriptRuntime) -> T
    ): T? = liveOperations.run(block)

    override fun close() {
        val removed = synchronized(lock) {
            if (closed) return
            closed = true
            current.also {
                current = null
                readyResource = null
                mutableReadiness.value = EpubCfiReadiness.Closed
            }
        }
        removed?.scope?.cancel()
    }

    private fun observeResource(lease: Lease, href: String): ReadiumCfiResourceIdentity? {
        val normalized = runCatching { normalizeEpubHref(href) }.getOrNull() ?: return null
        return synchronized(lock) {
            if (closed || current !== lease) return@synchronized null
            lease.observe(normalized)
            readyResource = null
            mutableReadiness.value = EpubCfiReadiness.PreparingDocument
            lease.identity()
        }
    }

    private suspend fun awaitLiveDocument(lease: Lease, resource: ReadiumCfiResourceIdentity) {
        var lastFailure: EpubCfiFailure? = null
        val timedOut = withTimeoutOrNull(DOCUMENT_READINESS_TIMEOUT_MILLIS) {
            while (isCurrentResource(lease, resource)) {
                when (val probe = probeLiveDocument(lease, resource)) {
                    DocumentProbeResult.Ready -> {
                        publishAvailable(lease, resource)
                        return@withTimeoutOrNull
                    }

                    DocumentProbeResult.Stale -> return@withTimeoutOrNull

                    is DocumentProbeResult.Failed -> {
                        publishFailed(lease, resource, probe.reason)
                        return@withTimeoutOrNull
                    }

                    is DocumentProbeResult.Retry -> {
                        lastFailure = probe.reason ?: lastFailure
                        delay(DOCUMENT_READINESS_RETRY_MILLIS)
                    }
                }
            }
        } == null
        if (timedOut) {
            publishFailed(
                lease,
                resource,
                lastFailure ?: EpubCfiFailure.JAVASCRIPT_RUNTIME_TIMEOUT
            )
        }
    }

    private suspend fun probeLiveDocument(
        lease: Lease,
        expected: ReadiumCfiResourceIdentity
    ): DocumentProbeResult = lease.liveOperationMutex.withLock {
        val before = resourceIdentity(lease.navigator)
        if (before != expected) return@withLock DocumentProbeResult.Stale
        val ready = runtime.documentReady(lease.navigator)
        val after = resourceIdentity(lease.navigator)
        when {
            after != expected -> DocumentProbeResult.Stale

            ready is ReadiumCfiJavascriptResult.Success && ready.value ->
                DocumentProbeResult.Ready

            ready is ReadiumCfiJavascriptResult.Failure &&
                ready.reason.isTerminalReadinessFailure ->
                DocumentProbeResult.Failed(ready.reason)

            ready is ReadiumCfiJavascriptResult.Failure ->
                DocumentProbeResult.Retry(ready.reason)

            else -> DocumentProbeResult.Retry()
        }
    }

    private fun publishAvailable(lease: Lease, expected: ReadiumCfiResourceIdentity) {
        synchronized(lock) {
            val sameLease = !closed && current === lease
            if (sameLease && lease.identity() == expected) {
                readyResource = expected
                mutableReadiness.value = EpubCfiReadiness.Available
            }
        }
    }

    private fun publishFailed(
        lease: Lease,
        expected: ReadiumCfiResourceIdentity,
        reason: EpubCfiFailure
    ) {
        synchronized(lock) {
            val sameLease = !closed && current === lease
            if (sameLease && lease.identity() == expected) {
                readyResource = null
                mutableReadiness.value = EpubCfiReadiness.Failed(reason)
            }
        }
    }

    private fun isCurrentResource(lease: Lease, resource: ReadiumCfiResourceIdentity): Boolean =
        synchronized(lock) {
            !closed && current === lease && lease.identity() == resource
        }

    private data class Lease(
        val generation: Long,
        val navigator: EpubNavigatorFragment,
        val scope: CoroutineScope,
        var resourceHref: String?,
        var resourceGeneration: Long = 0,
        val liveOperationMutex: Mutex = Mutex()
    ) {
        fun observe(href: String): Boolean = if (resourceHref != href) {
            resourceHref = href
            resourceGeneration += 1
            true
        } else {
            false
        }

        fun identity(): ReadiumCfiResourceIdentity? = resourceHref?.let { href ->
            ReadiumCfiResourceIdentity(generation, resourceGeneration, href)
        }
    }

    private inner class LiveOperationExecutor {
        suspend fun <T> run(
            block: suspend (EpubNavigatorFragment, ReadiumCfiJavascriptRuntime) -> T
        ): T? {
            var result: BoundOperationResult<T>
            do {
                val lease = awaitAvailableLease()
                result = if (lease == null) {
                    BoundOperationResult.Unavailable
                } else {
                    executeOnLease(lease, block)
                }
            } while (result == BoundOperationResult.Retry)
            return (result as? BoundOperationResult.Complete)?.value
        }

        private suspend fun <T> executeOnLease(
            lease: Lease,
            block: suspend (EpubNavigatorFragment, ReadiumCfiJavascriptRuntime) -> T
        ): BoundOperationResult<T> {
            val operation = lease.scope.async {
                lease.liveOperationMutex.withLock {
                    val resource = resourceIdentity(lease.navigator)
                    if (isAvailable(lease, resource)) {
                        BoundOperationResult.Complete(block(lease.navigator, runtime))
                    } else {
                        BoundOperationResult.Retry
                    }
                }
            }
            val result = try {
                operation.await()
            } catch (cancelled: CancellationException) {
                if (isCurrent(lease)) throw cancelled
                BoundOperationResult.Unavailable
            } finally {
                if (!operation.isCompleted) {
                    withContext(NonCancellable) {
                        operation.cancelAndJoin()
                    }
                }
            }
            return if (
                result is BoundOperationResult.Complete && !isCurrent(lease)
            ) {
                BoundOperationResult.Unavailable
            } else {
                result
            }
        }

        private suspend fun awaitAvailableLease(): Lease? {
            var availability = leaseAvailability()
            while (availability is LeaseAvailability.Preparing) {
                if (!awaitPreparation(availability.lease)) return null
                availability = leaseAvailability()
            }
            return (availability as? LeaseAvailability.Available)?.lease
        }

        private suspend fun awaitPreparation(lease: Lease): Boolean {
            val waiter = lease.scope.async {
                readiness.first { it != EpubCfiReadiness.PreparingDocument }
            }
            return try {
                waiter.await()
                true
            } catch (cancelled: CancellationException) {
                if (isCurrent(lease)) throw cancelled
                false
            } finally {
                if (!waiter.isCompleted) waiter.cancel()
            }
        }

        private fun leaseAvailability(): LeaseAvailability = synchronized(lock) {
            when {
                closed || current == null -> LeaseAvailability.Unavailable

                mutableReadiness.value == EpubCfiReadiness.Available ->
                    LeaseAvailability.Available(requireNotNull(current))

                mutableReadiness.value == EpubCfiReadiness.PreparingDocument ->
                    LeaseAvailability.Preparing(requireNotNull(current))

                else -> LeaseAvailability.Unavailable
            }
        }

        private fun isCurrent(lease: Lease): Boolean = synchronized(lock) {
            !closed && current?.generation == lease.generation
        }

        private fun isAvailable(lease: Lease, resource: ReadiumCfiResourceIdentity?): Boolean =
            synchronized(lock) {
                !closed && current === lease &&
                    mutableReadiness.value == EpubCfiReadiness.Available &&
                    resource != null && readyResource == resource
            }
    }

    private sealed interface LeaseAvailability {
        data class Available(val lease: Lease) : LeaseAvailability

        data class Preparing(val lease: Lease) : LeaseAvailability

        data object Unavailable : LeaseAvailability
    }
}

private sealed interface DocumentProbeResult {
    data object Ready : DocumentProbeResult

    data object Stale : DocumentProbeResult

    data class Retry(val reason: EpubCfiFailure? = null) : DocumentProbeResult

    data class Failed(val reason: EpubCfiFailure) : DocumentProbeResult
}

private sealed interface BoundOperationResult<out T> {
    data class Complete<T>(val value: T) : BoundOperationResult<T>

    data object Retry : BoundOperationResult<Nothing>

    data object Unavailable : BoundOperationResult<Nothing>
}

internal data class ReadiumCfiResourceIdentity(
    val navigatorGeneration: Long,
    val resourceGeneration: Long,
    val href: String
)

internal sealed interface ReadiumCfiResourceCapture<out T> {
    data class Stable<T>(val identity: ReadiumCfiResourceIdentity, val value: T) :
        ReadiumCfiResourceCapture<T>

    data object Changed : ReadiumCfiResourceCapture<Nothing>
}

internal fun <T> coherentResourceCapture(
    before: ReadiumCfiResourceIdentity?,
    after: ReadiumCfiResourceIdentity?,
    value: T
): ReadiumCfiResourceCapture<T> = if (before != null && before == after) {
    ReadiumCfiResourceCapture.Stable(before, value)
} else {
    ReadiumCfiResourceCapture.Changed
}

private fun EpubNavigatorFragment.canonicalResourceHref(): String? =
    runCatching { normalizeEpubHref(currentLocator.value.href.toString()) }.getOrNull()

private const val DOCUMENT_READINESS_RETRY_MILLIS = 50L
private const val DOCUMENT_READINESS_TIMEOUT_MILLIS = 30_000L

private val EpubCfiFailure.isTerminalReadinessFailure: Boolean
    get() = when (this) {
        EpubCfiFailure.UNSUPPORTED_FIXED_LAYOUT,
        EpubCfiFailure.UNSUPPORTED_SCROLL_MODE,
        EpubCfiFailure.UNSUPPORTED_WRITING_MODE -> true

        else -> false
    }

internal fun <T> ReadiumCfiNavigatorBinding.unavailableOutcome(): EpubCfiOutcome<T> =
    EpubCfiOutcome.Failure(
        (readiness.value as? EpubCfiReadiness.Failed)?.reason
            ?: EpubCfiFailure.NAVIGATOR_UNAVAILABLE
    )
