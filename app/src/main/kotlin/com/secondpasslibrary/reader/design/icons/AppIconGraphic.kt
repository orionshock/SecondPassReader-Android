package com.secondpasslibrary.reader.design.icons

import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource

@Composable
fun AppIconGraphic(
    icon: AppIcon,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    val symbol = MaterialSymbolMapper.resolve(icon)
    Icon(
        painter = painterResource(symbol.drawableResource),
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint
    )
}
