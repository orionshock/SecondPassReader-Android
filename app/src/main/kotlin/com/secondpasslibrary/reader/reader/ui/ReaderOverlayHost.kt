package com.secondpasslibrary.reader.reader.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import kotlinx.coroutines.launch

internal class ReaderOverlayHost internal constructor(
    val openTableOfContents: () -> Unit,
    val openAppearance: () -> Unit,
    val openAnnotations: () -> Unit
)

/** Owns Reader-local overlay exclusion and Back priority; it owns no Reader business state. */
@Composable
internal fun ReaderOverlayLayout(
    onExit: () -> Unit,
    transientOverlayVisible: Boolean = false,
    onDismissTransientOverlay: () -> Unit = {},
    tableOfContents: @Composable (dismiss: () -> Unit) -> Unit,
    appearance: @Composable (dismiss: () -> Unit) -> Unit,
    annotations: @Composable (dismiss: () -> Unit) -> Unit,
    content: @Composable (ReaderOverlayHost) -> Unit
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var panel by remember { mutableStateOf(ReaderOverlayPanel.NONE) }
    val dismissPanel = { panel = ReaderOverlayPanel.NONE }
    val dismissDrawer = {
        scope.launch { drawerState.close() }
        Unit
    }
    val dismissTransientOverlay = {
        if (transientOverlayVisible) {
            focusManager.clearFocus()
            keyboard?.hide()
            onDismissTransientOverlay()
        }
    }
    val actions = ReaderOverlayHost(
        openTableOfContents = {
            dismissTransientOverlay()
            panel = ReaderOverlayPanel.NONE
            scope.launch { drawerState.open() }
        },
        openAppearance = {
            dismissTransientOverlay()
            scope.launch {
                drawerState.close()
                panel = ReaderOverlayPanel.APPEARANCE
            }
        },
        openAnnotations = {
            dismissTransientOverlay()
            scope.launch {
                drawerState.close()
                panel = ReaderOverlayPanel.ANNOTATIONS
            }
        }
    )

    BackHandler {
        when {
            transientOverlayVisible -> dismissTransientOverlay()

            panel != ReaderOverlayPanel.NONE -> dismissPanel()

            drawerState.currentValue == DrawerValue.Open ||
                drawerState.targetValue == DrawerValue.Open -> dismissDrawer()

            else -> onExit()
        }
    }
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = false,
        drawerContent = { tableOfContents(dismissDrawer) }
    ) {
        Box(Modifier.fillMaxSize()) {
            content(actions)
            if (panel == ReaderOverlayPanel.APPEARANCE) appearance(dismissPanel)
            if (panel == ReaderOverlayPanel.ANNOTATIONS) annotations(dismissPanel)
        }
    }
}

private enum class ReaderOverlayPanel { NONE, APPEARANCE, ANNOTATIONS }
