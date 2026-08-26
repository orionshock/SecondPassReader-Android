package com.secondpasslibrary.reader.reader.annotations

import com.secondpasslibrary.reader.reader.cfi.EpubCfi
import com.secondpasslibrary.reader.reader.cfi.EpubCfiFailure
import com.secondpasslibrary.reader.reader.cfi.EpubCfiNavigator
import com.secondpasslibrary.reader.reader.cfi.EpubCfiOutcome
import com.secondpasslibrary.reader.reader.cfi.EpubCfiPosition
import com.secondpasslibrary.reader.reader.cfi.EpubCfiReadiness
import com.secondpasslibrary.reader.reader.cfi.EpubCfiResolution
import com.secondpasslibrary.reader.reader.cfi.EpubCfiSelection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderBookmarkPositionTest {
    @Test
    fun `capture waits for navigation and requests current durable position`() = runTest {
        val expected = EpubCfiPosition(EpubCfi(CFI), 4, 0.55)
        val navigator = RecordingNavigator(EpubCfiOutcome.Success(expected))

        assertEquals(EpubCfiOutcome.Success(expected), captureReaderBookmarkPosition(navigator))
        assertTrue(navigator.contextRequested)
    }

    @Test
    fun `transient CFI failure does not produce a bookmark position`() = runTest {
        val navigator = RecordingNavigator(
            EpubCfiOutcome.Failure(EpubCfiFailure.RESOURCE_CHANGED_DURING_OPERATION)
        )

        assertEquals(
            EpubCfiOutcome.Failure(EpubCfiFailure.RESOURCE_CHANGED_DURING_OPERATION),
            captureReaderBookmarkPosition(navigator)
        )
        assertTrue(navigator.contextRequested)

        navigator.readiness.value = EpubCfiReadiness.Failed(EpubCfiFailure.NAVIGATOR_UNAVAILABLE)
        navigator.contextRequested = false
        assertTrue(captureReaderBookmarkPosition(navigator) is EpubCfiOutcome.Failure)
        assertFalse(navigator.contextRequested)
    }

    private class RecordingNavigator(private val position: EpubCfiOutcome<EpubCfiPosition>) :
        EpubCfiNavigator {
        override val readiness = MutableStateFlow<EpubCfiReadiness>(EpubCfiReadiness.Available)
        var contextRequested = false

        override suspend fun currentPositionWithContext(): EpubCfiOutcome<EpubCfiPosition> {
            contextRequested = true
            return position
        }

        override suspend fun goTo(cfi: EpubCfi) = unused<Unit>()

        override suspend fun currentPosition() = unused<EpubCfi>()

        override suspend fun currentSelection() = unused<EpubCfiSelection?>()

        override suspend fun resolve(cfi: EpubCfi) = unused<EpubCfiResolution>()
    }
}

private fun <T> unused(): EpubCfiOutcome<T> =
    EpubCfiOutcome.Failure(EpubCfiFailure.NAVIGATOR_UNAVAILABLE)

private const val CFI = "epubcfi(/6/4!/4/2:0)"
