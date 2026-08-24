package com.secondpasslibrary.reader.reader.readium.cfi

import com.secondpasslibrary.reader.reader.cfi.EpubCfi
import com.secondpasslibrary.reader.reader.cfi.EpubCfiFailure
import com.secondpasslibrary.reader.reader.cfi.EpubCfiNavigator
import com.secondpasslibrary.reader.reader.cfi.EpubCfiOutcome
import com.secondpasslibrary.reader.reader.cfi.EpubCfiResolution
import com.secondpasslibrary.reader.reader.cfi.EpubCfiSelection
import com.secondpasslibrary.reader.reader.cfi.EpubPackageDocument

internal class ReadiumEpubCfiNavigator(
    private val binding: ReadiumCfiNavigatorBinding,
    packageDocument: EpubPackageDocument,
    readingOrderHrefs: List<String>
) : EpubCfiNavigator,
    AutoCloseable {
    private val packageCfiMapper = ReadiumEpubPackageCfiMapper(
        packageDocument = packageDocument,
        readingOrderHrefs = readingOrderHrefs,
        binding = binding
    )

    override suspend fun goTo(cfi: EpubCfi): EpubCfiOutcome<Unit> = withRuntime()

    override suspend fun currentPosition(): EpubCfiOutcome<EpubCfi> {
        val captured = binding.withNavigator { navigator, runtime ->
            navigator.currentLocator.value.href.toString() to
                runtime.generateVisiblePosition(navigator)
        } ?: return EpubCfiOutcome.Failure(EpubCfiFailure.NAVIGATOR_UNAVAILABLE)
        return when (val contentCfi = captured.second) {
            is ReadiumCfiJavascriptResult.Failure ->
                EpubCfiOutcome.Failure(contentCfi.reason)

            is ReadiumCfiJavascriptResult.Success ->
                packageCfiMapper.compose(captured.first, contentCfi.value)
        }
    }

    override suspend fun currentSelection(): EpubCfiOutcome<EpubCfiSelection?> {
        val captured = binding.withNavigator { navigator, runtime ->
            navigator.currentLocator.value.href.toString() to runtime.generateSelection(navigator)
        }
        return if (captured == null) {
            EpubCfiOutcome.Failure(EpubCfiFailure.NAVIGATOR_UNAVAILABLE)
        } else {
            selectionOutcome(captured.first, captured.second)
        }
    }

    private suspend fun selectionOutcome(
        resourceHref: String,
        result: ReadiumCfiJavascriptResult<ReadiumContentSelection?>
    ): EpubCfiOutcome<EpubCfiSelection?> = when (result) {
        is ReadiumCfiJavascriptResult.Failure ->
            EpubCfiOutcome.Failure(result.reason)

        is ReadiumCfiJavascriptResult.Success -> {
            val selection = result.value
            if (selection == null) {
                EpubCfiOutcome.Success(null)
            } else {
                composeSelection(resourceHref, selection)
            }
        }
    }

    private suspend fun composeSelection(
        resourceHref: String,
        selection: ReadiumContentSelection
    ): EpubCfiOutcome<EpubCfiSelection> =
        when (val fullCfi = packageCfiMapper.compose(resourceHref, selection.contentCfi)) {
            is EpubCfiOutcome.Failure -> fullCfi

            is EpubCfiOutcome.Success -> EpubCfiOutcome.Success(
                EpubCfiSelection(
                    cfi = fullCfi.value,
                    selectedText = selection.selectedText,
                    prefix = selection.prefix,
                    suffix = selection.suffix
                )
            )
        }

    override suspend fun resolve(cfi: EpubCfi): EpubCfiOutcome<EpubCfiResolution> = withRuntime()

    internal suspend fun resolvePackage(cfi: EpubCfi): EpubCfiOutcome<ReadiumEpubPackageTarget> =
        packageCfiMapper.resolve(cfi)

    internal suspend fun compose(
        resourceHref: String,
        contentCfi: EpubCfi
    ): EpubCfiOutcome<EpubCfi> = packageCfiMapper.compose(resourceHref, contentCfi)

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
