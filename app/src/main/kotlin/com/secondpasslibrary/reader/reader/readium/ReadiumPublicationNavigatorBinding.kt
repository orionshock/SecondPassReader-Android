package com.secondpasslibrary.reader.reader.readium

import com.secondpasslibrary.reader.reader.cfi.normalizeEpubHref
import com.secondpasslibrary.reader.reader.toc.ReaderPublicationResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator

/** Lifecycle binding for renderer-neutral publication navigation such as TOC targets. */
internal class ReadiumPublicationNavigatorBinding : AutoCloseable {
    private val lock = Any()
    private val operationMutex = Mutex()
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

    suspend fun goTo(link: Link): Boolean = operationMutex.withLock {
        val lease = synchronized(lock) {
            if (closed) null else navigator?.let { it to generation }
        } ?: return@withLock false
        withContext(Dispatchers.Main.immediate) {
            val current = synchronized(lock) {
                !closed && navigator === lease.first && generation == lease.second
            }
            current && lease.first.go(link, animated = false)
        }
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
}

private fun Locator.toReaderResource(): ReaderPublicationResource? =
    runCatching { ReaderPublicationResource(normalizeEpubHref(href.toString())) }.getOrNull()
