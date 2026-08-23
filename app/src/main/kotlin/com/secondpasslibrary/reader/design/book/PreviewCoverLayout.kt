package com.secondpasslibrary.reader.design.book

import androidx.compose.ui.unit.Dp

internal fun overlappingPreviewCapacity(
    containerWidth: Dp,
    reservedContentWidth: Dp,
    coverWidth: Dp,
    coverStep: Dp,
    availableCount: Int
): Int {
    if (availableCount <= 0) return 0
    val previewWidth = (containerWidth - reservedContentWidth).coerceAtLeast(coverWidth)
    val additionalCovers = ((previewWidth - coverWidth) / coverStep).toInt()
    return (additionalCovers + 1).coerceIn(1, availableCount)
}

internal fun separatedPreviewCapacity(
    containerWidth: Dp,
    reservedContentWidth: Dp,
    coverWidth: Dp,
    spacing: Dp,
    availableCount: Int
): Int {
    if (availableCount <= 0) return 0
    val previewWidth = (containerWidth - reservedContentWidth).coerceAtLeast(coverWidth)
    val capacity = ((previewWidth + spacing) / (coverWidth + spacing)).toInt()
    return capacity.coerceIn(1, availableCount)
}
