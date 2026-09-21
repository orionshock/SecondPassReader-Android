package com.secondpasslibrary.reader.reader

import androidx.compose.ui.Modifier
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotation
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotationColor
import com.secondpasslibrary.reader.reader.annotations.decoration.ReaderAnnotationDecoration
import com.secondpasslibrary.reader.reader.annotations.decoration.ReaderAnnotationDecorationActivation
import com.secondpasslibrary.reader.reader.annotations.decoration.ReaderAnnotationDecorationFailure
import com.secondpasslibrary.reader.reader.annotations.decoration.ReaderAnnotationDecorationGroupId
import com.secondpasslibrary.reader.reader.annotations.decoration.ReaderAnnotationDecorations
import com.secondpasslibrary.reader.reader.annotations.mutation.ReaderAnnotationMutationRequest
import com.secondpasslibrary.reader.reader.appearance.ReaderAppearance
import com.secondpasslibrary.reader.reader.appearance.ReaderAppearanceController
import com.secondpasslibrary.reader.reader.appearance.ReaderAppearanceStore
import com.secondpasslibrary.reader.reader.cfi.EpubCfi
import com.secondpasslibrary.reader.reader.cfi.EpubCfiFailure
import com.secondpasslibrary.reader.reader.cfi.EpubCfiNavigator
import com.secondpasslibrary.reader.reader.cfi.EpubCfiOutcome
import com.secondpasslibrary.reader.reader.cfi.EpubCfiReadiness
import com.secondpasslibrary.reader.reader.cfi.EpubCfiResolution
import com.secondpasslibrary.reader.reader.cfi.EpubCfiSelection
import com.secondpasslibrary.reader.reader.domain.ReaderEngine
import com.secondpasslibrary.reader.reader.domain.ReaderViewport
import com.secondpasslibrary.reader.reader.domain.ReaderViewportMovements
import com.secondpasslibrary.reader.reader.marginalia.ReaderMarginaliaLayerRole
import com.secondpasslibrary.reader.reader.marginalia.ReaderMarginaliaLayerSummary
import com.secondpasslibrary.reader.reader.marginalia.preferences.ReaderMarginaliaLayerPreferenceStore
import com.secondpasslibrary.reader.reader.marginalia.preferences.ReaderMarginaliaLayerVisibilityStore
import com.secondpasslibrary.reader.reader.marginalia.preferences.ReaderMarginaliaVisibilityScope
import com.secondpasslibrary.reader.reader.persistence.LocalReaderStateStore
import com.secondpasslibrary.reader.reader.persistence.LocalReaderWriteProvenance
import com.secondpasslibrary.reader.reader.session.ReaderSessionContext
import com.secondpasslibrary.reader.reader.session.ReaderSessionStatus
import com.secondpasslibrary.reader.reader.toc.EmptyReaderTableOfContents
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

internal class MarginaliaTestRecordingEngine : ReaderEngine {
    override val viewport = ReaderViewport { _: Modifier -> }
    override val cfiNavigator = MarginaliaTestFakeCfiNavigator
    override val viewportMovements = ReaderViewportMovements { emptyFlow() }
    override val tableOfContents = EmptyReaderTableOfContents
    override val appearance = MarginaliaTestFakeAppearanceController()
    override val annotationDecorations = MarginaliaTestRecordingDecorations()
    val decorations: MarginaliaTestRecordingDecorations
        get() = annotationDecorations

    override fun close() = Unit
}

internal class MarginaliaTestRecordingDecorations : ReaderAnnotationDecorations {
    override val failures = MutableStateFlow(
        emptyMap<String, ReaderAnnotationDecorationFailure>()
    )
    override val activations = MutableSharedFlow<ReaderAnnotationDecorationActivation>()
    val groups = mutableMapOf<ReaderAnnotationDecorationGroupId, List<ReaderAnnotationDecoration>>()
    var previousReplacements = 0
    var previousClears = 0

    override suspend fun replace(
        groupId: ReaderAnnotationDecorationGroupId,
        decorations: List<ReaderAnnotationDecoration>
    ) {
        groups[groupId] = decorations
        if (groupId is ReaderAnnotationDecorationGroupId.Previous) previousReplacements += 1
    }

    override suspend fun clear(groupId: ReaderAnnotationDecorationGroupId) {
        groups.remove(groupId)
        if (groupId is ReaderAnnotationDecorationGroupId.Previous) previousClears += 1
    }
}

internal class MarginaliaTestFakeAppearanceController : ReaderAppearanceController {
    override val appearance: StateFlow<ReaderAppearance> = MutableStateFlow(ReaderAppearance())

    override suspend fun update(appearance: ReaderAppearance) = Unit
}

internal data object MarginaliaTestFakeAppearanceStore : ReaderAppearanceStore {
    override suspend fun read() = ReaderAppearance()

    override suspend fun write(appearance: ReaderAppearance) = Unit
}

internal data object MarginaliaTestFakeLayerPreferenceStore : ReaderMarginaliaLayerPreferenceStore {
    override suspend fun readAutoShowPrevious() = true

    override suspend fun writeAutoShowPrevious(enabled: Boolean) = Unit
}

internal data object MarginaliaTestFakeLayerVisibilityStore : ReaderMarginaliaLayerVisibilityStore {
    override suspend fun read(
        scope: ReaderMarginaliaVisibilityScope,
        sessionId: String,
        now: Instant
    ): Boolean? = null

    override suspend fun write(
        scope: ReaderMarginaliaVisibilityScope,
        sessionId: String,
        visible: Boolean,
        touchedAt: Instant
    ) = Unit

    override suspend fun clearAccountState() = Unit
}

internal data object MarginaliaTestFakeLocalReaderStateStore : LocalReaderStateStore {
    override suspend fun selectOfflineSession(account: LocalReaderAccountKey, bookId: String) =
        error("Offline selection is not expected.")

    override suspend fun retainServerSession(
        account: LocalReaderAccountKey,
        bookId: String,
        session: ReaderSessionContext
    ) = session

    override suspend fun writeProgress(
        account: LocalReaderAccountKey,
        localSessionId: String,
        cfi: String,
        provenance: LocalReaderWriteProvenance,
        locationLabel: String?
    ) = Unit

    override suspend fun acknowledgeProgress(
        account: LocalReaderAccountKey,
        localSessionId: String,
        cfi: String
    ) = Unit

    override suspend fun readAnnotations(account: LocalReaderAccountKey, localSessionId: String) =
        emptyList<ReaderAnnotation>()

    override suspend fun applyAnnotationMutation(
        account: LocalReaderAccountKey,
        localSessionId: String,
        request: ReaderAnnotationMutationRequest
    ) = emptyList<ReaderAnnotation>()

    override suspend fun replaceAuthoritativeAnnotations(
        account: LocalReaderAccountKey,
        localSessionId: String,
        annotations: List<ReaderAnnotation>,
        acknowledgedMutation: ReaderAnnotationMutationRequest?
    ) = Unit

    override suspend fun purgeAccount(account: LocalReaderAccountKey) = Unit
}

internal data object MarginaliaTestFakeCfiNavigator : EpubCfiNavigator {
    override val readiness = MutableStateFlow<EpubCfiReadiness>(EpubCfiReadiness.Available)

    override suspend fun goTo(cfi: EpubCfi): EpubCfiOutcome<Unit> = EpubCfiOutcome.Success(Unit)

    override suspend fun currentSelection(): EpubCfiOutcome<EpubCfiSelection?> =
        EpubCfiOutcome.Success(null)

    override suspend fun resolve(cfi: EpubCfi): EpubCfiOutcome<EpubCfiResolution> =
        EpubCfiOutcome.Failure(EpubCfiFailure.DOM_TARGET_NOT_FOUND)
}

internal fun marginaliaTestPreviousLayer(sessionId: String) = ReaderMarginaliaLayerSummary(
    sessionId = sessionId,
    role = ReaderMarginaliaLayerRole.PREVIOUS,
    sessionStatus = ReaderSessionStatus.CLOSED,
    sessionName = null,
    startedAt = null,
    closedAt = null,
    lastActivityAt = null,
    annotationCount = 1
)

internal fun marginaliaTestHighlight(sessionId: String) = ReaderAnnotation.Highlight(
    id = "highlight-$sessionId",
    clientId = "client-$sessionId",
    cfi = "epubcfi(/6/2!/4/2:3)",
    locationLabel = null,
    updatedAt = "2026-09-16T00:00:00Z",
    quote = "quote",
    prefix = null,
    suffix = null,
    note = null,
    color = ReaderAnnotationColor.YELLOW
)

internal fun marginaliaTestProfile() = ConnectionProfile(
    serverId = "a6722b5a-7982-4778-8c74-39be4241a654",
    serverOrigin = "https://library.example",
    libraryBaseUrl = "https://library.example",
    serverName = "Library",
    serverDescription = "",
    serverVersion = "1",
    serverReleaseDate = "2026-09-16",
    clientSessionId = "client-session",
    clientName = "Reader",
    clientType = "reader"
)
