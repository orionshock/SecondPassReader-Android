package com.secondpasslibrary.reader.reader.annotations

import com.secondpasslibrary.reader.reader.cfi.EpubCfi
import com.secondpasslibrary.reader.reader.cfi.EpubCfiFailure
import com.secondpasslibrary.reader.reader.cfi.EpubCfiNavigator
import com.secondpasslibrary.reader.reader.cfi.EpubCfiOutcome
import com.secondpasslibrary.reader.reader.cfi.EpubCfiReadiness
import com.secondpasslibrary.reader.reader.cfi.EpubCfiResolution
import com.secondpasslibrary.reader.reader.cfi.EpubCfiSelection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderAnnotationNavigationTest {
    @Test
    fun `exact canonical CFI is passed to navigation`() = runTest {
        val navigator = FakeNavigator()

        val result = navigateToReaderAnnotation(annotation(CFI), navigator)

        assertEquals(ReaderAnnotationNavigationResult.NAVIGATED, result)
        assertEquals(listOf(EpubCfi(CFI)), navigator.destinations)
    }

    @Test
    fun `malformed and unresolved CFIs fail without approximate fallback`() = runTest {
        val navigator = FakeNavigator()

        assertEquals(
            ReaderAnnotationNavigationResult.INVALID_CFI,
            navigateToReaderAnnotation(annotation(" "), navigator)
        )
        navigator.goToResult = EpubCfiOutcome.Failure(EpubCfiFailure.DOM_TARGET_NOT_FOUND)
        assertEquals(
            ReaderAnnotationNavigationResult.UNAVAILABLE,
            navigateToReaderAnnotation(annotation(CFI), navigator)
        )
        assertEquals(listOf(EpubCfi(CFI)), navigator.destinations)
    }

    private fun annotation(cfi: String) = ReaderAnnotation.Bookmark(
        id = "annotation",
        cfi = cfi,
        locationLabel = null,
        updatedAt = "2026-08-24T13:00:00Z"
    )

    private class FakeNavigator : EpubCfiNavigator {
        override val readiness = MutableStateFlow<EpubCfiReadiness>(EpubCfiReadiness.Available)
        val destinations = mutableListOf<EpubCfi>()
        var goToResult: EpubCfiOutcome<Unit> = EpubCfiOutcome.Success(Unit)

        override suspend fun goTo(cfi: EpubCfi): EpubCfiOutcome<Unit> {
            destinations += cfi
            return goToResult
        }

        override suspend fun currentPosition() = unavailable<EpubCfi>()
        override suspend fun currentSelection() = unavailable<EpubCfiSelection?>()
        override suspend fun resolve(cfi: EpubCfi) = unavailable<EpubCfiResolution>()
    }
}

private fun <T> unavailable(): EpubCfiOutcome<T> =
    EpubCfiOutcome.Failure(EpubCfiFailure.NAVIGATOR_UNAVAILABLE)

private const val CFI = "epubcfi(/6/2!/4/2:3)"
