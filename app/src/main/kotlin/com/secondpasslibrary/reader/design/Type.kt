package com.secondpasslibrary.reader.design

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

internal val SecondPassTypography =
    Typography(
        displayMedium =
            TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontSize = 44.sp,
                lineHeight = 48.sp
            ),
        bodyLarge =
            TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontSize = 16.sp,
                lineHeight = 22.sp
            )
    )
