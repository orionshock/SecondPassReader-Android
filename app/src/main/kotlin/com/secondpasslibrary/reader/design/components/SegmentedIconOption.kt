package com.secondpasslibrary.reader.design.components

import com.secondpasslibrary.reader.design.icons.AppIcon

internal data class SegmentedIconOption<T>(
    val value: T,
    val icon: AppIcon,
    val contentDescription: String
)
