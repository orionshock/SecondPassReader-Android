package com.secondpasslibrary.reader.reader.readium

import kotlin.time.Duration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/** Owns priority, bounds, and serialization for the one live navigator/DOM command lane. */
internal class ReadiumNavigatorOperationLane : AutoCloseable {
    private val lock = Any()
    private val mutex = Mutex()
    private var closed = false
    private var activeNavigation: Deferred<*>? = null
    private var activeRead: Deferred<*>? = null

    suspend fun <T> runNavigation(
        timeout: Duration,
        block: suspend () -> T
    ): ReadiumNavigatorCommandResult<T> = coroutineScope {
        require(timeout.isPositive()) { "Navigator command timeout must be positive." }
        val operation = async(start = CoroutineStart.LAZY) {
            mutex.withLock {
                withTimeoutOrNull(timeout) {
                    ReadiumNavigatorCommandResult.Completed(block())
                } ?: ReadiumNavigatorCommandResult.TimedOut
            }
        }
        val superseded = synchronized(lock) {
            check(!closed) { "The navigator operation lane is closed." }
            SupersededOperations(
                navigation = activeNavigation.also { activeNavigation = operation },
                read = activeRead.also { activeRead = null }
            )
        }
        superseded.navigation?.cancel(
            CancellationException("Superseded by a newer navigator command.")
        )
        superseded.read?.cancel(CancellationException("Superseded by navigator command."))
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
            check(!closed) { "The navigator operation lane is closed." }
            activeRead.also { activeRead = operation }
        }
        supersededRead?.cancel(CancellationException("Superseded by a newer navigator read."))
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
        removed.navigation?.cancel(CancellationException("The navigator operation lane closed."))
        removed.read?.cancel(CancellationException("The navigator operation lane closed."))
    }

    private data class SupersededOperations(val navigation: Deferred<*>?, val read: Deferred<*>?)
}

internal sealed interface ReadiumNavigatorCommandResult<out T> {
    data class Completed<T>(val value: T) : ReadiumNavigatorCommandResult<T>
    data object TimedOut : ReadiumNavigatorCommandResult<Nothing>
}
