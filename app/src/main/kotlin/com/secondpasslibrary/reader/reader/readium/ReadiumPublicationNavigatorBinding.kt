package com.secondpasslibrary.reader.reader.readium

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.publication.Link

/** Lifecycle binding for renderer-neutral publication navigation such as TOC targets. */
internal class ReadiumPublicationNavigatorBinding : AutoCloseable {
    private val lock = Any()
    private val operationMutex = Mutex()
    private var navigator: EpubNavigatorFragment? = null
    private var generation = 0L
    private var closed = false

    fun bind(value: EpubNavigatorFragment) = synchronized(lock) {
        check(!closed) { "Publication navigator binding is closed." }
        generation += 1
        navigator = value
    }

    fun unbind(value: EpubNavigatorFragment) = synchronized(lock) {
        if (navigator === value) {
            generation += 1
            navigator = null
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

    override fun close() = synchronized(lock) {
        closed = true
        generation += 1
        navigator = null
    }
}
