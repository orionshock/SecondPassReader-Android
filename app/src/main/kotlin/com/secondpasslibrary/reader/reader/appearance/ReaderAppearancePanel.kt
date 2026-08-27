package com.secondpasslibrary.reader.reader.appearance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.secondpasslibrary.reader.reader.ui.ReaderChromeColors
import com.secondpasslibrary.reader.reader.ui.chromeColors

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun ReaderAppearancePanel(
    appearance: ReaderAppearance,
    onAppearanceChanged: (ReaderAppearance) -> Unit,
    onDismissRequest: () -> Unit
) {
    val colors = appearance.theme.chromeColors()
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.panelBackground,
        contentColor = colors.content
    ) {
        ReaderAppearancePanelContent(appearance, colors, onAppearanceChanged)
    }
}

@Composable
private fun ReaderAppearancePanelContent(
    appearance: ReaderAppearance,
    colors: ReaderChromeColors,
    onAppearanceChanged: (ReaderAppearance) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Reading appearance", style = MaterialTheme.typography.titleMedium)
        ReaderAppearanceControls(
            appearance,
            colors.content,
            colors.secondaryContent,
            colors.selectedBackground,
            onAppearanceChanged
        )
    }
}
