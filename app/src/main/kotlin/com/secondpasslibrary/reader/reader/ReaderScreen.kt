package com.secondpasslibrary.reader.reader

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.secondpasslibrary.reader.design.components.AppBarNavigation
import com.secondpasslibrary.reader.design.components.AppBarPresentation
import com.secondpasslibrary.reader.design.components.ContextualAppBar

@Composable
internal fun ReaderScreen(state: ReaderState, onBack: () -> Unit, onRetry: () -> Unit) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize()) {
        ContextualAppBar(
            AppBarPresentation(
                navigation = AppBarNavigation.BACK,
                title = (state as? ReaderState.Ready)?.title ?: "Reader"
            ),
            onNavigation = onBack
        )
        when (state) {
            ReaderState.Resolving -> ReaderLoading("Preparing book…")
            ReaderState.Downloading -> ReaderLoading("Downloading book…")
            ReaderState.Opening -> ReaderLoading("Opening EPUB…")
            is ReaderState.Ready -> ReaderNavigatorHost(state.publication, Modifier.weight(1f))
            is ReaderState.Failure -> ReaderFailureContent(state.kind, onBack, onRetry)
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
