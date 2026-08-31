package com.secondpasslibrary.reader.reader.readium.annotations

import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotationColor
import com.secondpasslibrary.reader.reader.annotations.decoration.ReaderAnnotationDecoration
import com.secondpasslibrary.reader.reader.annotations.decoration.ReaderAnnotationDecorationActivation
import com.secondpasslibrary.reader.reader.annotations.decoration.ReaderAnnotationDecorationFailure
import com.secondpasslibrary.reader.reader.annotations.decoration.ReaderAnnotationDecorationGroupId
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.readium.r2.navigator.DecorableNavigator
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.publication.Locator

private const val CURRENT_ANNOTATION_DECORATION_GROUP = "second-pass-current-session-annotations"
private const val PREVIOUS_HIGHLIGHT_ALPHA = 0x66
private const val RGB_MASK = 0x00FFFFFFL
private const val ARGB_ALPHA_SHIFT = 24

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
    private val mutableActivations = MutableSharedFlow<ReaderAnnotationDecorationActivation>(
        extraBufferCapacity = 1
    )

    @Volatile
    private var activationIndex = emptyMap<ReadiumActivationKey, ActivationTarget>()
    private var desired = emptyMap<DecorationKey, ReaderAnnotationDecoration>()
    private val targets = mutableMapOf<DecorationKey, ReadiumEpubPackageTarget>()
    private val resolved = mutableMapOf<DecorationKey, Decoration>()
    private var appliedGroups = emptySet<ReaderAnnotationDecorationGroupId>()
    private var navigator: EpubNavigatorFragment? = null
    private var resourceJob: Job? = null
    private var registeredGroups = emptySet<String>()
    private val activationListener = object : DecorableNavigator.Listener {
        override fun onDecorationActivated(event: DecorableNavigator.OnActivatedEvent): Boolean {
            val target = activationIndex[
                ReadiumActivationKey(event.group, event.decoration.id)
            ] ?: return false
            return mutableActivations.tryEmit(
                ReaderAnnotationDecorationActivation(
                    sessionId = target.decoration.sessionId,
                    groupId = target.group,
                    annotationId = target.decoration.annotationId
                )
            )
        }
    }

    override val failures = mutableFailures.asStateFlow()
    override val activations = mutableActivations.asSharedFlow()

    override suspend fun replace(
        groupId: ReaderAnnotationDecorationGroupId,
        decorations: List<ReaderAnnotationDecoration>
    ) {
        val next = decorations
            .filter { it.kind == ReaderAnnotationKind.HIGHLIGHT }
            .associateBy { DecorationKey(groupId, it.annotationId) }
        stateMutex.withLock {
            val previous = desired.filterKeys { it.group == groupId }
            val changed = (previous.keys + next.keys).filter { desired[it] != next[it] }
            changed.forEach {
                targets.remove(it)
                resolved.remove(it)
            }
            mutableFailures.value = mutableFailures.value - changed.map { it.failureKey }.toSet()
            desired = desired.filterKeys { it.group != groupId } + next
            activationIndex = desired.toActivationIndex()
        }
        refreshCurrentResource()
    }

    override suspend fun clear(groupId: ReaderAnnotationDecorationGroupId) {
        stateMutex.withLock {
            val keys = desired.keys.filter { it.group == groupId }.toSet()
            desired = desired - keys
            targets.keys.removeAll(keys)
            resolved.keys.removeAll(keys)
            mutableFailures.value = mutableFailures.value - keys.map { it.failureKey }.toSet()
            activationIndex = desired.toActivationIndex()
        }
        applyResolved()
    }

    fun bind(value: EpubNavigatorFragment) {
        navigator?.removeDecorationListener(activationListener)
        navigator = value
        appliedGroups = emptySet()
        registeredGroups = emptySet()
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
            value.removeDecorationListener(activationListener)
            navigator = null
            registeredGroups = emptySet()
            resourceJob?.cancel()
            resourceJob = null
        }
    }

    private suspend fun refreshCurrentResource() {
        refreshMutex.withLock {
            val bound = navigator ?: return@withLock
            val activeHref = bound.currentLocator.value.href.toString().canonicalHrefOrNull()
                ?: return@withLock
            val snapshot = stateMutex.withLock { desired.toList() }
            snapshot.forEach { (key, decoration) -> resolveIfCurrent(key, decoration, activeHref) }
            applyResolved()
        }
    }

    private suspend fun resolveIfCurrent(
        key: DecorationKey,
        decoration: ReaderAnnotationDecoration,
        activeHref: String
    ) {
        val target = stateMutex.withLock { targets[key] }
        val alreadyResolved = stateMutex.withLock {
            resolved.containsKey(key)
        }
        if (target != null && target.resourceHref != activeHref) return
        if (alreadyResolved) return

        when (val outcome = cfiNavigator.resolveDecoration(decoration.cfi, activeHref)) {
            is EpubCfiOutcome.Failure -> recordFailure(
                key,
                outcome.reason.toDecorationFailure()
            )

            is EpubCfiOutcome.Success -> {
                stateMutex.withLock {
                    targets[key] = outcome.value.packageTarget
                }
                outcome.value.resolution?.let {
                    installResolved(key, decoration, outcome.value.packageTarget, it)
                }
            }
        }
    }

    private suspend fun installResolved(
        key: DecorationKey,
        decoration: ReaderAnnotationDecoration,
        target: ReadiumEpubPackageTarget,
        resolution: EpubCfiResolution
    ) {
        val readiumDecoration = decoration.toReadiumDecoration(
            target,
            resolution,
            historical = key.group is ReaderAnnotationDecorationGroupId.Previous
        )
        if (readiumDecoration == null) {
            recordFailure(key, ReaderAnnotationDecorationFailure.UNSUPPORTED)
            return
        }
        stateMutex.withLock {
            if (desired[key] == decoration) {
                resolved[key] = readiumDecoration
                mutableFailures.value = mutableFailures.value - key.failureKey
            }
        }
    }

    private suspend fun applyResolved() {
        val bound = navigator ?: return
        val values = stateMutex.withLock {
            resolved.entries.groupBy({ it.key.group }, { it.value })
        }
        val groups = appliedGroups + values.keys
        try {
            withContext(Dispatchers.Main.immediate) {
                if (navigator === bound) {
                    values.keys.forEach { group ->
                        val name = group.readiumName
                        if (name !in registeredGroups) {
                            bound.addDecorationListener(name, activationListener)
                            registeredGroups = registeredGroups + name
                        }
                    }
                    groups.forEach { group ->
                        bound.applyDecorations(values[group].orEmpty(), group.readiumName)
                    }
                    appliedGroups = values.keys
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // The next navigator bind/resource event retries the current authoritative set.
        }
    }

    private suspend fun recordFailure(
        key: DecorationKey,
        failure: ReaderAnnotationDecorationFailure
    ) = stateMutex.withLock {
        if (key in desired) {
            mutableFailures.value = mutableFailures.value + (key.failureKey to failure)
        }
    }

    override fun close() {
        resourceJob?.cancel()
        navigator?.removeDecorationListener(activationListener)
        navigator = null
        scope.cancel()
    }
}

internal fun ReaderAnnotationDecoration.toReadiumDecoration(
    target: ReadiumEpubPackageTarget,
    resolution: EpubCfiResolution,
    historical: Boolean = false
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
            style = Decoration.Style.Highlight(
                tint = color.readiumTint(historical),
                isActive = true
            )
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

private fun ReaderAnnotationColor.readiumTint(historical: Boolean): Int = if (historical) {
    (displayArgb and RGB_MASK or (PREVIOUS_HIGHLIGHT_ALPHA.toLong() shl ARGB_ALPHA_SHIFT)).toInt()
} else {
    displayArgb.toInt()
}

private data class DecorationKey(
    val group: ReaderAnnotationDecorationGroupId,
    val annotationId: String
) {
    val failureKey: String
        get() = if (group is ReaderAnnotationDecorationGroupId.Current) {
            annotationId
        } else {
            "${group.readiumName}:$annotationId"
        }
}

private val ReaderAnnotationDecorationGroupId.readiumName: String
    get() = when (this) {
        ReaderAnnotationDecorationGroupId.Current -> CURRENT_ANNOTATION_DECORATION_GROUP

        is ReaderAnnotationDecorationGroupId.Previous ->
            "second-pass-previous-session-$sessionId"
    }

private data class ReadiumActivationKey(val group: String, val annotationId: String)

private data class ActivationTarget(
    val group: ReaderAnnotationDecorationGroupId,
    val decoration: ReaderAnnotationDecoration
)

private fun Map<DecorationKey, ReaderAnnotationDecoration>.toActivationIndex() =
    map { (key, decoration) ->
        ReadiumActivationKey(key.group.readiumName, key.annotationId) to
            ActivationTarget(key.group, decoration)
    }.toMap()
