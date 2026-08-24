package com.secondpasslibrary.reader.reader.readium.cfi

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Owns the one live navigator/DOM command lane. A newer command supersedes an older command, then
 * waits for its cancellation cleanup before touching the renderer.
 */
internal class ReadiumCfiOperationLane : AutoCloseable {
    private val lock = Any()
    private val mutex = Mutex()
    private var closed = false
    private var active: Deferred<*>? = null

    suspend fun <T> runLatest(block: suspend () -> T): T = coroutineScope {
        val operation = async(start = CoroutineStart.LAZY) {
            mutex.withLock { block() }
        }
        val superseded = synchronized(lock) {
            check(!closed) { "The CFI operation lane is closed." }
            active.also { active = operation }
        }
        superseded?.cancel(CancellationException("Superseded by a newer CFI operation."))
        operation.start()
        try {
            operation.await()
        } finally {
            synchronized(lock) {
                if (active === operation) active = null
            }
            if (!operation.isCompleted) operation.cancel()
        }
    }

    override fun close() {
        val removed = synchronized(lock) {
            if (closed) return
            closed = true
            active.also { active = null }
        }
        removed?.cancel(CancellationException("The CFI operation lane was closed."))
    }
}
