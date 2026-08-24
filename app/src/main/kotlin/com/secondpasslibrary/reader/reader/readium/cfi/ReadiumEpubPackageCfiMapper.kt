package com.secondpasslibrary.reader.reader.readium.cfi

import com.secondpasslibrary.reader.reader.cfi.EpubCfi
import com.secondpasslibrary.reader.reader.cfi.EpubCfiFailure
import com.secondpasslibrary.reader.reader.cfi.EpubCfiOutcome
import com.secondpasslibrary.reader.reader.cfi.EpubCfiTargetKind
import com.secondpasslibrary.reader.reader.cfi.EpubLayout
import com.secondpasslibrary.reader.reader.cfi.EpubPackageDocument
import com.secondpasslibrary.reader.reader.cfi.EpubSpineItem
import com.secondpasslibrary.reader.reader.cfi.normalizeEpubHref
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.mediatype.MediaType

internal data class ReadiumEpubPackageTarget(
    val spineIndex: Int,
    val itemrefId: String?,
    val idref: String,
    val resourceHref: String,
    val resourceUrl: Url,
    val mediaType: MediaType,
    val resourceLink: Link,
    val kind: EpubCfiTargetKind,
    val layout: EpubLayout
)

internal class ReadiumEpubPackageCfiMapper(
    private val packageDocument: EpubPackageDocument,
    readingOrder: List<Link>,
    private val binding: ReadiumCfiNavigatorBinding
) {
    private val readingOrderByHref = readingOrder
        .mapNotNull { link ->
            val url = link.url()
            val mediaType = link.mediaType ?: return@mapNotNull null
            runCatching {
                normalizeEpubHref(link.href.toString()) to
                    ReadiumReadingOrderResource(link, url, mediaType)
            }.getOrNull()
        }
        .groupBy({ it.first }, { it.second })

    suspend fun resolve(cfi: EpubCfi): EpubCfiOutcome<ReadiumEpubPackageTarget> =
        withRuntime { navigator, runtime ->
            runtime.resolvePackage(navigator, cfi, packageDocument).mapPackageTarget()
        }

    suspend fun compose(resourceHref: String, contentCfi: EpubCfi): EpubCfiOutcome<EpubCfi> =
        when (val spineItem = verifiedSpineItem(resourceHref)) {
            null -> EpubCfiOutcome.Failure(EpubCfiFailure.RESOURCE_NOT_IN_READING_ORDER)

            else -> if (spineItem.layout == EpubLayout.FIXED) {
                EpubCfiOutcome.Failure(EpubCfiFailure.UNSUPPORTED_FIXED_LAYOUT)
            } else {
                composeReflowable(spineItem, contentCfi)
            }
        }

    private suspend fun composeReflowable(
        spineItem: EpubSpineItem,
        contentCfi: EpubCfi
    ): EpubCfiOutcome<EpubCfi> = withRuntime { navigator, runtime ->
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

            uniqueReadingOrderResource(spineItem) == null ->
                EpubCfiOutcome.Failure(EpubCfiFailure.RESOURCE_NOT_IN_READING_ORDER)

            targetKind == null -> EpubCfiOutcome.Failure(EpubCfiFailure.INVALID_CFI)

            else -> {
                val readingOrderResource = checkNotNull(uniqueReadingOrderResource(spineItem))
                EpubCfiOutcome.Success(
                    ReadiumEpubPackageTarget(
                        spineIndex = spineItem.index,
                        itemrefId = spineItem.id,
                        idref = spineItem.idref,
                        resourceHref = spineItem.resourceHref,
                        resourceUrl = readingOrderResource.url,
                        mediaType = readingOrderResource.mediaType,
                        resourceLink = readingOrderResource.link,
                        kind = targetKind,
                        layout = spineItem.layout
                    )
                )
            }
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
            ?.let(packageDocument::spineItemForHref)
            ?.takeIf { uniqueReadingOrderResource(it) != null }
    }

    private fun uniqueReadingOrderResource(spineItem: EpubSpineItem): ReadiumReadingOrderResource? =
        readingOrderByHref[spineItem.resourceHref]
            ?.singleOrNull()
            ?.takeIf { resource -> resource.mediaType.matches(spineItem.mediaType) }

    private suspend fun <T> withRuntime(
        block: suspend (
            org.readium.r2.navigator.epub.EpubNavigatorFragment,
            ReadiumCfiJavascriptRuntime
        ) -> EpubCfiOutcome<T>
    ): EpubCfiOutcome<T> = binding.withNavigator(block)
        ?: binding.unavailableOutcome()
}

private data class ReadiumReadingOrderResource(
    val link: Link,
    val url: Url,
    val mediaType: MediaType
)

private fun <T> ReadiumCfiJavascriptResult.Failure.toOutcome(): EpubCfiOutcome<T> =
    EpubCfiOutcome.Failure(reason)
