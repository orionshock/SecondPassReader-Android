package com.secondpasslibrary.reader.reader.cfi

import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class EpubCfiNavigatorTest {
    @Test
    fun `readiness wait publishes the terminal renderer-neutral failure`() = runTest {
        val readiness = MutableStateFlow<EpubCfiReadiness>(
            EpubCfiReadiness.PreparingDocument
        )
        val navigator = ReadinessOnlyCfiNavigator(readiness)
        val waiting = async { navigator.awaitNavigationAvailable() }

        readiness.value = EpubCfiReadiness.Failed(
            EpubCfiFailure.UNSUPPORTED_FIXED_LAYOUT
        )

        assertEquals(
            EpubCfiOutcome.Failure(EpubCfiFailure.UNSUPPORTED_FIXED_LAYOUT),
            waiting.await()
        )
    }
}

private class ReadinessOnlyCfiNavigator(
    override val readiness: MutableStateFlow<EpubCfiReadiness>
) : EpubCfiNavigator {
    override suspend fun goTo(cfi: EpubCfi): EpubCfiOutcome<Unit> = unused()

    override suspend fun currentPosition(): EpubCfiOutcome<EpubCfi> = unused()

    override suspend fun currentSelection(): EpubCfiOutcome<EpubCfiSelection?> = unused()

    override suspend fun resolve(cfi: EpubCfi): EpubCfiOutcome<EpubCfiResolution> = unused()
}

private fun <T> unused(): T = error("Only readiness is exercised by this test.")
