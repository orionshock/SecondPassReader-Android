package com.secondpasslibrary.reader.reader.readium.cfi

import android.graphics.RectF
import com.secondpasslibrary.reader.reader.cfi.EpubCfi
import com.secondpasslibrary.reader.reader.cfi.EpubCfiFailure
import com.secondpasslibrary.reader.reader.cfi.EpubCfiNavigator
import com.secondpasslibrary.reader.reader.cfi.EpubCfiOutcome
import com.secondpasslibrary.reader.reader.cfi.EpubCfiPosition
import com.secondpasslibrary.reader.reader.cfi.EpubCfiReadiness
import com.secondpasslibrary.reader.reader.cfi.EpubCfiResolution
import com.secondpasslibrary.reader.reader.cfi.EpubCfiSelection
import com.secondpasslibrary.reader.reader.cfi.EpubCfiTargetKind
import com.secondpasslibrary.reader.reader.cfi.EpubLayout
import com.secondpasslibrary.reader.reader.cfi.EpubPackageDocument
import com.secondpasslibrary.reader.reader.cfi.EpubSelectionBounds
import kotlinx.coroutines.flow.StateFlow
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.publication.Link

@Suppress("TooManyFunctions") // One cohesive serialized CFI/navigation boundary.
internal class ReadiumEpubCfiNavigator(
    private val binding: ReadiumCfiNavigatorBinding,
    private val packageDocument: EpubPackageDocument,
    readingOrder: List<Link>
) : EpubCfiNavigator,
    AutoCloseable {
    private val operations = ReadiumCfiOperationLane()
    private val packageCfiMapper = ReadiumEpubPackageCfiMapper(
        packageDocument = packageDocument,
        readingOrder = readingOrder,
        binding = binding
    )
    private val incomingNavigation = ReadiumCfiIncomingNavigation(
        binding = binding,
        packageDocument = packageDocument,
        packageCfiMapper = packageCfiMapper
    )

    override val readiness: StateFlow<EpubCfiReadiness> = binding.readiness

    override suspend fun goTo(cfi: EpubCfi): EpubCfiOutcome<Unit> =
        operations.runLatest { incomingNavigation.goTo(cfi) }

    override suspend fun currentPositionWithContext(): EpubCfiOutcome<EpubCfiPosition> =
        operations.runLatest {
            val captured = binding.withNavigator { navigator, runtime ->
                val before = binding.resourceIdentity(navigator)
                val value = runtime.generateVisiblePosition(navigator)
                coherentResourceCapture(
                    before,
                    binding.resourceIdentity(navigator),
                    PositionCapture(
                        value,
                        navigator.currentLocator.value.locations.totalProgression
                    )
                )
            }
            captured?.toPositionOutcome(packageDocument, packageCfiMapper)
                ?: binding.unavailableOutcome()
        }

    override suspend fun currentSelection(): EpubCfiOutcome<EpubCfiSelection?> =
        operations.runLatest {
            val captured = binding.withNavigator { navigator, runtime ->
                val before = binding.resourceIdentity(navigator)
                val locator = navigator.currentLocator.value
                val bounds = navigator.currentSelection()?.rect?.toSelectionBounds()
                val value = runtime.generateSelection(navigator)
                coherentResourceCapture(
                    before,
                    binding.resourceIdentity(navigator),
                    SelectionCapture(value, locator.locations.totalProgression, bounds)
                )
            }
            when (captured) {
                null -> binding.unavailableOutcome()

                ReadiumCfiResourceCapture.Changed -> resourceChanged()

                is ReadiumCfiResourceCapture.Stable ->
                    selectionOutcome(captured.identity.href, captured.value)
            }
        }

    private suspend fun selectionOutcome(
        resourceHref: String,
        capture: SelectionCapture
    ): EpubCfiOutcome<EpubCfiSelection?> = when (val result = capture.result) {
        is ReadiumCfiJavascriptResult.Failure ->
            EpubCfiOutcome.Failure(result.reason)

        is ReadiumCfiJavascriptResult.Success -> {
            val selection = result.value
            if (selection == null) {
                EpubCfiOutcome.Success(null)
            } else {
                composeSelection(
                    resourceHref,
                    selection,
                    capture.totalProgression,
                    capture.selectionBounds
                )
            }
        }
    }

    private suspend fun composeSelection(
        resourceHref: String,
        selection: ReadiumContentSelection,
        totalProgression: Double?,
        selectionBounds: EpubSelectionBounds?
    ): EpubCfiOutcome<EpubCfiSelection> {
        val chapterOrdinal = packageDocument.spineItemForHref(resourceHref)?.index?.plus(1)
            ?: return EpubCfiOutcome.Failure(EpubCfiFailure.RESOURCE_NOT_IN_READING_ORDER)
        return when (val fullCfi = packageCfiMapper.compose(resourceHref, selection.contentCfi)) {
            is EpubCfiOutcome.Failure -> fullCfi

            is EpubCfiOutcome.Success -> EpubCfiOutcome.Success(
                EpubCfiSelection(
                    cfi = fullCfi.value,
                    selectedText = selection.selectedText,
                    prefix = selection.prefix,
                    suffix = selection.suffix,
                    chapterOrdinal = chapterOrdinal,
                    totalProgression = totalProgression,
                    bounds = selectionBounds
                )
            )
        }
    }

    private data class SelectionCapture(
        val result: ReadiumCfiJavascriptResult<ReadiumContentSelection?>,
        val totalProgression: Double?,
        val selectionBounds: EpubSelectionBounds?
    )

    override suspend fun resolve(cfi: EpubCfi): EpubCfiOutcome<EpubCfiResolution> =
        operations.runLatest {
            when (val resolved = resolveForNavigation(cfi)) {
                is EpubCfiOutcome.Failure -> resolved
                is EpubCfiOutcome.Success -> resolved.value.toDomainResolution(cfi)
            }
        }

    internal suspend fun resolveForNavigation(cfi: EpubCfi): EpubCfiOutcome<ReadiumResolvedCfi> =
        when (val resolvedPackage = resolvePackage(cfi)) {
            is EpubCfiOutcome.Failure -> resolvedPackage

            is EpubCfiOutcome.Success -> resolvePackageTarget(
                binding,
                packageDocument,
                cfi,
                resolvedPackage.value
            )
        }

    internal suspend fun resolvePackage(cfi: EpubCfi): EpubCfiOutcome<ReadiumEpubPackageTarget> =
        packageCfiMapper.resolve(cfi)

    internal suspend fun resolveDecoration(
        cfi: EpubCfi,
        activeResourceHref: String
    ): EpubCfiOutcome<ReadiumDecorationCfiTarget> = operations.runSerialized {
        when (val target = resolvePackage(cfi)) {
            is EpubCfiOutcome.Failure -> target

            is EpubCfiOutcome.Success -> resolveDecorationTarget(
                cfi,
                activeResourceHref,
                target.value
            )
        }
    }

    internal suspend fun compose(
        resourceHref: String,
        contentCfi: EpubCfi
    ): EpubCfiOutcome<EpubCfi> = packageCfiMapper.compose(resourceHref, contentCfi)

    internal suspend fun visiblePointCfis(
        candidates: Map<String, EpubCfi>
    ): EpubCfiOutcome<Set<String>> = operations.runSerialized {
        if (candidates.isEmpty()) return@runSerialized EpubCfiOutcome.Success(emptySet())
        val captured = binding.withNavigator { navigator, runtime ->
            val before = binding.resourceIdentity(navigator)
            val href = before?.href
            val spineItem = href?.let(packageDocument::spineItemForHref)
            val result = if (spineItem == null || spineItem.layout == EpubLayout.FIXED) {
                ReadiumCfiJavascriptResult.Failure(EpubCfiFailure.RESOURCE_NOT_IN_READING_ORDER)
            } else {
                runtime.visiblePointTargets(
                    navigator = navigator,
                    candidates = candidates,
                    packageDocument = packageDocument,
                    spineIndex = spineItem.index,
                    idref = spineItem.idref,
                    itemrefId = spineItem.id,
                    resourceHref = spineItem.resourceHref
                )
            }
            coherentResourceCapture(before, binding.resourceIdentity(navigator), result)
        } ?: return@runSerialized binding.unavailableOutcome()
        when (captured) {
            ReadiumCfiResourceCapture.Changed -> resourceChanged()

            is ReadiumCfiResourceCapture.Stable -> when (val result = captured.value) {
                is ReadiumCfiJavascriptResult.Failure -> EpubCfiOutcome.Failure(result.reason)
                is ReadiumCfiJavascriptResult.Success -> EpubCfiOutcome.Success(result.value)
            }
        }
    }

    override fun close() {
        operations.close()
        binding.close()
    }
}

private fun RectF.toSelectionBounds(): EpubSelectionBounds? {
    if (!listOf(left, top, right, bottom).all(Float::isFinite)) return null
    return EpubSelectionBounds(
        left = minOf(left, right),
        top = minOf(top, bottom),
        right = maxOf(left, right),
        bottom = maxOf(top, bottom)
    )
}

private data class PositionCapture(
    val result: ReadiumCfiJavascriptResult<EpubCfi>,
    val totalProgression: Double?
)

private suspend fun ReadiumCfiResourceCapture<PositionCapture>.toPositionOutcome(
    packageDocument: EpubPackageDocument,
    packageCfiMapper: ReadiumEpubPackageCfiMapper
): EpubCfiOutcome<EpubCfiPosition> = when (this) {
    ReadiumCfiResourceCapture.Changed -> resourceChanged()

    is ReadiumCfiResourceCapture.Stable -> value.toPositionOutcome(
        identity.href,
        packageDocument,
        packageCfiMapper
    )
}

private suspend fun PositionCapture.toPositionOutcome(
    resourceHref: String,
    packageDocument: EpubPackageDocument,
    packageCfiMapper: ReadiumEpubPackageCfiMapper
): EpubCfiOutcome<EpubCfiPosition> = when (val contentCfi = result) {
    is ReadiumCfiJavascriptResult.Failure -> EpubCfiOutcome.Failure(contentCfi.reason)

    is ReadiumCfiJavascriptResult.Success -> when (
        val cfi = packageCfiMapper.compose(resourceHref, contentCfi.value)
    ) {
        is EpubCfiOutcome.Failure -> cfi
        is EpubCfiOutcome.Success -> packageDocument.position(cfi.value, resourceHref, this)
    }
}

private fun EpubPackageDocument.position(
    cfi: EpubCfi,
    resourceHref: String,
    capture: PositionCapture
): EpubCfiOutcome<EpubCfiPosition> {
    val chapterOrdinal = spineItemForHref(resourceHref)?.index?.plus(1)
        ?: return EpubCfiOutcome.Failure(EpubCfiFailure.RESOURCE_NOT_IN_READING_ORDER)
    return EpubCfiOutcome.Success(
        EpubCfiPosition(cfi, chapterOrdinal, capture.totalProgression)
    )
}

internal data class ReadiumDecorationCfiTarget(
    val packageTarget: ReadiumEpubPackageTarget,
    val resolution: EpubCfiResolution?
)

private suspend fun ReadiumEpubCfiNavigator.resolveDecorationTarget(
    cfi: EpubCfi,
    activeResourceHref: String,
    target: ReadiumEpubPackageTarget
): EpubCfiOutcome<ReadiumDecorationCfiTarget> {
    if (target.resourceHref != activeResourceHref) {
        return EpubCfiOutcome.Success(ReadiumDecorationCfiTarget(target, null))
    }
    return when (val resolved = resolveForNavigation(cfi)) {
        is EpubCfiOutcome.Failure -> resolved

        is EpubCfiOutcome.Success -> when (val domain = resolved.value.toDomainResolution(cfi)) {
            is EpubCfiOutcome.Failure -> domain

            is EpubCfiOutcome.Success -> EpubCfiOutcome.Success(
                ReadiumDecorationCfiTarget(target, domain.value)
            )
        }
    }
}

private fun <T> resourceChanged(): EpubCfiOutcome<T> =
    EpubCfiOutcome.Failure(EpubCfiFailure.RESOURCE_CHANGED_DURING_OPERATION)

private typealias ResolvedContentResult =
    ReadiumCfiJavascriptResult<ReadiumContentResolution>

private typealias CoherentContentCapture =
    ReadiumCfiResourceCapture<ResolvedContentResult>

private typealias ContentCaptureOutcome =
    EpubCfiOutcome<CoherentContentCapture>

private suspend fun resolvePackageTarget(
    binding: ReadiumCfiNavigatorBinding,
    packageDocument: EpubPackageDocument,
    cfi: EpubCfi,
    packageTarget: ReadiumEpubPackageTarget
): EpubCfiOutcome<ReadiumResolvedCfi> = if (packageTarget.layout == EpubLayout.FIXED) {
    EpubCfiOutcome.Failure(EpubCfiFailure.UNSUPPORTED_FIXED_LAYOUT)
} else {
    binding.withNavigator { navigator, runtime ->
        captureResolvedContent(binding, packageDocument, navigator, runtime, cfi, packageTarget)
    }?.toResolvedOutcome(packageTarget)
        ?: binding.unavailableOutcome()
}

private suspend fun captureResolvedContent(
    binding: ReadiumCfiNavigatorBinding,
    packageDocument: EpubPackageDocument,
    navigator: EpubNavigatorFragment,
    runtime: ReadiumCfiJavascriptRuntime,
    cfi: EpubCfi,
    packageTarget: ReadiumEpubPackageTarget
): ContentCaptureOutcome {
    val before = binding.resourceIdentity(navigator)
    return if (before?.href != packageTarget.resourceHref) {
        EpubCfiOutcome.Failure(EpubCfiFailure.NAVIGATION_FAILED)
    } else {
        val result = runtime.resolveContent(
            navigator = navigator,
            cfi = cfi,
            packageDocument = packageDocument,
            packageTarget = packageTarget
        )
        EpubCfiOutcome.Success(
            coherentResourceCapture(before, binding.resourceIdentity(navigator), result)
        )
    }
}

private fun ContentCaptureOutcome.toResolvedOutcome(
    packageTarget: ReadiumEpubPackageTarget
): EpubCfiOutcome<ReadiumResolvedCfi> = when (this) {
    is EpubCfiOutcome.Failure -> this

    is EpubCfiOutcome.Success -> when (val coherent = value) {
        ReadiumCfiResourceCapture.Changed -> resourceChanged()
        is ReadiumCfiResourceCapture.Stable -> coherent.value.toResolvedOutcome(packageTarget)
    }
}

internal data class ReadiumResolvedCfi(
    val packageTarget: ReadiumEpubPackageTarget,
    val content: ReadiumContentResolution
)

private fun ReadiumCfiJavascriptResult<ReadiumContentResolution>.toResolvedOutcome(
    packageTarget: ReadiumEpubPackageTarget
): EpubCfiOutcome<ReadiumResolvedCfi> = when (this) {
    is ReadiumCfiJavascriptResult.Failure -> EpubCfiOutcome.Failure(reason)

    is ReadiumCfiJavascriptResult.Success -> if (value.kind == packageTarget.kind.runtimeName) {
        EpubCfiOutcome.Success(ReadiumResolvedCfi(packageTarget, value))
    } else {
        EpubCfiOutcome.Failure(EpubCfiFailure.INVALID_CFI)
    }
}

private fun ReadiumResolvedCfi.toDomainResolution(
    originalCfi: EpubCfi
): EpubCfiOutcome<EpubCfiResolution> = EpubCfiOutcome.Success(
    EpubCfiResolution(
        originalCfi = originalCfi,
        resourceHref = packageTarget.resourceHref,
        kind = packageTarget.kind,
        selectedText = content.selectedText,
        prefix = content.prefix,
        suffix = content.suffix
    )
)

private val EpubCfiTargetKind.runtimeName: String
    get() = when (this) {
        EpubCfiTargetKind.POINT -> "point"
        EpubCfiTargetKind.RANGE -> "range"
    }
