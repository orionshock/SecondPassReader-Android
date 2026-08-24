package com.secondpasslibrary.reader.reader.readium.cfi

import com.secondpasslibrary.reader.reader.cfi.EpubCfi
import com.secondpasslibrary.reader.reader.cfi.EpubCfiFailure
import com.secondpasslibrary.reader.reader.cfi.EpubCfiNavigator
import com.secondpasslibrary.reader.reader.cfi.EpubCfiOutcome
import com.secondpasslibrary.reader.reader.cfi.EpubCfiResolution
import com.secondpasslibrary.reader.reader.cfi.EpubCfiSelection
import com.secondpasslibrary.reader.reader.cfi.EpubCfiTargetKind
import com.secondpasslibrary.reader.reader.cfi.EpubLayout
import com.secondpasslibrary.reader.reader.cfi.EpubPackageDocument
import com.secondpasslibrary.reader.reader.cfi.normalizeEpubHref
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator

private val NAVIGATION_TIMEOUT = 10.seconds
private val TARGET_VERIFICATION_INTERVAL = 50.milliseconds

internal class ReadiumEpubCfiNavigator(
    private val binding: ReadiumCfiNavigatorBinding,
    private val packageDocument: EpubPackageDocument,
    readingOrder: List<Link>
) : EpubCfiNavigator,
    AutoCloseable {
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

    override suspend fun goTo(cfi: EpubCfi): EpubCfiOutcome<Unit> = incomingNavigation.goTo(cfi)

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

    override suspend fun resolve(cfi: EpubCfi): EpubCfiOutcome<EpubCfiResolution> =
        when (val resolved = resolveForNavigation(cfi)) {
            is EpubCfiOutcome.Failure -> resolved
            is EpubCfiOutcome.Success -> resolved.value.toDomainResolution(cfi)
        }

    internal suspend fun resolveForNavigation(cfi: EpubCfi): EpubCfiOutcome<ReadiumResolvedCfi> =
        when (val resolvedPackage = resolvePackage(cfi)) {
            is EpubCfiOutcome.Failure -> resolvedPackage

            is EpubCfiOutcome.Success -> if (resolvedPackage.value.layout == EpubLayout.FIXED) {
                EpubCfiOutcome.Failure(EpubCfiFailure.UNSUPPORTED_FIXED_LAYOUT)
            } else {
                val packageTarget = resolvedPackage.value
                binding.withNavigator { navigator, runtime ->
                    val activeBefore = navigator.activeResourceHref()
                    if (activeBefore != packageTarget.resourceHref) {
                        EpubCfiOutcome.Failure(EpubCfiFailure.NAVIGATION_FAILED)
                    } else {
                        val result = runtime.resolveContent(
                            navigator = navigator,
                            cfi = cfi,
                            packageDocument = packageDocument,
                            packageTarget = packageTarget
                        )
                        if (navigator.activeResourceHref() != activeBefore) {
                            EpubCfiOutcome.Failure(EpubCfiFailure.NAVIGATION_FAILED)
                        } else {
                            result.toResolvedOutcome(packageTarget)
                        }
                    }
                } ?: EpubCfiOutcome.Failure(EpubCfiFailure.NAVIGATOR_UNAVAILABLE)
            }
        }

    internal suspend fun resolvePackage(cfi: EpubCfi): EpubCfiOutcome<ReadiumEpubPackageTarget> =
        packageCfiMapper.resolve(cfi)

    internal suspend fun compose(
        resourceHref: String,
        contentCfi: EpubCfi
    ): EpubCfiOutcome<EpubCfi> = packageCfiMapper.compose(resourceHref, contentCfi)

    override fun close() = binding.close()
}

private class ReadiumCfiIncomingNavigation(
    private val binding: ReadiumCfiNavigatorBinding,
    private val packageDocument: EpubPackageDocument,
    private val packageCfiMapper: ReadiumEpubPackageCfiMapper
) {
    suspend fun goTo(cfi: EpubCfi): EpubCfiOutcome<Unit> = try {
        when (val packageOutcome = packageCfiMapper.resolve(cfi)) {
            is EpubCfiOutcome.Failure -> packageOutcome
            is EpubCfiOutcome.Success -> navigate(cfi, packageOutcome.value)
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        EpubCfiOutcome.Failure(EpubCfiFailure.NAVIGATION_FAILED)
    }

    private suspend fun navigate(
        cfi: EpubCfi,
        target: ReadiumEpubPackageTarget
    ): EpubCfiOutcome<Unit> {
        if (target.layout == EpubLayout.FIXED) {
            return EpubCfiOutcome.Failure(EpubCfiFailure.UNSUPPORTED_FIXED_LAYOUT)
        }
        return binding.withNavigator { navigator, runtime ->
            val resourceFailure = navigateToResource(navigator, runtime, target)
            if (resourceFailure != null) {
                return@withNavigator EpubCfiOutcome.Failure(resourceFailure)
            }
            val resolution = when (
                val content = runtime.resolveContent(
                    navigator,
                    cfi,
                    packageDocument,
                    target
                )
            ) {
                is ReadiumCfiJavascriptResult.Failure ->
                    return@withNavigator EpubCfiOutcome.Failure(content.reason)

                is ReadiumCfiJavascriptResult.Success -> content.value
            }
            val locator = Locator(
                href = target.resourceUrl,
                mediaType = target.mediaType,
                locations = Locator.Locations(),
                text = Locator.Text(
                    before = resolution.movementAnchor.before,
                    highlight = resolution.movementAnchor.exact,
                    after = resolution.movementAnchor.after
                )
            )
            if (!navigator.go(locator, animated = false)) {
                return@withNavigator EpubCfiOutcome.Failure(EpubCfiFailure.NAVIGATION_FAILED)
            }
            val verificationFailure = awaitVerifiedTarget(
                navigator,
                runtime,
                cfi,
                target,
                resolution
            )
            if (verificationFailure == null) {
                EpubCfiOutcome.Success(Unit)
            } else {
                EpubCfiOutcome.Failure(verificationFailure)
            }
        } ?: EpubCfiOutcome.Failure(EpubCfiFailure.NAVIGATOR_UNAVAILABLE)
    }

    private suspend fun navigateToResource(
        navigator: EpubNavigatorFragment,
        runtime: ReadiumCfiJavascriptRuntime,
        target: ReadiumEpubPackageTarget
    ): EpubCfiFailure? {
        val alreadyActive = navigator.isActiveResource(target)
        val navigationAccepted = alreadyActive ||
            navigator.go(target.resourceLink, animated = false)
        val arrived = if (alreadyActive) {
            true
        } else if (!navigationAccepted) {
            false
        } else {
            withTimeoutOrNull(NAVIGATION_TIMEOUT) {
                navigator.currentLocator.first { locator ->
                    locator.href.isEquivalent(target.resourceUrl)
                }
            } != null
        }
        return when {
            !navigationAccepted -> EpubCfiFailure.NAVIGATION_FAILED
            !arrived -> EpubCfiFailure.NAVIGATION_TIMEOUT
            !navigator.isActiveResource(target) -> EpubCfiFailure.NAVIGATION_FAILED
            !runtime.ensureInstalled(navigator) -> EpubCfiFailure.JAVASCRIPT_RUNTIME_UNAVAILABLE
            else -> null
        }
    }

    private suspend fun awaitVerifiedTarget(
        navigator: EpubNavigatorFragment,
        runtime: ReadiumCfiJavascriptRuntime,
        cfi: EpubCfi,
        target: ReadiumEpubPackageTarget,
        resolution: ReadiumContentResolution
    ): EpubCfiFailure? {
        val verified = withTimeoutOrNull(NAVIGATION_TIMEOUT) {
            var targetVerified = false
            while (!targetVerified) {
                val active = navigator.isActiveResource(target)
                val result = runtime.verifyContentTarget(
                    navigator,
                    cfi,
                    packageDocument,
                    target,
                    resolution
                )
                targetVerified = active && result.isVerifiedTarget()
                if (!targetVerified) delay(TARGET_VERIFICATION_INTERVAL)
            }
            targetVerified
        } ?: false
        return if (verified) null else EpubCfiFailure.NAVIGATION_TIMEOUT
    }
}

private typealias TargetVerificationResult =
    ReadiumCfiJavascriptResult<ReadiumContentTargetVerification>

private fun TargetVerificationResult.isVerifiedTarget(): Boolean = when (this) {
    is ReadiumCfiJavascriptResult.Failure -> false
    is ReadiumCfiJavascriptResult.Success -> value.semanticMatch && value.visible
}

private fun EpubNavigatorFragment.activeResourceHref(): String? =
    runCatching { normalizeEpubHref(currentLocator.value.href.toString()) }.getOrNull()

private fun EpubNavigatorFragment.isActiveResource(target: ReadiumEpubPackageTarget): Boolean =
    currentLocator.value.href.isEquivalent(target.resourceUrl) &&
        activeResourceHref() == target.resourceHref

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
