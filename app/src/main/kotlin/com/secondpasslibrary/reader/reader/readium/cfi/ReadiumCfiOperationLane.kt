package com.secondpasslibrary.reader.reader.readium.cfi

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Owns priority and serialization for the one live navigator/DOM command lane. */
internal class ReadiumCfiOperationLane : AutoCloseable {
    private val lock = Any()
    private val mutex = Mutex()
    private var closed = false
    private var activeNavigation: Deferred<*>? = null
    private var activeRead: Deferred<*>? = null

    suspend fun <T> runNavigation(block: suspend () -> T): T = coroutineScope {
        val operation = async(start = CoroutineStart.LAZY) {
            mutex.withLock { block() }
        }
        val superseded = synchronized(lock) {
            check(!closed) { "The CFI operation lane is closed." }
            SupersededOperations(
                navigation = activeNavigation.also { activeNavigation = operation },
                read = activeRead.also { activeRead = null }
            )
        }
        superseded.navigation?.cancel(
            CancellationException("Superseded by a newer CFI navigation.")
        )
        superseded.read?.cancel(CancellationException("Superseded by CFI navigation."))
        operation.start()
        try {
            operation.await()
        } finally {
            synchronized(lock) {
                if (activeNavigation === operation) activeNavigation = null
            }
            if (!operation.isCompleted) operation.cancel()
        }
    }

    suspend fun <T> runLatestRead(block: suspend () -> T): T = coroutineScope {
        val operation = async(start = CoroutineStart.LAZY) {
            mutex.withLock { block() }
        }
        val supersededRead = synchronized(lock) {
            check(!closed) { "The CFI operation lane is closed." }
            activeRead.also { activeRead = operation }
        }
        supersededRead?.cancel(CancellationException("Superseded by a newer CFI read."))
        operation.start()
        try {
            operation.await()
        } finally {
            synchronized(lock) {
                if (activeRead === operation) activeRead = null
            }
            if (!operation.isCompleted) operation.cancel()
        }
    }

    override fun close() {
        val removed = synchronized(lock) {
            if (closed) return
            closed = true
            SupersededOperations(activeNavigation, activeRead).also {
                activeNavigation = null
                activeRead = null
            }
        }
        removed.navigation?.cancel(CancellationException("The CFI operation lane was closed."))
        removed.read?.cancel(CancellationException("The CFI operation lane was closed."))
    }

    private data class SupersededOperations(val navigation: Deferred<*>?, val read: Deferred<*>?)
}
