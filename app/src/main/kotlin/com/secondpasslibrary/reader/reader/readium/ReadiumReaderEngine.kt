package com.secondpasslibrary.reader.reader.readium

import android.content.Context
import android.content.res.Configuration
import androidx.fragment.app.FragmentFactory
import com.secondpasslibrary.reader.reader.appearance.ReaderAppearance
import com.secondpasslibrary.reader.reader.appearance.ReaderViewportOrientation
import com.secondpasslibrary.reader.reader.cfi.EpubPackageDocument
import com.secondpasslibrary.reader.reader.cfi.ZipEpubPackageResolver
import com.secondpasslibrary.reader.reader.domain.ReaderEngine
import com.secondpasslibrary.reader.reader.domain.ReaderEngineOpenException
import com.secondpasslibrary.reader.reader.domain.ReaderEngineOpener
import com.secondpasslibrary.reader.reader.domain.ReaderViewport
import com.secondpasslibrary.reader.reader.lifecycle.ReaderPositionRetentionController
import com.secondpasslibrary.reader.reader.readium.annotations.ReadiumReaderAnnotationDecorations
import com.secondpasslibrary.reader.reader.readium.annotations.ReadiumSelectionEvents
import com.secondpasslibrary.reader.reader.readium.annotations.ReadiumVisiblePageBookmarks
import com.secondpasslibrary.reader.reader.readium.annotations.SELECTION_JAVASCRIPT_INTERFACE
import com.secondpasslibrary.reader.reader.readium.cfi.ReadiumCfiJavascriptRuntime
import com.secondpasslibrary.reader.reader.readium.cfi.ReadiumCfiNavigatorBinding
import com.secondpasslibrary.reader.reader.readium.cfi.ReadiumEpubCfiNavigator
import com.secondpasslibrary.reader.reader.readium.viewport.ReadiumNavigatorOperationLane
import com.secondpasslibrary.reader.reader.readium.viewport.ReadiumPublicationNavigatorBinding
import com.secondpasslibrary.reader.reader.readium.viewport.ReadiumReaderHudEvents
import com.secondpasslibrary.reader.reader.readium.viewport.ReadiumReaderViewport
import com.secondpasslibrary.reader.reader.readium.viewport.ReadiumViewportMovements
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.css.Length
import org.readium.r2.navigator.epub.css.RsProperties
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.asset.Asset
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser

@OptIn(ExperimentalReadiumApi::class)
private class ReadiumReaderEngine(
    private val publication: Publication,
    packageDocument: EpubPackageDocument,
    context: Context,
    initialAppearance: ReaderAppearance
) : ReaderEngine {
    private val navigatorFactory = EpubNavigatorFactory(publication)
    private val navigatorOperations = ReadiumNavigatorOperationLane()
    private val cfiBinding = ReadiumCfiNavigatorBinding(ReadiumCfiJavascriptRuntime(context))
    private val publicationBinding = ReadiumPublicationNavigatorBinding(navigatorOperations)
    private val appearanceController = ReadiumReaderAppearanceController(
        initialAppearance,
        context.resources.configuration.readerViewportOrientation()
    )
    private val movements = ReadiumViewportMovements()
    private val selections = ReadiumSelectionEvents(cfiBinding)
    private val readiumCfiNavigator = ReadiumEpubCfiNavigator(
        binding = cfiBinding,
        packageDocument = packageDocument,
        readingOrder = publication.readingOrder,
        operations = navigatorOperations
    )
    private val decorations = ReadiumReaderAnnotationDecorations(readiumCfiNavigator)
    private val positionRetentionController = ReaderPositionRetentionController(
        navigator = readiumCfiNavigator,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
        suppressMovementCapture = movements::suppressSettledMovement
    )
    private val hud = ReadiumReaderHudEvents(
        onPageChanged = {
            movements.pageChanged()
            positionRetentionController.captureAfterViewportMovement()
        },
        onDocumentLoaded = {
            selections.documentLoaded()
            decorations.documentLoaded()
        }
    )
    private val visibleBookmarks = ReadiumVisiblePageBookmarks(readiumCfiNavigator, hud)

    override val viewport: ReaderViewport = ReadiumReaderViewport(
        fragmentFactory = {
            navigatorFactory.createFragmentFactory(
                initialLocator = null,
                initialPreferences = appearanceController.initialPreferences(),
                paginationListener = hud.paginationListener(),
                configuration = EpubNavigatorFragment.Configuration().apply {
                    readiumCssRsProperties = readerCssProperties()
                    registerJavascriptInterface(SELECTION_JAVASCRIPT_INTERFACE) {
                        selections.javascriptInterface()
                    }
                }
            )
        },
        cfiBinding = cfiBinding,
        publicationBinding = publicationBinding,
        appearanceController = appearanceController,
        movements = movements,
        hudEvents = hud,
        selectionEvents = selections,
        annotationDecorations = decorations,
        positionRetention = positionRetentionController
    )
    override val cfiNavigator = readiumCfiNavigator
    override val annotationDecorations = decorations
    override val visiblePageBookmarks = visibleBookmarks
    override val viewportMovements = movements
    override val selectionEvents = selections
    override val hudEvents = hud
    override val positionRetention = positionRetentionController
    override val appearance = appearanceController
    override val tableOfContents = ReadiumReaderTableOfContents(
        links = publication.tableOfContents,
        readingOrder = publication.readingOrder,
        binding = publicationBinding
    )

    override fun close() {
        positionRetentionController.close()
        movements.close()
        selections.close()
        hud.close()
        decorations.close()
        navigatorOperations.close()
        readiumCfiNavigator.close()
        publicationBinding.close()
        appearanceController.close()
        publication.close()
    }
}

@OptIn(ExperimentalReadiumApi::class)
internal fun readerCssProperties(): RsProperties = RsProperties(
    maxLineLength = Length.Rem(READER_MAX_LINE_LENGTH_REM),
    pageGutter = Length.Px(READER_PAGE_GUTTER_PX)
)

private const val READER_MAX_LINE_LENGTH_REM = 68.0
private const val READER_PAGE_GUTTER_PX = 32.0

private fun Configuration.readerViewportOrientation(): ReaderViewportOrientation =
    if (orientation == Configuration.ORIENTATION_PORTRAIT) {
        ReaderViewportOrientation.PORTRAIT
    } else {
        ReaderViewportOrientation.LANDSCAPE
    }

internal fun interface ReadiumNavigatorFragmentFactory {
    fun create(): FragmentFactory
}

@Singleton
internal class ReadiumReaderEngineOpener @Inject constructor(
    @ApplicationContext private val context: Context
) : ReaderEngineOpener {
    private val httpClient = DefaultHttpClient()
    private val assetRetriever = AssetRetriever(context.contentResolver, httpClient)
    private val publicationOpener = PublicationOpener(
        DefaultPublicationParser(context, httpClient, assetRetriever, pdfFactory = null)
    )
    private val packageResolver = ZipEpubPackageResolver()

    override suspend fun open(file: File): ReaderEngine = open(file, ReaderAppearance())

    override suspend fun open(file: File, initialAppearance: ReaderAppearance): ReaderEngine {
        var pendingAsset: Asset? = null
        var pendingPublication: Publication? = null
        var pendingEngine: ReaderEngine? = null
        try {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val packageDocument = packageResolver.resolve(file)
                    val assetResult = assetRetriever.retrieve(file)
                    val asset = assetResult.getOrNull()
                        ?: failReaderEngineOpen(
                            assetResult.failureOrNull()?.message
                                ?: "The EPUB asset could not be read."
                        )
                    pendingAsset = asset
                    val publicationResult =
                        publicationOpener.open(asset, allowUserInteraction = false)
                    val publication = publicationResult.getOrNull()
                        ?: failReaderEngineOpen(
                            publicationResult.failureOrNull()?.message
                                ?: "The EPUB could not be opened."
                        )
                    pendingPublication = publication
                    pendingAsset = null
                    if (!publication.conformsTo(Publication.Profile.EPUB)) {
                        failReaderEngineOpen("The Book asset is not an EPUB.")
                    }
                    ReadiumReaderEngine(
                        publication,
                        packageDocument,
                        context,
                        initialAppearance
                    ).also {
                        pendingEngine = it
                        pendingPublication = null
                    }
                }
            }
            val engine = result.getOrElse { failure ->
                throw failure.toReaderEngineOpenException()
            }
            pendingEngine = null
            return engine
        } finally {
            pendingEngine?.close()
            pendingPublication?.close()
            pendingAsset?.close()
        }
    }
}

private fun failReaderEngineOpen(message: String): Nothing =
    throw ReaderEngineOpenException(message)

private fun Throwable.toReaderEngineOpenException(): ReaderEngineOpenException = when (this) {
    is CancellationException -> throw this
    is ReaderEngineOpenException -> this
    else -> ReaderEngineOpenException("The EPUB could not be opened.", this)
}
