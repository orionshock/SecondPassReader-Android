package com.secondpasslibrary.reader.reader.lifecycle

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderActivityRestorationBootstrapTest {
    @Test
    fun `preparation and completion surround activity restoration`() {
        val events = mutableListOf<String>()

        runReaderActivityRestore(
            prepare = { events += "prepare" },
            restore = { events += "super-on-create" },
            complete = { events += "complete" }
        )

        assertEquals(listOf("prepare", "super-on-create", "complete"), events)
    }
}
