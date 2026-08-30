package com.secondpasslibrary.reader.reader.annotations.decoration

import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotation
import com.secondpasslibrary.reader.reader.marginalia.ReaderPreviousMarginaliaLayer
import com.secondpasslibrary.reader.reader.session.ReaderSessionContext
import com.secondpasslibrary.reader.reader.session.ReaderSessionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class ReaderReadOnlyHighlightDetail(
    val sessionId: String,
    val annotation: ReaderAnnotation.Highlight,
    val sessionName: String?,
    val startedAt: String?,
    val historical: Boolean
)

/** Resolves renderer activation identities against the latest Session-owned collections. */
internal class ReaderHighlightActivationController(
    private val scope: CoroutineScope,
    private val onEdit: (ReaderAnnotation.Highlight) -> Unit
) {
    private val mutableDetail = MutableStateFlow<ReaderReadOnlyHighlightDetail?>(null)
    val detail = mutableDetail.asStateFlow()

    private var currentSession: ReaderSessionContext? = null
    private var currentAnnotations = emptyList<ReaderAnnotation>()
    private var previousLayers = emptyList<ReaderPreviousMarginaliaLayer>()
    private var target: ReaderAnnotationDecorations? = null
    private var activationJob: Job? = null
    private var generation = 0L

    fun select(target: ReaderAnnotationDecorations?) {
        if (this.target === target) return
        this.target = target
        activationJob?.cancel()
        val selectedGeneration = ++generation
        activationJob = target?.let { decorations ->
            scope.launch {
                decorations.activations.collect { activation ->
                    if (generation == selectedGeneration) activate(activation)
                }
            }
        }
    }

    fun replaceContext(
        session: ReaderSessionContext?,
        annotations: List<ReaderAnnotation>,
        layers: List<ReaderPreviousMarginaliaLayer>
    ) {
        currentSession = session
        currentAnnotations = annotations
        previousLayers = layers
        mutableDetail.value?.let { shown ->
            if (resolve(shown.sessionId, shown.annotation.id) == null) mutableDetail.value = null
        }
    }

    fun dismiss() {
        mutableDetail.value = null
    }

    fun clear() {
        select(null)
        currentSession = null
        currentAnnotations = emptyList()
        previousLayers = emptyList()
        mutableDetail.value = null
    }

    private fun activate(activation: ReaderAnnotationDecorationActivation) {
        when (val resolved = resolve(activation)) {
            is ActivatedHighlight.Current -> {
                if (resolved.session.status == ReaderSessionStatus.ACTIVE) {
                    mutableDetail.value = null
                    onEdit(resolved.annotation)
                } else {
                    mutableDetail.value = ReaderReadOnlyHighlightDetail(
                        sessionId = resolved.session.sessionId,
                        annotation = resolved.annotation,
                        sessionName = resolved.session.sessionName,
                        startedAt = resolved.session.startedAt,
                        historical = false
                    )
                }
            }

            is ActivatedHighlight.Previous -> mutableDetail.value = ReaderReadOnlyHighlightDetail(
                sessionId = resolved.layer.summary.sessionId,
                annotation = resolved.annotation,
                sessionName = resolved.layer.summary.sessionName,
                startedAt = resolved.layer.summary.startedAt,
                historical = true
            )

            null -> Unit
        }
    }

    private fun resolve(sessionId: String, annotationId: String): ActivatedHighlight? =
        currentSession
            ?.takeIf { it.sessionId == sessionId }
            ?.let { session ->
                currentAnnotations.highlight(annotationId)?.let { annotation ->
                    ActivatedHighlight.Current(session, annotation)
                }
            }
            ?: previousLayers.firstOrNull { it.summary.sessionId == sessionId }?.let { layer ->
                layer.annotations.highlight(annotationId)?.let { annotation ->
                    ActivatedHighlight.Previous(layer, annotation)
                }
            }

    private fun resolve(activation: ReaderAnnotationDecorationActivation): ActivatedHighlight? =
        when (val group = activation.groupId) {
            ReaderAnnotationDecorationGroupId.Current -> resolveCurrent(activation)
            is ReaderAnnotationDecorationGroupId.Previous -> resolvePrevious(group, activation)
        }

    private fun resolveCurrent(
        activation: ReaderAnnotationDecorationActivation
    ): ActivatedHighlight.Current? = currentSession
        ?.takeIf { it.sessionId == activation.sessionId }
        ?.let { session ->
            currentAnnotations.highlight(activation.annotationId)?.let { annotation ->
                ActivatedHighlight.Current(session, annotation)
            }
        }

    private fun resolvePrevious(
        group: ReaderAnnotationDecorationGroupId.Previous,
        activation: ReaderAnnotationDecorationActivation
    ): ActivatedHighlight.Previous? = previousLayers
        .takeIf { group.sessionId == activation.sessionId }
        ?.firstOrNull { it.summary.sessionId == activation.sessionId }
        ?.let { layer ->
            layer.annotations.highlight(activation.annotationId)?.let { annotation ->
                ActivatedHighlight.Previous(layer, annotation)
            }
        }
}

private fun List<ReaderAnnotation>.highlight(id: String): ReaderAnnotation.Highlight? =
    filterIsInstance<ReaderAnnotation.Highlight>().firstOrNull { it.id == id }

private sealed interface ActivatedHighlight {
    data class Current(
        val session: ReaderSessionContext,
        val annotation: ReaderAnnotation.Highlight
    ) : ActivatedHighlight

    data class Previous(
        val layer: ReaderPreviousMarginaliaLayer,
        val annotation: ReaderAnnotation.Highlight
    ) : ActivatedHighlight
}
