enum class ArchitectureSourceKind {
    PRODUCTION,
    AUTHORED_JAVASCRIPT
}

data class ArchitectureSource(
    val path: String,
    val content: String,
    val kind: ArchitectureSourceKind
)

data class ArchitectureViolation(
    val rule: String,
    val path: String,
    val message: String
) {
    fun render(): String = "$path: $message"
}

/** Pure evaluator for repository architecture constraints. */
object ArchitectureBoundaryEvaluator {
    fun evaluate(sources: Iterable<ArchitectureSource>): List<ArchitectureViolation> =
        sources.flatMap(::evaluateSource)
            .sortedWith(compareBy(ArchitectureViolation::path, ArchitectureViolation::rule))

    private fun evaluateSource(source: ArchitectureSource): List<ArchitectureViolation> {
        val path = source.path.replace('\\', '/')
        val content = source.content
        if (source.kind == ArchitectureSourceKind.AUTHORED_JAVASCRIPT) {
            return FORBIDDEN_MOVEMENT_CALLS.mapNotNull { call ->
                violationIf(call in content, "reader.raw-movement", path, "forbidden raw movement $call")
            }
        }

        val violations = mutableListOf<ArchitectureViolation>()
        val isReaderFeature = READER_FEATURE_PATH in path
        val isReadiumAdapter = READIUM_ADAPTER_PATH in path
        val isConnectionFeature = CONNECTION_FEATURE_PATH in path
        val isLibraryFeature = LIBRARY_FEATURE_PATH in path
        val isAppSource = path.startsWith(APP_SOURCE_PATH)
        val isSdkSource = path.startsWith(SDK_SOURCE_PATH)
        val isSdkTransport = SDK_TRANSPORT_PATH in path
        val isAnonymousSdkTransport = path.endsWith(SDK_ANONYMOUS_TRANSPORT_PATH)

        violations.addIf(
            isAppSource && SDK_TRANSPORT_IMPORTS.any { it in content },
            "transport.app-import",
            path,
            "app imports raw HTTP transport"
        )
        violations.addIf(
            isSdkSource && !isSdkTransport && "HttpHeaders.Authorization" in content,
            "transport.bearer-attachment",
            path,
            "bearer attachment outside the SDK transport seam"
        )
        violations.addIf(
            isSdkSource && !isSdkTransport && !isAnonymousSdkTransport &&
                "import io.ktor.client.statement.HttpResponse" in content,
            "transport.raw-response",
            path,
            "raw HttpResponse outside the SDK transport seam"
        )
        violations.addIf(
            !isReadiumAdapter && ("CfiProtocol" in content || "CfiRuntimeMethod" in content),
            "reader.cfi-protocol",
            path,
            "CFI wire protocol outside the Readium adapter"
        )
        val isConnectionPresentation = isConnectionFeature &&
            ("@Composable" in content || path.endsWith("ConnectionUiState.kt"))
        violations.addIf(
            isConnectionPresentation && PAIRING_PROTOCOL_MARKERS.any { it in content },
            "connection.pairing-presentation",
            path,
            "Connection presentation exposes pairing protocol details"
        )
        violations.addIf(
            !isReadiumAdapter && "import org.readium." in content,
            "reader.readium-import",
            path,
            "Readium import outside the Reader adapter"
        )
        violations.addIf(
            !isReadiumAdapter && "import android.webkit." in content,
            "reader.webview-type",
            path,
            "WebView type outside the Reader adapter"
        )
        violations.addIf(
            !isReaderFeature && "reader.cfi.EpubCfi" in content,
            "reader.cfi-contract",
            path,
            "app-owned CFI contract referenced outside the Reader feature"
        )
        violations.addIf(
            isReadiumAdapter && REFLECTION_MARKERS.any { it in content },
            "reader.readium-reflection",
            path,
            "reflection is forbidden at the Readium boundary"
        )
        violations.addIf(
            isConnectionFeature && CONNECTION_LATERAL_IMPORTS.any { it in content },
            "connection.feature-storage",
            path,
            "Connection imports feature-owned account storage"
        )
        violations.addIf(
            isLibraryFeature && LIBRARY_LATERAL_IMPORTS.any { it in content },
            "library.lateral-import",
            path,
            "Library imports another feature's account storage"
        )
        return violations
    }

    private fun MutableList<ArchitectureViolation>.addIf(
        condition: Boolean,
        rule: String,
        path: String,
        message: String
    ) {
        violationIf(condition, rule, path, message)?.let(::add)
    }

    private fun violationIf(
        condition: Boolean,
        rule: String,
        path: String,
        message: String
    ): ArchitectureViolation? =
        if (condition) ArchitectureViolation(rule, path, message) else null

    private const val READER_FEATURE_PATH = "/com/secondpasslibrary/reader/reader/"
    private const val READIUM_ADAPTER_PATH = "${READER_FEATURE_PATH}readium/"
    private const val CONNECTION_FEATURE_PATH = "/com/secondpasslibrary/reader/connection/"
    private const val LIBRARY_FEATURE_PATH = "/com/secondpasslibrary/reader/library/"
    private const val APP_SOURCE_PATH = "app/src/"
    private const val SDK_SOURCE_PATH = "spl-client/src/"
    private const val SDK_TRANSPORT_PATH = "/client/internal/transport/"
    private const val SDK_ANONYMOUS_TRANSPORT_PATH = "/client/KtorSecondPassClient.kt"
    private val SDK_TRANSPORT_IMPORTS = listOf("import io.ktor.", "import okhttp3.")
    private val PAIRING_PROTOCOL_MARKERS = listOf(
        "PairingRequest",
        "PairingStatus",
        "PairingConsumption",
        "pollUrl",
        "consumeUrl"
    )
    private val CONNECTION_LATERAL_IMPORTS = listOf(
        "import com.secondpasslibrary.reader.home.",
        "import com.secondpasslibrary.reader.reader."
    )
    private val LIBRARY_LATERAL_IMPORTS = listOf(
        "import com.secondpasslibrary.reader.home.",
        "import com.secondpasslibrary.reader.reader."
    )
    private val REFLECTION_MARKERS =
        listOf("java.lang.reflect", "getDeclaredField(", "getDeclaredMethod(")
    private val FORBIDDEN_MOVEMENT_CALLS = listOf("scrollIntoView(", "window.scrollTo(")
}
