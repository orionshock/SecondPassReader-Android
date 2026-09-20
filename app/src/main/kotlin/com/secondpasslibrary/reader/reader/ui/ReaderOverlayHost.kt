package com.secondpasslibrary.reader.reader.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.clearAndSetSemantics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class ReaderOverlayHost internal constructor(
    val openTableOfContents: () -> Unit,
    val openAppearance: () -> Unit,
    val openAnnotations: () -> Unit,
    val openSearch: () -> Unit
)

/** Owns Reader-local overlay exclusion and Back priority; it owns no Reader business state. */
@Composable
// Owns mutual exclusion and Back priority for Reader panels.
@Suppress("CognitiveComplexMethod", "LongMethod")
internal fun ReaderOverlayLayout(
    onExit: () -> Unit,
    publicationKey: Any? = null,
    drawerScrimColor: Color,
    transientOverlayVisible: Boolean = false,
    onDismissTransientOverlay: () -> Unit,
    onOverlayVisibilityChanged: (Boolean) -> Unit,
    tableOfContents: @Composable (dismiss: () -> Unit) -> Unit,
    appearance: @Composable (dismiss: () -> Unit) -> Unit,
    annotations: @Composable (dismiss: () -> Unit) -> Unit,
    search: @Composable (dismiss: () -> Unit) -> Unit = {},
    onSearchOpened: () -> Unit = {},
    onSearchClosed: () -> Unit = {},
    content: @Composable (ReaderOverlayHost) -> Unit
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var panel by remember { mutableStateOf(ReaderOverlayPanel.NONE) }
    val dismissPanel = {
        if (panel == ReaderOverlayPanel.SEARCH) onSearchClosed()
        panel = ReaderOverlayPanel.NONE
    }
    LaunchedEffect(publicationKey) {
        if (panel == ReaderOverlayPanel.SEARCH) dismissPanel()
    }
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
    val actions = readerOverlayActions(
        scope,
        drawerState,
        dismissTransientOverlay,
        setPanel = {
            if (panel == ReaderOverlayPanel.SEARCH &&
                it != ReaderOverlayPanel.SEARCH
            ) {
                onSearchClosed()
            }
            panel = it
            if (it == ReaderOverlayPanel.SEARCH) onSearchOpened()
        }
    )
    val drawerVisible = drawerState.currentValue == DrawerValue.Open ||
        drawerState.targetValue == DrawerValue.Open
    ReaderOverlayVisibilityReporter(
        transientOverlayVisible || panel != ReaderOverlayPanel.NONE || drawerVisible,
        onOverlayVisibilityChanged
    )
    ReaderOverlayBackHandler(
        transientOverlayVisible,
        panel != ReaderOverlayPanel.NONE,
        drawerVisible,
        dismissTransientOverlay,
        dismissPanel,
        dismissDrawer,
        onExit
    )
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
        scrimColor = drawerScrimColor,
        drawerContent = { tableOfContents(dismissDrawer) }
    ) {
        Box(Modifier.fillMaxSize()) {
            ReaderContentBehindPanel(panel, actions, content)
            if (panel == ReaderOverlayPanel.APPEARANCE) appearance(dismissPanel)
            if (panel == ReaderOverlayPanel.ANNOTATIONS) annotations(dismissPanel)
            if (panel == ReaderOverlayPanel.SEARCH) {
                Box(Modifier.align(Alignment.TopEnd)) { search(dismissPanel) }
            }
        }
    }
}

@Composable
private fun ReaderContentBehindPanel(
    panel: ReaderOverlayPanel,
    actions: ReaderOverlayHost,
    content: @Composable (ReaderOverlayHost) -> Unit
) {
    val accessibilityModifier =
        if (panel == ReaderOverlayPanel.NONE) Modifier else Modifier.clearAndSetSemantics { }
    Box(Modifier.fillMaxSize().then(accessibilityModifier)) { content(actions) }
}

private fun readerOverlayActions(
    scope: CoroutineScope,
    drawerState: DrawerState,
    dismissTransient: () -> Unit,
    setPanel: (ReaderOverlayPanel) -> Unit
) = ReaderOverlayHost(
    openTableOfContents = {
        dismissTransient()
        setPanel(ReaderOverlayPanel.NONE)
        scope.launch { drawerState.open() }
    },
    openAppearance = {
        dismissTransient()
        scope.launch {
            drawerState.close()
            setPanel(ReaderOverlayPanel.APPEARANCE)
        }
    },
    openAnnotations = {
        dismissTransient()
        scope.launch {
            drawerState.close()
            setPanel(ReaderOverlayPanel.ANNOTATIONS)
        }
    },
    openSearch = {
        dismissTransient()
        scope.launch {
            drawerState.close()
            setPanel(ReaderOverlayPanel.SEARCH)
        }
    }
)

@Composable
private fun ReaderOverlayVisibilityReporter(visible: Boolean, report: (Boolean) -> Unit) {
    LaunchedEffect(visible) { report(visible) }
}

@Composable
private fun ReaderOverlayBackHandler(
    transientVisible: Boolean,
    panelVisible: Boolean,
    drawerVisible: Boolean,
    dismissTransient: () -> Unit,
    dismissPanel: () -> Unit,
    dismissDrawer: () -> Unit,
    exit: () -> Unit
) {
    BackHandler {
        when {
            transientVisible -> dismissTransient()
            panelVisible -> dismissPanel()
            drawerVisible -> dismissDrawer()
            else -> exit()
        }
    }
}

private enum class ReaderOverlayPanel { NONE, APPEARANCE, ANNOTATIONS, SEARCH }
