@file:Suppress("MagicNumber")

package com.secondpasslibrary.reader.reader

import androidx.compose.ui.graphics.Color
import com.secondpasslibrary.reader.reader.domain.ReaderTheme

internal data class ReaderChromeColors(
    val background: Color,
    val content: Color,
    val secondaryContent: Color,
    val panelBackground: Color,
    val selectedBackground: Color
)

internal fun ReaderTheme.chromeColors(): ReaderChromeColors = when (this) {
    ReaderTheme.LIGHT -> ReaderChromeColors(
        background = Color(0xFFF5F2EA),
        content = Color(0xFF242321),
        secondaryContent = Color(0xFF65615A),
        panelBackground = Color(0xFFFCF9F2),
        selectedBackground = Color(0xFFDCE6F4)
    )

    ReaderTheme.DARK -> ReaderChromeColors(
        background = Color(0xFF15151B),
        content = Color(0xFFE8E6EC),
        secondaryContent = Color(0xFFA8A5AF),
        panelBackground = Color(0xFF1B1B22),
        selectedBackground = Color(0xFF313847)
    )

    ReaderTheme.SEPIA -> ReaderChromeColors(
        background = Color(0xFFE9DFC7),
        content = Color(0xFF3B3226),
        secondaryContent = Color(0xFF746754),
        panelBackground = Color(0xFFF2E8D2),
        selectedBackground = Color(0xFFD8C49E)
    )
}
