package com.secondpasslibrary.reader.reader.readium.cfi

import com.secondpasslibrary.reader.reader.cfi.EpubCfi
import com.secondpasslibrary.reader.reader.cfi.EpubCfiFailure
import com.secondpasslibrary.reader.reader.cfi.EpubCfiOutcome
import com.secondpasslibrary.reader.reader.cfi.EpubLayout
import com.secondpasslibrary.reader.reader.cfi.EpubPackageDocument
import com.secondpasslibrary.reader.reader.cfi.EpubSpineItem
import org.readium.r2.navigator.epub.EpubNavigatorFragment

/** Prefilters point CFIs by package resource, then runs bounded live-page geometry batches. */
internal class ReadiumPointVisibility(
    private val binding: ReadiumCfiNavigatorBinding,
    private val packageDocument: EpubPackageDocument
) {
    suspend fun resolve(candidates: Map<String, EpubCfi>): EpubCfiOutcome<Set<String>> =
        if (candidates.isEmpty()) {
            EpubCfiOutcome.Success(emptySet())
        } else {
            resolveCandidates(candidates)
        }

    private suspend fun resolveCandidates(
        candidates: Map<String, EpubCfi>
    ): EpubCfiOutcome<Set<String>> {
        val captured = binding.withNavigator { navigator, runtime ->
            val before = binding.resourceIdentity(navigator)
            val href = before?.href
            val spineItem = href?.let(packageDocument::spineItemForHref)
            val result = if (spineItem == null || spineItem.layout == EpubLayout.FIXED) {
                ReadiumCfiJavascriptResult.Failure(EpubCfiFailure.RESOURCE_NOT_IN_READING_ORDER)
            } else {
                visibleInActiveResource(navigator, runtime, candidates, spineItem)
            }
            coherentResourceCapture(before, binding.resourceIdentity(navigator), result)
        } ?: return binding.unavailableOutcome()
        return when (captured) {
            ReadiumCfiResourceCapture.Changed ->
                EpubCfiOutcome.Failure(EpubCfiFailure.RESOURCE_CHANGED_DURING_OPERATION)

            is ReadiumCfiResourceCapture.Stable -> when (val result = captured.value) {
                is ReadiumCfiJavascriptResult.Failure -> EpubCfiOutcome.Failure(result.reason)
                is ReadiumCfiJavascriptResult.Success -> EpubCfiOutcome.Success(result.value)
            }
        }
    }

    private suspend fun visibleInActiveResource(
        navigator: EpubNavigatorFragment,
        runtime: ReadiumCfiJavascriptRuntime,
        candidates: Map<String, EpubCfi>,
        spineItem: EpubSpineItem
    ): ReadiumCfiJavascriptResult<Set<String>> {
        val activeCandidates = when (
            val resolved = resolveActiveCandidates(navigator, runtime, candidates, spineItem)
        ) {
            is ReadiumCfiJavascriptResult.Failure -> return resolved
            is ReadiumCfiJavascriptResult.Success -> resolved.value
        }
        return resolveVisibleCandidates(navigator, runtime, activeCandidates, spineItem)
    }

    private suspend fun resolveActiveCandidates(
        navigator: EpubNavigatorFragment,
        runtime: ReadiumCfiJavascriptRuntime,
        candidates: Map<String, EpubCfi>,
        spineItem: EpubSpineItem
    ): ReadiumCfiJavascriptResult<Map<String, EpubCfi>> {
        val activeCandidates = linkedMapOf<String, EpubCfi>()
        pointCandidateChunks(candidates).forEach { chunk ->
            when (
                val packageTargets = runtime.resolvePackageCandidates(
                    navigator,
                    chunk,
                    packageDocument
                )
            ) {
                is ReadiumCfiJavascriptResult.Failure -> return packageTargets

                is ReadiumCfiJavascriptResult.Success -> {
                    activeCandidates += activePointCandidates(
                        chunk,
                        packageTargets.value,
                        spineItem
                    )
                }
            }
        }
        return ReadiumCfiJavascriptResult.Success(activeCandidates)
    }

    private suspend fun resolveVisibleCandidates(
        navigator: EpubNavigatorFragment,
        runtime: ReadiumCfiJavascriptRuntime,
        activeCandidates: Map<String, EpubCfi>,
        spineItem: EpubSpineItem
    ): ReadiumCfiJavascriptResult<Set<String>> {
        val visible = mutableSetOf<String>()
        pointCandidateChunks(activeCandidates).forEach { chunk ->
            when (
                val result = runtime.visiblePointTargets(
                    navigator = navigator,
                    candidates = chunk,
                    packageDocument = packageDocument,
                    spineIndex = spineItem.index,
                    idref = spineItem.idref,
                    itemrefId = spineItem.id,
                    resourceHref = spineItem.resourceHref
                )
            ) {
                is ReadiumCfiJavascriptResult.Failure -> return result
                is ReadiumCfiJavascriptResult.Success -> visible += result.value
            }
        }
        return ReadiumCfiJavascriptResult.Success(visible)
    }
}

private const val MAX_POINT_CANDIDATES_PER_BATCH = 1_000

internal fun pointCandidateChunks(candidates: Map<String, EpubCfi>) = candidates.entries
    .chunked(MAX_POINT_CANDIDATES_PER_BATCH)
    .map { entries -> entries.associate { it.key to it.value } }

internal fun activePointCandidates(
    candidates: Map<String, EpubCfi>,
    packageTargets: Map<String, ReadiumPackageTarget>,
    spineItem: EpubSpineItem
): Map<String, EpubCfi> = candidates.filter { (id, _) ->
    packageTargets[id]?.matchesPointIn(spineItem) == true
}

private fun ReadiumPackageTarget.matchesPointIn(spineItem: EpubSpineItem): Boolean =
    kind == "point" &&
        spineIndex == spineItem.index &&
        idref == spineItem.idref &&
        itemrefId == spineItem.id
