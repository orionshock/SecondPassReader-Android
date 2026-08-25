package com.secondpasslibrary.reader.reader

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
internal fun ReaderScreen(state: ReaderState, onBack: () -> Unit, onRetry: () -> Unit) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val ready = state as? ReaderState.Ready
    BackHandler {
        val drawerOpen = drawerState.currentValue == DrawerValue.Open ||
            drawerState.targetValue == DrawerValue.Open
        if (drawerOpen) scope.launch { drawerState.close() } else onBack()
    }
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = false,
        drawerContent = {
            ReaderTocDrawer(
                bookTitle = ready?.title ?: "Reader",
                entries = ready?.engine?.tableOfContents?.entries.orEmpty(),
                onEntrySelected = { target ->
                    scope.launch {
                        drawerState.close()
                        ready?.engine?.tableOfContents?.goTo(target)
                    }
                },
                onReturnToBook = {
                    scope.launch {
                        drawerState.close()
                        onBack()
                    }
                }
            )
        }
    ) {
        Box(Modifier.fillMaxSize()) {
            when (state) {
                ReaderState.Resolving -> ReaderLoading("Preparing book…")

                ReaderState.Downloading -> ReaderLoading("Downloading book…")

                ReaderState.Opening -> ReaderLoading("Opening EPUB…")

                is ReaderState.Ready -> Box(Modifier.fillMaxSize()) {
                    state.engine.viewport.Content(Modifier.fillMaxSize())
                    ReaderCfiProbe(state.engine, Modifier.align(Alignment.TopEnd))
                }

                is ReaderState.Failure -> ReaderFailureContent(state.kind, onBack, onRetry)
            }
            ReaderChrome(
                title = ready?.title ?: "Reader",
                onNavigationMenuRequested = { scope.launch { drawerState.open() } }
            )
        }
    }
}

@Composable
private fun ReaderLoading(label: String) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Text(label, Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun ReaderFailureContent(kind: ReaderFailure, onBack: () -> Unit, onRetry: () -> Unit) {
    val message = when (kind) {
        ReaderFailure.DOWNLOAD -> "Couldn’t download this book."
        ReaderFailure.OPEN -> "Couldn’t open this EPUB."
        ReaderFailure.NO_EPUB -> "This book does not have an EPUB file."
        ReaderFailure.SESSION -> "Couldn't prepare this reading session."
    }
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(message)
        if (kind != ReaderFailure.NO_EPUB) TextButton(onClick = onRetry) { Text("Retry") }
        TextButton(onClick = onBack) { Text("Back") }
    }
}
