package com.secondpasslibrary.reader.reader.readium.cfi

import com.secondpasslibrary.reader.reader.cfi.EpubCfi
import com.secondpasslibrary.reader.reader.cfi.EpubCfiFailure
import com.secondpasslibrary.reader.reader.cfi.EpubCfiNavigator
import com.secondpasslibrary.reader.reader.cfi.EpubCfiOutcome
import com.secondpasslibrary.reader.reader.cfi.EpubCfiResolution
import com.secondpasslibrary.reader.reader.cfi.EpubCfiSelection

internal class ReadiumEpubCfiNavigator(private val binding: ReadiumCfiNavigatorBinding) :
    EpubCfiNavigator,
    AutoCloseable {
    override suspend fun goTo(cfi: EpubCfi): EpubCfiOutcome<Unit> = withRuntime()

    override suspend fun currentPosition(): EpubCfiOutcome<EpubCfi> = withRuntime()

    override suspend fun currentSelection(): EpubCfiOutcome<EpubCfiSelection?> = withRuntime()

    override suspend fun resolve(cfi: EpubCfi): EpubCfiOutcome<EpubCfiResolution> = withRuntime()

    override fun close() = binding.close()

    private suspend fun <T> withRuntime(): EpubCfiOutcome<T> {
        val result = binding.withNavigator { navigator, runtime ->
            if (runtime.ensureInstalled(navigator)) {
                EpubCfiOutcome.Failure(EpubCfiFailure.UNSUPPORTED_CFI_FEATURE)
            } else {
                EpubCfiOutcome.Failure(EpubCfiFailure.JAVASCRIPT_RUNTIME_UNAVAILABLE)
            }
        }
        return result ?: EpubCfiOutcome.Failure(EpubCfiFailure.NAVIGATOR_UNAVAILABLE)
    }
}
