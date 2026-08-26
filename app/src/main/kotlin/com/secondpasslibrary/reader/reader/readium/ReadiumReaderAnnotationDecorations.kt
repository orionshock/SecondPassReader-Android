package com.secondpasslibrary.reader.reader.readium

import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotationColor
import com.secondpasslibrary.reader.reader.annotations.decoration.ReaderAnnotationDecoration
import com.secondpasslibrary.reader.reader.annotations.decoration.ReaderAnnotationDecorationFailure
import com.secondpasslibrary.reader.reader.annotations.decoration.ReaderAnnotationDecorations
import com.secondpasslibrary.reader.reader.annotations.decoration.ReaderAnnotationKind
import com.secondpasslibrary.reader.reader.cfi.EpubCfiFailure
import com.secondpasslibrary.reader.reader.cfi.EpubCfiOutcome
import com.secondpasslibrary.reader.reader.cfi.EpubCfiResolution
import com.secondpasslibrary.reader.reader.cfi.EpubCfiTargetKind
import com.secondpasslibrary.reader.reader.cfi.normalizeEpubHref
import com.secondpasslibrary.reader.reader.readium.cfi.ReadiumEpubCfiNavigator
import com.secondpasslibrary.reader.reader.readium.cfi.ReadiumEpubPackageTarget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.publication.Locator

private const val ANNOTATION_DECORATION_GROUP = "second-pass-current-session-annotations"

/** Resolves canonical CFIs only in their live resource, then gives Readium ordinary locators. */
internal class ReadiumReaderAnnotationDecorations(
    private val cfiNavigator: ReadiumEpubCfiNavigator
) : ReaderAnnotationDecorations,
    AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val stateMutex = Mutex()
    private val refreshMutex = Mutex()
    private val mutableFailures = MutableStateFlow(
        emptyMap<String, ReaderAnnotationDecorationFailure>()
    )
    private var desired = emptyMap<String, ReaderAnnotationDecoration>()
    private val targets = mutableMapOf<String, ReadiumEpubPackageTarget>()
    private val resolved = mutableMapOf<String, Decoration>()
    private var navigator: EpubNavigatorFragment? = null
    private var resourceJob: Job? = null

    override val failures = mutableFailures.asStateFlow()

    override suspend fun replace(decorations: List<ReaderAnnotationDecoration>) {
        val next = decorations
            .filter { it.kind == ReaderAnnotationKind.HIGHLIGHT }
            .associateBy(ReaderAnnotationDecoration::annotationId)
        stateMutex.withLock {
            val changed = (desired.keys + next.keys).filter { desired[it] != next[it] }
            changed.forEach {
                targets.remove(it)
                resolved.remove(it)
            }
            mutableFailures.value = mutableFailures.value - changed.toSet()
            desired = next
        }
        refreshCurrentResource()
    }

    override suspend fun clear() {
        stateMutex.withLock {
            desired = emptyMap()
            targets.clear()
            resolved.clear()
            mutableFailures.value = emptyMap()
        }
        applyResolved()
    }

    fun bind(value: EpubNavigatorFragment) {
        navigator = value
        resourceJob?.cancel()
        resourceJob = scope.launch {
            value.currentLocator
                .map { it.href.toString().canonicalHrefOrNull() }
                .distinctUntilChanged()
                .collect { refreshCurrentResource() }
        }
        scope.launch {
            applyResolved()
            refreshCurrentResource()
        }
    }

    fun unbind(value: EpubNavigatorFragment) {
        if (navigator === value) {
            navigator = null
            resourceJob?.cancel()
            resourceJob = null
        }
    }

    private suspend fun refreshCurrentResource() {
        refreshMutex.withLock {
            val bound = navigator ?: return@withLock
            val activeHref = bound.currentLocator.value.href.toString().canonicalHrefOrNull()
                ?: return@withLock
            val snapshot = stateMutex.withLock { desired.values.toList() }
            snapshot.forEach { decoration -> resolveIfCurrent(decoration, activeHref) }
            applyResolved()
        }
    }

    private suspend fun resolveIfCurrent(
        decoration: ReaderAnnotationDecoration,
        activeHref: String
    ) {
        val target = stateMutex.withLock { targets[decoration.annotationId] }
        val alreadyResolved = stateMutex.withLock {
            resolved.containsKey(decoration.annotationId)
        }
        if (target != null && target.resourceHref != activeHref) return
        if (alreadyResolved) return

        when (val outcome = cfiNavigator.resolveDecoration(decoration.cfi, activeHref)) {
            is EpubCfiOutcome.Failure -> recordFailure(
                decoration.annotationId,
                outcome.reason.toDecorationFailure()
            )

            is EpubCfiOutcome.Success -> {
                stateMutex.withLock {
                    targets[decoration.annotationId] = outcome.value.packageTarget
                }
                outcome.value.resolution?.let {
                    installResolved(decoration, outcome.value.packageTarget, it)
                }
            }
        }
    }

    private suspend fun installResolved(
        decoration: ReaderAnnotationDecoration,
        target: ReadiumEpubPackageTarget,
        resolution: EpubCfiResolution
    ) {
        val readiumDecoration = decoration.toReadiumDecoration(target, resolution)
        if (readiumDecoration == null) {
            recordFailure(decoration.annotationId, ReaderAnnotationDecorationFailure.UNSUPPORTED)
            return
        }
        stateMutex.withLock {
            if (desired[decoration.annotationId] == decoration) {
                resolved[decoration.annotationId] = readiumDecoration
                mutableFailures.value = mutableFailures.value - decoration.annotationId
            }
        }
    }

    private suspend fun applyResolved() {
        val bound = navigator ?: return
        val values = stateMutex.withLock { resolved.values.toList() }
        try {
            withContext(Dispatchers.Main.immediate) {
                if (navigator === bound) {
                    bound.applyDecorations(values, ANNOTATION_DECORATION_GROUP)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // The next navigator bind/resource event retries the current authoritative set.
        }
    }

    private suspend fun recordFailure(
        annotationId: String,
        failure: ReaderAnnotationDecorationFailure
    ) = stateMutex.withLock {
        if (annotationId in desired) {
            mutableFailures.value = mutableFailures.value + (annotationId to failure)
        }
    }

    override fun close() {
        resourceJob?.cancel()
        navigator = null
        scope.cancel()
    }
}

internal fun ReaderAnnotationDecoration.toReadiumDecoration(
    target: ReadiumEpubPackageTarget,
    resolution: EpubCfiResolution
): Decoration? = when {
    kind != ReaderAnnotationKind.HIGHLIGHT || color == null -> null

    resolution.kind != EpubCfiTargetKind.RANGE -> null

    else -> resolution.selectedText?.takeIf(String::isNotBlank)?.let { exact ->
        Decoration(
            id = annotationId,
            locator = Locator(
                href = target.resourceUrl,
                mediaType = target.mediaType,
                locations = Locator.Locations(),
                text = Locator.Text(
                    before = resolution.prefix,
                    highlight = exact,
                    after = resolution.suffix
                )
            ),
            style = Decoration.Style.Highlight(tint = color.readiumTint, isActive = false)
        )
    }
}

private fun String.canonicalHrefOrNull(): String? =
    runCatching { normalizeEpubHref(this) }.getOrNull()?.takeIf(String::isNotBlank)

private fun EpubCfiFailure.toDecorationFailure(): ReaderAnnotationDecorationFailure = when (this) {
    EpubCfiFailure.INVALID_CFI,
    EpubCfiFailure.INVALID_RANGE,
    EpubCfiFailure.DOM_TARGET_NOT_FOUND,
    EpubCfiFailure.PACKAGE_TARGET_NOT_FOUND,
    EpubCfiFailure.RESOURCE_NOT_IN_READING_ORDER ->
        ReaderAnnotationDecorationFailure.INVALID_LOCATION

    EpubCfiFailure.UNSUPPORTED_CFI_FEATURE,
    EpubCfiFailure.UNSUPPORTED_FIXED_LAYOUT,
    EpubCfiFailure.UNSUPPORTED_SCROLL_MODE,
    EpubCfiFailure.UNSUPPORTED_WRITING_MODE -> ReaderAnnotationDecorationFailure.UNSUPPORTED

    else -> ReaderAnnotationDecorationFailure.UNAVAILABLE
}

private val ReaderAnnotationColor.readiumTint: Int
    get() = displayArgb.toInt()
