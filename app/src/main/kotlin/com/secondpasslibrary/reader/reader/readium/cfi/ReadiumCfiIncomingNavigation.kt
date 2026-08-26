package com.secondpasslibrary.reader.reader.readium.cfi

import com.secondpasslibrary.reader.reader.cfi.EpubCfi
import com.secondpasslibrary.reader.reader.cfi.EpubCfiFailure
import com.secondpasslibrary.reader.reader.cfi.EpubCfiOutcome
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
import org.readium.r2.shared.publication.Locator

/** Owns the two-stage resource and exact-content movement used for incoming full EPUB CFIs. */
internal class ReadiumCfiIncomingNavigation(
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
                val content = runtime.resolveContent(navigator, cfi, packageDocument, target)
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
            awaitVerifiedTarget(navigator, runtime, cfi, target, resolution)?.let {
                EpubCfiOutcome.Failure(it)
            } ?: EpubCfiOutcome.Success(Unit)
        } ?: binding.unavailableOutcome()
    }

    private suspend fun navigateToResource(
        navigator: EpubNavigatorFragment,
        runtime: ReadiumCfiJavascriptRuntime,
        target: ReadiumEpubPackageTarget
    ): EpubCfiFailure? {
        val alreadyActive = navigator.isActiveResource(target)
        val navigationAccepted =
            alreadyActive || navigator.go(target.resourceLink, animated = false)
        val arrived = when {
            alreadyActive -> true

            !navigationAccepted -> false

            else -> withTimeoutOrNull(NAVIGATION_TIMEOUT) {
                navigator.currentLocator.first { it.href.isEquivalent(target.resourceUrl) }
            } != null
        }
        return when {
            !navigationAccepted -> EpubCfiFailure.NAVIGATION_FAILED
            !arrived -> EpubCfiFailure.NAVIGATION_TIMEOUT
            !navigator.isActiveResource(target) -> EpubCfiFailure.NAVIGATION_FAILED
            else -> runtime.installationFailure(navigator)
        }
    }

    private suspend fun awaitVerifiedTarget(
        navigator: EpubNavigatorFragment,
        runtime: ReadiumCfiJavascriptRuntime,
        cfi: EpubCfi,
        target: ReadiumEpubPackageTarget,
        resolution: ReadiumContentResolution
    ): EpubCfiFailure? {
        val outcome = withTimeoutOrNull(NAVIGATION_TIMEOUT) {
            var completed: TargetVerificationAttempt.Complete? = null
            while (completed == null) {
                when (val attempt = verifyTargetOnce(navigator, runtime, cfi, target, resolution)) {
                    TargetVerificationAttempt.Pending -> delay(TARGET_VERIFICATION_INTERVAL)
                    is TargetVerificationAttempt.Complete -> completed = attempt
                }
            }
            completed
        } ?: return EpubCfiFailure.NAVIGATION_TIMEOUT
        return outcome.failure
    }

    private suspend fun verifyTargetOnce(
        navigator: EpubNavigatorFragment,
        runtime: ReadiumCfiJavascriptRuntime,
        cfi: EpubCfi,
        target: ReadiumEpubPackageTarget,
        resolution: ReadiumContentResolution
    ): TargetVerificationAttempt {
        if (!navigator.isActiveResource(target)) {
            return TargetVerificationAttempt.Complete(EpubCfiFailure.NAVIGATION_FAILED)
        }
        return when (val result = runtime.verifyContentTarget(navigator, cfi, target, resolution)) {
            is ReadiumCfiJavascriptResult.Failure ->
                TargetVerificationAttempt.Complete(result.reason)

            is ReadiumCfiJavascriptResult.Success -> if (
                result.value.semanticMatch && result.value.visible
            ) {
                TargetVerificationAttempt.Complete(null)
            } else {
                TargetVerificationAttempt.Pending
            }
        }
    }
}

private sealed interface TargetVerificationAttempt {
    data object Pending : TargetVerificationAttempt
    data class Complete(val failure: EpubCfiFailure?) : TargetVerificationAttempt
}

private fun EpubNavigatorFragment.isActiveResource(target: ReadiumEpubPackageTarget): Boolean =
    currentLocator.value.href.isEquivalent(target.resourceUrl) &&
        runCatching { normalizeEpubHref(currentLocator.value.href.toString()) }.getOrNull() ==
        target.resourceHref

private val NAVIGATION_TIMEOUT = 10.seconds
private val TARGET_VERIFICATION_INTERVAL = 50.milliseconds
