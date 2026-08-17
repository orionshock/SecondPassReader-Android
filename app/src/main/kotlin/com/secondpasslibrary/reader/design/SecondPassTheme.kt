package com.secondpasslibrary.reader.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SecondPassDarkColors =
    darkColorScheme(
        primary = Color(0xFF7CA8E8),
        onPrimary = Color(0xFF071A31),
        tertiary = Color(0xFF5FBE7B),
        onTertiary = Color(0xFF051F0D),
        background = Color(0xFF0D0F12),
        onBackground = Color(0xFFF0F2F5),
        surface = Color(0xFF0D0F12),
        onSurface = Color(0xFFF0F2F5),
        surfaceContainer = Color(0xFF16191E),
        onSurfaceVariant = Color(0xFFA9B0BA),
        outline = Color(0xFF59616D),
        outlineVariant = Color(0xFF2C323A)
    )

@Composable
fun SecondPassTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SecondPassDarkColors,
        typography = SecondPassTypography,
        content = content
    )
}
