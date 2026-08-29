package com.secondpasslibrary.reader.reader.ui.hud

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal const val READER_HUD_IDLE_TIMEOUT_MILLIS = 3_000L

/** Owns the Reader HUD's one visibility state and bounded idle timer. */
internal class ReaderHudController(
    private val scope: CoroutineScope,
    private val idleTimeoutMillis: Long = READER_HUD_IDLE_TIMEOUT_MILLIS
) : AutoCloseable {
    private val mutableVisible = MutableStateFlow(true)
    private var hideJob: Job? = null
    private var idleSuspended = false

    val visible = mutableVisible.asStateFlow()

    init {
        scheduleHide()
    }

    fun toggle() {
        if (idleSuspended) return
        if (mutableVisible.value) {
            hideJob?.cancel()
            mutableVisible.value = false
        } else {
            reveal()
        }
    }

    fun reveal() {
        mutableVisible.value = true
        scheduleHide()
    }

    fun setIdleSuspended(suspended: Boolean) {
        if (idleSuspended == suspended) return
        idleSuspended = suspended
        if (suspended) {
            hideJob?.cancel()
            mutableVisible.value = true
        } else {
            reveal()
        }
    }

    private fun scheduleHide() {
        hideJob?.cancel()
        if (idleSuspended) return
        hideJob = scope.launch {
            delay(idleTimeoutMillis)
            mutableVisible.value = false
        }
    }

    override fun close() {
        hideJob?.cancel()
        hideJob = null
    }
}
