package com.secondpasslibrary.reader.reader.readium.cfi

import com.secondpasslibrary.reader.reader.cfi.EpubCfi
import com.secondpasslibrary.reader.reader.cfi.EpubCfiFailure
import com.secondpasslibrary.reader.reader.cfi.EpubCfiOutcome
import com.secondpasslibrary.reader.reader.cfi.EpubCfiTargetKind
import com.secondpasslibrary.reader.reader.cfi.EpubLayout
import com.secondpasslibrary.reader.reader.cfi.EpubPackageDocument
import com.secondpasslibrary.reader.reader.cfi.EpubSpineItem
import com.secondpasslibrary.reader.reader.cfi.normalizeEpubHref

internal data class ReadiumEpubPackageTarget(
    val spineIndex: Int,
    val itemrefId: String?,
    val idref: String,
    val resourceHref: String,
    val kind: EpubCfiTargetKind,
    val layout: EpubLayout
)

internal class ReadiumEpubPackageCfiMapper(
    private val packageDocument: EpubPackageDocument,
    readingOrderHrefs: List<String>,
    private val binding: ReadiumCfiNavigatorBinding
) {
    private val readingOrderHrefCounts = readingOrderHrefs
        .map(::normalizeEpubHref)
        .groupingBy { it }
        .eachCount()

    suspend fun resolve(cfi: EpubCfi): EpubCfiOutcome<ReadiumEpubPackageTarget> =
        withRuntime { navigator, runtime ->
            runtime.resolvePackage(navigator, cfi, packageDocument).mapPackageTarget()
        }

    suspend fun compose(resourceHref: String, contentCfi: EpubCfi): EpubCfiOutcome<EpubCfi> {
        val spineItem = verifiedSpineItem(resourceHref)
            ?: return EpubCfiOutcome.Failure(EpubCfiFailure.RESOURCE_NOT_IN_READING_ORDER)
        return withRuntime { navigator, runtime ->
            when (
                val packageResult = runtime.generatePackage(
                    navigator = navigator,
                    packageDocument = packageDocument,
                    spineIndex = spineItem.index,
                    idref = spineItem.idref,
                    itemrefId = spineItem.id
                )
            ) {
                is ReadiumCfiJavascriptResult.Failure -> packageResult.toOutcome()

                is ReadiumCfiJavascriptResult.Success -> {
                    val composed = runtime.composeFullCfi(
                        navigator = navigator,
                        packageCfi = packageResult.value,
                        contentCfi = contentCfi
                    )
                    when (composed) {
                        is ReadiumCfiJavascriptResult.Failure -> composed.toOutcome()

                        is ReadiumCfiJavascriptResult.Success -> {
                            runtime.resolvePackage(
                                navigator = navigator,
                                cfi = composed.value,
                                packageDocument = packageDocument
                            ).verifyComposedCfi(composed.value, spineItem)
                        }
                    }
                }
            }
        }
    }

    private fun ReadiumCfiJavascriptResult<ReadiumPackageTarget>.mapPackageTarget():
        EpubCfiOutcome<ReadiumEpubPackageTarget> =
        when (this) {
            is ReadiumCfiJavascriptResult.Failure -> toOutcome()
            is ReadiumCfiJavascriptResult.Success -> value.toVerifiedTarget()
        }

    private fun ReadiumPackageTarget.toVerifiedTarget(): EpubCfiOutcome<ReadiumEpubPackageTarget> {
        val spineItem = packageDocument.spine.getOrNull(spineIndex)
        val targetKind = when (kind) {
            "point" -> EpubCfiTargetKind.POINT
            "range" -> EpubCfiTargetKind.RANGE
            else -> null
        }
        return when {
            spineItem == null || spineItem.idref != idref || spineItem.id != itemrefId ->
                EpubCfiOutcome.Failure(EpubCfiFailure.PACKAGE_TARGET_NOT_FOUND)

            readingOrderHrefCounts[spineItem.resourceHref] != 1 ->
                EpubCfiOutcome.Failure(EpubCfiFailure.RESOURCE_NOT_IN_READING_ORDER)

            targetKind == null -> EpubCfiOutcome.Failure(EpubCfiFailure.INVALID_CFI)

            else -> EpubCfiOutcome.Success(
                ReadiumEpubPackageTarget(
                    spineIndex = spineItem.index,
                    itemrefId = spineItem.id,
                    idref = spineItem.idref,
                    resourceHref = spineItem.resourceHref,
                    kind = targetKind,
                    layout = spineItem.layout
                )
            )
        }
    }

    private fun ReadiumCfiJavascriptResult<ReadiumPackageTarget>.verifyComposedCfi(
        cfi: EpubCfi,
        expectedSpineItem: EpubSpineItem
    ): EpubCfiOutcome<EpubCfi> = when (this) {
        is ReadiumCfiJavascriptResult.Failure -> toOutcome()

        is ReadiumCfiJavascriptResult.Success -> if (
            value.spineIndex == expectedSpineItem.index &&
            value.idref == expectedSpineItem.idref &&
            value.itemrefId == expectedSpineItem.id
        ) {
            EpubCfiOutcome.Success(cfi)
        } else {
            EpubCfiOutcome.Failure(EpubCfiFailure.PACKAGE_TARGET_NOT_FOUND)
        }
    }

    private fun verifiedSpineItem(resourceHref: String): EpubSpineItem? {
        val normalized = runCatching { normalizeEpubHref(resourceHref) }.getOrNull()
        return normalized
            ?.takeIf { readingOrderHrefCounts[it] == 1 }
            ?.let(packageDocument::spineItemForHref)
    }

    private suspend fun <T> withRuntime(
        block: suspend (
            org.readium.r2.navigator.epub.EpubNavigatorFragment,
            ReadiumCfiJavascriptRuntime
        ) -> EpubCfiOutcome<T>
    ): EpubCfiOutcome<T> = binding.withNavigator(block)
        ?: EpubCfiOutcome.Failure(EpubCfiFailure.NAVIGATOR_UNAVAILABLE)
}

private fun <T> ReadiumCfiJavascriptResult.Failure.toOutcome(): EpubCfiOutcome<T> =
    EpubCfiOutcome.Failure(reason)
