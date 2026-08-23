package com.secondpasslibrary.reader.design.book

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class PreviewCoverLayoutTest {
    @Test
    fun `preview capacity uses available width without exceeding loaded data`() {
        assertEquals(1, overlappingPreviewCapacity(280.dp, 260.dp, 44.dp, 27.dp, 6))
        assertEquals(3, overlappingPreviewCapacity(360.dp, 260.dp, 44.dp, 27.dp, 6))
        assertEquals(6, overlappingPreviewCapacity(800.dp, 260.dp, 44.dp, 27.dp, 6))
        assertEquals(2, overlappingPreviewCapacity(800.dp, 260.dp, 44.dp, 27.dp, 2))
        assertEquals(0, overlappingPreviewCapacity(800.dp, 260.dp, 44.dp, 27.dp, 0))
    }
}
