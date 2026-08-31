package com.secondpasslibrary.reader.app.shell

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.secondpasslibrary.reader.app.ReaderSyncOutcomeNotice
import com.secondpasslibrary.reader.app.ReaderSyncOutcomeNoticePresenter

@Composable
internal fun ReaderSyncOutcomeSnackbar(
    notice: ReaderSyncOutcomeNotice?,
    hostState: SnackbarHostState,
    onAcknowledged: (Long) -> Unit
) {
    LaunchedEffect(notice?.id) {
        val current = notice ?: return@LaunchedEffect
        hostState.showSnackbar(
            message = ReaderSyncOutcomeNoticePresenter.message(current),
            actionLabel = "Dismiss",
            duration = SnackbarDuration.Long
        )
        onAcknowledged(current.id)
    }
}
