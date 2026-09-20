package com.secondpasslibrary.reader.reader.readium.viewport

import com.secondpasslibrary.reader.reader.cfi.normalizeEpubHref
import com.secondpasslibrary.reader.reader.toc.ReaderPublicationNavigationResult
import com.secondpasslibrary.reader.reader.toc.ReaderPublicationResource
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator

/** Lifecycle binding for renderer-neutral publication navigation such as TOC targets. */
internal class ReadiumPublicationNavigatorBinding(
    private val operations: ReadiumNavigatorOperationLane
) : AutoCloseable {
    private val lock = Any()
    private val scope = MainScope()
    private val mutableCurrentResource = MutableStateFlow<ReaderPublicationResource?>(null)
    private var navigator: EpubNavigatorFragment? = null
    private var locatorJob: Job? = null
    private var generation = 0L
    private var closed = false
    val currentResource = mutableCurrentResource.asStateFlow()

    fun bind(value: EpubNavigatorFragment) {
        val attachment = synchronized(lock) {
            check(!closed) { "Publication navigator binding is closed." }
            generation += 1
            navigator = value
            generation
        }
        locatorJob?.cancel()
        locatorJob = scope.launch {
            value.currentLocator.collect { locator ->
                val current = synchronized(lock) {
                    !closed && navigator === value && generation == attachment
                }
                if (current) mutableCurrentResource.value = locator.toReaderResource()
            }
        }
    }

    fun unbind(value: EpubNavigatorFragment) {
        val removed = synchronized(lock) {
            if (navigator === value) {
                generation += 1
                navigator = null
                true
            } else {
                false
            }
        }
        if (removed) {
            locatorJob?.cancel()
            locatorJob = null
            mutableCurrentResource.value = null
        }
    }

    suspend fun goTo(link: Link): ReaderPublicationNavigationResult = try {
        operations.runNavigation(TOC_NAVIGATION_COMMAND_TIMEOUT) {
            submit(link)
        }.toPublicationNavigationResult()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        ReaderPublicationNavigationResult.UNAVAILABLE
    }

    suspend fun goTo(locator: Locator): ReaderPublicationNavigationResult = try {
        operations.runNavigation(TOC_NAVIGATION_COMMAND_TIMEOUT) {
            val lease = synchronized(lock) {
                if (closed) null else navigator?.let { NavigatorLease(it, generation) }
            } ?: return@runNavigation false
            withContext(Dispatchers.Main.immediate) {
                isCurrent(lease) && lease.navigator.go(locator, animated = false)
            }
        }.toPublicationNavigationResult()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        ReaderPublicationNavigationResult.UNAVAILABLE
    }

    private suspend fun submit(link: Link): Boolean {
        val lease = synchronized(lock) {
            if (closed) null else navigator?.let { NavigatorLease(it, generation) }
        } ?: return false
        val startingResource = lease.navigator.currentLocator.value.toReaderResource()
        val targetResource = link.toReaderResource()
        val accepted = withContext(Dispatchers.Main.immediate) {
            if (isCurrent(lease)) {
                lease.navigator.go(link, animated = false)
            } else {
                false
            }
        }
        if (accepted && targetResource != null && targetResource != startingResource) {
            lease.navigator.currentLocator.first { locator ->
                locator.toReaderResource() == targetResource || !isCurrent(lease)
            }
        }
        return accepted && isCurrent(lease)
    }

    private fun isCurrent(lease: NavigatorLease): Boolean = synchronized(lock) {
        !closed && navigator === lease.navigator && generation == lease.generation
    }

    override fun close() {
        synchronized(lock) {
            closed = true
            generation += 1
            navigator = null
        }
        locatorJob?.cancel()
        locatorJob = null
        mutableCurrentResource.value = null
        scope.cancel()
    }

    private data class NavigatorLease(val navigator: EpubNavigatorFragment, val generation: Long)
}

private fun Locator.toReaderResource(): ReaderPublicationResource? =
    runCatching { ReaderPublicationResource(normalizeEpubHref(href.toString())) }.getOrNull()

private fun Link.toReaderResource(): ReaderPublicationResource? =
    runCatching { ReaderPublicationResource(normalizeEpubHref(href.toString())) }.getOrNull()

private val TOC_NAVIGATION_COMMAND_TIMEOUT = 10.seconds

private fun ReadiumNavigatorCommandResult<Boolean>.toPublicationNavigationResult() = when (this) {
    is ReadiumNavigatorCommandResult.Completed -> if (value) {
        ReaderPublicationNavigationResult.NAVIGATED
    } else {
        ReaderPublicationNavigationResult.UNAVAILABLE
    }

    ReadiumNavigatorCommandResult.TimedOut -> ReaderPublicationNavigationResult.UNAVAILABLE
}
