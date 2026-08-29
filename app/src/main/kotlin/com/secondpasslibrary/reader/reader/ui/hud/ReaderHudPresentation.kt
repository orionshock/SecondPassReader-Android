package com.secondpasslibrary.reader.reader.ui.hud

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.secondpasslibrary.reader.reader.domain.ReaderEngine
import com.secondpasslibrary.reader.reader.domain.ReaderReadingStatus
import kotlinx.coroutines.flow.MutableStateFlow

internal data class ReaderHudPresentation(
    val visible: Boolean,
    val readingStatus: ReaderReadingStatus?,
    val reveal: () -> Unit
)

/** Binds one engine's renderer events to the Reader-owned HUD visibility controller. */
@Composable
internal fun rememberReaderHudPresentation(
    engine: ReaderEngine?,
    interactionSuppressed: Boolean
): ReaderHudPresentation {
    val scope = rememberCoroutineScope()
    val controller = remember(scope) { ReaderHudController(scope) }
    val visible by controller.visible.collectAsState()
    val readingStatus by remember(engine) {
        engine?.hudEvents?.readingStatus ?: EMPTY_READING_STATUS
    }.collectAsState()
    DisposableEffect(controller) { onDispose { controller.close() } }
    LaunchedEffect(engine, interactionSuppressed) {
        engine?.hudEvents?.publicationTaps()?.collect {
            if (!interactionSuppressed) controller.toggle()
        }
    }
    LaunchedEffect(engine) {
        engine?.viewportMovements?.settled()?.collect { controller.reveal() }
    }
    LaunchedEffect(interactionSuppressed) {
        controller.setIdleSuspended(interactionSuppressed)
    }
    return ReaderHudPresentation(visible, readingStatus, controller::reveal)
}

private val EMPTY_READING_STATUS = MutableStateFlow<ReaderReadingStatus?>(null)
