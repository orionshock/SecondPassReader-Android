package com.secondpasslibrary.reader.reader.readium.cfi

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.readium.r2.navigator.epub.EpubNavigatorFragment

internal class ReadiumCfiNavigatorBinding(private val runtime: ReadiumCfiJavascriptRuntime) :
    AutoCloseable {
    private val lock = Any()
    private val generations = AtomicLong()
    private var closed = false
    private var current: Lease? = null

    fun bind(navigator: EpubNavigatorFragment) {
        val lease = synchronized(lock) {
            check(!closed) { "The CFI navigator binding is closed." }
            current?.scope?.cancel()
            Lease(
                generation = generations.incrementAndGet(),
                navigator = navigator,
                scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
            ).also { current = it }
        }
        lease.scope.launch {
            navigator.currentLocator
                .map { it.href }
                .distinctUntilChanged()
                .collectLatest { runtime.ensureInstalled(navigator) }
        }
    }

    fun unbind(navigator: EpubNavigatorFragment) {
        val removed = synchronized(lock) {
            current?.takeIf { it.navigator === navigator }?.also { current = null }
        }
        removed?.scope?.cancel()
    }

    suspend fun <T> withNavigator(
        block: suspend (EpubNavigatorFragment, ReadiumCfiJavascriptRuntime) -> T
    ): T? {
        val lease = synchronized(lock) { current } ?: return null
        return lease.scope.async { block(lease.navigator, runtime) }.await().also {
            checkCurrent(lease)
        }
    }

    override fun close() {
        val removed = synchronized(lock) {
            if (closed) return
            closed = true
            current.also { current = null }
        }
        removed?.scope?.cancel()
    }

    private fun checkCurrent(lease: Lease) {
        val isCurrent = synchronized(lock) {
            !closed && current?.generation == lease.generation
        }
        check(isCurrent) { "The CFI navigator binding changed during the operation." }
    }

    private data class Lease(
        val generation: Long,
        val navigator: EpubNavigatorFragment,
        val scope: CoroutineScope
    )
}
