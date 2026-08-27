@file:Suppress("MagicNumber")

package com.secondpasslibrary.reader.reader.appearance

import androidx.compose.ui.graphics.Color

/** Small presentation palette shared by the publication and Reader-owned surfaces. */
internal data class ReaderPalette(
    val publicationBackground: Color,
    val publicationForeground: Color,
    val primaryForeground: Color,
    val secondaryForeground: Color,
    val floatingSurface: Color,
    val panelSurface: Color,
    val border: Color,
    val scrim: Color,
    val selectedSurface: Color
)

internal fun ReaderTheme.readerPalette(): ReaderPalette = when (this) {
    ReaderTheme.LIGHT -> ReaderPalette(
        publicationBackground = Color(0xFFF7F4EC),
        publicationForeground = Color(0xFF242321),
        primaryForeground = Color(0xFF242321),
        secondaryForeground = Color(0xFF65615A),
        floatingSurface = Color(0xFFFCF9F2),
        panelSurface = Color(0xFFFCF9F2),
        border = Color(0x33242321),
        scrim = Color(0x5C000000),
        selectedSurface = Color(0xFFDCE6F4)
    )

    ReaderTheme.DARK -> ReaderPalette(
        publicationBackground = Color(0xFF15151B),
        publicationForeground = Color(0xFFE8E6EC),
        primaryForeground = Color(0xFFE8E6EC),
        secondaryForeground = Color(0xFFA8A5AF),
        floatingSurface = Color(0xFF23232B),
        panelSurface = Color(0xFF1B1B22),
        border = Color(0x40E8E6EC),
        scrim = Color(0x85000000),
        selectedSurface = Color(0xFF313847)
    )

    ReaderTheme.SEPIA -> ReaderPalette(
        publicationBackground = Color(0xFFE9DFC7),
        publicationForeground = Color(0xFF302A21),
        primaryForeground = Color(0xFF3B3226),
        secondaryForeground = Color(0xFF6B5E4C),
        floatingSurface = Color(0xFFF2E8D2),
        panelSurface = Color(0xFFF4EAD4),
        border = Color(0x403B3226),
        scrim = Color(0x5C2B241B),
        selectedSurface = Color(0xFFD8C49E)
    )
}
